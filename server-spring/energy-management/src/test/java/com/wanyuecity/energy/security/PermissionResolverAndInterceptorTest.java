package com.wanyuecity.energy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 权限码化后的路径级校验：默认拒绝、公开路径不受影响、有码通过、无码 403。 */
class PermissionResolverAndInterceptorTest {

    private final RequestPermissionResolver resolver = new RequestPermissionResolver();
    private final PermissionAuthorizationInterceptor interceptor =
            new PermissionAuthorizationInterceptor(resolver, new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unmappedApiPathIsDeniedByDefault() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/unknown/module");
        RequestPermissionResolver.Rule rule = resolver.resolve(request);
        assertFalse(rule.publicAccess());
        assertTrue(rule.permissions().isEmpty());

        authenticate("anything.view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("默认拒绝"));
    }

    @Test
    void protectedPathWithoutLoginReturns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("GET", "/api/v1/operations/dashboard"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void protectedPathPassesWithRequiredPermissionCode() throws Exception {
        authenticate("operation.dashboard.view");
        assertTrue(interceptor.preHandle(request("GET", "/api/v1/operations/dashboard"),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void protectedPathWithWrongCodeReturns403AndNamesRequiredCode() throws Exception {
        authenticate("dashboard.view");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("GET", "/api/v1/operations/dashboard"), response, new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("operation.dashboard.view"));
    }

    @Test
    void publicQrEntryAndRectifyAreNotAffected() throws Exception {
        assertTrue(resolver.resolve(request("GET", "/api/v1/qr-inspection/stalls/token-1/entry")).publicAccess());
        assertTrue(resolver.resolve(request("POST", "/api/v1/qr-inspection/rectify/token-1")).publicAccess());
        assertTrue(interceptor.preHandle(request("GET", "/api/v1/qr-inspection/stalls/token-1/entry"),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void otherPublicPathsRemainPublic() {
        assertTrue(resolver.resolve(request("GET", "/api/v1/patrol/entry")).publicAccess());
        assertTrue(resolver.resolve(request("GET", "/api/v1/patrol/points/9/form")).publicAccess());
        assertTrue(resolver.resolve(request("POST", "/api/v1/patrol/submissions")).publicAccess());
        assertTrue(resolver.resolve(request("GET", "/api/v1/verification/tasks/abc")).publicAccess());
        assertTrue(resolver.resolve(request("GET", "/api/v1/system/migration/exports/9/download")).publicAccess());
        assertTrue(resolver.resolve(request("GET", "/api/v1/access-public/anything")).publicAccess());
        assertTrue(resolver.resolve(request("GET", "/api/v1/construction-public/anything")).publicAccess());
    }

    @Test
    void newlyMappedPathsUsePermissionCodes() {
        assertTrue(resolver.resolve(request("GET", "/api/v1/departments")).permissions().contains("system.dept.view"));
        assertTrue(resolver.resolve(request("POST", "/api/v1/departments")).permissions().contains("system.dept.manage"));
        assertTrue(resolver.resolve(request("POST", "/api/v1/operations/form-images"))
                .permissions().contains("operation.form.image.upload"));
        assertTrue(resolver.resolve(request("GET", "/api/v1/operations/form-images/12"))
                .permissions().contains("operation.form.image.view"));
        assertTrue(resolver.resolve(request("GET", "/api/v1/complaints")).permissions().contains("complaint.view"));
        assertTrue(resolver.resolve(request("POST", "/api/v1/complaints/import/update"))
                .permissions().contains("complaint.import"));
    }

    @Test
    void qrSubmitAcceptsAnySubmitOrRectifyPermission() throws Exception {
        // WY、QD、RD 前缀岗位角色没有历史角色别名，只要持任一提交/整改权限码即可进入
        authenticate("qr.inspection.submit.warehouse");
        assertTrue(interceptor.preHandle(request("POST", "/api/v1/qr-inspection/submit"),
                new MockHttpServletResponse(), new Object()));

        authenticate("qr.rectify");
        assertTrue(interceptor.preHandle(request("POST", "/api/v1/qr-inspection/stalls/token-1/submit"),
                new MockHttpServletResponse(), new Object()));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private void authenticate(String... codes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "tester", null, Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()));
    }
}
