import React from 'react';
import type { UserSession } from '../hooks/useAuth';
import { Activity, LayoutDashboard, PlayCircle, LogOut, ShieldCheck } from 'lucide-react';

export type PageView = 'dashboard' | 'events' | 'simulator';

interface NavbarProps {
  currentPage: PageView;
  onNavigate: (page: PageView) => void;
  user: UserSession | null;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentPage,
  onNavigate,
  user,
  onLogout,
}) => {
  return (
    <header className="navbar">
      <div className="navbar-container">
        <div className="navbar-left">
          <div className="navbar-brand" onClick={() => onNavigate('dashboard')}>
            <span className="brand-icon">⚡</span>
            <div className="brand-text">
              <span className="brand-title">Webhook Reliability Lab</span>
              <span className="brand-subtitle">Idempotency & Fault-Tolerance Engine</span>
            </div>
          </div>

          <nav className="navbar-nav">
            <button
              type="button"
              className={`nav-item ${currentPage === 'dashboard' ? 'active' : ''}`}
              onClick={() => onNavigate('dashboard')}
            >
              <LayoutDashboard size={16} />
              <span>Dashboard</span>
            </button>

            <button
              type="button"
              className={`nav-item ${currentPage === 'events' ? 'active' : ''}`}
              onClick={() => onNavigate('events')}
            >
              <Activity size={16} />
              <span>Event Inspection</span>
            </button>

            <button
              type="button"
              className={`nav-item ${currentPage === 'simulator' ? 'active' : ''}`}
              onClick={() => onNavigate('simulator')}
            >
              <PlayCircle size={16} />
              <span>Simulator</span>
            </button>
          </nav>
        </div>

        <div className="navbar-right">
          {user && (
            <div className="user-profile">
              <div className="user-avatar">
                <ShieldCheck size={16} className="user-shield-icon" />
                <span className="username">{user.username}</span>
                <span className="role-tag">{user.role}</span>
              </div>
              <button
                type="button"
                className="btn-logout"
                onClick={onLogout}
                title="Sign out"
              >
                <LogOut size={15} />
                <span>Sign Out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
