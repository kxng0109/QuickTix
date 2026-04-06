package io.github.kxng0109.quicktix.event;

/**
 * Domain event published when an administrator successfully cancels a scheduled event.
 * <p>
 * Consumed asynchronously by the {@link io.github.kxng0109.quicktix.listener.EventCancellationListener}
 * to trigger mass refunds without blocking the main HTTP request thread.
 * </p>
 *
 * @param eventId The unique identifier of the newly cancelled event.
 */
public record EventCancelledEvent(
		Long eventId
) {
}
