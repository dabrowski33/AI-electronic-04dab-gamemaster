import React, { useState } from 'react';
import FitnessChart from './FitnessChart';
import { useExperiment } from '../hooks/useExperiment';
import type { LrmSession, SystemEvent, SimulatorType } from '../types';

interface ExperimentDetailProps {
  experimentId: string;
  onBack: () => void;
}

const simulatorLabel: Record<SimulatorType, string> = {
  pacman: 'Pac-Man',
  super_mario: 'Super Mario',
};

// ─── Mutation item (collapsible reasoning) ────────────────────────────────────

const MutationItem: React.FC<{ mut: LrmSession }> = ({ mut }) => {
  const [open, setOpen] = useState(false);

  const statusColor: Record<LrmSession['validation_status'], string> = {
    approved: 'var(--nbp-success)',
    rejected: 'var(--nbp-danger)',
    pending: 'var(--nbp-warning)',
  };

  const statusLabel: Record<LrmSession['validation_status'], string> = {
    approved: 'Zatwierdzona',
    rejected: 'Odrzucona',
    pending: 'Oczekuje',
  };

  return (
    <div className="mutation-item">
      <div className="mutation-item__header" onClick={() => setOpen((v) => !v)}>
        <span className="mutation-item__type">{mut.mutation_type.replace(/_/g, ' ')}</span>
        <span className="mutation-item__gen">Gen. {mut.generation}</span>
        <span
          style={{
            fontSize: '0.75rem',
            fontWeight: 700,
            color: statusColor[mut.validation_status] ?? '#555',
            marginLeft: 'auto',
          }}
        >
          {statusLabel[mut.validation_status] ?? mut.validation_status}
        </span>
        <span style={{ fontSize: '0.75rem', color: 'var(--nbp-text-muted)', marginLeft: '0.5rem' }}>
          {open ? '▲' : '▼'}
        </span>
      </div>

      {open && mut.reasoning && (
        <div className="mutation-item__reasoning">
          <div className="mutation-item__reasoning--label">Rozumowanie LRM (Chain-of-Thought)</div>
          {mut.reasoning}
        </div>
      )}
    </div>
  );
};

// ─── Telemetry log ────────────────────────────────────────────────────────────

const TelemetryLog: React.FC<{ events: SystemEvent[] }> = ({ events }) => {
  const fmt = (ts: string) => {
    try {
      return new Date(ts).toLocaleTimeString('pl-PL');
    } catch {
      return ts;
    }
  };

  return (
    <div className="telemetry-log">
      {events.length === 0 && (
        <span style={{ color: 'rgba(168,196,224,0.5)' }}>Oczekiwanie na zdarzenia...</span>
      )}
      {events.map((ev, idx) => (
        <div key={idx} className="telemetry-log__entry">
          <span className="telemetry-log__time">{fmt(ev.timestamp)}</span>
          <span>
            <span className="telemetry-log__type">{ev.type}</span>{' '}
            <span className="telemetry-log__body">
              {ev.type === 'generation_update' &&
                `gen=${ev.generation} best=${(ev.best_fitness as number)?.toFixed(3)} avg=${(ev.avg_fitness as number)?.toFixed(3)}`}
              {ev.type === 'mutation_triggered' &&
                `${String(ev.mutation_type ?? '')} → ${String(ev.validation_status ?? '')}`}
              {ev.type === 'sandbox_result' &&
                `score=${String(ev.score ?? '')} status=${String(ev.status ?? '')}`}
              {ev.type === 'heartbeat' &&
                `active=${String(ev.activeExperiments ?? '')} clients=${String(ev.connectedClients ?? '')}`}
              {['connected', 'subscribed'].includes(ev.type) && ''}
            </span>
          </span>
        </div>
      ))}
    </div>
  );
};

// ─── Main component ───────────────────────────────────────────────────────────

