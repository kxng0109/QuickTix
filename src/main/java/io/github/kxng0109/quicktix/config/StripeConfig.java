package io.github.kxng0109.quicktix.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Stripe integration.
 * <p>
 * This class is responsible for initializing the modern {@link com.stripe.StripeClient}
 * as a Spring Bean, ensuring a single, thread-safe instance is used throughout
 * the application lifecycle.
 */
@Configuration
public class StripeConfig {

	@Value("${stripe.api.key}")
	private String stripeApiKey;

	/**
	 * Creates and configures the StripeClient instance.
	 *
	 * @return a fully initialized {@link com.stripe.StripeClient} using the secret API key.
	 */
	@Bean
	public StripeClient stripeClient() {
		return new StripeClient(stripeApiKey);
	}
}
