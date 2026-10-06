export type Role = 'USER' | 'ADMIN';

export interface AuthResponse {
  token: string;
  username: string;
  role: Role;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface SourceResponse {
  id: number;
  name: string;
  createdAt: string;
}

export interface CreateSourceRequest {
  name: string;
}

export type EventStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'DELIVERED'
  | 'FAILED'
  | 'DUPLICATE'
  | 'OUT_OF_ORDER';

export interface WebhookEventResponse {
  id: number;
  sourceId: number;
  eventId: string;
  resourceId: string;
  sequence: number;
  type: string;
  occurredAt: string;
  receivedAt: string;
  status: EventStatus;
  currentStatus: string | null;
  lastSequence: number | null;
}

export type DeliveryOutcome = 'SUCCESS' | 'FAILURE' | 'DUPLICATE' | 'REJECTED';

export interface DeliveryAttemptResponse {
  id: number;
  eventId: number;
  receivedAt: string;
  outcome: DeliveryOutcome;
  details: string;
}

export type SimulatorScenario =
  | 'NORMAL'
  | 'DUPLICATE'
  | 'CONCURRENT_DUPLICATE'
  | 'OUT_OF_ORDER'
  | 'LATE'
  | 'INVALID_SIGNATURE';

export interface SimulatorRequest {
  sourceId: number;
  eventId: string;
  resourceId: string;
  sequence: number;
  type: string;
  occurredAt: string;
  currentStatus?: string;
  scenario: SimulatorScenario;
}

export interface SimulatorResponse {
  scenario: string;
  status: string;
  message: string;
  events: WebhookEventResponse[];
}

export interface ApiError {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  details?: Record<string, string>;
}
