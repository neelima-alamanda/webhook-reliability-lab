import React, { useEffect, useState } from 'react';
import type { DeliveryAttemptResponse, WebhookEventResponse } from '../types';
import { eventApi, ApiException } from '../api/client';
import { formatDate, getOutcomeBadge } from '../utils/formatters';
import { X, History, AlertCircle, RefreshCw } from 'lucide-react';

interface DeliveryAttemptsModalProps {
  event: WebhookEventResponse;
  onClose: () => void;
}

export const DeliveryAttemptsModal: React.FC<DeliveryAttemptsModalProps> = ({
  event,
  onClose,
}) => {
  const [attempts, setAttempts] = useState<DeliveryAttemptResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAttempts = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await eventApi.getDeliveryAttempts(event.id);
      setAttempts(data);
    } catch (err) {
      if (err instanceof ApiException) {
        setError(err.message);
      } else {
        setError('Failed to load delivery attempts');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAttempts();
  }, [event.id]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal-container"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="modal-header">
          <div className="modal-title-group">
            <div className="modal-icon-badge">
              <History size={18} />
            </div>
            <div>
              <h3 className="modal-title">Delivery Attempt Audit</h3>
              <p className="modal-subtitle">
                History for event <code className="code-badge">{event.eventId}</code> (DB #{event.id})
              </p>
            </div>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        <div className="modal-meta-bar">
          <div className="meta-item">
            <span className="meta-label">Resource ID:</span>
            <span className="meta-value">{event.resourceId}</span>
          </div>
          <div className="meta-item">
            <span className="meta-label">Sequence:</span>
            <span className="meta-value">#{event.sequence}</span>
          </div>
          <div className="meta-item">
            <span className="meta-label">Event Type:</span>
            <span className="meta-value">{event.type}</span>
          </div>
          <div className="meta-item">
            <span className="meta-label">Current Status:</span>
            <span className="meta-value">{event.currentStatus || event.status}</span>
          </div>
        </div>

        <div className="modal-body">
          {loading && (
            <div className="loading-state">
              <RefreshCw size={24} className="spin-icon" />
              <span>Fetching delivery attempt log from backend...</span>
            </div>
          )}

          {error && (
            <div className="error-banner">
              <AlertCircle size={18} />
              <span>{error}</span>
              <button
                type="button"
                className="btn-retry"
                onClick={fetchAttempts}
              >
                Retry
              </button>
            </div>
          )}

          {!loading && !error && attempts.length === 0 && (
            <div className="empty-state">
              <p>No delivery attempts recorded for this event.</p>
            </div>
          )}

          {!loading && !error && attempts.length > 0 && (
            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Attempt ID</th>
                    <th>Received At</th>
                    <th>Outcome</th>
                    <th>Pipeline Details</th>
                  </tr>
                </thead>
                <tbody>
                  {attempts.map((attempt) => {
                    const badge = getOutcomeBadge(attempt.outcome);
                    return (
                      <tr key={attempt.id}>
                        <td>
                          <span className="cell-monospace">#{attempt.id}</span>
                        </td>
                        <td>
                          <span className="cell-timestamp">{formatDate(attempt.receivedAt)}</span>
                        </td>
                        <td>
                          <span className={`status-badge ${badge.className}`}>
                            {badge.label}
                          </span>
                        </td>
                        <td>
                          <span className="cell-details">{attempt.details || '—'}</span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
