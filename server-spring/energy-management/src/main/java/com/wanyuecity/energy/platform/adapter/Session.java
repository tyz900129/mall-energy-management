package com.wanyuecity.energy.platform.adapter;

/** Opaque session token returned by {@link PlatformAdapter#login}. */
public record Session(String token, String cookieHeader, long expiresAtEpochMs) {
}
