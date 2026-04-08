package io.github.kxng0109.quicktix.dto.request.projection;

/**
 * A Spring Data JPA Projection interface used to capture the results of aggregate queries.
 * <p>
 * This is specifically designed to solve the N+1 query problem when fetching seat counts
 * for a paginated list of events. Instead of executing a separate database query for
 * every single event, the database groups the counts and maps the result directly
 * to this interface.
 */
public interface EventSeatCount {
	/**
	 * @return The unique identifier of the Event.
	 */
	Long getEventId();
	/**
	 * @return The total number of seats matching the requested status for this specific event.
	 * <p>
	 * Note: SQL COUNT() operations naturally return a Long type.
	 */
	Long getAvailableSeats();
}
