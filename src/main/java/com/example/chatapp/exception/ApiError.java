package com.example.chatapp.exception;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
