package com.company.platform.system;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Test
    void loginStoresOpaqueSessionInHttpOnlyCookieWithoutReturningToken() {
        AuthService service = mock(AuthService.class);
        when(service.login(any())).thenReturn(new AuthService.AuthSession("test-session-value", "admin", Instant.now().plusSeconds(3600)));
        AuthController controller = new AuthController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.login(new AuthRequests.LoginRequest("admin", "test-password"), request, response);

        assertEquals("", result.data().token());
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("platform_session=test-session-value"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void forwardedHttpsMarksSessionCookieSecure() {
        AuthService service = mock(AuthService.class);
        when(service.login(any())).thenReturn(new AuthService.AuthSession("secure-session", "admin", Instant.now().plusSeconds(3600)));
        AuthController controller = new AuthController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(new AuthRequests.LoginRequest("admin", "test-password"), request, response);

        assertTrue(response.getHeader("Set-Cookie").contains("; Secure"));
    }

    @Test
    void cookieLogoutRequiresRequestedWithHeader() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("platform_session", "test-cookie-value"));

        assertThrows(ResponseStatusException.class, () -> controller.logout(null, request, new MockHttpServletResponse()));
        verify(service, never()).logout(anyString());
    }

    @Test
    void meReadsSessionFromCookie() {
        AuthService service = mock(AuthService.class);
        when(service.currentUsername("test-cookie-value")).thenReturn("alice");
        when(service.displayNameForToken("test-cookie-value")).thenReturn("Alice");
        when(service.roleForToken("test-cookie-value")).thenReturn("USER");
        when(service.permissionsForToken("test-cookie-value")).thenReturn(java.util.Set.of("WORKBENCH_VIEW"));
        AuthController controller = new AuthController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("platform_session", "test-cookie-value"));

        var result = controller.me(null, request);

        assertEquals("alice", result.data().get("username"));
        verify(service).currentUsername("test-cookie-value");
    }
}
