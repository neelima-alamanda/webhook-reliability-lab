import { useState } from 'react';
import { useAuth } from './hooks/useAuth';
import { Navbar, type PageView } from './components/Navbar';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { EventInspectionPage } from './pages/EventInspectionPage';
import { SimulatorPage } from './pages/SimulatorPage';

export function App() {
  const { user, isAuthenticated, logout } = useAuth();
  const [currentPage, setCurrentPage] = useState<PageView>('dashboard');

  if (!isAuthenticated) {
    return <LoginPage onLoginSuccess={() => setCurrentPage('dashboard')} />;
  }

  return (
    <div className="app-root">
      <Navbar
        currentPage={currentPage}
        onNavigate={setCurrentPage}
        user={user}
        onLogout={logout}
      />
      <main className="app-main">
        {currentPage === 'dashboard' && <DashboardPage onNavigate={setCurrentPage} />}
        {currentPage === 'events' && <EventInspectionPage />}
        {currentPage === 'simulator' && (
          <SimulatorPage onNavigateToEvents={() => setCurrentPage('events')} />
        )}
      </main>
    </div>
  );
}

export default App;
