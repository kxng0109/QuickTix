package io.github.kxng0109.quicktix.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentProvider {
	PAYSTACK("Paystack"),
	STRIPE("Stripe");


	private final String displayName;
}
