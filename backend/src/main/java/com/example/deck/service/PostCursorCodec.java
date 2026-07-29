package com.example.deck.service;

import com.example.deck.model.PostCursor;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class PostCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode(PostCursor cursor) {
        String payload = cursor.createdAt().getEpochSecond() + ":" + cursor.id();
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public PostCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.contains("=")
                || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw invalidCursor();
        }

        try {
            byte[] bytes = DECODER.decode(encoded);
            String payload = new String(bytes, StandardCharsets.UTF_8);
            String[] parts = payload.split(":", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }

            PostCursor cursor = new PostCursor(
                    Instant.ofEpochSecond(Long.parseLong(parts[0])),
                    Long.parseLong(parts[1]));
            if (!encode(cursor).equals(encoded)) {
                throw invalidCursor();
            }
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid cursor");
    }
}
