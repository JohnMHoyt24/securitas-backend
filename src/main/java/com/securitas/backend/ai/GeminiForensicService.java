package com.securitas.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Turns a flagged subgraph into a plain-English investigation narrative via the Gemini
 * REST API. No SDK dependency — a single POST built with Spring's RestClient.
 */
@Service
public class GeminiForensicService {

    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final RestClient restClient;
    private final String apiKey;

    public GeminiForensicService(RestClient.Builder restClientBuilder,
                                  @Value("${gemini.api.key}") String apiKey,
                                  @Value("${gemini.model:gemini-3.1-flash-lite}") String model) {
        this.restClient = restClientBuilder.baseUrl(ENDPOINT_TEMPLATE.formatted(model)).build();
        this.apiKey = apiKey;
    }

    public String generateNarrative(String patternType, String subgraphJson) {
        GeminiResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(GeminiRequest.of(buildPrompt(patternType, subgraphJson)))
                .retrieve()
                .body(GeminiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini response contained no candidates");
        }
        return response.candidates().get(0).content().parts().get(0).text();
    }

    private String buildPrompt(String patternType, String subgraphJson) {
        return """
                You are a financial crimes investigator. Given the following transaction \
                subgraph flagged as a potential %s, write a concise 3-5 sentence plain-English \
                summary of why this looks suspicious, referencing the specific accounts involved.

                Subgraph:
                %s
                """.formatted(patternType, subgraphJson);
    }
}
