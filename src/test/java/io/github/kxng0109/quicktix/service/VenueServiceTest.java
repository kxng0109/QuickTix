package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VenueServiceTest {

    private final long venueId = 100L;
    private final String city = "a city sha";
    @Mock
    private VenueRepository venueRepository;
    @InjectMocks
    private VenueService venueService;
    private CreateVenueRequest request;
    private Venue venue;

    @BeforeEach
    public void setup() {
        request = CreateVenueRequest.builder()
                                    .name("something stadium")
                                    .address("5, a place, a city, a coutry")
                                    .city(city)
                                    .totalCapacity(1000000)
                                    .build();

        venue = Venue.builder()
                     .id(venueId)
                     .name(request.name())
                     .address(request.address())
                     .city(request.city())
                     .totalCapacity(request.totalCapacity())
                     .createdAt(Instant.now())
                     .updatedAt(Instant.now())
                     .build();
    }

    @Test
    public void createVenue_should_returnVenueResponse_whenRequestIsValid() {
        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(i -> i.getArgument(0));

        VenueResponse response = venueService.createVenue(request);

        assertNotNull(response);
        assertEquals(venue.getName(), response.name());
        assertEquals(venue.getCity(), response.city());

        verify(venueRepository).save(any(Venue.class));
    }

    @Test
    public void getVenueById_should_returnVenueResponse_whenIdExists() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(venue));

        VenueResponse response = venueService.getVenueById(venueId);

        assertNotNull(response);
        assertEquals(venue.getName(), response.name());

        verify(venueRepository).findById(venueId);
    }

    @Test
    public void getVenueById_should_throwEntityNotFoundException_whenIdDoesNotExist() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> venueService.getVenueById(venueId)
        );

        verify(venueRepository).findById(venueId);
        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    public void getAllVenues_should_returnAListOfVenueResponse() {
        when(venueRepository.findAll())
                .thenReturn(List.of(venue));

        List<VenueResponse> response = venueService.getAllVenues();

        assertNotNull(response);
        assertEquals(venue.getName(), response.getFirst().name());

        verify(venueRepository).findAll();
    }

    @Test
    public void getVenuesByCity_should_returnAListOfVenueResponse() {
        when(venueRepository.findByCity(city))
                .thenReturn(List.of(venue));

        List<VenueResponse> response = venueService.getVenuesByCity(city);

        assertNotNull(response);
        assertEquals(venue.getName(), response.getFirst().name());

        verify(venueRepository).findByCity(city);
    }

    @Test
    public void updateVenueById_should_returnVenueResponse_whenIdExists() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(venue));
        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(i -> i.getArgument(0));

        VenueResponse response = venueService.updateVenueById(venueId, request);

        assertNotNull(response);
        assertEquals(venue.getName(), response.name());

        verify(venueRepository).findById(venueId);
        verify(venueRepository).save(any(Venue.class));
    }

    @Test
    public void updateVenueById_should_throwEntityNotFoundException_whenIdDoesNotExist() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> venueService.updateVenueById(venueId, request)
        );

        verify(venueRepository).findById(venueId);
        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    public void deleteVenueById_should_returnNothing_whenIdExists() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(venue));

        venueService.deleteVenueById(venueId);

        verify(venueRepository).findById(venueId);
        verify(venueRepository).delete(any(Venue.class));
    }

    @Test
    public void deleteVenueById_should_throwEntityNotFoundException_whenIdDoesNotExist() {
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> venueService.deleteVenueById(venueId)
        );

        verify(venueRepository).findById(venueId);
        verify(venueRepository, never()).delete(any(Venue.class));
    }
}
