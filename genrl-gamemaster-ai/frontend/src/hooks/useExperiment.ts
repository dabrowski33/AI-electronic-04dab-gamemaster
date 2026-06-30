import { useState, useEffect, useCallback, useRef } from 'react';
import { experimentsApi, generationsApi, lrmApi } from '../api/client';
import type {
  Experiment,
  Generation,
  FitnessPoint,
  LrmSession,
  SystemEvent,
} from '../types';

const WS_URL = '/ws';
const MAX_EVENTS = 20;

interface UseExperimentResult {
  experiment: Experiment | null;
  generations: Generation[];
  fitnessHistory: FitnessPoint[];
  mutations: LrmSession[];
  events: SystemEvent[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useExperiment(experimentId: string | null): UseExperimentResult {
  const [experiment, setExperiment] = useState<Experiment | null>(null);
  const [generations, setGenerations] = useState<Generation[]>([]);
  const [fitnessHistory, setFitnessHistory] = useState<FitnessPoint[]>([]);
  const [mutations, setMutations] = useState<LrmSession[]>([]);
  const [events, setEvents] = useState<SystemEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const wsRef = useRef<WebSocket | null>(null);

  const pushEvent = useCallback((ev: SystemEvent) => {
    setEvents((prev) => [ev, ...prev].slice(0, MAX_EVENTS));
  }, []);

  // ─── Fetch experiment data ───────────────────────────────────────────────────

  const fetchData = useCallback(async () => {
    if (!experimentId) return;
    setLoading(true);
    setError(null);
    try {
      const [exp, gens, mut] = await Promise.allSettled([
        experimentsApi.get(experimentId),
        generationsApi.list(experimentId),
        lrmApi.sessions(experimentId),
      ]);

      if (exp.status === 'fulfilled') setExperiment(exp.value);
      if (gens.status === 'fulfilled') {
        setGenerations(gens.value);
        setFitnessHistory(
          gens.value.map((g) => ({
            generation: g.generation_number,
            best_fitness: g.best_fitness,
            avg_fitness: g.avg_fitness,
          }))
        );
      }
      if (mut.status === 'fulfilled') setMutations(mut.value);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Błąd pobierania danych');
    } finally {
      setLoading(false);
    }
  }, [experimentId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // ─── WebSocket live updates ──────────────────────────────────────────────────

  useEffect(() => {
    if (!experimentId) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}${WS_URL}`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'subscribe_experiment', experimentId }));
    };

    ws.onmessage = (event: MessageEvent) => {
      try {
        const data: SystemEvent = JSON.parse(event.data as string);
        pushEvent(data);

        if (data.type === 'generation_update' && data.experimentId === experimentId) {
          // Update live fitness
          setExperiment((prev) =>
            prev
              ? {
                  ...prev,
                  generation: data.generation ?? prev.generation,
                  best_fitness: data.best_fitness ?? prev.best_fitness,
                  avg_fitness: data.avg_fitness ?? prev.avg_fitness,
                }
              : prev
          );

          if (data.generation !== undefined && data.best_fitness !== undefined && data.avg_fitness !== undefined) {
            setFitnessHistory((prev) => {
              const point: FitnessPoint = {
                generation: data.generation as number,
                best_fitness: data.best_fitness as number,
                avg_fitness: data.avg_fitness as number,
              };
              // Avoid duplicate generation entries
              const exists = prev.some((p) => p.generation === point.generation);
              return exists ? prev.map((p) => (p.generation === point.generation ? point : p)) : [...prev, point];
            });
          }
        }

        if (data.type === 'mutation_triggered' && data.experimentId === experimentId) {
          const newMutation: LrmSession = {
            id: `live-${Date.now()}`,
            experiment_id: experimentId,
            generation: data.generation ?? 0,
            mutation_type: data.mutation_type ?? 'unknown',
            reasoning: (data.reasoning as string) ?? '',
            validation_status: (data.validation_status as LrmSession['validation_status']) ?? 'pending',
            applied: data.validation_status === 'approved',
            timestamp: data.timestamp,
          };
          setMutations((prev) => [newMutation, ...prev].slice(0, 50));
        }
      } catch {
        // malformed message — ignore
      }
    };

    ws.onerror = () => setError('Błąd połączenia WebSocket');

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [experimentId, pushEvent]);

  return {
    experiment,
    generations,
    fitnessHistory,
    mutations,
    events,
    loading,
    error,
    refresh: fetchData,
  };
}
