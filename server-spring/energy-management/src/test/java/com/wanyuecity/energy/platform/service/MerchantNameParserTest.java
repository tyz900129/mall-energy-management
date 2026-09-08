package com.wanyuecity.energy.platform.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MerchantNameParserTest {
    @Test void extractsStandardAccountName() {
        assertEquals("霸王茶姬", MerchantNameParser.extract("3066-000090-霸王茶姬", "000090"));
    }

    @Test void extractsAlternativeAccountPrefix() {
        assertEquals("添可洗地机", MerchantNameParser.extract("JD-420029-添可洗地机", "420029"));
    }

    @Test void removesLegacyStallPrefix() {
        assertEquals("王全兴", MerchantNameParser.extract("B103-王全兴", "000192"));
        assertEquals("松和路", MerchantNameParser.extract("3066-000041-B117-松和路", "000041"));
    }
}
