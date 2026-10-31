package kz.jondeu.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds, UserView user) {}
    public record UserView(UUID id, String phone, String email, String fullName, Role role, String city, String language, String avatarUrl) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getPhone(), u.getEmail(), u.getFullName(), u.getRole(), u.getCity(), u.getLanguage(), u.getAvatarUrl());
        }
    }
    public record RegisterCommand(String phone, String email, String password, String fullName, Role role, String city, String language, String otpCode) {}

    private final UserRepository users;
    private final RefreshTokenRepository refresh;
    private final OtpCodeRepository otps;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SmsSender sms;
    private final AppProperties props;

    public AuthService(UserRepository users, RefreshTokenRepository refresh, OtpCodeRepository otps, PasswordEncoder encoder,
                       JwtService jwt, SmsSender sms, AppProperties props) {
        this.users = users; this.refresh = refresh; this.otps = otps; this.encoder = encoder; this.jwt = jwt; this.sms = sms; this.props = props;
    }

    /** Normalizes Kazakhstan numbers to +7XXXXXXXXXX. */
    public static String normalizePhone(String raw) {
        String d = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (d.length() == 11 && (d.startsWith("8") || d.startsWith("7"))) d = d.substring(1);
        if (d.length() != 10 || !d.startsWith("7") && !d.startsWith("6")) {
            throw ApiException.bad("INVALID_PHONE", "Phone must be a valid Kazakhstan number");
        }
        return "+7" + d;
    }

    /** Returns the dev code only if configured to expose it (never in production). */
    @Transactional
    public Optional<String> requestOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        OtpCode o = otps.findById(phone).orElseGet(OtpCode::new);
        o.setPhone(phone); o.setCodeHash(sha256(phone + ":" + code));
        o.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)); o.setAttempts(0);
        otps.save(o);
        sms.send(phone, "JONDEU: код подтверждения " + code);
        return props.otp().exposeDevCode() ? Optional.of(code) : Optional.empty();
    }

    private void verifyOtp(String phone, String code) {
        OtpCode o = otps.findById(phone).orElseThrow(() -> ApiException.bad("OTP_INVALID", "Invalid or expired code"));
        if (o.getExpiresAt().isBefore(Instant.now()) || o.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otps.delete(o);
            throw ApiException.bad("OTP_INVALID", "Invalid or expired code");
        }
        if (code == null || !MessageDigest.isEqual(o.getCodeHash().getBytes(StandardCharsets.UTF_8), sha256(phone + ":" + code).getBytes(StandardCharsets.UTF_8))) {
            o.setAttempts(o.getAttempts() + 1);
            otps.save(o);
            throw ApiException.bad("OTP_INVALID", "Invalid or expired code");
        }
        otps.delete(o);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Tokens register(RegisterCommand c) {
        String phone = normalizePhone(c.phone());
        if (c.role() == null || !(c.role() == Role.CUSTOMER || c.role() == Role.MASTER || c.role() == Role.CONTRACTOR || c.role() == Role.COMPANY_ADMIN)) {
            throw ApiException.bad("INVALID_ROLE", "Role cannot be self-assigned");
        }
        if (props.otp().required()) verifyOtp(phone, c.otpCode());
        if (users.existsByPhone(phone)) throw ApiException.conflict("PHONE_TAKEN", "Phone is already registered");
        if (c.email() != null && !c.email().isBlank() && users.existsByEmail(c.email().toLowerCase())) {
            throw ApiException.conflict("EMAIL_TAKEN", "Email is already registered");
        }
        User u = new User();
        u.setPhone(phone);
        u.setEmail(c.email() == null || c.email().isBlank() ? null : c.email().toLowerCase());
        u.setPasswordHash(encoder.encode(c.password()));
        u.setFullName(c.fullName().trim());
        u.setRole(c.role());
        if (c.city() != null) u.setCity(c.city());
        if (c.language() != null) u.setLanguage(c.language());
        users.save(u);
        return issue(u, UUID.randomUUID());
    }

    @Transactional
    public Tokens login(String rawPhone, String password) {
        User u = users.findByPhone(normalizePhone(rawPhone)).orElse(null);
        // Same error for unknown user and wrong password: no account enumeration.
        if (u == null || u.getPasswordHash() == null || !encoder.matches(password, u.getPasswordHash()) || !u.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid phone or password");
        }
        return issue(u, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Tokens loginWithOtp(String rawPhone, String code) {
        String phone = normalizePhone(rawPhone);
        verifyOtp(phone, code);
        User u = users.findByPhone(phone).filter(User::isEnabled)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Account not found, please register"));
        return issue(u, UUID.randomUUID());
    }

    /** Refresh-token rotation. Re-use of a rotated token revokes the whole token family. */
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens refresh(String rawToken) {
        RefreshToken t = refresh.findByTokenHash(sha256(rawToken == null ? "" : rawToken))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Invalid refresh token"));
        if (t.isRevoked()) {
            refresh.revokeFamily(t.getFamilyId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSE", "Refresh token reuse detected, please sign in again");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Refresh token expired");
        User u = users.findById(t.getUserId()).filter(User::isEnabled)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Account disabled"));
        t.setRevoked(true);
        refresh.save(t);
        return issue(u, t.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        refresh.findByTokenHash(sha256(rawToken == null ? "" : rawToken)).ifPresent(t -> refresh.revokeFamily(t.getFamilyId()));
    }

    @Transactional
    public void logoutAll(UUID userId) { refresh.revokeAllForUser(userId); }

    private Tokens issue(User u, UUID family) {
        byte[] raw = new byte[48];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId()); rt.setFamilyId(family); rt.setTokenHash(sha256(token));
        rt.setExpiresAt(Instant.now().plus(props.jwt().refreshTtlDays(), ChronoUnit.DAYS));
        refresh.save(rt);
        return new Tokens(jwt.issueAccess(u), token, jwt.accessTtlSeconds(), UserView.of(u));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
