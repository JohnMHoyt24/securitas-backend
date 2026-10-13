package com.securitas.backend.graph;

import com.securitas.backend.domain.Alert;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.Relationship;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs an alert's flagged subgraph from the live Neo4j graph, rather than storing
 * edges redundantly in Postgres — the alert row only keeps the account IDs involved
 * (subgraph_json), so this re-queries the current relationships among them.
 */
@Service
public class SubgraphQueryService {

    private static final Renderer RENDERER = Renderer.getDefaultRenderer();

    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    public SubgraphQueryService(Neo4jClient neo4jClient, ObjectMapper objectMapper) {
        this.neo4jClient = neo4jClient;
        this.objectMapper = objectMapper;
    }

    public record SubgraphNode(String id, String label, String role) {
    }

    public record SubgraphEdge(String source, String target, String type, Double amount, String currency) {
    }

    public record SubgraphResponse(List<SubgraphNode> nodes, List<SubgraphEdge> edges) {
    }

    private record StoredSubgraph(String patternType, List<String> accountIds) {
    }

    public SubgraphResponse buildSubgraph(Alert alert) {
        List<String> accountIds = objectMapper.readValue(alert.getSubgraphJson(), StoredSubgraph.class).accountIds();
        List<SubgraphEdge> edges = queryEdges(accountIds);

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        accountIds.forEach(id -> {
            inDegree.put(id, 0);
            outDegree.put(id, 0);
        });
        for (SubgraphEdge edge : edges) {
            outDegree.merge(edge.source(), 1, Integer::sum);
            inDegree.merge(edge.target(), 1, Integer::sum);
        }

        List<SubgraphNode> nodes = accountIds.stream()
                .map(id -> new SubgraphNode(id, id, roleFor(alert.getPatternType(), id, inDegree, outDegree)))
                .toList();

        return new SubgraphResponse(nodes, edges);
    }

    /**
     * Role is derived purely from degree within the subgraph, not a stored label: in our
     * detection queries a FAN_IN mule is the only node any edge points *to*, and a FAN_OUT
     * origin is the only node any edge points *from* — CYCLE has no distinguished node.
     */
    private String roleFor(String patternType, String accountId, Map<String, Integer> inDegree, Map<String, Integer> outDegree) {
        return switch (patternType) {
            case "FAN_IN" -> inDegree.get(accountId) > 0 ? "mule" : "origin";
            case "FAN_OUT" -> outDegree.get(accountId) > 0 ? "origin" : "destination";
            default -> "cycle";
        };
    }

    private List<SubgraphEdge> queryEdges(List<String> accountIds) {
        Node a = Cypher.node("Account").named("a");
        Node b = Cypher.node("Account").named("b");
        Relationship rel = a.relationshipTo(b, "TRANSFERRED_TO");

        Statement statement = Cypher.match(rel)
                .where(a.property("accountId").in(Cypher.parameter("accountIds"))
                        .and(b.property("accountId").in(Cypher.parameter("accountIds"))))
                .returning(
                        a.property("accountId").as("source"),
                        b.property("accountId").as("target"),
                        rel.property("amount").as("amount"),
                        rel.property("currency").as("currency")
                )
                .build();

        Collection<Map<String, Object>> rows = neo4jClient.query(RENDERER.render(statement))
                .bind(accountIds).to("accountIds")
                .fetch().all();

        return rows.stream()
                .map(row -> new SubgraphEdge(
                        (String) row.get("source"),
                        (String) row.get("target"),
                        "TRANSFERRED_TO",
                        row.get("amount") == null ? null : ((Number) row.get("amount")).doubleValue(),
                        (String) row.get("currency")
                ))
                .toList();
    }
}
