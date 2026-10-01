package com.securitas.backend.ai;

import java.util.List;

record GeminiResponse(List<Candidate> candidates) {

    record Candidate(Content content) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }
}
