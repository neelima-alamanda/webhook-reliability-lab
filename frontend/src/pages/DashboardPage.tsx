import React, { useEffect, useState } from 'react';
import type { SourceResponse, WebhookEventResponse } from '../types';
import { sourceApi, eventApi, ApiException } from '../api/client';
import { formatDate, getStatusBadge } from '../utils/formatters';
import type { PageView } from '../components/Navbar';
import {
  Server,
  Activity,
  Copy,
  AlertTriangle,
  PlusCircle,
  RefreshCw,
  PlayCircle,
  ExternalLink,
  Layers,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';

interface DashboardPageProps {
  onNavigate: (page: PageView) => void;
}

export const DashboardPage: React.FC<DashboardPageProps> = ({ onNavigate }) => {
  const [sources, setSources] = useState<SourceResponse[]>([]);
  const [events, setEvents] = useState<WebhookEventResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // New Source creation modal / form state
  const [showNewSource, setShowNewSource] = useState<boolean>(false);
  const [newSourceName, setNewSourceName] = useState<string>('');
  const [creatingSource, setCreatingSource] = useState<boolean>(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [sourcesData, eventsData] = await Promise.all([
        sourceApi.getSources(),
        eventApi.getEvents(),
      ]);
      setSources(sourcesData);
      setEvents(eventsData);
    } catch (err) {
      if (err instanceof ApiException) {
        setError(err.message);
      } else {
        setError('Failed to fetch dashboard data from backend');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleCreateSource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSourceName.trim()) return;

    setCreatingSource(true);
    setCreateError(null);
    try {
      const created = await sourceApi.createSource({ name: newSourceName.trim() });
      setSources((prev) => [...prev, created]);
      setNewSourceName('');
      setShowNewSource(false);
    } catch (err) {
      if (err instanceof ApiException) {
        setCreateError(err.message);
      } else {
        setCreateError('Could not create webhook source');
      }
    } finally {
      setCreatingSource(false);
    }
  };

  const copyUrl = (sourceId: number) => {
    const url = `${window.location.origin}/api/webhooks/${sourceId}`;
    navigator.clipboard.writeText(url);
    setCopiedId(sourceId);
    setTimeout(() => setCopiedId(null), 2000);
  };

  // Metrics computed purely from real API data
  const totalEvents = events.length;
  const deliveredCount = events.filter((e) => e.status === 'DELIVERED').length;
  const duplicateCount = events.filter((e) => e.status === 'DUPLICATE').length;
  const outOfOrderCount = events.filter((e) => e.status === 'OUT_OF_ORDER').length;
  const failedCount = events.filter((e) => e.status === 'FAILED').length;

  return (
    <div className="page-container">
      {/* Top Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Operational Reliability Dashboard</h1>
          <p className="page-description">
            High-level metrics and source configuration derived directly from the ingestion pipeline.
          </p>
        </div>
        <div className="header-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={fetchData}
            disabled={loading}
          >
            <RefreshCw size={15} className={loading ? 'spin-icon' : ''} />
            <span>Refresh</span>
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowNewSource(true)}
          >
            <PlusCircle size={15} />
            <span>New Webhook Source</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="error-banner">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Metrics Row */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Active Sources</span>
            <div className="stat-icon-wrapper stat-blue">
              <Server size={18} />
            </div>
          </div>
          <div className="stat-value">{sources.length}</div>
          <span className="stat-footer">Owned webhook sources</span>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Total Events</span>
            <div className="stat-icon-wrapper stat-indigo">
              <Layers size={18} />
            </div>
          </div>
          <div className="stat-value">{totalEvents}</div>
          <span className="stat-footer">Total recorded events</span>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Delivered Events</span>
            <div className="stat-icon-wrapper stat-green">
              <CheckCircle2 size={18} />
            </div>
          </div>
          <div className="stat-value">{deliveredCount}</div>
          <span className="stat-footer">Successfully processed</span>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Duplicates Caught</span>
            <div className="stat-icon-wrapper stat-amber">
              <Copy size={18} />
            </div>
          </div>
          <div className="stat-value">{duplicateCount}</div>
          <span className="stat-footer">Deduplicated idempotently</span>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Out of Order</span>
            <div className="stat-icon-wrapper stat-purple">
              <AlertTriangle size={18} />
            </div>
          </div>
          <div className="stat-value">{outOfOrderCount}</div>
          <span className="stat-footer">Sequence regressions detected</span>
        </div>

        <div className="stat-card">
          <div className="stat-header">
            <span className="stat-label">Failed Events</span>
            <div className="stat-icon-wrapper stat-red">
              <AlertCircle size={18} />
            </div>
          </div>
          <div className="stat-value">{failedCount}</div>
          <span className="stat-footer">Processing failures</span>
        </div>
      </div>

      {/* Navigation Quick Cards */}
      <div className="quick-actions-grid">
        <div className="action-card" onClick={() => onNavigate('simulator')}>
          <div className="action-card-icon action-simulator">
            <PlayCircle size={22} />
          </div>
          <div className="action-card-content">
            <h3 className="action-card-title">Fault & Concurrency Simulator</h3>
            <p className="action-card-desc">
              Dispatch simulated webhooks: test concurrent duplicate delivery, out-of-order sequencing, late events, and HMAC tampering.
            </p>
          </div>
          <ExternalLink size={16} className="action-card-arrow" />
        </div>

        <div className="action-card" onClick={() => onNavigate('events')}>
          <div className="action-card-icon action-events">
            <Activity size={22} />
          </div>
          <div className="action-card-content">
            <h3 className="action-card-title">Event Ingestion Audit</h3>
            <p className="action-card-desc">
              Filter by source and status, inspect sequence numbers, timestamps, and audit raw delivery attempts per event.
            </p>
          </div>
          <ExternalLink size={16} className="action-card-arrow" />
        </div>
      </div>

      {/* Webhook Sources List Section */}
      <div className="card dashboard-section">
        <div className="section-header">
          <div>
            <h2 className="section-title">Webhook Sources</h2>
            <p className="section-subtitle">
              Configured endpoints receiving webhooks with HMAC-SHA256 signature validation.
            </p>
          </div>
        </div>

        {loading && (
          <div className="loading-state">
            <RefreshCw size={24} className="spin-icon" />
            <span>Loading sources from backend...</span>
          </div>
        )}

        {!loading && sources.length === 0 && (
          <div className="empty-state">
            <Server size={32} className="empty-state-icon" />
            <p className="empty-state-title">No webhook sources configured yet</p>
            <p className="empty-state-desc">
              Create your first webhook source to start receiving and simulating events.
            </p>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setShowNewSource(true)}
            >
              <PlusCircle size={15} />
              <span>Create Webhook Source</span>
            </button>
          </div>
        )}

        {!loading && sources.length > 0 && (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Source ID</th>
                  <th>Source Name</th>
                  <th>Created At</th>
                  <th>Public Ingestion URL</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sources.map((src) => (
                  <tr key={src.id}>
                    <td>
                      <span className="cell-monospace">#{src.id}</span>
                    </td>
                    <td>
                      <span className="cell-strong">{src.name}</span>
                    </td>
                    <td>
                      <span className="cell-timestamp">{formatDate(src.createdAt)}</span>
                    </td>
                    <td>
                      <code className="code-endpoint">
                        /api/webhooks/{src.id}
                      </code>
                    </td>
                    <td>
                      <div className="table-row-actions">
                        <button
                          type="button"
                          className="btn-table-action"
                          onClick={() => copyUrl(src.id)}
                          title="Copy full ingestion endpoint URL"
                        >
                          <Copy size={14} />
                          <span>{copiedId === src.id ? 'Copied!' : 'Copy URL'}</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Recent Events Table Preview */}
      <div className="card dashboard-section">
        <div className="section-header">
          <div>
            <h2 className="section-title">Recent Event Feed</h2>
            <p className="section-subtitle">
              Most recently recorded events across your registered webhook sources.
            </p>
          </div>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => onNavigate('events')}
          >
            <span>View All ({events.length})</span>
            <ExternalLink size={14} />
          </button>
        </div>

        {events.length === 0 ? (
          <div className="empty-state">
            <Activity size={32} className="empty-state-icon" />
            <p className="empty-state-title">No events recorded yet</p>
            <p className="empty-state-desc">
              Use the Simulator to trigger normal, duplicate, or out-of-order test deliveries.
            </p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Event ID</th>
                  <th>Source</th>
                  <th>Resource ID</th>
                  <th>Seq</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Occurred At</th>
                  <th>Received At</th>
                </tr>
              </thead>
              <tbody>
                {events.slice(0, 5).map((evt) => {
                  const badge = getStatusBadge(evt.status);
                  return (
                    <tr key={evt.id}>
                      <td>
                        <span className="cell-monospace">{evt.eventId}</span>
                      </td>
                      <td>
                        <span className="cell-monospace">#{evt.sourceId}</span>
                      </td>
                      <td>{evt.resourceId}</td>
                      <td>
                        <span className="cell-monospace">#{evt.sequence}</span>
                      </td>
                      <td>
                        <span className="cell-tag">{evt.type}</span>
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
                        <span className="cell-timestamp">{formatDate(evt.receivedAt)}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create Source Modal */}
      {showNewSource && (
        <div className="modal-backdrop" onClick={() => setShowNewSource(false)}>
          <div className="modal-container modal-sm" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-title-group">
                <div className="modal-icon-badge">
                  <PlusCircle size={18} />
                </div>
                <div>
                  <h3 className="modal-title">Create Webhook Source</h3>
                  <p className="modal-subtitle">Register a new source to ingest HMAC signed events.</p>
                </div>
              </div>
            </div>

            <form onSubmit={handleCreateSource}>
              <div className="modal-body">
                {createError && (
                  <div className="error-banner">
                    <AlertCircle size={16} />
                    <span>{createError}</span>
                  </div>
                )}

                <div className="form-group">
                  <label htmlFor="source-name">Source Name</label>
                  <input
                    id="source-name"
                    type="text"
                    required
                    placeholder="e.g. Stripe Payments, Shopify Store, GitHub CI"
                    value={newSourceName}
                    onChange={(e) => setNewSourceName(e.target.value)}
                    autoFocus
                  />
                  <span className="form-hint">
                    A cryptographically secure HMAC secret will be generated server-side.
                  </span>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowNewSource(false)}
                  disabled={creatingSource}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={creatingSource || !newSourceName.trim()}
                >
                  {creatingSource ? 'Creating...' : 'Create Source'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
