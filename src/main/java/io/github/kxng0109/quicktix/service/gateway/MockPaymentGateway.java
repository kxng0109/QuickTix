package io.github.kxng0109.quicktix.service.gateway;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class MockPaymentGateway implements PaymentGateway {

    private final Random random = new Random();

    /**
     * Checks if a transaction was successful on the provider's side.
     * This implementation will pause for 2s to simulate a network delay
     * then it will either return true for a successful transaction or false
     * for a failed transaction
     *
     * @param transactionReference the unique transfer reference used for getting the status of the transfer
     *                             from the payment gateway provider
     * @return either true or false
     */
    @Override
    public boolean verifyTransaction(String transactionReference) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException _) {
        }

        return random.nextBoolean();
    }

    /**
     * Attempts to refund a transaction on the provider's side.
     *
     * @param transactionReference the unique transfer reference used for getting the status of the transfer
     *                             from the payment gateway provider
     * @return either true or false
     */
    @Override
    public boolean refundTransaction(String transactionReference) {
        return random.nextBoolean();
    }
}
