package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the supported payment mechanisms clients can use to finalize their bookings.
 * <p>
 * These values are typically supplied by the frontend during the checkout initialization phase
 * and passed along to the respective payment gateway.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    CREDIT_CARD("Credit Card"),
    DEBIT_CARD("Debit Card"),
    PAYPAL("PayPal"),
    BANK_TRANSFER("Bank Transfer");

    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
