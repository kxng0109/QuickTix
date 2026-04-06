package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for the management of physical event locations (Venues).
 * <p>
 * Extensively utilizes Spring Cache ({@code @Cacheable}, {@code @CacheEvict}) to optimize
 * read-heavy operations, as venue data changes infrequently but is queried constantly by the frontend.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class VenueService {

	private final VenueRepository venueRepository;

	/**
	 * Registers a new venue in the system. Triggers cache eviction.
	 *
	 * @param request The payload containing the venue's name, address, city, and capacity.
	 * @return A {@link VenueResponse} representing the newly created venue.
	 */
	@CacheEvict(value = "venues", allEntries = true)
	@Transactional
	public VenueResponse createVenue(CreateVenueRequest request) {
		Venue venue = Venue.builder()
		                   .name(request.name())
		                   .address(request.address())
		                   .city(request.city())
		                   .totalCapacity(request.totalCapacity())
		                   .build();

		Venue createdVenue = venueRepository.save(venue);

		return buildVenueResponse(createdVenue);
	}

	/**
	 * Retrieves a specific venue by its ID.
	 *
	 * @param venueId The unique identifier of the venue.
	 * @return The requested {@link VenueResponse}.
	 * @throws EntityNotFoundException if the venue does not exist.
	 */
	@Transactional(readOnly = true)
	@Cacheable(value = "venues", key = "#venueId", sync = true)
	public VenueResponse getVenueById(Long venueId) {
		Venue venue = getVenueEntityById(venueId);

		return buildVenueResponse(venue);
	}

	/**
	 * Retrieves a paginated list of all venues.
	 */
	@Transactional(readOnly = true)
	@Cacheable(value = "venues", key = "'all-' + #pageable.pageNumber", sync = true)
	public Page<VenueResponse> getAllVenues(Pageable pageable) {
		Page<Venue> venues = venueRepository.findAll(pageable);

		return venues.map(this::buildVenueResponse);
	}

	/**
	 * Retrieves a paginated list of venues filtered by their city.
	 *
	 * @param city The exact string representation of the city.
	 */
	@Transactional(readOnly = true)
	@Cacheable(value = "venues", key = "#city", sync = true)
	public Page<VenueResponse> getVenuesByCity(String city, Pageable pageable) {
		Page<Venue> venues = venueRepository.findByCity(city, pageable);

		return venues.map(this::buildVenueResponse);
	}

	/**
	 * Updates an existing venue's details and triggers a cache eviction.
	 *
	 * @param venueId The ID of the venue to update.
	 * @param request The updated venue details.
	 * @return The updated {@link VenueResponse}.
	 */
	@Transactional
	@CacheEvict(value = "venues", allEntries = true)
	public VenueResponse updateVenueById(Long venueId, CreateVenueRequest request) {
		Venue venue = getVenueEntityById(venueId);

		venue.setAddress(request.address());
		venue.setCity(request.city());
		venue.setTotalCapacity(request.totalCapacity());
		venue.setName(request.name());

		Venue updatedVenue = venueRepository.save(venue);

		return buildVenueResponse(updatedVenue);
	}

	/**
	 * Deletes a venue from the system.
	 * <p>
	 * <b>Constraint:</b> A venue cannot be deleted if it has events assigned to it.
	 * All associated events must be cancelled/deleted first to preserve referential integrity.
	 * </p>
	 *
	 * @param venueId The ID of the venue to delete.
	 * @throws ResourceInUseException if the venue currently hosts events.
	 */
	@Transactional
	@CacheEvict(value = "venues", allEntries = true)
	public void deleteVenueById(Long venueId) {
		Venue venue = getVenueEntityById(venueId);

		// Prevent deleting a venue that has events
		boolean hasEvents = venue.getEvents() != null && !venue.getEvents().isEmpty();
		if (hasEvents) {
			throw new ResourceInUseException(
					"Cannot delete venue that has associated events. Delete the events first.");
		}

		venueRepository.delete(venue);
	}


	@Transactional(readOnly = true)
	Venue getVenueEntityById(Long venueId) {
		return venueRepository.findById(venueId)
		                      .orElseThrow(
				                      () -> new EntityNotFoundException("Venue with id: " + venueId + " not found.")
		                      );
	}

	private VenueResponse buildVenueResponse(Venue venue) {
		return VenueResponse.builder()
		                    .id(venue.getId())
		                    .name(venue.getName())
		                    .address(venue.getAddress())
		                    .city(venue.getCity())
		                    .totalCapacity(venue.getTotalCapacity())
		                    .build();
	}
}
