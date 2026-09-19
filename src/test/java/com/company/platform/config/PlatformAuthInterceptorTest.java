package com.company.platform.config;

import com.company.platform.system.AuditService;
import com.company.platform.system.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAuthInterceptorTest {
    @Test
    void cookieMutationRequiresDatasphereRequestMarker() throws Exception {
        AuthService auth = mock(AuthService.class);
        AuditService audit = mock(AuditService.class);
        when(auth.enabled()).thenReturn(true);
        when(auth.authenticate("test-cookie")).thenReturn(true);
        when(auth.hasPermission(eq("test-cookie"), anyString(), anyString())).thenReturn(true);
        when(auth.currentUsername("test-cookie")).thenReturn("alice");
        PlatformAuthInterceptor interceptor = new PlatformAuthInterceptor(auth, audit);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/workflows");
        request.setCookies(new Cookie("platform_session", "test-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());

        request.addHeader("X-Requested-With", "DataSphere");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, allowed, new Object()));
    }

    @Test
    void bearerMutationDoesNotRequireBrowserMarker() throws Exception {
        AuthService auth = mock(AuthService.class);
        AuditService audit = mock(AuditService.class);
        when(auth.enabled()).thenReturn(true);
        when(auth.authenticate("api-token")).thenReturn(true);
        when(auth.hasPermission(eq("api-token"), anyString(), anyString())).thenReturn(true);
        when(auth.currentUsername("api-token")).thenReturn("service");
        PlatformAuthInterceptor interceptor = new PlatformAuthInterceptor(auth, audit);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/workflows");
        request.addHeader("Authorization", "Bearer api-token");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
