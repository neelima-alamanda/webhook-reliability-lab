import React, { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { ApiException } from '../api/client';
import { Lock, Mail, User as UserIcon, AlertCircle, ArrowRight, CheckCircle2 } from 'lucide-react';

interface LoginPageProps {
  onLoginSuccess: () => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLoginSuccess }) => {
  const { login, register, loading } = useAuth();

  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setSuccessNotice(null);

    try {
      if (mode === 'login') {
        await login({ email: email.trim(), password });
        onLoginSuccess();
      } else {
        await register({
          username: username.trim(),
          email: email.trim(),
          password,
        });
        setSuccessNotice('Account created successfully! You are now logged in.');
        onLoginSuccess();
      }
    } catch (err) {
      if (err instanceof ApiException) {
        setError(err.message);
        if (err.details) {
          setFieldErrors(err.details);
        }
      } else {
        setError('Network error or server unreachable. Please verify Spring Boot is running on port 8080.');
      }
    }
  };

  return (
    <div className="login-page-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-badge-icon">
            <Lock size={24} />
          </div>
          <h1 className="login-title">Webhook Reliability Lab</h1>
          <p className="login-subtitle">
            Sign in to access idempotent ingestion controls, audits, and failure simulators.
          </p>
        </div>

        <div className="auth-mode-switch">
          <button
            type="button"
            className={`switch-tab ${mode === 'login' ? 'active' : ''}`}
            onClick={() => {
              setMode('login');
              setError(null);
              setFieldErrors({});
            }}
          >
            Sign In
          </button>
          <button
            type="button"
            className={`switch-tab ${mode === 'register' ? 'active' : ''}`}
            onClick={() => {
              setMode('register');
              setError(null);
              setFieldErrors({});
            }}
          >
            Register Account
          </button>
        </div>

        {error && (
          <div className="auth-error-banner">
            <AlertCircle size={18} className="shrink-0" />
            <div className="error-text-content">
              <strong>Authentication Error:</strong>
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

        {successNotice && (
          <div className="auth-success-banner">
            <CheckCircle2 size={18} />
            <span>{successNotice}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="auth-form" noValidate>
          {mode === 'register' && (
            <div className="form-group">
              <label htmlFor="reg-username">Username</label>
              <div className="input-with-icon">
                <UserIcon size={16} className="input-icon" />
                <input
                  id="reg-username"
                  type="text"
                  required
                  placeholder="e.g. dev_engineer"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className={fieldErrors.username ? 'input-error' : ''}
                />
              </div>
              {fieldErrors.username && (
                <span className="field-error-text">{fieldErrors.username}</span>
              )}
            </div>
          )}

          <div className="form-group">
            <label htmlFor="auth-email">Email Address</label>
            <div className="input-with-icon">
              <Mail size={16} className="input-icon" />
              <input
                id="auth-email"
                type="email"
                required
                placeholder="developer@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={fieldErrors.email ? 'input-error' : ''}
              />
            </div>
            {fieldErrors.email && (
              <span className="field-error-text">{fieldErrors.email}</span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="auth-password">Password</label>
            <div className="input-with-icon">
              <Lock size={16} className="input-icon" />
              <input
                id="auth-password"
                type="password"
                required
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={fieldErrors.password ? 'input-error' : ''}
              />
            </div>
            {fieldErrors.password && (
              <span className="field-error-text">{fieldErrors.password}</span>
            )}
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-block"
            disabled={loading}
          >
            {loading ? (
              <span>Authenticating...</span>
            ) : (
              <>
                <span>{mode === 'login' ? 'Sign In to Dashboard' : 'Create Account'}</span>
                <ArrowRight size={16} />
              </>
            )}
          </button>
        </form>

        <div className="login-footer">
          <p>
            Connected to Spring Boot backend on{' '}
            <code className="code-badge">
              {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}
            </code>
          </p>
        </div>
      </div>
    </div>
  );
};
