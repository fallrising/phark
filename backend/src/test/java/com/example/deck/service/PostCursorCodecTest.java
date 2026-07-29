package com.example.deck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.deck.model.PostCursor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostCursorCodecTest {

    private final PostCursorCodec codec = new PostCursorCodec();

    @Test
    void cursorRoundTripsAsUrlSafeBase64WithoutPadding() {
        PostCursor cursor = new PostCursor(Instant.parse("2026-07-29T12:34:56Z"), 42);

        String encoded = codec.encode(cursor);

        assertThat(encoded).matches("[A-Za-z0-9_-]+").doesNotContain("=");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "not*base64",
            "bm90LWN1cnNvcg",
            "MTow",
            "MTotMQ",
            "MTox="
    })
    void malformedCursorIsRejected(String encoded) {
        assertThatThrownBy(() -> codec.decode(encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid cursor");
    }
}
