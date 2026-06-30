-- GenRL GameMaster AI Database Schema
-- PostgreSQL 16+

-- ============================================
-- CORE TABLES
-- ============================================

-- Experiments table - top-level experiment tracking
CREATE TABLE experiments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    simulator_type VARCHAR(50) NOT NULL CHECK (simulator_type IN ('pacman', 'super_mario', 'custom')),
    objective_function TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'created' CHECK (status IN ('created', 'running', 'paused', 'completed', 'failed')),
    config JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Generations table - tracks each generation in an experiment
CREATE TABLE generations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    generation_number INTEGER NOT NULL,
    population_size INTEGER NOT NULL,
    best_fitness DECIMAL(10, 4),
    avg_fitness DECIMAL(10, 4),
    worst_fitness DECIMAL(10, 4),
    mutation_code TEXT,
    mutation_reasoning TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'evaluating', 'completed', 'failed')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(experiment_id, generation_number)
);

-- Agents table - individual agents in each generation
CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
    agent_index INTEGER NOT NULL,
    genome JSONB NOT NULL DEFAULT '{}',
    neural_weights JSONB,
    q_table JSONB,
    fitness_score DECIMAL(10, 4),
    survival_time INTEGER,
    score INTEGER,
    win_status BOOLEAN DEFAULT FALSE,
    execution_log TEXT,
    error_message TEXT,
    evaluation_duration_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    evaluated_at TIMESTAMPTZ,
    UNIQUE(generation_id, agent_index)
);

-- ============================================
-- LRM/ORCHESTRATION TABLES
-- ============================================

-- LRM Sessions - tracks LLM interactions for mutations
CREATE TABLE lrm_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    generation_id UUID REFERENCES generations(id) ON DELETE SET NULL,
    provider VARCHAR(50) NOT NULL CHECK (provider IN ('openrouter', 'openai_codex')),
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    reasoning_tokens INTEGER,
    total_tokens INTEGER,
    request_payload JSONB NOT NULL,
    response_payload JSONB,
    reasoning_content TEXT,
    latency_ms INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'success', 'failed', 'fallback')),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Code Mutations - tracks algorithmic code changes
CREATE TABLE code_mutations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lrm_session_id UUID NOT NULL REFERENCES lrm_sessions(id) ON DELETE CASCADE,
    generation_id UUID NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
    mutation_type VARCHAR(50) NOT NULL CHECK (mutation_type IN ('hyperparameter', 'reward_shaping', 'network_structure', 'selection_mechanism', 'full_algorithm')),
    original_code TEXT NOT NULL,
    mutated_code TEXT NOT NULL,
    diff_patch TEXT,
    validation_status VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (validation_status IN ('pending', 'syntax_ok', 'syntax_error', 'runtime_error', 'executed')),
    validation_error TEXT,
    sandbox_execution_id UUID,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- SANDBOX/EXECUTION TABLES
-- ============================================

-- Sandbox Executions - tracks OpenCode container runs
CREATE TABLE sandbox_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id UUID REFERENCES agents(id) ON DELETE SET NULL,
    code_mutation_id UUID REFERENCES code_mutations(id) ON DELETE SET NULL,
    container_id VARCHAR(100),
    language VARCHAR(20) NOT NULL CHECK (language IN ('python', 'cpp')),
    source_code TEXT NOT NULL,
    stdin_input TEXT,
    stdout_output TEXT,
    stderr_output TEXT,
    exit_code INTEGER,
    execution_time_ms INTEGER,
    memory_used_mb INTEGER,
    cpu_time_ms INTEGER,
    timeout_triggered BOOLEAN DEFAULT FALSE,
    security_violation BOOLEAN DEFAULT FALSE,
    violation_details TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'running', 'completed', 'failed', 'timeout', 'killed')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- TELEMETRY/AUDIT TABLES
-- ============================================

-- System Events - audit log for critical events
CREATE TABLE system_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID REFERENCES experiments(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('debug', 'info', 'warning', 'error', 'critical')),
    message TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    source_service VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fitness History - for convergence analysis
CREATE TABLE fitness_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID NOT NULL REFERENCES experiments(id) ON DELETE CASCADE,
    generation_number INTEGER NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(15, 6) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(experiment_id, generation_number, metric_name)
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

CREATE INDEX idx_generations_experiment ON generations(experiment_id);
CREATE INDEX idx_generations_status ON generations(status);
CREATE INDEX idx_agents_generation ON agents(generation_id);
CREATE INDEX idx_agents_fitness ON agents(fitness_score DESC);
CREATE INDEX idx_lrm_sessions_experiment ON lrm_sessions(experiment_id);
CREATE INDEX idx_lrm_sessions_generation ON lrm_sessions(generation_id);
CREATE INDEX idx_code_mutations_generation ON code_mutations(generation_id);
CREATE INDEX idx_sandbox_executions_agent ON sandbox_executions(agent_id);
CREATE INDEX idx_sandbox_executions_status ON sandbox_executions(status);
CREATE INDEX idx_system_events_experiment ON system_events(experiment_id);
CREATE INDEX idx_system_events_created ON system_events(created_at DESC);
CREATE INDEX idx_fitness_history_experiment ON fitness_history(experiment_id, generation_number);

-- ============================================
-- TRIGGERS FOR UPDATED_AT
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_experiments_updated_at BEFORE UPDATE ON experiments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- VIEWS FOR COMMON QUERIES
-- ============================================

-- Experiment summary view
CREATE VIEW experiment_summary AS
SELECT
    e.id,
    e.name,
    e.simulator_type,
    e.status,
    e.created_at,
    e.completed_at,
    COUNT(DISTINCT g.id) as total_generations,
    COUNT(DISTINCT a.id) as total_agents,
    MAX(g.best_fitness) as best_fitness_overall,
    AVG(g.avg_fitness) as avg_fitness_overall
FROM experiments e
LEFT JOIN generations g ON g.experiment_id = e.id
LEFT JOIN agents a ON a.generation_id = g.id
GROUP BY e.id, e.name, e.simulator_type, e.status, e.created_at, e.completed_at;

-- Generation detail view
CREATE VIEW generation_detail AS
SELECT
    g.id,
    g.experiment_id,
    g.generation_number,
    g.population_size,
    g.best_fitness,
    g.avg_fitness,
    g.worst_fitness,
    g.status,
    g.started_at,
    g.completed_at,
    COUNT(a.id) as evaluated_agents,
    COUNT(a.id) FILTER (WHERE a.win_status = TRUE) as winning_agents,
    AVG(a.evaluation_duration_ms) as avg_eval_time_ms
FROM generations g
LEFT JOIN agents a ON a.generation_id = g.id
GROUP BY g.id, g.experiment_id, g.generation_number, g.population_size, g.best_fitness, g.avg_fitness, g.worst_fitness, g.status, g.started_at, g.completed_at;
