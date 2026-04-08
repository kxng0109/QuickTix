package io.github.kxng0109.quicktix.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Identifies the external payment gateways integrated into the QuickTix platform.
 * <p>
 * Used to route payment initialization requests and webhook verifications to the
 * correct vendor-specific SDK or API implementation.
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum PaymentProvider {

	/** Paystack gateway, primarily used for African/Nigerian transactions. */
	PAYSTACK("Paystack"),

	/** Stripe gateway, used for global/international transactions. */
	STRIPE("Stripe");


	private final String displayName;
}
