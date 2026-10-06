import type {
  AuthResponse,
  CreateSourceRequest,
  DeliveryAttemptResponse,
  EventStatus,
  LoginRequest,
  RegisterRequest,
  SimulatorRequest,
  SimulatorResponse,
  SourceResponse,
  WebhookEventResponse,
  ApiError,
} from '../types';

// Use same-origin proxy in development to avoid browser CORS issues,
// or fallback to configured VITE_API_BASE_URL.
const getBaseUrl = (): string => {
  if (typeof window !== 'undefined' && window.location.port === '5173') {
    return '';
  }
  return import.meta.env.VITE_API_BASE_URL || '';
};

const BASE_URL = getBaseUrl();

export class ApiException extends Error {
  status: number;
  error: string;
  details?: Record<string, string>;

  constructor(apiError: ApiError) {
    super(apiError.message || apiError.error || 'An unexpected API error occurred');
    this.name = 'ApiException';
    this.status = apiError.status;
    this.error = apiError.error;
    this.details = apiError.details;
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${BASE_URL}${endpoint}`;
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const token = localStorage.getItem('auth_token');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorData: ApiError;
    try {
      errorData = await response.json();
    } catch {
      errorData = {
        status: response.status,
        error: response.statusText,
        message: `HTTP ${response.status}: ${response.statusText}`,
      };
    }

    // Auto-logout if token is expired or unauthorized (except on login/register endpoints)
    if (response.status === 401 && !endpoint.startsWith('/api/auth/')) {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      window.dispatchEvent(new CustomEvent('auth:expired'));
    }

    throw new ApiException(errorData);
  }

  // Handle empty bodies (204 No Content, etc.)
  if (response.status === 204) {
    return {} as T;
  }

  return response.json();
}

export const authApi = {
  login: (data: LoginRequest): Promise<AuthResponse> =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  register: (data: RegisterRequest): Promise<AuthResponse> =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

export const sourceApi = {
  getSources: (): Promise<SourceResponse[]> =>
    request<SourceResponse[]>('/api/sources'),

  createSource: (data: CreateSourceRequest): Promise<SourceResponse> =>
    request<SourceResponse>('/api/sources', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

export const eventApi = {
  getEvents: (sourceId?: number, status?: EventStatus): Promise<WebhookEventResponse[]> => {
    const params = new URLSearchParams();
    if (sourceId !== undefined && sourceId !== null) {
      params.append('sourceId', sourceId.toString());
    }
    if (status) {
      params.append('status', status);
    }
    const queryString = params.toString();
    const endpoint = `/api/events${queryString ? `?${queryString}` : ''}`;
    return request<WebhookEventResponse[]>(endpoint);
  },

  getDeliveryAttempts: (id: number): Promise<DeliveryAttemptResponse[]> =>
    request<DeliveryAttemptResponse[]>(`/api/events/${id}/attempts`),
};

export const simulatorApi = {
  send: (data: SimulatorRequest): Promise<SimulatorResponse> =>
    request<SimulatorResponse>('/api/simulator/send', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};
