import type { DeliveryOutcome, EventStatus, SimulatorScenario } from '../types';

export const formatDate = (dateStr?: string | null): string => {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    });
  } catch {
    return dateStr;
  }
};

export const getStatusBadge = (status: EventStatus | string) => {
  switch (status) {
    case 'DELIVERED':
      return {
        label: 'DELIVERED',
        className: 'badge-delivered',
        description: 'Successfully processed and delivered',
      };
    case 'DUPLICATE':
      return {
        label: 'DUPLICATE',
        className: 'badge-duplicate',
        description: 'Identical (sourceId, eventId) was already ingested; duplicate recorded',
      };
    case 'OUT_OF_ORDER':
      return {
        label: 'OUT OF ORDER',
        className: 'badge-out-of-order',
        description: 'Received with a lower sequence than a prior event on the same resource',
      };
    case 'FAILED':
      return {
        label: 'FAILED',
        className: 'badge-failed',
        description: 'Processing encountered an error',
      };
    case 'PROCESSING':
      return {
        label: 'PROCESSING',
        className: 'badge-processing',
        description: 'Currently in pipeline execution',
      };
    case 'PENDING':
      return {
        label: 'PENDING',
        className: 'badge-pending',
        description: 'Queued or awaiting processing',
      };
    default:
      return {
        label: status || 'UNKNOWN',
        className: 'badge-default',
        description: '',
      };
  }
};

export const getOutcomeBadge = (outcome: DeliveryOutcome | string) => {
  switch (outcome) {
    case 'SUCCESS':
      return {
        label: 'SUCCESS',
        className: 'badge-delivered',
      };
    case 'DUPLICATE':
      return {
        label: 'DUPLICATE',
        className: 'badge-duplicate',
      };
    case 'REJECTED':
      return {
        label: 'REJECTED',
        className: 'badge-rejected',
      };
    case 'FAILURE':
      return {
        label: 'FAILURE',
        className: 'badge-failed',
      };
    default:
      return {
        label: outcome || 'UNKNOWN',
        className: 'badge-default',
      };
  }
};

export interface ScenarioInfo {
  scenario: SimulatorScenario;
  title: string;
  shortDesc: string;
  fullDesc: string;
  expectedBehavior: string;
}

export const SCENARIO_DETAILS: Record<SimulatorScenario, ScenarioInfo> = {
  NORMAL: {
    scenario: 'NORMAL',
    title: 'Normal Ingestion',
    shortDesc: 'Single delivery with valid HMAC',
    fullDesc: 'Simulates a standard, valid webhook delivery with a newly generated HMAC-SHA256 signature.',
    expectedBehavior: 'Returns HTTP 200 with DELIVERED status and a single delivery attempt recorded as SUCCESS.',
  },
  DUPLICATE: {
    scenario: 'DUPLICATE',
    title: 'Sequential Duplicate',
    shortDesc: 'Sends the identical event twice in a row',
    fullDesc: 'Simulates sending the exact same payload (same sourceId, eventId, sequence) twice sequentially.',
    expectedBehavior: 'First request succeeds with DELIVERED. Second request detects existing record, avoids re-processing, records DUPLICATE attempt, and returns idempotent HTTP 200.',
  },
  CONCURRENT_DUPLICATE: {
    scenario: 'CONCURRENT_DUPLICATE',
    title: 'Concurrent Duplicate',
    shortDesc: 'Sends identical events simultaneously in parallel threads',
    fullDesc: 'Dispatches 2 identical requests at the exact same moment across parallel threads with a CountDownLatch barrier.',
    expectedBehavior: 'Database unique constraint (source_id, event_id) and transaction isolation prevent race conditions. One thread persists event, other thread catches DataIntegrityViolationException and persists duplicate attempt.',
  },
  OUT_OF_ORDER: {
    scenario: 'OUT_OF_ORDER',
    title: 'Out-of-Order Delivery',
    shortDesc: 'Sends sequence N+1 before sequence N',
    fullDesc: 'Simulates network latency or retries where event with sequence N+1 arrives before event with sequence N on the same resource.',
    expectedBehavior: 'First event (N+1) updates resource sequence. When event N arrives, it detects sequence regression and flags event as OUT_OF_ORDER to protect state consistency.',
  },
  LATE: {
    scenario: 'LATE',
    title: 'Late Event',
    shortDesc: 'Sends event with occurredAt older than threshold (300s)',
    fullDesc: 'Simulates a delayed delivery where event occurredAt is older than the configured threshold (default: 300 seconds).',
    expectedBehavior: 'Application flags the event as stale/late in the delivery attempt details, demonstrating awareness of delivery latency.',
  },
  INVALID_SIGNATURE: {
    scenario: 'INVALID_SIGNATURE',
    title: 'Invalid HMAC Signature',
    shortDesc: 'Sends payload with corrupted/tampered signature',
    fullDesc: 'Simulates a spoofed or tampered webhook transmission using an invalid HMAC-SHA256 signature header.',
    expectedBehavior: 'Pipeline detects signature mismatch and immediately rejects with HTTP 401 Unauthorized (InvalidSignatureException). The event is NOT persisted.',
  },
};
