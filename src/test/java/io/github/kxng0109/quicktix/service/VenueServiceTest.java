package io.github.kxng0109.quicktix.service;

import io.github.kxng0109.quicktix.dto.request.CreateVenueRequest;
import io.github.kxng0109.quicktix.dto.response.VenueResponse;
import io.github.kxng0109.quicktix.entity.Event;
import io.github.kxng0109.quicktix.entity.Venue;
import io.github.kxng0109.quicktix.exception.ResourceInUseException;
import io.github.kxng0109.quicktix.repositories.VenueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private final Pageable pageable = PageRequest.of(0, 10);

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
        Page<Venue> venuePage = new PageImpl<>(List.of(venue));

        when(venueRepository.findAll(any(Pageable.class)))
                .thenReturn(venuePage);

        Page<VenueResponse> response = venueService.getAllVenues(pageable);

        assertNotNull(response);
        assertEquals(venue.getName(), response.getContent().getFirst().name());

        verify(venueRepository).findAll(any(Pageable.class));
    }

    @Test
    public void getVenuesByCity_should_returnAListOfVenueResponse() {
        Page<Venue> venuePage = new PageImpl<>(List.of(venue));

        when(venueRepository.findByCity(city, pageable))
                .thenReturn(venuePage);

        Page<VenueResponse> response = venueService.getVenuesByCity(city, pageable);

        assertNotNull(response);
        assertEquals(venue.getName(), response.getContent().getFirst().name());

        verify(venueRepository).findByCity(city, pageable);
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

    @Test
    public void deleteVenueById_should_throwResourceInUseException_whenVenueHasEvents() {
        Venue venueWithEvents = Venue.builder()
                                     .id(venueId)
                                     .events(List.of(new Event()))
                                     .build();

        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(venueWithEvents));

        ResourceInUseException ex = assertThrows(
                ResourceInUseException.class,
                () -> venueService.deleteVenueById(venueId)
        );

        assertEquals("Cannot delete venue that has associated events. Delete the events first.", ex.getMessage());

        verify(venueRepository).findById(venueId);
        verify(venueRepository, never()).delete(any(Venue.class));
    }
}
