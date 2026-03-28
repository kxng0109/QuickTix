package io.github.kxng0109.quicktix.service.gateway;

import io.github.kxng0109.quicktix.entity.Payment;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@Profile({"test", "mock"})
@Primary
public class MockPaymentGateway implements PaymentGateway {

    private final Random random = new Random();

    /**
     * @param payment the internal payment record to be processed
     * @return a client secret
     */
    @Override
    public String initializePayment(Payment payment) {
        return "clientSecret";
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
