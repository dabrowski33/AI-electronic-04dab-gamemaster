// ─── Domain types ─────────────────────────────────────────────────────────────

export type SimulatorType = 'pacman' | 'super_mario';

export type ExperimentStatus = 'pending' | 'running' | 'completed' | 'failed' | 'paused';

export type ValidationStatus = 'approved' | 'rejected' | 'pending';

export type MutationType =
  | 'weight_perturbation'
  | 'topology_change'
  | 'learning_rate_adjust'
  | 'activation_swap'
  | string;

// ─── Experiment ───────────────────────────────────────────────────────────────

export interface Experiment {
  id: string;
  name: string;
  description?: string;
  simulator_type: SimulatorType;
  objective_function: string;
  population_size: number;
  stagnation_threshold: number;
  status: ExperimentStatus;
  generation: number;
  best_fitness: number;
  avg_fitness: number;
  created_at: string;
  updated_at?: string;
}

export interface NewExperimentPayload {
  name: string;
  description: string;
  simulator_type: SimulatorType;
  objective_function: string;
  population_size: number;
  stagnation_threshold: number;
}

// ─── Generation ───────────────────────────────────────────────────────────────

export interface Generation {
  id: string;
  experiment_id: string;
  generation_number: number;
  best_fitness: number;
  avg_fitness: number;
  worst_fitness: number;
  population_size: number;
  timestamp: string;
}

// ─── Agent (individual in population) ────────────────────────────────────────

export interface Agent {
  id: string;
  experiment_id: string;
  generation_number: number;
  fitness: number;
  genome?: Record<string, unknown>;
  created_at: string;
}

// ─── LRM Session (Large Reasoning Model mutation session) ─────────────────────

export interface LrmSession {
  id: string;
  experiment_id: string;
  generation: number;
  mutation_type: MutationType;
  reasoning: string;
  validation_status: ValidationStatus;
  applied: boolean;
  fitness_before?: number;
  fitness_after?: number;
  timestamp: string;
}

// ─── System event (telemetry log) ─────────────────────────────────────────────

export type SystemEventType =
  | 'generation_update'
  | 'mutation_triggered'
  | 'sandbox_result'
  | 'fitness_updated'
  | 'experiment_started'
  | 'experiment_completed'
  | 'heartbeat'
  | 'connected'
  | 'subscribed'
  | string;

export interface SystemEvent {
  type: SystemEventType;
  experimentId?: string;
  generation?: number;
  best_fitness?: number;
  avg_fitness?: number;
  population_size?: number;
  mutation_type?: MutationType;
  reasoning?: string;
  validation_status?: ValidationStatus;
  score?: number;
  status?: string;
  timestamp: string;
  [key: string]: unknown;
}

// ─── Fitness history point (for chart) ───────────────────────────────────────

export interface FitnessPoint {
  generation: number;
  best_fitness: number;
  avg_fitness: number;
}
