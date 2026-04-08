package io.github.kxng0109.quicktix.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the financial state of a transaction recorded in the system.
 * <p>
 * Crucial for determining whether a pending booking should be upgraded to confirmed,
 * or if a cancelled event requires a financial refund.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {
    /**
     *  Payment initialized with the gateway but awaiting user action/webhook confirmation.
     */
    PENDING("Pending"),

    /**
     * Funds successfully captured and verified.
     */
    COMPLETED("Completed"),

    /**
     * Payment was declined by the bank, or the checkout session expired.
     */
    FAILED("Failed"),

    /**
     * Funds have been successfully returned to the customer's original payment method.
     */
    REFUNDED("Refunded");


    private final String displayName;

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}