package com.wanyuecity.energy.workflow.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SmsPhoneComparisonTest {
    @Test void equivalentNumbersStillRequireAnExplicitChoice() {
        String source=SmsPhoneComparison.snapshot(List.of("+86 138-0013-8000", "13800138000"));
        assertEquals("13800138000",source);
        assertThrows(RuntimeException.class,()->SmsPhoneComparison.validate("13800138000",source,source,null));
        assertFalse(SmsPhoneComparison.validate("13800138000",source,source,false));
        assertTrue(SmsPhoneComparison.validate("13800138000",source,source,true));
    }
    @Test void changedNumberRequiresAnExplicitChoiceAgainstCurrentSource() {
        assertThrows(RuntimeException.class,()->SmsPhoneComparison.validate("13900139000","13800138000","13800138000",null));
        assertThrows(RuntimeException.class,()->SmsPhoneComparison.validate("13900139000","13700137000","13800138000",true));
        assertTrue(SmsPhoneComparison.validate("13900139000","13800138000","13800138000",true));
        assertFalse(SmsPhoneComparison.validate("13900139000","13800138000","13800138000",false));
    }
    @Test void missingOrMultipleSourceNumbersRequireConfirmation() {
        assertTrue(SmsPhoneComparison.differs("13900139000", ""));
        assertTrue(SmsPhoneComparison.differs("13900139000", SmsPhoneComparison.snapshot(List.of("13800138000","13900139000"))));
        assertTrue(SmsPhoneComparison.validate("13900139000","","",true));
    }
}
