package com.securitas.backend.ingest;

import com.securitas.backend.domain.Account;
import com.securitas.backend.domain.AccountRepository;
import com.securitas.backend.domain.AlertRepository;
import com.securitas.backend.domain.Transaction;
import com.securitas.backend.domain.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Relationship;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.SymbolicName;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Imports an IBM-AML-schema CSV into both Postgres (via JPA repositories) and Neo4j
 * (via a Cypher-DSL-built statement). Sampling keeps every Is Laundering=1 row and
 * fills the rest of the budget with normal traffic, so a cutoff needed to stay within
 * Aura/Neon free-tier limits never silently drops a planted laundering pattern.
 */
@Service
public class AmlCsvImportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final Renderer RENDERER = Renderer.getDefaultRenderer();

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final Neo4jClient neo4jClient;

    public AmlCsvImportService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                                AlertRepository alertRepository, Neo4jClient neo4jClient) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.neo4jClient = neo4jClient;
    }

    public record ImportOptions(int maxAccounts, int maxTransactions) {
        public static ImportOptions unlimited() {
            return new ImportOptions(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    public record ImportResult(int accountsImported, int transactionsImported) {
    }

    private record ParsedRow(
            String fromBank, String fromAccount,
            String toBank, String toAccount,
            BigDecimal amount, String currency,
            String paymentFormat, Timestamp occurredAt,
            boolean isLaundering) {
    }

    public ImportResult importCsv(Path csvPath, ImportOptions options) throws IOException {
        List<ParsedRow> rows = parse(csvPath);
        List<ParsedRow> sampled = sample(rows, options);

        Map<String, String> accountIdToBank = new LinkedHashMap<>();
        for (ParsedRow row : sampled) {
            accountIdToBank.put(row.fromAccount(), row.fromBank());
            accountIdToBank.put(row.toAccount(), row.toBank());
        }

        saveAccounts(accountIdToBank);
        saveTransactions(sampled);
        writeToNeo4j(sampled);

        return new ImportResult(accountIdToBank.size(), sampled.size());
    }

    /** Clears previously imported data from both databases, for re-seeding sample data during development. */
    public void resetImportedData() {
        alertRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();

        Node account = Cypher.node("Account").named("n");
        Statement deleteAll = Cypher.match(account).detachDelete(account).build();
        neo4jClient.query(RENDERER.render(deleteAll)).run();
    }

    private List<ParsedRow> parse(Path csvPath) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(csvPath);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                rows.add(new ParsedRow(
                        record.get("From Bank"),
                        record.get("From Account"),
                        record.get("To Bank"),
                        record.get("To Account"),
                        new BigDecimal(record.get("Amount Paid")),
                        record.get("Payment Currency"),
                        record.get("Payment Format"),
                        Timestamp.valueOf(LocalDateTime.parse(record.get("Timestamp"), TIMESTAMP_FORMAT)),
                        "1".equals(record.get("Is Laundering").trim())
                ));
            }
        }
        return rows;
    }

    private List<ParsedRow> sample(List<ParsedRow> rows, ImportOptions options) {
        if (rows.size() <= options.maxTransactions()) {
            return capByAccountCount(rows, options.maxAccounts());
        }
        List<ParsedRow> laundering = new ArrayList<>();
        List<ParsedRow> normal = new ArrayList<>();
        for (ParsedRow row : rows) {
            (row.isLaundering() ? laundering : normal).add(row);
        }
        Collections.shuffle(normal, new Random(42));
        int remainingBudget = Math.max(0, options.maxTransactions() - laundering.size());
        List<ParsedRow> selected = new ArrayList<>(laundering);
        selected.addAll(normal.subList(0, Math.min(remainingBudget, normal.size())));
        return capByAccountCount(selected, options.maxAccounts());
    }

    private List<ParsedRow> capByAccountCount(List<ParsedRow> rows, int maxAccounts) {
        List<ParsedRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(ParsedRow::isLaundering).reversed());

        Set<String> accounts = new LinkedHashSet<>();
        List<ParsedRow> kept = new ArrayList<>();
        for (ParsedRow row : ordered) {
            Set<String> candidate = new HashSet<>(accounts);
            candidate.add(row.fromAccount());
            candidate.add(row.toAccount());
            if (candidate.size() > maxAccounts && !row.isLaundering()) {
                continue;
            }
            accounts.addAll(candidate);
            kept.add(row);
        }
        return kept;
    }

    private void saveAccounts(Map<String, String> accountIdToBank) {
        Set<String> existingIds = new HashSet<>();
        accountRepository.findAllById(accountIdToBank.keySet()).forEach(a -> existingIds.add(a.getAccountId()));

        List<Account> newAccounts = accountIdToBank.entrySet().stream()
                .filter(e -> !existingIds.contains(e.getKey()))
                .map(e -> new Account(e.getKey(), e.getValue()))
                .toList();
        accountRepository.saveAll(newAccounts);
    }

    private void saveTransactions(List<ParsedRow> rows) {
        List<Transaction> transactions = rows.stream()
                .map(r -> new Transaction(
                        r.fromAccount(), r.toAccount(), r.amount(), r.currency(),
                        r.paymentFormat(), r.occurredAt().toInstant().atOffset(ZoneOffset.UTC), r.isLaundering()
                ))
                .toList();
        transactionRepository.saveAll(transactions);
    }

    private void writeToNeo4j(List<ParsedRow> rows) {
        SymbolicName row = Cypher.name("row");
        Node from = Cypher.node("Account").withProperties("accountId", Cypher.property(row, "from")).named("a");
        Node to = Cypher.node("Account").withProperties("accountId", Cypher.property(row, "to")).named("b");
        Relationship transfer = from.relationshipTo(to, "TRANSFERRED_TO")
                .withProperties(
                        "amount", Cypher.property(row, "amount"),
                        "currency", Cypher.property(row, "currency"),
                        "occurredAt", Cypher.property(row, "occurredAt")
                );

        Statement statement = Cypher.unwind(Cypher.parameter("rows")).as(row)
                .merge(from)
                .merge(to)
                .create(transfer)
                .build();

        List<Map<String, Object>> params = rows.stream()
                .map(r -> Map.<String, Object>of(
                        "from", r.fromAccount(),
                        "to", r.toAccount(),
                        "amount", r.amount().doubleValue(),
                        "currency", r.currency(),
                        "occurredAt", r.occurredAt().toInstant().toString()
                ))
                .toList();

        neo4jClient.query(RENDERER.render(statement))
                .bind(params).to("rows")
                .run();
    }
}
