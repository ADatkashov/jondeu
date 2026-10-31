package kz.jondeu.identity;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import kz.jondeu.common.ApiException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    public record OtpRequest(@NotBlank String phone) {}
    public record RegisterRequest(@NotBlank String phone, @Email String email, @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 150) String fullName, @NotNull Role role, String city, String language, String otpCode) {}
    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {}
    public record OtpLoginRequest(@NotBlank String phone, @NotBlank String code) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}

    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) { this.auth = auth; this.users = users; }

    @PostMapping("/otp/request")
    public Map<String, Object> otp(@Valid @RequestBody OtpRequest r) {
        var dev = auth.requestOtp(r.phone());
        return dev.<Map<String, Object>>map(c -> Map.of("sent", true, "devCode", c)).orElse(Map.of("sent", true));
    }

    @PostMapping("/register")
    public AuthService.Tokens register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(new AuthService.RegisterCommand(r.phone(), r.email(), r.password(), r.fullName(), r.role(), r.city(), r.language(), r.otpCode()));
    }

    @PostMapping("/login")
    public AuthService.Tokens login(@Valid @RequestBody LoginRequest r) { return auth.login(r.phone(), r.password()); }

    @PostMapping("/otp/login")
    public AuthService.Tokens otpLogin(@Valid @RequestBody OtpLoginRequest r) { return auth.loginWithOtp(r.phone(), r.code()); }

    @PostMapping("/refresh")
    public AuthService.Tokens refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@Valid @RequestBody RefreshRequest r) { auth.logout(r.refreshToken()); return Map.of("ok", true); }

    @PostMapping("/logout-all")
    public Map<String, Boolean> logoutAll() { auth.logoutAll(CurrentUser.require().id()); return Map.of("ok", true); }

    @GetMapping("/me")
    public AuthService.UserView me() {
        return users.findById(CurrentUser.require().id()).map(AuthService.UserView::of).orElseThrow(() -> ApiException.notFound("User"));
    }

    public record ProfileUpdate(@Size(max = 150) String fullName, String city, String language, String avatarUrl) {}

    @PatchMapping("/me")
    public AuthService.UserView update(@Valid @RequestBody ProfileUpdate p) {
        User u = users.findById(CurrentUser.require().id()).orElseThrow(() -> ApiException.notFound("User"));
        if (p.fullName() != null && !p.fullName().isBlank()) u.setFullName(p.fullName().trim());
        if (p.city() != null) u.setCity(p.city());
        if (p.language() != null) u.setLanguage(p.language());
        if (p.avatarUrl() != null) u.setAvatarUrl(p.avatarUrl());
        u.setUpdatedAt(java.time.Instant.now());
        return AuthService.UserView.of(users.save(u));
    }
}
