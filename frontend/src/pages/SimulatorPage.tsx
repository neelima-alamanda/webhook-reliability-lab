import React, { useEffect, useState } from 'react';
import type {
  SimulatorRequest,
  SimulatorResponse,
  SimulatorScenario,
  SourceResponse,
  WebhookEventResponse,
} from '../types';
import { simulatorApi, sourceApi, ApiException } from '../api/client';
import { SCENARIO_DETAILS, formatDate, getStatusBadge } from '../utils/formatters';
import { DeliveryAttemptsModal } from '../components/DeliveryAttemptsModal';
import {
  PlayCircle,
  Zap,
  Sparkles,
  AlertCircle,
  CheckCircle2,
  HelpCircle,
  History,
  Send,
  Layers,
} from 'lucide-react';

interface SimulatorPageProps {
  onNavigateToEvents?: () => void;
}

export const SimulatorPage: React.FC<SimulatorPageProps> = () => {
  const [sources, setSources] = useState<SourceResponse[]>([]);
  const [loadingSources, setLoadingSources] = useState<boolean>(true);

  // Form State
  const [scenario, setScenario] = useState<SimulatorScenario>('NORMAL');
  const [sourceId, setSourceId] = useState<number | ''>('');
  const [eventId, setEventId] = useState<string>('');
  const [resourceId, setResourceId] = useState<string>('');
  const [sequence, setSequence] = useState<number>(1);
  const [type, setType] = useState<string>('order.created');
  const [occurredAt, setOccurredAt] = useState<string>('');
  const [currentStatus, setCurrentStatus] = useState<string>('CREATED');

  // Execution & Response State
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [response, setResponse] = useState<SimulatorResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [inspectedEvent, setInspectedEvent] = useState<WebhookEventResponse | null>(null);

  // Helper to format now as YYYY-MM-DDTHH:mm:ss for datetime-local
  const getNowLocal = () => {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 19);
  };

  const generateSampleData = () => {
    const rand = Math.floor(1000 + Math.random() * 9000);
    setEventId(`evt_sim_${rand}`);
    setResourceId(`res_ord_${rand}`);
    setSequence(1);
    setType('order.created');
    setOccurredAt(getNowLocal());
    setCurrentStatus('CREATED');
    setError(null);
    setFieldErrors({});
  };

  useEffect(() => {
    const fetchSources = async () => {
      setLoadingSources(true);
      try {
        const data = await sourceApi.getSources();
        setSources(data);
        if (data.length > 0 && sourceId === '') {
          setSourceId(data[0].id);
        }
      } catch {
        setError('Failed to fetch webhook sources. Ensure you are signed in and backend is running.');
      } finally {
        setLoadingSources(false);
      }
    };

    fetchSources();
    generateSampleData();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceId) {
      setError('Please select a valid Webhook Source.');
      return;
    }

    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    setResponse(null);

    // Format occurredAt to ISO string without offset or standard format
    const occurredAtFormatted = occurredAt ? new Date(occurredAt).toISOString().slice(0, 19) : getNowLocal();

    const requestPayload: SimulatorRequest = {
      sourceId: Number(sourceId),
      eventId: eventId.trim(),
      resourceId: resourceId.trim(),
      sequence: Number(sequence),
      type: type.trim(),
      occurredAt: occurredAtFormatted,
      currentStatus: currentStatus.trim() || undefined,
      scenario,
    };

    try {
      const res = await simulatorApi.send(requestPayload);
      setResponse(res);
    } catch (err) {
      if (err instanceof ApiException) {
        setError(err.message);
        if (err.details) {
          setFieldErrors(err.details);
        }
      } else {
        setError('Error sending simulation request to backend');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const currentScenarioDetails = SCENARIO_DETAILS[scenario];

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Webhook Failure & Reliability Simulator</h1>
          <p className="page-description">
            Safely test edge cases including concurrent duplicates, sequence inversions, stale timestamps, and HMAC signature tamperings.
          </p>
        </div>
        <div className="header-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={generateSampleData}
            title="Generate fresh IDs and timestamps"
          >
            <Sparkles size={15} />
            <span>Generate Sample Values</span>
          </button>
        </div>
      </div>

      {/* Scenario Cards Selector */}
      <div className="simulator-scenarios-section">
        <label className="section-label">1. Choose Delivery Scenario to Simulate</label>
        <div className="scenarios-grid">
          {(Object.keys(SCENARIO_DETAILS) as SimulatorScenario[]).map((scKey) => {
            const sc = SCENARIO_DETAILS[scKey];
            const isSelected = scenario === scKey;
            return (
              <div
                key={scKey}
                className={`scenario-card ${isSelected ? 'selected' : ''}`}
                onClick={() => {
                  setScenario(scKey);
                  setError(null);
                  setFieldErrors({});
                }}
              >
                <div className="scenario-card-header">
                  <span className="scenario-badge">{scKey}</span>
                  {isSelected && <Zap size={16} className="text-blue" />}
                </div>
                <h3 className="scenario-title">{sc.title}</h3>
                <p className="scenario-desc">{sc.shortDesc}</p>
              </div>
            );
          })}
        </div>

        {/* Selected Scenario Explanation Banner */}
        <div className="scenario-info-banner">
          <div className="info-icon-wrapper">
            <HelpCircle size={20} />
          </div>
          <div className="info-text">
            <strong>Expected Reliability Behavior:</strong>
            <p>{currentScenarioDetails.fullDesc}</p>
            <div className="expected-rule">
              <span className="rule-badge">Verification Target:</span>
              <span>{currentScenarioDetails.expectedBehavior}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Simulator Form & Results Split Grid */}
      <div className="simulator-layout">
        {/* Left Column: Input Form */}
        <div className="card simulator-form-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">2. Configure Payload Parameters</h2>
              <p className="section-subtitle">
                Payload fields required by the <code>SimulatorRequest</code> contract.
              </p>
            </div>
          </div>

          {error && (
            <div className="auth-error-banner">
              <AlertCircle size={18} className="shrink-0" />
              <div className="error-text-content">
                <strong>Simulation Error:</strong>
                <p>{error}</p>
                {Object.keys(fieldErrors).length > 0 && (
                  <ul className="field-error-list">
                    {Object.entries(fieldErrors).map(([field, msg]) => (
                      <li key={field}>
                        <code>{field}</code>: {msg}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} className="simulator-form">
            <div className="form-group">
              <label htmlFor="sim-source">Webhook Source</label>
              {loadingSources ? (
                <div className="form-loading">Loading webhook sources...</div>
              ) : sources.length === 0 ? (
                <div className="form-warning">
                  No sources found. Please create a webhook source on the Dashboard first.
                </div>
              ) : (
                <select
                  id="sim-source"
                  value={sourceId}
                  onChange={(e) => setSourceId(Number(e.target.value))}
                  required
                  className={fieldErrors.sourceId ? 'input-error' : ''}
                >
                  {sources.map((s) => (
                    <option key={s.id} value={s.id}>
                      #{s.id} — {s.name}
                    </option>
                  ))}
                </select>
              )}
              {fieldErrors.sourceId && (
                <span className="field-error-text">{fieldErrors.sourceId}</span>
              )}
            </div>

            <div className="form-row">
              <div className="form-group flex-1">
                <label htmlFor="sim-event-id">Event ID</label>
                <input
                  id="sim-event-id"
                  type="text"
                  required
                  placeholder="e.g. evt_order_9001"
                  value={eventId}
                  onChange={(e) => setEventId(e.target.value)}
                  className={fieldErrors.eventId ? 'input-error' : ''}
                />
                {fieldErrors.eventId && (
                  <span className="field-error-text">{fieldErrors.eventId}</span>
                )}
              </div>

              <div className="form-group flex-1">
                <label htmlFor="sim-resource-id">Resource ID</label>
                <input
                  id="sim-resource-id"
                  type="text"
                  required
                  placeholder="e.g. ord_cust_4432"
                  value={resourceId}
                  onChange={(e) => setResourceId(e.target.value)}
                  className={fieldErrors.resourceId ? 'input-error' : ''}
                />
                {fieldErrors.resourceId && (
                  <span className="field-error-text">{fieldErrors.resourceId}</span>
                )}
              </div>
            </div>

            <div className="form-row">
              <div className="form-group flex-1">
                <label htmlFor="sim-sequence">Sequence Number</label>
                <input
                  id="sim-sequence"
                  type="number"
                  min="1"
                  required
                  value={sequence}
                  onChange={(e) => setSequence(parseInt(e.target.value, 10) || 1)}
                  className={fieldErrors.sequence ? 'input-error' : ''}
                />
                {fieldErrors.sequence && (
                  <span className="field-error-text">{fieldErrors.sequence}</span>
                )}
              </div>

              <div className="form-group flex-1">
                <label htmlFor="sim-type">Event Type</label>
                <input
                  id="sim-type"
                  type="text"
                  required
                  placeholder="order.created"
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  className={fieldErrors.type ? 'input-error' : ''}
                />
                {fieldErrors.type && (
                  <span className="field-error-text">{fieldErrors.type}</span>
                )}
              </div>
            </div>

            <div className="form-row">
              <div className="form-group flex-1">
                <label htmlFor="sim-occurred-at">Occurred Timestamp</label>
                <input
                  id="sim-occurred-at"
                  type="datetime-local"
                  step="1"
                  required
                  value={occurredAt}
                  onChange={(e) => setOccurredAt(e.target.value)}
                  className={fieldErrors.occurredAt ? 'input-error' : ''}
                />
                {fieldErrors.occurredAt && (
                  <span className="field-error-text">{fieldErrors.occurredAt}</span>
                )}
              </div>

              <div className="form-group flex-1">
                <label htmlFor="sim-current-status">Current Status (Optional)</label>
                <input
                  id="sim-current-status"
                  type="text"
                  placeholder="e.g. COMPLETED"
                  value={currentStatus}
                  onChange={(e) => setCurrentStatus(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-block"
              disabled={submitting || sources.length === 0}
            >
              {submitting ? (
                <>
                  <Zap size={16} className="spin-icon" />
                  <span>Dispatching Simulated Webhook...</span>
                </>
              ) : (
                <>
                  <Send size={16} />
                  <span>Execute {scenario} Scenario</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Right Column: Execution Output */}
        <div className="card simulator-output-card">
          <div className="section-header">
            <div>
              <h2 className="section-title">3. Ingestion Result & Verification</h2>
              <p className="section-subtitle">
                Response returned by <code>POST /api/simulator/send</code>
              </p>
            </div>
          </div>

          {!response && !submitting && (
            <div className="empty-state">
              <PlayCircle size={40} className="empty-state-icon" />
              <p className="empty-state-title">Ready for Simulation</p>
              <p className="empty-state-desc">
                Select a scenario and click "Execute" to run the test through the live Spring Boot ingestion pipeline.
              </p>
            </div>
          )}

          {submitting && (
            <div className="loading-state">
              <Zap size={32} className="spin-icon text-blue" />
              <p>Simulating webhook delivery against backend...</p>
              <span className="loading-hint">
                Verifying HMAC, unique database keys, transaction locks, and delivery attempts.
              </span>
            </div>
          )}

          {response && (
            <div className="simulator-results-view">
              {/* Result Status Banner */}
              <div
                className={`result-status-banner ${
                  response.status === 'SUCCESS' ? 'banner-success' : 'banner-warning'
                }`}
              >
                <div className="banner-icon-group">
                  {response.status === 'SUCCESS' ? (
                    <CheckCircle2 size={20} className="text-green" />
                  ) : (
                    <AlertCircle size={20} className="text-amber" />
                  )}
                  <div>
                    <div className="result-headline">
                      <span className="result-scenario-tag">{response.scenario}</span>
                      <span className="result-status-tag">{response.status}</span>
                    </div>
                    <p className="result-message">{response.message}</p>
                  </div>
                </div>
              </div>

              {/* Returned Ingestion Events */}
              <div className="result-events-section">
                <div className="section-subheading">
                  <Layers size={16} />
                  <span>Resulting Events Ingested ({response.events.length})</span>
                </div>

                {response.events.length === 0 ? (
                  <div className="empty-mini-state">
                    No event persisted (as expected for rejected scenario like INVALID_SIGNATURE).
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Event ID</th>
                          <th>Resource</th>
                          <th>Seq</th>
                          <th>Status</th>
                          <th>Occurred</th>
                          <th>Audit</th>
                        </tr>
                      </thead>
                      <tbody>
                        {response.events.map((evt) => {
                          const badge = getStatusBadge(evt.status);
                          return (
                            <tr key={evt.id}>
                              <td>
                                <span className="cell-monospace">{evt.eventId}</span>
                              </td>
                              <td>{evt.resourceId}</td>
                              <td>
                                <span className="cell-monospace">#{evt.sequence}</span>
                              </td>
                              <td>
                                <span className={`status-badge ${badge.className}`}>
                                  {badge.label}
                                </span>
                              </td>
                              <td>
                                <span className="cell-timestamp">{formatDate(evt.occurredAt)}</span>
                              </td>
                              <td>
                                <button
                                  type="button"
                                  className="btn btn-secondary btn-xs"
                                  onClick={() => setInspectedEvent(evt)}
                                  title="Inspect delivery attempts"
                                >
                                  <History size={13} />
                                  <span>Attempts</span>
                                </button>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              {/* Raw JSON Debug View */}
              <div className="json-preview-group">
                <span className="preview-label">API Response JSON:</span>
                <pre className="code-block">{JSON.stringify(response, null, 2)}</pre>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Modal for Delivery Attempts */}
      {inspectedEvent && (
        <DeliveryAttemptsModal
          event={inspectedEvent}
          onClose={() => setInspectedEvent(null)}
        />
      )}
    </div>
  );
};
