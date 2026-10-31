package kz.jondeu.payments;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Abstraction over payment gateways (Kaspi, bank acquiring, sandbox). A provider NEVER reports success
 * on initiation; success arrives only through {@link #verifyCallback}, which must authenticate the
 * request (signature) before returning a result.
 */
public interface PaymentProvider {
    String name();
    boolean supports(PaymentEnums.Method method);
    /** Whether this provider is configured and usable in the current environment. */
    boolean available();

    record InitiateResult(String providerRef, String checkoutUrl) {}
    record CallbackResult(String providerRef, boolean success, String failureReason) {}

    InitiateResult initiate(String reference, BigDecimal amount, String currency, String description);

    /** @throws kz.jondeu.common.ApiException 401 if the signature is invalid. */
    CallbackResult verifyCallback(Map<String, String> headers, String rawBody);
}
