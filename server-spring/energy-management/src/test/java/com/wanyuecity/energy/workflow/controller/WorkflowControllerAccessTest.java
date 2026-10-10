package com.wanyuecity.energy.workflow.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowControllerAccessTest {
    @Test
    void applicantRemainsOwnOnlyWhenViewerRoleIsAlsoPresent() {
        assertTrue(WorkflowController.ownOnly(auth("ROLE_APPLICANT", "ROLE_VIEWER", "workflow.view", "workflow.create")));
    }

    @Test
    void systemAdministratorCanAccessAllWorkflows() {
        assertFalse(WorkflowController.ownOnly(auth("ROLE_SYSTEM_ADMIN", "workflow.view", "workflow.create")));
    }

    @Test
    void individuallyGrantedCreatePermissionIsOwnOnly() {
        assertTrue(WorkflowController.ownOnly(auth("ROLE_VIEWER", "workflow.view", "workflow.create")));
    }

    private UsernamePasswordAuthenticationToken auth(String... values) {
        return new UsernamePasswordAuthenticationToken("tester", null,
                Arrays.stream(values).map(SimpleGrantedAuthority::new).toList());
    }
}
