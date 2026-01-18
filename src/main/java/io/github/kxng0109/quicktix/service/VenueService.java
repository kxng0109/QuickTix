package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

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

    @Transactional(readOnly = true)
    public VenueResponse getVenueById(Long venueId) {
        Venue venue = getVenueEntityById(venueId);

        return buildVenueResponse(venue);
    }

    @Transactional(readOnly = true)
    public Page<VenueResponse> getAllVenues(Pageable pageable) {
        Page<Venue> venues = venueRepository.findAll(pageable);

        return venues.map(this::buildVenueResponse);
    }

    @Transactional(readOnly = true)
    public Page<VenueResponse> getVenuesByCity(String city, Pageable pageable) {
        Page<Venue> venues = venueRepository.findByCity(city, pageable);

        return venues.map(this::buildVenueResponse);
    }

    @Transactional
    public VenueResponse updateVenueById(Long venueId, CreateVenueRequest request) {
        Venue venue = getVenueEntityById(venueId);

        venue.setAddress(request.address());
        venue.setCity(request.city());
        venue.setTotalCapacity(request.totalCapacity());
        venue.setName(request.name());

        Venue updatedVenue = venueRepository.save(venue);

        return buildVenueResponse(updatedVenue);
    }

    @Transactional
    public void deleteVenueById(Long venueId) {
        Venue venue = getVenueEntityById(venueId);

        // Prevent deleting a venue that has events
        boolean hasEvents = venue.getEvents() != null && !venue.getEvents().isEmpty();
        if (hasEvents) {
            throw new IllegalStateException("Cannot delete venue that has associated events. Delete the events first.");
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
