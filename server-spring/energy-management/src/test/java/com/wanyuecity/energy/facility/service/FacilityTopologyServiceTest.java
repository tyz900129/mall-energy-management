package com.wanyuecity.energy.facility.service;

import com.wanyuecity.energy.core.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FacilityTopologyServiceTest {
    private final FacilityTopologyService service = new FacilityTopologyService(null, null, null);

    @Test
    void acceptsExactlyTheFourSupportedFacilitySystems() {
        assertEquals("WATER_SUPPLY", service.normalizeSystemType("water_supply"));
        assertEquals("DRAINAGE", service.normalizeSystemType("drainage"));
        assertEquals("OIL_EXHAUST", service.normalizeSystemType("oil_exhaust"));
        assertEquals("FRESH_AIR", service.normalizeSystemType("fresh_air"));
        assertThrows(BizException.class, () -> service.normalizeSystemType("POWER"));
    }

    @Test
    void createsStablePublicRouteSegmentsForQrCodes() {
        assertEquals("water-supply", service.routeSegment("WATER_SUPPLY"));
        assertEquals("drainage", service.routeSegment("DRAINAGE"));
        assertEquals("oil-exhaust", service.routeSegment("OIL_EXHAUST"));
        assertEquals("fresh-air", service.routeSegment("FRESH_AIR"));
    }
}
