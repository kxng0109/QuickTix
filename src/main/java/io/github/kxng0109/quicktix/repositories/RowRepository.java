package io.github.kxng0109.quicktix.repositories;

import io.github.kxng0109.quicktix.entity.Row;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RowRepository extends JpaRepository<Row, Long> {
}
