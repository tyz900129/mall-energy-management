package com.wanyuecity.energy.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Unified API response envelope.
 *
 * <pre>
 * { "code": 0, "message": "ok", "data": ..., "traceId": "..." }
 * </pre>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FAILURE = 1;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_SERVER_ERROR = 500;

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(CODE_SUCCESS);
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(CODE_FAILURE, message);
    }
}
