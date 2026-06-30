import React, { useState } from 'react';
import { experimentsApi } from '../api/client';
import type { NewExperimentPayload, SimulatorType } from '../types';

interface NewExperimentFormProps {
  onSuccess: () => void;
  onCancel: () => void;
}

const defaultValues: NewExperimentPayload = {
  name: '',
  description: '',
  simulator_type: 'pacman',
  objective_function: 'maximize_score',
  population_size: 50,
  stagnation_threshold: 50,
};

const NewExperimentForm: React.FC<NewExperimentFormProps> = ({ onSuccess, onCancel }) => {
  const [form, setForm] = useState<NewExperimentPayload>(defaultValues);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof NewExperimentPayload, string>>>({});

  const validate = (): boolean => {
    const errors: Partial<Record<keyof NewExperimentPayload, string>> = {};
    if (!form.name.trim()) errors.name = 'Nazwa jest wymagana.';
    if (form.population_size < 1 || form.population_size > 1000)
      errors.population_size = 'Rozmiar populacji musi być między 1 a 1000.';
    if (form.stagnation_threshold < 1 || form.stagnation_threshold > 500)
      errors.stagnation_threshold = 'Próg stagnacji musi być między 1 a 500.';
    if (!form.objective_function.trim())
      errors.objective_function = 'Funkcja celu jest wymagana.';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleChange = <K extends keyof NewExperimentPayload>(key: K, value: NewExperimentPayload[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setFieldErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    setError(null);
    try {
      await experimentsApi.create(form);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Błąd tworzenia eksperymentu');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page-content" style={{ maxWidth: 700 }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1>Nowy Eksperyment</h1>
        <p style={{ color: 'var(--nbp-text-muted)', marginTop: '0.25rem', fontSize: '0.9rem' }}>
          Skonfiguruj parametry procesu ewolucji algorytmicznej.
        </p>
      </div>

      <div className="card">
        {error && (
          <div
            style={{
              background: '#ffe0e5',
              color: 'var(--nbp-danger)',
              borderRadius: 'var(--radius-md)',
              padding: '0.75rem 1rem',
              marginBottom: '1.25rem',
              fontSize: '0.875rem',
            }}
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          {/* Name */}
          <div className="form-group">
            <label className="form-label" htmlFor="exp-name">
              Nazwa eksperymentu *
            </label>
            <input
              id="exp-name"
              className="form-input"
              type="text"
              placeholder="np. Ewolucja agenta Pac-Man v1"
              value={form.name}
              onChange={(e) => handleChange('name', e.target.value)}
              maxLength={120}
            />
            {fieldErrors.name && <span className="form-error">{fieldErrors.name}</span>}
          </div>

          {/* Description */}
          <div className="form-group">
            <label className="form-label" htmlFor="exp-desc">
              Opis
            </label>
            <textarea
              id="exp-desc"
              className="form-textarea"
              placeholder="Opis celu i oczekiwanych wyników eksperymentu..."
              value={form.description}
              onChange={(e) => handleChange('description', e.target.value)}
              rows={3}
            />
          </div>

          {/* Simulator type */}
          <div className="form-group">
            <label className="form-label" htmlFor="exp-sim">
              Typ symulatora *
            </label>
            <select
              id="exp-sim"
              className="form-select"
              value={form.simulator_type}
              onChange={(e) => handleChange('simulator_type', e.target.value as SimulatorType)}
            >
              <option value="pacman">Pac-Man</option>
              <option value="super_mario">Super Mario</option>
            </select>
          </div>

          {/* Objective function */}
          <div className="form-group">
            <label className="form-label" htmlFor="exp-obj">
              Funkcja celu *
            </label>
            <textarea
              id="exp-obj"
              className="form-textarea"
              placeholder="np. maximize_score"
              value={form.objective_function}
              onChange={(e) => handleChange('objective_function', e.target.value)}
              rows={2}
            />
            <span className="form-hint">
              Identyfikator lub wyrażenie opisujące kryterium optymalizacji.
            </span>
            {fieldErrors.objective_function && (
              <span className="form-error">{fieldErrors.objective_function}</span>
            )}
          </div>

          {/* Population size + stagnation threshold */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="exp-pop">
                Rozmiar populacji *
              </label>
              <input
                id="exp-pop"
                className="form-input"
                type="number"
                min={1}
                max={1000}
                value={form.population_size}
                onChange={(e) => handleChange('population_size', parseInt(e.target.value, 10) || 1)}
              />
              {fieldErrors.population_size && (
                <span className="form-error">{fieldErrors.population_size}</span>
              )}
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label className="form-label" htmlFor="exp-stag">
                Próg stagnacji *
              </label>
              <input
                id="exp-stag"
                className="form-input"
                type="number"
                min={1}
                max={500}
                value={form.stagnation_threshold}
                onChange={(e) => handleChange('stagnation_threshold', parseInt(e.target.value, 10) || 1)}
              />
              <span className="form-hint">Liczba generacji bez poprawy.</span>
              {fieldErrors.stagnation_threshold && (
                <span className="form-error">{fieldErrors.stagnation_threshold}</span>
              )}
            </div>
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '2rem', justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn--secondary" onClick={onCancel} disabled={submitting}>
              Anuluj
            </button>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? 'Tworzenie...' : 'Utwórz Eksperyment'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default NewExperimentForm;
