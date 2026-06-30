import React, { useEffect, useState } from 'react';
import { experimentsApi } from '../api/client';
import type { Experiment, SimulatorType } from '../types';

interface DashboardProps {
  onSelectExperiment: (id: string) => void;
  onNewExperiment: () => void;
  refreshKey?: number;
}

const simulatorLabel: Record<SimulatorType, string> = {
  pacman: 'Pac-Man',
  super_mario: 'Super Mario',
};

const StatusBadge: React.FC<{ status: Experiment['status'] }> = ({ status }) => {
  const labels: Record<Experiment['status'], string> = {
    running: 'Uruchomiony',
    pending: 'Oczekuje',
    completed: 'Zakończony',
    failed: 'Błąd',
    paused: 'Wstrzymany',
  };
  return <span className={`badge badge--${status}`}>{labels[status] ?? status}</span>;
};

const ExperimentCard: React.FC<{
  experiment: Experiment;
  onSelect: () => void;
}> = ({ experiment, onSelect }) => (
  <div
    className="card card--accent"
    onClick={onSelect}
    style={{ cursor: 'pointer', transition: 'box-shadow var(--transition)' }}
    onMouseEnter={(e) => {
      (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-lg)';
    }}
    onMouseLeave={(e) => {
      (e.currentTarget as HTMLElement).style.boxShadow = 'var(--shadow-sm)';
    }}
  >
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem', marginBottom: '0.75rem' }}>
      <h3 style={{ fontSize: '1rem', wordBreak: 'break-word', flex: 1 }}>{experiment.name}</h3>
      <StatusBadge status={experiment.status} />
    </div>

    <div style={{ fontSize: '0.82rem', color: 'var(--nbp-text-muted)', marginBottom: '1rem' }}>
      Symulator: <strong>{simulatorLabel[experiment.simulator_type] ?? experiment.simulator_type}</strong>
    </div>

    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.5rem' }}>
      <MetricCell label="Najl. Fitness" value={experiment.best_fitness.toFixed(2)} gold />
      <MetricCell label="Śr. Fitness" value={experiment.avg_fitness.toFixed(2)} />
      <MetricCell label="Generacja" value={String(experiment.generation)} />
    </div>
  </div>
);

const MetricCell: React.FC<{ label: string; value: string; gold?: boolean }> = ({ label, value, gold }) => (
  <div
    style={{
      background: 'var(--nbp-cream)',
      borderRadius: 'var(--radius-sm)',
      padding: '0.4rem 0.6rem',
      textAlign: 'center',
    }}
  >
    <div style={{ fontSize: '0.68rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--nbp-text-muted)', fontWeight: 700 }}>
      {label}
    </div>
    <div
      style={{
        fontFamily: 'var(--font-heading)',
        fontSize: '1.1rem',
        color: gold ? 'var(--nbp-gold-dark)' : 'var(--nbp-navy)',
        fontWeight: 700,
      }}
    >
      {value}
    </div>
  </div>
);

const Dashboard: React.FC<DashboardProps> = ({ onSelectExperiment, onNewExperiment, refreshKey }) => {
  const [experiments, setExperiments] = useState<Experiment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    experimentsApi
      .list()
      .then(setExperiments)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [refreshKey]);

  return (
    <div className="page-content">
      {/* Page header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ marginBottom: '0.25rem' }}>Eksperymenty Ewolucyjne</h1>
          <p style={{ color: 'var(--nbp-text-muted)', fontSize: '0.9rem' }}>
            Przegląd wszystkich uruchomionych procesów uczenia ze wzmocnieniem.
          </p>
        </div>
        <button className="btn btn--primary" onClick={onNewExperiment}>
          + Nowy Eksperyment
        </button>
      </div>

      {/* Summary tiles */}
      <div className="grid-3" style={{ marginBottom: '2rem' }}>
        <SummaryTile
          label="Wszystkie"
          value={experiments.length}
        />
        <SummaryTile
          label="Uruchomione"
          value={experiments.filter((e) => e.status === 'running').length}
          gold
        />
        <SummaryTile
          label="Zakończone"
          value={experiments.filter((e) => e.status === 'completed').length}
        />
      </div>

      {/* Experiment list */}
      {loading && <div className="spinner" />}

      {error && (
        <div
          style={{
            background: '#ffe0e5',
            color: 'var(--nbp-danger)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem',
            marginBottom: '1.5rem',
            fontSize: '0.9rem',
          }}
        >
          Błąd ładowania eksperymentów: {error}. Upewnij się, że serwer BFF jest uruchomiony.
        </div>
      )}

      {!loading && experiments.length === 0 && !error && (
        <div className="empty-state">
          <div className="empty-state__icon">&#9881;</div>
          <div className="empty-state__text">Brak eksperymentów</div>
          <p style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>
            Utwórz pierwszy eksperyment, klikając &quot;Nowy Eksperyment&quot;.
          </p>
        </div>
      )}

      {!loading && experiments.length > 0 && (
        <div className="grid-2">
          {experiments.map((exp) => (
            <ExperimentCard
              key={exp.id}
              experiment={exp}
              onSelect={() => onSelectExperiment(exp.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

const SummaryTile: React.FC<{ label: string; value: number; gold?: boolean }> = ({ label, value, gold }) => (
  <div className="stat-tile">
    <span className="stat-tile__label">{label}</span>
    <span className={`stat-tile__value${gold ? ' stat-tile__value--gold' : ''}`}>{value}</span>
  </div>
);

export default Dashboard;
