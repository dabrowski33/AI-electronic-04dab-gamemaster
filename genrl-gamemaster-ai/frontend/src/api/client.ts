import axios from 'axios';

/**
 * Axios instance pointing at the Node BFF orchestrator proxy.
 * Vite dev-server forwards /api → http://localhost:3000, so
 * the same base URL works in both dev and Docker (nginx proxy).
 */
const apiClient = axios.create({
  baseURL: '/api/orchestrator',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

apiClient.interceptors.response.use(
  (res) => res,
  (err) => {
    const message: string =
      err.response?.data?.error ?? err.response?.data?.message ?? err.message ?? 'Nieznany błąd';
    console.error('[api]', err.config?.url, message);
    return Promise.reject(new Error(message));
  }
);

export default apiClient;

// ─── Experiment endpoints ─────────────────────────────────────────────────────

import type { Experiment, NewExperimentPayload, Generation, LrmSession } from '../types';

export const experimentsApi = {
  list: () => apiClient.get<Experiment[]>('/experiments').then((r) => r.data),
  get: (id: string) => apiClient.get<Experiment>(`/experiments/${id}`).then((r) => r.data),
  create: (payload: NewExperimentPayload) =>
    apiClient.post<Experiment>('/experiments', payload).then((r) => r.data),
};

export const generationsApi = {
  list: (experimentId: string) =>
    apiClient.get<Generation[]>(`/experiments/${experimentId}/generations`).then((r) => r.data),
};

export const lrmApi = {
  sessions: (experimentId: string) =>
    apiClient.get<LrmSession[]>(`/lrm-sessions/${experimentId}`).then((r) => r.data),
};
