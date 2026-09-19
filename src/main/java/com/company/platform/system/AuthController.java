package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE = "platform_session";
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/login")
    public Result<AuthService.AuthSession> login(@Valid @RequestBody AuthRequests.LoginRequest request,
                                                  HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.AuthSession session = service.login(request);
        long seconds = Math.max(0, Duration.between(java.time.Instant.now(), session.expiresAt()).getSeconds());
        setCookie(response, httpRequest.isSecure(), session.token(), seconds);
        return Result.ok(new AuthService.AuthSession("", session.username(), session.expiresAt()), "登录成功");
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody AuthRequests.ChangePasswordRequest request,
                                       HttpServletRequest httpRequest, HttpServletResponse response) {
        service.changePassword(token(authorization, httpRequest), request);
        clearCookie(response, httpRequest.isSecure());
        return Result.ok(null, "密码已修改，请使用新密码登录");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpServletRequest httpRequest, HttpServletResponse response) {
        service.logout(token(authorization, httpRequest));
        clearCookie(response, httpRequest.isSecure());
        return Result.ok(null, "已退出登录");
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          HttpServletRequest httpRequest) {
        String current = token(authorization, httpRequest);
        return Result.ok(Map.of(
                "username", service.currentUsername(current),
                "displayName", service.displayNameForToken(current),
                "authenticated", service.authenticate(current),
                "permissions", service.permissionsForToken(current),
                "roleCode", service.roleForToken(current),
                "superAdmin", service.isSuperAdminToken(current)));
    }

    private String token(String authorization, HttpServletRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring(7).trim();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return "";
    }

    private void setCookie(HttpServletResponse response, boolean secure, String token, long maxAge) {
        response.addHeader("Set-Cookie", COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + (secure ? "; Secure" : ""));
    }
    private void clearCookie(HttpServletResponse response, boolean secure) { setCookie(response, secure, "", 0); }
}
