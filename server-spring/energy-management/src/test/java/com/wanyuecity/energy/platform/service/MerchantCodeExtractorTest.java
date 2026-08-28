package com.wanyuecity.energy.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantCodeExtractorTest {

    @Test
    void extractsExactlySixDigitsFromAccountName() {
        MerchantCodeExtractor.Match match = MerchantCodeExtractor.match("3066-000001-沪上阿姨");

        assertTrue(match.found());
        assertEquals("000001", match.code());
        assertEquals("AUTO_MATCHED", match.status());
    }

    @Test
    void rejectsSixDigitSubstringOfLongerNumber() {
        MerchantCodeExtractor.Match match = MerchantCodeExtractor.match("账户-1234567-测试");

        assertFalse(match.found());
        assertEquals("NOT_FOUND", match.status());
    }

    @Test
    void marksMultipleCandidatesAsAmbiguous() {
        MerchantCodeExtractor.Match match = MerchantCodeExtractor.match("000001/000002");

        assertEquals("000001", match.code());
        assertEquals("AMBIGUOUS", match.status());
    }

    @Test
    void handlesBlankInput() {
        assertFalse(MerchantCodeExtractor.match("  ").found());
        assertFalse(MerchantCodeExtractor.match(null).found());
    }
}
