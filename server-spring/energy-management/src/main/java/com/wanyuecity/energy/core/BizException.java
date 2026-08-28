package com.wanyuecity.energy.core;

import lombok.Getter;

/**
 * Domain-level business exception. Mapped to ApiResponse with the same {@link #getCode()}.
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ApiResponse.CODE_FAILURE;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static BizException unauthorized(String message) {
        return new BizException(ApiResponse.CODE_UNAUTHORIZED, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ApiResponse.CODE_FORBIDDEN, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ApiResponse.CODE_NOT_FOUND, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ApiResponse.CODE_BAD_REQUEST, message);
    }
}

