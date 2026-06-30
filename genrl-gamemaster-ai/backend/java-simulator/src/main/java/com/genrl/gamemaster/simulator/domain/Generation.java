package com.genrl.gamemaster.simulator.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "generations", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Generation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private Experiment experiment;

    @Column(name = "generation_number", nullable = false)
    private Integer generationNumber;

    @Column(name = "population_size", nullable = false)
    private Integer populationSize;

    @Column(name = "best_fitness", precision = 10, scale = 4)
    private Double bestFitness;

    @Column(name = "avg_fitness", precision = 10, scale = 4)
    private Double avgFitness;

    @Column(name = "worst_fitness", precision = 10, scale = 4)
    private Double worstFitness;

    @Column(name = "mutation_code", columnDefinition = "TEXT")
    private String mutationCode;

    @Column(name = "mutation_reasoning", columnDefinition = "TEXT")
    private String mutationReasoning;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "pending";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}