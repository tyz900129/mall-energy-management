package com.wanyuecity.energy.platform.adapter;

/** Result of a platform connection test. */
public record TestConnectionResult(boolean success, String message, Integer accountCountSample) {
}
