package kz.jondeu.payments;

public final class PaymentEnums {
    private PaymentEnums() {}
    public enum Type { ADVANCE, STAGE, MATERIAL, FINAL, ADDITIONAL }
    public enum Method { CASH, CARD, KASPI, BANK_TRANSFER }
    /** REQUESTED: contractor asked for it, customer hasn't paid. PENDING: initiated, awaiting verified confirmation. */
    public enum Status { REQUESTED, PENDING, PAID, FAILED, CANCELLED, REFUNDED }
}
