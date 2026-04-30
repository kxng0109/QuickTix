package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
}
