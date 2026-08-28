package com.wanyuecity.energy.workflow.service;

import com.wanyuecity.energy.core.BizException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowStateMachineTest {
    private final WorkflowStateMachine machine = new WorkflowStateMachine();

    @Test void allowsNewMerchantMainPath() {
        assertDoesNotThrow(() -> machine.requireTransition("DRAFT", "FIELD_CHECK_PENDING"));
        assertDoesNotThrow(() -> machine.requireTransition("FIELD_CHECK_PENDING", "FIELD_CHECKING"));
        assertDoesNotThrow(() -> machine.requireTransition("PLATFORM_VERIFYING", "VERIFIED"));
        assertDoesNotThrow(() -> machine.requireTransition("VERIFIED", "COMPLETED"));
    }

    @Test void rejectsSkippingFieldVerification() {
        BizException ex = assertThrows(BizException.class,
                () -> machine.requireTransition("DRAFT", "WAITING_PLATFORM_CHANGE"));
        assertTrue(ex.getMessage().contains("invalid workflow transition"));
    }
}
