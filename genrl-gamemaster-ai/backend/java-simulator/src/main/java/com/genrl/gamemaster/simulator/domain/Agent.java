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
@Table(name = "agents", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private Generation generation;

    @Column(name = "agent_index", nullable = false)
    private Integer agentIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    @Builder.Default
    private Map<String, Object> genome = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "neural_weights", columnDefinition = "JSONB")
    private Map<String, Object> neuralWeights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "q_table", columnDefinition = "JSONB")
    private Map<String, Object> qTable;

    @Column(name = "fitness_score", precision = 10, scale = 4)
    private Double fitnessScore;

    @Column(name = "survival_time")
    private Integer survivalTime;

    @Column
    private Integer score;

    @Column(name = "win_status")
    @Builder.Default
    private Boolean winStatus = false;

    @Column(name = "execution_log", columnDefinition = "TEXT")
    private String executionLog;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "evaluation_duration_ms")
    private Long evaluationDurationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "evaluated_at")
    private OffsetDateTime evaluatedAt;
}