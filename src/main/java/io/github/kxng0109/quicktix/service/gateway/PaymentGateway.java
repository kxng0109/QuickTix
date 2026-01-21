package io.github.kxng0109.quicktix.service.gateway;

public interface PaymentGateway {
    /**
     * Checks if a transaction was successful on the provider's side.
     */
    boolean verifyTransaction(String transactionReference);

    /**
     * Attempts to refund a transaction on the provider's side.
     */
    boolean refundTransaction(String transactionReference);
}
