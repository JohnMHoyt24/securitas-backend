package com.securitas.backend.ai;

import java.util.List;

record GeminiRequest(List<Content> contents) {

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }

    static GeminiRequest of(String prompt) {
        return new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));
    }
}
