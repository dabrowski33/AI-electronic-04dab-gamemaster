package com.genrl.gamemaster.simulator.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "code_mutations", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeMutation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lrm_session_id", nullable = false)
    private LrmSession lrmSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private Generation generation;

    @Column(name = "mutation_type", nullable = false, length = 50)
    private String mutationType;

    @Column(name = "original_code", columnDefinition = "TEXT", nullable = false)
    private String originalCode;

    @Column(name = "mutated_code", columnDefinition = "TEXT", nullable = false)
    private String mutatedCode;

    @Column(name = "diff_patch", columnDefinition = "TEXT")
    private String diffPatch;

    @Column(name = "validation_status", nullable = false, length = 50)
    @Builder.Default
    private String validationStatus = "pending";

    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

    @Column(name = "sandbox_execution_id")
    private UUID sandboxExecutionId;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}