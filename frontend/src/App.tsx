import { Loader2 } from 'lucide-react';
import { AuthProvider } from './context/AuthContext';
import { useAuth } from './hooks/useAuth';
import { LoginPage } from './components/auth/LoginPage';
import { AppLayout } from './components/layout/AppLayout';

function AppContent() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen bg-paper-100 flex flex-col items-center justify-center text-ink-600">
        <div className="w-10 h-10 rounded-xl bg-amberAccent-700 text-paper-50 flex items-center justify-center font-serif font-bold text-xl shadow-xs mb-4">
          M
        </div>
        <div className="flex items-center space-x-2 text-xs font-medium text-ink-500">
          <Loader2 className="w-4 h-4 animate-spin text-amberAccent-700" />
          <span>Verifying session...</span>
        </div>
      </div>
    );
  }

  if (!user) {
    return <LoginPage />;
  }

  return <AppLayout />;
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
