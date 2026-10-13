package com.securitas.backend.metrics;

import com.securitas.backend.domain.Account;
import com.securitas.backend.domain.AccountRepository;
import com.securitas.backend.domain.Transaction;
import com.securitas.backend.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Aggregates transaction/account data for dashboard chart widgets. Aggregation happens
 * in plain Java over repository results (dataset is capped by the importer's sampling,
 * see AmlCsvImportService) rather than a hand-written GROUP BY query string.
 */
@Service
public class MetricsService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public MetricsService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    public record DayCount(String date, long count) {
    }

    public record DayAmount(String date, BigDecimal amount) {
    }

    public record CategoryCount(String category, long count) {
    }

    public List<DayCount> transactionsByDay() {
        Map<LocalDate, Long> byDay = transactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getOccurredAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()
                ));
        return byDay.entrySet().stream()
                .map(e -> new DayCount(e.getKey().toString(), e.getValue()))
                .toList();
    }

    public List<DayAmount> transactionVolumeByDay() {
        Map<LocalDate, BigDecimal> byDay = transactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getOccurredAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
        return byDay.entrySet().stream()
                .map(e -> new DayAmount(e.getKey().toString(), e.getValue()))
                .toList();
    }

    public List<CategoryCount> transactionsByPaymentFormat() {
        return countBy(transactionRepository.findAll(), Transaction::getPaymentFormat);
    }

    public List<CategoryCount> accountsByBank() {
        return countBy(accountRepository.findAll(), Account::getBankId);
    }

    private <T> List<CategoryCount> countBy(List<T> items, java.util.function.Function<T, String> classifier) {
        Map<String, Long> counts = items.stream()
                .collect(Collectors.groupingBy(classifier, Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> new CategoryCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoryCount::category))
                .toList();
    }
}
