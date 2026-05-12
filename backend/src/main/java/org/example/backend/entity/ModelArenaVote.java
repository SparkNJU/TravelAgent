package org.example.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_arena_votes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelArenaVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_a", nullable = false, length = 60)
    private String modelA;

    @Column(name = "model_b", nullable = false, length = 60)
    private String modelB;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
