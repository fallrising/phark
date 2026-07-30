package com.example.deck.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
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

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiErrorCode code = ex.getCode();
        ProblemDetail problem = buildProblemDetail(code, request.getRequestURI(), ex.getDetail());
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
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
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
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.MALFORMED_REQUEST;
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.METHOD_NOT_ALLOWED;
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiErrorCode code = ApiErrorCode.RESOURCE_NOT_FOUND;
        ProblemDetail problem = buildProblemDetail(code, getRequestPath(request), code.getDefaultDetail());
        return handleExceptionInternal(ex, problem, headers, code.getHttpStatus(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllUnhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ApiErrorCode code = ApiErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = buildProblemDetail(code, request.getRequestURI(), code.getDefaultDetail());
        return new ResponseEntity<>(problem, code.getHttpStatus());
    }

    private static ProblemDetail buildProblemDetail(ApiErrorCode code, String path, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(code.getHttpStatus());
        problem.setType(code.getType());
        problem.setTitle(code.getTitle());
        problem.setDetail(detail);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code.name());
        return problem;
    }

    private static String getRequestPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return request.getDescription(false).replace("uri=", "");
    }
}
