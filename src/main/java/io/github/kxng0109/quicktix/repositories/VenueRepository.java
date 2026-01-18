package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByCity(String city);
}
