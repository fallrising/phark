package com.example.deck.error;

import com.example.deck.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiProblemWriter problemWriter;

    public ApiExceptionHandler(ApiProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiErrorCode code = ex.getCode();
        ProblemDetail problem = problemWriter.build(
                code,
                request.getRequestURI(),
                ex.getDetail(),
                RequestIdFilter.resolveRequestId(request));
        return new ResponseEntity<>(problem, code.getHttpStatus());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.VALIDATION_FAILED;
        List<ApiViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiViolation(fe.getField(), fe.getDefaultMessage()))
                .sorted()
                .toList();
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        problem.setProperty("violations", violations);
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ApiErrorCode code = ApiErrorCode.MALFORMED_REQUEST;
        if (ex instanceof MethodArgumentTypeMismatchException mex) {
            code = switch (mex.getName()) {
                case "limit" -> ApiErrorCode.INVALID_LIMIT;
                case "postId" -> ApiErrorCode.INVALID_POST_ID;
                default -> ApiErrorCode.MALFORMED_REQUEST;
            };
        }
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.MALFORMED_REQUEST;
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.METHOD_NOT_ALLOWED;
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.RESOURCE_NOT_FOUND;
        ProblemDetail problem = problemWriter.build(
                code,
                getRequestPath(request),
                code.getDefaultDetail(),
                resolveRequestId(request));
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUnhandled(Exception ex, HttpServletRequest request) {
        String requestId = RequestIdFilter.resolveRequestId(request);
        log.error("Unhandled API exception [requestId={}]", requestId, ex);
        ApiErrorCode code = ApiErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = problemWriter.build(
                code,
                request.getRequestURI(),
                code.getDefaultDetail(),
                requestId);
        return new ResponseEntity<>(problem, code.getHttpStatus());
    }

    private static String getRequestPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return request.getDescription(false).replace("uri=", "");
    }

    private static String resolveRequestId(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return RequestIdFilter.resolveRequestId(swr.getRequest());
        }
        return UUID.randomUUID().toString();
    }
}
