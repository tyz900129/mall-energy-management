package com.wanyuecity.energy.platform.adapter;

import com.wanyuecity.energy.platform.dto.PlatformAccount;
import com.wanyuecity.energy.platform.dto.PlatformAccountDeviceRelation;
import com.wanyuecity.energy.platform.dto.PlatformDevice;

import java.util.List;

/**
 * Read-only abstraction over the source energy platform. The implementation is
 * responsible for authentication, cookie management, pagination, and request
 * retries; consumers only see the typed DTOs.
 *
 * <p>All methods are blocking and must throw {@link PlatformAdapterException}
 * on any failure (network, auth, schema, etc.) — never return partial data.</p>
 */
public interface PlatformAdapter {

    /**
     * Authenticate against the platform. Implementations may cache the session
     * but must support re-login when {@link #testConnection()} is called.
     */
    Session login(String baseUrl, String username, String password);

    /**
     * Lightweight connectivity probe. Must call {@link #login(String, String, String)}
     * first, then hit a read-only endpoint and verify the configured {@code spaceId}
     * is accessible.
     */
    TestConnectionResult testConnection(String baseUrl, String username, String password, String spaceId);

    /** Fetch all accounts under the given space. Paginates internally. */
    List<PlatformAccount> fetchAccounts(String baseUrl, String sessionToken, String spaceId);

    /** Fetch all devices under the given space. Paginates internally. */
    List<PlatformDevice> fetchDevices(String baseUrl, String sessionToken, String spaceId);

    /**
     * Fetch account-device binding relations. The default strategy is to use the
     * account detail endpoint; implementations may override for efficiency.
     */
    List<PlatformAccountDeviceRelation> fetchAccountDeviceRelations(String baseUrl, String sessionToken, String spaceId);
}
