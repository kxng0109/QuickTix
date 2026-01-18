package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {
    Page<Venue> findByCity(String city, Pageable pageable);
}
