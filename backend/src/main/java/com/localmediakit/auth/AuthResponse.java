package com.localmediakit.auth;

public record AuthResponse(String token, String email, String displayName) {
}
