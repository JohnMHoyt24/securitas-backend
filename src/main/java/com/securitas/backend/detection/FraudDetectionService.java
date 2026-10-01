package com.securitas.backend.detection;

import com.securitas.backend.ai.GeminiForensicService;
import com.securitas.backend.domain.Alert;
import com.securitas.backend.domain.AlertRepository;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Expression;
import org.neo4j.cypherdsl.core.NamedPath;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Relationship;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.SymbolicName;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Scans the Neo4j transaction graph for laundering-shaped patterns (cycles, fan-in,
 * fan-out) and turns new matches into Alert rows in Postgres. Dedupes by hashing the
 * pattern type + sorted account set so re-scans don't re-flag the same ring. Queries
 * are built with Cypher-DSL rather than hand-written Cypher strings.
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private static final Renderer RENDERER = Renderer.getDefaultRenderer();
    private static final int MIN_FAN_DEGREE = 5;

    private final Neo4jClient neo4jClient;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final GeminiForensicService geminiForensicService;

    public FraudDetectionService(Neo4jClient neo4jClient, AlertRepository alertRepository, ObjectMapper objectMapper,
                                  GeminiForensicService geminiForensicService) {
        this.neo4jClient = neo4jClient;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
        this.geminiForensicService = geminiForensicService;
    }

    public List<Alert> scan() {
        List<FlaggedPattern> patterns = new ArrayList<>();
        patterns.addAll(runQuery("CYCLE", buildCycleStatement()));
        patterns.addAll(runQuery("FAN_IN", buildFanInStatement()));
        patterns.addAll(runQuery("FAN_OUT", buildFanOutStatement()));

        List<Alert> newAlerts = new ArrayList<>();
        for (FlaggedPattern pattern : patterns) {
            String fingerprint = fingerprint(pattern);
            if (alertRepository.existsByFingerprint(fingerprint)) {
                continue;
            }
            Alert alert = new Alert(pattern.patternType(), pattern.subgraphJson(), fingerprint);
            alert = alertRepository.save(alert);
            attachNarrative(alert);
            newAlerts.add(alert);
        }
        return newAlerts;
    }

    /** Best-effort: a Gemini outage shouldn't stop the alert itself from being persisted. */
    private void attachNarrative(Alert alert) {
        try {
            String narrative = geminiForensicService.generateNarrative(alert.getPatternType(), alert.getSubgraphJson());
            alert.setNarrative(narrative);
            alertRepository.save(alert);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Gemini narrative generation failed for alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void scheduledScan() {
        scan();
    }

    /** MATCH p=(a:Account)-[:TRANSFERRED_TO*2..6]->(a) RETURN [n IN nodes(p) | n.accountId] AS accountIds LIMIT 50 */
    private Statement buildCycleStatement() {
        Node a = Cypher.node("Account").named("a");
        Relationship cycleRel = a.relationshipTo(a, "TRANSFERRED_TO").length(2, 6);
        NamedPath path = Cypher.path("p").definedBy(cycleRel);
        SymbolicName n = Cypher.name("n");

        Expression accountIds = Cypher.listWith(n)
                .in(Cypher.nodes(path))
                .returning(Cypher.property(n, "accountId"));

        return Cypher.match(path)
                .returning(accountIds.as("accountIds"))
                .limit(50)
                .build();
    }

    /**
     * MATCH (source:Account)-[:TRANSFERRED_TO]->(mule:Account)
     * WITH mule, collect(DISTINCT source.accountId) AS sources
     * WHERE size(sources) >= 5
     * RETURN sources + [mule.accountId] AS accountIds
     */
    private Statement buildFanInStatement() {
        Node source = Cypher.node("Account").named("source");
        Node mule = Cypher.node("Account").named("mule");
        Relationship rel = source.relationshipTo(mule, "TRANSFERRED_TO");
        SymbolicName sources = Cypher.name("sources");

        return Cypher.match(rel)
                .with(mule, Cypher.collectDistinct(source.property("accountId")).as(sources))
                .where(Cypher.size(sources).gte(Cypher.literalOf(MIN_FAN_DEGREE)))
                .returning(sources.add(Cypher.listOf(mule.property("accountId"))).as("accountIds"))
                .build();
    }

    /**
     * MATCH (source:Account)-[:TRANSFERRED_TO]->(dest:Account)
     * WITH source, collect(DISTINCT dest.accountId) AS destinations
     * WHERE size(destinations) >= 5
     * RETURN destinations + [source.accountId] AS accountIds
     */
    private Statement buildFanOutStatement() {
        Node source = Cypher.node("Account").named("source");
        Node dest = Cypher.node("Account").named("dest");
        Relationship rel = source.relationshipTo(dest, "TRANSFERRED_TO");
        SymbolicName destinations = Cypher.name("destinations");

        return Cypher.match(rel)
                .with(source, Cypher.collectDistinct(dest.property("accountId")).as(destinations))
                .where(Cypher.size(destinations).gte(Cypher.literalOf(MIN_FAN_DEGREE)))
                .returning(destinations.add(Cypher.listOf(source.property("accountId"))).as("accountIds"))
                .build();
    }

    private List<FlaggedPattern> runQuery(String patternType, Statement statement) {
        Collection<Map<String, Object>> rows = neo4jClient.query(RENDERER.render(statement)).fetch().all();
        List<FlaggedPattern> patterns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> accountIds = ((List<?>) row.get("accountIds")).stream()
                    .map(Object::toString)
                    .distinct()
                    .sorted()
                    .toList();
            patterns.add(new FlaggedPattern(patternType, accountIds, toJson(patternType, accountIds)));
        }
        return patterns;
    }

    private String toJson(String patternType, List<String> accountIds) {
        return objectMapper.writeValueAsString(Map.of("patternType", patternType, "accountIds", accountIds));
    }

    private String fingerprint(FlaggedPattern pattern) {
        String raw = pattern.patternType() + ":" + String.join(",", pattern.accountIds());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
