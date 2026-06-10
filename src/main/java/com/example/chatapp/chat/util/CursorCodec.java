package com.example.chatapp.chat.util;

import java.time.Instant;
import java.util.Base64;

public class CursorCodec {

    public record Cursor(Instant sentAt, Long id) {}

    public static String encodeCursor(Instant sentAt, Long id) {
        String raw = sentAt.toString() + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split("_");
            if (parts.length != 2) return null;
            return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            return null; // invalid cursor
        }
    }
}
