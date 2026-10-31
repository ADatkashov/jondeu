package kz.jondeu.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jondeu")
public record AppProperties(Jwt jwt, Cors cors, Otp otp, Storage storage, Payments payments, Demo demo, RateLimit rateLimit) {
    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Otp(boolean required, boolean exposeDevCode) {}
    public record Storage(String localDir, long maxBytes) {}
    public record Payments(boolean sandboxEnabled, String sandboxSecret, String kaspiMerchantId, String kaspiApiKey) {}
    public record Demo(boolean enabled) {}
    public record RateLimit(int authPerMinute) {}
}
