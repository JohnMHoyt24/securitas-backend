package com.securitas.backend.security;

public record LoginResponse(String token, String username, String role) {
}
