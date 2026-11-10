package com.wanyuecity.energy.core;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler. Maps exceptions to {@link ApiResponse} so the API surface
 * stays uniform across the application.
 *
 * <p>2026-11-10 复核说明：本文件在 baseline/deployed-20261109 上按线上 2026-11-09 war 内
 * {@code GlobalExceptionHandler.class} 的 javap 输出补齐了 {@code handleResponseStatus} /
 * {@code handleNoResourceFound} / {@code buildStatusResponse}。行为约定（与线上一致）：
 * HTTP 状态码原样保留（400/401/403/404/405 等 4xx 不降级）；4xx 的响应体 code 仅
 * 401/403/404/400 显式对应，其余 4xx 记为 400；5xx 的响应体文案固定为
 * "Internal server error"，不回报异常内部信息，异常对象只进日志。
 * 仍未确认：线上日志实现为 Lombok {@code @Generated} + LoggerFactory（与本文件的
 * {@code @Slf4j} 等价），其余差异仅存在于反编译产生的字节码细节，不影响行为。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest req) {
        log.warn("biz exception on {} {}: code={}, msg={}", req.getMethod(), req.getRequestURI(), ex.getCode(), ex.getMessage());
        HttpStatus status = switch (ex.getCode()) {
            case ApiResponse.CODE_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ApiResponse.CODE_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ApiResponse.CODE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ApiResponse.CODE_BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, msg));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ApiResponse.CODE_BAD_REQUEST, "HTTP method not allowed"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        // Never echo the credential-related message; keep it generic.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, "Invalid username or password"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthn(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ApiResponse.CODE_UNAUTHORIZED, "Authentication failed"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ApiResponse.CODE_FORBIDDEN, "Access denied"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        return buildStatusResponse(HttpStatus.resolve(ex.getStatusCode().value()), ex.getReason(), req, ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
        return buildStatusResponse(HttpStatus.NOT_FOUND, null, req, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex, HttpServletRequest req) {
        // Log full stack only for the unknown bucket; BizException / validation are logged at warn above.
        log.error("unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ApiResponse.CODE_SERVER_ERROR, "Internal server error"));
    }

    private ResponseEntity<ApiResponse<Void>> buildStatusResponse(HttpStatus status, String reason, HttpServletRequest req, Exception ex) {
        HttpStatus httpStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        int code = switch (httpStatus) {
            case UNAUTHORIZED -> ApiResponse.CODE_UNAUTHORIZED;
            case FORBIDDEN -> ApiResponse.CODE_FORBIDDEN;
            case NOT_FOUND -> ApiResponse.CODE_NOT_FOUND;
            case BAD_REQUEST -> ApiResponse.CODE_BAD_REQUEST;
            default -> httpStatus.is4xxClientError() ? ApiResponse.CODE_BAD_REQUEST : ApiResponse.CODE_SERVER_ERROR;
        };
        String message = httpStatus.is5xxServerError()
                ? "Internal server error"
                : (reason == null || reason.isBlank() ? "Request rejected" : reason);
        if (httpStatus.is5xxServerError()) {
            log.error("{} {} failed with {}: {}", req.getMethod(), req.getRequestURI(), httpStatus.value(), reason, ex);
        } else {
            log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), httpStatus.value(), message);
        }
        return ResponseEntity.status(httpStatus).body(ApiResponse.fail(code, message));
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
