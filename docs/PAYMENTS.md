# Payments

* Money is `NUMERIC(16,2)` + currency everywhere; all arithmetic is `BigDecimal` (see `Money`, `EstimateCalculator`).
* Net paid comes from an append-only **ledger** (`ledger_entries`), never from a mutable counter.
* A payment can be created only against an **approved estimate** and only up to `approved − paid − reserved` (PENDING payments reserve funds). The project row is locked (`SELECT … FOR UPDATE`) so racing requests cannot oversell the budget (test: `ConcurrentPaymentsTest`).
* `Idempotency-Key` is mandatory for customer payments; the same key returns the same payment.

| Method | Provider | Confirmation |
|---|---|---|
| CASH, BANK_TRANSFER | `MANUAL` | the **receiving** side (contractor) confirms receipt; the payer cannot confirm own payment |
| CARD, KASPI | `SANDBOX` (dev only, labelled) | signed callback (HMAC-SHA256, `X-Sandbox-Signature`) at `POST /api/v1/payments/callbacks/sandbox`; forged signatures → 401 |
| KASPI (real) | `KaspiPaymentProvider` | **NOT CONNECTED.** Always unavailable → HTTP 503 `PROVIDER_NOT_CONFIGURED` |

The sandbox is enabled only in the `dev` profile (`jondeu.payments.sandbox-enabled`). Initiation never reports success.

## Connecting Kaspi (or an acquirer)
1. Obtain merchant credentials and the official API specification.
2. Implement `PaymentProvider.initiate` (create the remote order) and `verifyCallback` (authenticate the notification **before** trusting it) in `KaspiPaymentProvider`; make `available()` depend on `KASPI_MERCHANT_ID` / `KASPI_API_KEY`.
3. Register the callback URL `https://<host>/api/v1/payments/callbacks/kaspi` with the provider.
4. Add integration tests with recorded provider payloads.
