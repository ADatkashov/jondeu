package kz.jondeu.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kz.jondeu.common.ApiException;
import kz.jondeu.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * *** DEVELOPMENT SANDBOX — NOT A REAL PAYMENT PROVIDER. ***
 * Initiation leaves the payment PENDING. Confirmation requires a callback whose HMAC-SHA256 signature
 * (X-Sandbox-Signature) is verified exactly like a real gateway callback would be.
 */
@Component
public class SandboxPaymentProvider implements PaymentProvider {
    public static final String NAME = "SANDBOX";
    private final AppProperties props;
    private final ObjectMapper mapper;

    public SandboxPaymentProvider(AppProperties props, ObjectMapper mapper) { this.props = props; this.mapper = mapper; }

    @Override public String name() { return NAME; }
    @Override public boolean supports(PaymentEnums.Method m) { return m == PaymentEnums.Method.CARD || m == PaymentEnums.Method.KASPI; }
    @Override public boolean available() { return props.payments().sandboxEnabled() && props.payments().sandboxSecret() != null && !props.payments().sandboxSecret().isBlank(); }

    @Override
    public InitiateResult initiate(String reference, BigDecimal amount, String currency, String description) {
        return new InitiateResult("SBX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(), null);
    }

    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.payments().sandboxSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> headers, String rawBody) {
        if (!available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_DISABLED", "Sandbox provider is disabled");
        String sig = headers.getOrDefault("x-sandbox-signature", "");
        if (!MessageDigest.isEqual(sign(rawBody).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE", "Invalid callback signature");
        }
        try {
            JsonNode n = mapper.readTree(rawBody);
            return new CallbackResult(n.path("providerRef").asText(), "SUCCESS".equals(n.path("status").asText()), n.path("reason").asText(null));
        } catch (java.io.IOException e) {
            throw ApiException.bad("BAD_CALLBACK", "Malformed callback body");
        }
    }
}
