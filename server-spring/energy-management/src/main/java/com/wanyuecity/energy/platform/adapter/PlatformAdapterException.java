package com.wanyuecity.energy.platform.adapter;

/**
 * Thrown by {@link PlatformAdapter} for any failure that prevents returning a
 * complete result (network, auth, schema mismatch, etc.). The cause is
 * preserved but never contains the password in its message.
 */
public class PlatformAdapterException extends RuntimeException {

    private final String code;

    public PlatformAdapterException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PlatformAdapterException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

