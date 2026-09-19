package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        setCookie(response, secureRequest(httpRequest), session.token(), seconds);
        return Result.ok(new AuthService.AuthSession("", session.username(), session.expiresAt()), "登录成功");
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody AuthRequests.ChangePasswordRequest request,
                                       HttpServletRequest httpRequest, HttpServletResponse response) {
        requireCookieMutationHeader(authorization, httpRequest);
        service.changePassword(token(authorization, httpRequest), request);
        clearCookie(response, secureRequest(httpRequest));
        return Result.ok(null, "密码已修改，请使用新密码登录");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpServletRequest httpRequest, HttpServletResponse response) {
        requireCookieMutationHeader(authorization, httpRequest);
        service.logout(token(authorization, httpRequest));
        clearCookie(response, secureRequest(httpRequest));
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

    private void requireCookieMutationHeader(String authorization, HttpServletRequest request) {
        boolean bearer = authorization != null && authorization.startsWith("Bearer ");
        if (!bearer && hasSessionCookie(request) && !"DataSphere".equals(request.getHeader("X-Requested-With")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请求来源校验失败");
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) return true;
        return false;
    }

    private boolean secureRequest(HttpServletRequest request) {
        if (request.isSecure()) return true;
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim())) return true;
        String forwarded = request.getHeader("Forwarded");
        return forwarded != null && forwarded.toLowerCase(java.util.Locale.ROOT).contains("proto=https");
    }

    private void setCookie(HttpServletResponse response, boolean secure, String token, long maxAge) {
        response.addHeader("Set-Cookie", COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + (secure ? "; Secure" : ""));
    }
    private void clearCookie(HttpServletResponse response, boolean secure) { setCookie(response, secure, "", 0); }
}
