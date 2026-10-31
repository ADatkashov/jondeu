package kz.jondeu.payments;

import java.math.BigDecimal;
import java.util.Map;
import kz.jondeu.common.ApiException;
import kz.jondeu.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Kaspi Pay integration point. NOT CONNECTED: real merchant credentials and the Kaspi API specification
 * are required. Until KASPI_MERCHANT_ID / KASPI_API_KEY are configured and this class is implemented against
 * the official documentation, {@link #available()} is false and any attempt fails loudly instead of faking success.
 */
@Component
public class KaspiPaymentProvider implements PaymentProvider {
    private final AppProperties props;

    public KaspiPaymentProvider(AppProperties props) { this.props = props; }

    @Override public String name() { return "KASPI"; }
    @Override public boolean supports(PaymentEnums.Method m) { return m == PaymentEnums.Method.KASPI; }

    @Override
    public boolean available() {
        // Credentials alone are not sufficient: the gateway calls below are intentionally unimplemented.
        return false;
    }

    @Override
    public InitiateResult initiate(String reference, BigDecimal amount, String currency, String description) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED",
            "Kaspi integration is not connected. See docs/PAYMENTS.md for setup instructions.");
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> headers, String rawBody) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "Kaspi integration is not connected");
    }
}
