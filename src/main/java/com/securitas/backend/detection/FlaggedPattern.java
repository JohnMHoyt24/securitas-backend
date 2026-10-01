package com.securitas.backend.detection;

import java.util.List;

public record FlaggedPattern(String patternType, List<String> accountIds, String subgraphJson) {
}
