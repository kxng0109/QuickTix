package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
                           .createdAt(Instant.now())
                           .updatedAt(Instant.now())
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
    public List<VenueResponse> getAllVenues() {
        List<Venue> venues = venueRepository.findAll();

        return buildVenueResponseList(venues);
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> getVenuesByCity(String city) {
        List<Venue> venues = venueRepository.findByCity(city);

        return buildVenueResponseList(venues);
    }

    @Transactional
    public VenueResponse updateVenueById(Long venueId, CreateVenueRequest request) {
        Venue venue = getVenueEntityById(venueId);

        venue = Venue.builder()
                     .id(venue.getId())
                     .name(request.name())
                     .address(request.address())
                     .city(request.address())
                     .totalCapacity(request.totalCapacity())
                     .updatedAt(Instant.now())
                     .build();

        Venue updatedVenue = venueRepository.save(venue);

        return buildVenueResponse(updatedVenue);
    }

    @Transactional
    public void deleteVenueById(Long venueId) {
        Venue venue = getVenueEntityById(venueId);

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

    private List<VenueResponse> buildVenueResponseList(List<Venue> venues) {
        if (venues.isEmpty()) {
            return List.of();
        }

        return venues.stream()
                     .map(this::buildVenueResponse)
                     .toList();
    }
}
