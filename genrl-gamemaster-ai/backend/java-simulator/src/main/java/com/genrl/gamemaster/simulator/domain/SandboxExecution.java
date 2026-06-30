package com.genrl.gamemaster.simulator.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sandbox_executions", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_mutation_id")
    private CodeMutation codeMutation;

    @Column(name = "container_id", length = 100)
    private String containerId;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "source_code", columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Column(name = "stdin_input", columnDefinition = "TEXT")
    private String stdinInput;

    @Column(name = "stdout_output", columnDefinition = "TEXT")
    private String stdoutOutput;

    @Column(name = "stderr_output", columnDefinition = "TEXT")
    private String stderrOutput;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "memory_used_mb")
    private Integer memoryUsedMb;

    @Column(name = "cpu_time_ms")
    private Long cpuTimeMs;

    @Column(name = "timeout_triggered")
    @Builder.Default
    private Boolean timeoutTriggered = false;

    @Column(name = "security_violation")
    @Builder.Default
    private Boolean securityViolation = false;

    @Column(name = "violation_details", columnDefinition = "TEXT")
    private String violationDetails;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "queued";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}