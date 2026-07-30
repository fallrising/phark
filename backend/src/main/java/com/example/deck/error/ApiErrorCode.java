package com.example.deck.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    VALIDATION_FAILED("urn:phark:problem:validation-failed", HttpStatus.BAD_REQUEST,
            "Validation failed", "One or more request fields are invalid."),
    INVALID_CHANNEL("urn:phark:problem:invalid-channel", HttpStatus.BAD_REQUEST,
            "Invalid channel", "Channel must be one of: home, tech, ops."),
    INVALID_LIMIT("urn:phark:problem:invalid-limit", HttpStatus.BAD_REQUEST,
            "Invalid limit", "Limit must be between 1 and 100."),
    INVALID_CURSOR("urn:phark:problem:invalid-cursor", HttpStatus.BAD_REQUEST,
            "Invalid cursor", "The provided cursor is invalid or could not be decoded."),
    INVALID_POST_ID("urn:phark:problem:invalid-post-id", HttpStatus.BAD_REQUEST,
            "Invalid post ID", "Post ID must be a positive integer."),
    MALFORMED_REQUEST("urn:phark:problem:malformed-request", HttpStatus.BAD_REQUEST,
            "Malformed request", "The request body is missing or malformed."),
    HANDLE_UNAVAILABLE("urn:phark:problem:handle-unavailable", HttpStatus.CONFLICT,
            "Handle unavailable", "The requested handle is not available."),
    INVALID_CREDENTIALS("urn:phark:problem:invalid-credentials", HttpStatus.UNAUTHORIZED,
            "Invalid credentials", "The handle or password is invalid."),
    AUTHENTICATION_REQUIRED("urn:phark:problem:authentication-required", HttpStatus.UNAUTHORIZED,
            "Authentication required", "Authentication is required for this request."),
    CSRF_TOKEN_INVALID("urn:phark:problem:csrf-token-invalid", HttpStatus.FORBIDDEN,
            "CSRF token invalid", "A valid CSRF token is required for this request."),
    ACCESS_DENIED("urn:phark:problem:access-denied", HttpStatus.FORBIDDEN,
            "Access denied", "This account is not authorized for the request."),
    POST_NOT_FOUND("urn:phark:problem:post-not-found", HttpStatus.NOT_FOUND,
            "Post not found", "The requested post does not exist."),
    RESOURCE_NOT_FOUND("urn:phark:problem:resource-not-found", HttpStatus.NOT_FOUND,
            "Resource not found", "The requested resource was not found."),
    METHOD_NOT_ALLOWED("urn:phark:problem:method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED,
            "Method not allowed", "This HTTP method is not supported for this endpoint."),
    UNSUPPORTED_MEDIA_TYPE("urn:phark:problem:unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type", "The request content type is not supported."),
    INTERNAL_ERROR("urn:phark:problem:internal-error", HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error", "An unexpected error occurred.");

    private final URI type;
    private final HttpStatus httpStatus;
    private final String title;
    private final String defaultDetail;

    ApiErrorCode(String type, HttpStatus httpStatus, String title, String defaultDetail) {
        this.type = URI.create(type);
        this.httpStatus = httpStatus;
        this.title = title;
        this.defaultDetail = defaultDetail;
    }

    public URI getType() {
        return type;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getTitle() {
        return title;
    }

    public String getDefaultDetail() {
        return defaultDetail;
    }
}