const ExperimentDetail: React.FC<ExperimentDetailProps> = ({ experimentId, onBack }) => {
  const { experiment, fitnessHistory, mutations, events, loading, error, refresh } =
    useExperiment(experimentId);

  if (loading && !experiment) {
    return (
      <div className="page-content">
        <div className="spinner" />
      </div>
    );
  }

  if (error && !experiment) {
    return (
      <div className="page-content">
        <div
          style={{
            background: '#ffe0e5',
            color: 'var(--nbp-danger)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem',
            marginBottom: '1rem',
          }}
        >
          Błąd: {error}
        </div>
        <button className="btn btn--secondary btn--sm" onClick={onBack}>
          ← Powrót
        </button>
      </div>
    );
  }

  return (
    <div className="page-content">
      {/* Breadcrumb / back */}
      <button
        className="btn btn--secondary btn--sm"
        onClick={onBack}
        style={{ marginBottom: '1.5rem' }}
      >
        ← Powrót do listy
      </button>

      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        <div>
          <h1 style={{ marginBottom: '0.3rem' }}>{experiment?.name ?? '—'}</h1>
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <span
              className={`badge badge--${experiment?.status ?? 'pending'}`}
            >
              {experiment?.status === 'running'
                ? 'Uruchomiony'
                : experiment?.status === 'completed'
                ? 'Zakończony'
                : experiment?.status === 'failed'
                ? 'Błąd'
                : experiment?.status === 'paused'
                ? 'Wstrzymany'
                : 'Oczekuje'}
            </span>
            <span style={{ fontSize: '0.85rem', color: 'var(--nbp-text-muted)' }}>
              Symulator:{' '}
              <strong>
                {simulatorLabel[experiment?.simulator_type as SimulatorType] ??
                  experiment?.simulator_type ??
                  '—'}
              </strong>
            </span>
          </div>
        </div>
        <button className="btn btn--secondary btn--sm" onClick={refresh}>
          Odśwież
        </button>
      </div>

      {/* Current generation stats */}
      <h2 className="section-heading">Bieżąca Generacja</h2>
      <div className="grid-3" style={{ marginBottom: '2rem' }}>
        <div className="stat-tile">
          <span className="stat-tile__label">Generacja</span>
          <span className="stat-tile__value">{experiment?.generation ?? 0}</span>
        </div>
        <div className="stat-tile">
          <span className="stat-tile__label">Najlepszy Fitness</span>
          <span className="stat-tile__value stat-tile__value--gold">
            {(experiment?.best_fitness ?? 0).toFixed(4)}
          </span>
        </div>
        <div className="stat-tile">
          <span className="stat-tile__label">Średni Fitness</span>
          <span className="stat-tile__value">{(experiment?.avg_fitness ?? 0).toFixed(4)}</span>
        </div>
      </div>

      {/* Fitness convergence chart */}
      <h2 className="section-heading">Konwergencja Fitness</h2>
      <div className="card" style={{ marginBottom: '2rem' }}>
        <FitnessChart data={fitnessHistory} />
      </div>

      {/* Mutations + telemetry side-by-side on wide screens */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '2rem', marginBottom: '2rem' }}>
        {/* LRM Mutations */}
        <div>
          <h2 className="section-heading">Mutacje LRM</h2>
          {mutations.length === 0 ? (
            <div className="empty-state" style={{ padding: '2rem 0' }}>
              <div className="empty-state__icon">&#9883;</div>
              <div className="empty-state__text" style={{ fontSize: '0.95rem' }}>
                Brak mutacji
              </div>
            </div>
          ) : (
            <div className="mutation-list">
              {mutations.slice(0, 10).map((m) => (
                <MutationItem key={m.id} mut={m} />
              ))}
            </div>
          )}
        </div>

        {/* Telemetry */}
        <div>
          <h2 className="section-heading">Dziennik Telemetrii</h2>
          <TelemetryLog events={events} />
        </div>
      </div>
    </div>
  );
};

export default ExperimentDetail;
