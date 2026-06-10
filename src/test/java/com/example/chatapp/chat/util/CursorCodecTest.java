package com.example.chatapp.chat.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CursorCodecTest {

    @Test
    void shouldEncodeAndDecodeCursor() {
        Instant sentAt = Instant.parse("2023-01-01T10:00:00Z");
        Long messageId = 123L;
        
        String cursor = CursorCodec.encodeCursor(sentAt, messageId);
        assertNotNull(cursor);
        
        CursorCodec.Cursor decoded = CursorCodec.decodeCursor(cursor);
        assertEquals(sentAt, decoded.sentAt());
        assertEquals(messageId, decoded.id());
    }
}
