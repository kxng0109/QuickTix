package io.github.kxng0109.quicktix.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "venues")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500, nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @OneToMany(mappedBy = "venue", fetch = FetchType.LAZY)
    private List<Event> events;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
