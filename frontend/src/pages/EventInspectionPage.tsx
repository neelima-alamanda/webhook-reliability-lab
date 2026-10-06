import React, { useEffect, useState } from 'react';
import type { EventStatus, SourceResponse, WebhookEventResponse } from '../types';
import { eventApi, sourceApi, ApiException } from '../api/client';
import { formatDate, getStatusBadge } from '../utils/formatters';
import { DeliveryAttemptsModal } from '../components/DeliveryAttemptsModal';
import {
  Activity,
  Filter,
  RefreshCw,
  Search,
  History,
  AlertCircle,
  Copy,
  CheckCircle2,
} from 'lucide-react';

export const EventInspectionPage: React.FC = () => {
  const [events, setEvents] = useState<WebhookEventResponse[]>([]);
  const [sources, setSources] = useState<SourceResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [selectedSourceId, setSelectedSourceId] = useState<string>('all');
  const [selectedStatus, setSelectedStatus] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Selected event for delivery attempts modal
  const [inspectedEvent, setInspectedEvent] = useState<WebhookEventResponse | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const fetchSources = async () => {
    try {
      const data = await sourceApi.getSources();
      setSources(data);
    } catch {
      // Ignored non-fatal error
    }
  };

  const fetchEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const sourceId = selectedSourceId !== 'all' ? Number(selectedSourceId) : undefined;
      const status = selectedStatus !== 'all' ? (selectedStatus as EventStatus) : undefined;
      const data = await eventApi.getEvents(sourceId, status);
      setEvents(data);
    } catch (err) {
      if (err instanceof ApiException) {
        setError(err.message);
      } else {
        setError('Failed to load webhook events');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSources();
  }, []);

  useEffect(() => {
    fetchEvents();
  }, [selectedSourceId, selectedStatus]);

  const copyEventId = (id: string) => {
    navigator.clipboard.writeText(id);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 1500);
  };

  // Client-side text filter on top of the backend-filtered results
  const filteredEvents = events.filter((e) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      e.eventId.toLowerCase().includes(q) ||
      e.resourceId.toLowerCase().includes(q) ||
      e.type.toLowerCase().includes(q) ||
      (e.currentStatus && e.currentStatus.toLowerCase().includes(q))
    );
  });

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Webhook Event Inspection</h1>
          <p className="page-description">
            Audit ingested events, analyze sequence ordering, detect duplicates, and examine delivery attempt lifecycles.
          </p>
        </div>
        <div className="header-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={fetchEvents}
            disabled={loading}
          >
            <RefreshCw size={15} className={loading ? 'spin-icon' : ''} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {error && (
        <div className="error-banner">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Filter Toolbar */}
      <div className="card filter-toolbar">
        <div className="filter-group">
          <div className="filter-item">
            <label htmlFor="filter-source" className="filter-label">
              <Filter size={14} />
              <span>Source</span>
            </label>
            <select
              id="filter-source"
              value={selectedSourceId}
              onChange={(e) => setSelectedSourceId(e.target.value)}
              className="select-control"
            >
              <option value="all">All Sources</option>
              {sources.map((s) => (
                <option key={s.id} value={s.id}>
                  #{s.id} - {s.name}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-item">
            <label htmlFor="filter-status" className="filter-label">
              <span>Status</span>
            </label>
            <select
              id="filter-status"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value)}
              className="select-control"
            >
              <option value="all">All Statuses</option>
              <option value="DELIVERED">DELIVERED</option>
              <option value="DUPLICATE">DUPLICATE</option>
              <option value="OUT_OF_ORDER">OUT OF ORDER</option>
              <option value="FAILED">FAILED</option>
              <option value="PENDING">PENDING</option>
              <option value="PROCESSING">PROCESSING</option>
            </select>
          </div>
        </div>

        <div className="search-box">
          <Search size={15} className="search-icon" />
          <input
            type="text"
            placeholder="Search by eventId, resourceId, type..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="search-input"
          />
          {searchQuery && (
            <button
              type="button"
              className="search-clear"
              onClick={() => setSearchQuery('')}
            >
              ×
            </button>
          )}
        </div>
      </div>

      {/* Events Table Card */}
      <div className="card events-table-card">
        <div className="section-header">
          <div>
            <h2 className="section-title">Ingested Events</h2>
            <p className="section-subtitle">
              Showing {filteredEvents.length} of {events.length} recorded events
            </p>
          </div>
        </div>

        {loading && (
          <div className="loading-state">
            <RefreshCw size={24} className="spin-icon" />
            <span>Loading events from backend...</span>
          </div>
        )}

        {!loading && filteredEvents.length === 0 && (
          <div className="empty-state">
            <Activity size={32} className="empty-state-icon" />
            <p className="empty-state-title">No events found matching your criteria</p>
            <p className="empty-state-desc">
              Try adjusting the filters above or use the Simulator to generate new events.
            </p>
          </div>
        )}

        {!loading && filteredEvents.length > 0 && (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Event ID</th>
                  <th>Source</th>
                  <th>Resource ID</th>
                  <th>Seq</th>
                  <th>Last Seq</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th>Current Status</th>
                  <th>Occurred At</th>
                  <th>Received At</th>
                  <th>Audit</th>
                </tr>
              </thead>
              <tbody>
                {filteredEvents.map((event) => {
                  const badge = getStatusBadge(event.status);
                  return (
                    <tr key={event.id} className={`row-status-${event.status.toLowerCase()}`}>
                      <td>
                        <div className="cell-id-container">
                          <span className="cell-monospace">{event.eventId}</span>
                          <button
                            type="button"
                            className="btn-copy-mini"
                            onClick={() => copyEventId(event.eventId)}
                            title="Copy eventId"
                          >
                            {copiedId === event.eventId ? (
                              <CheckCircle2 size={13} className="text-green" />
                            ) : (
                              <Copy size={13} />
                            )}
                          </button>
                        </div>
                      </td>
                      <td>
                        <span className="cell-monospace">#{event.sourceId}</span>
                      </td>
                      <td>
                        <span className="cell-resource">{event.resourceId}</span>
                      </td>
                      <td>
                        <span className="cell-monospace">#{event.sequence}</span>
                      </td>
                      <td>
                        <span className="cell-monospace">
                          {event.lastSequence !== null && event.lastSequence !== undefined
                            ? `#${event.lastSequence}`
                            : '—'}
                        </span>
                      </td>
                      <td>
                        <span className="cell-tag">{event.type}</span>
                      </td>
                      <td>
                        <span
                          className={`status-badge ${badge.className}`}
                          title={badge.description}
                        >
                          {badge.label}
                        </span>
                      </td>
                      <td>
                        <span className="cell-current-status">
                          {event.currentStatus || '—'}
                        </span>
                      </td>
                      <td>
                        <span className="cell-timestamp">{formatDate(event.occurredAt)}</span>
                      </td>
                      <td>
                        <span className="cell-timestamp">{formatDate(event.receivedAt)}</span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary btn-xs"
                          onClick={() => setInspectedEvent(event)}
                          title="View delivery attempt log"
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
