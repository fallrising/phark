package com.example.deck.error;

import java.util.Objects;

public class ApiException extends RuntimeException {

    private final ApiErrorCode code;
    private final String detail;

    public ApiException(ApiErrorCode code) {
        this(code, null);
    }

    public ApiException(ApiErrorCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code").getDefaultDetail(), cause);
        this.code = code;
        this.detail = code.getDefaultDetail();
    }

    public ApiErrorCode getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
