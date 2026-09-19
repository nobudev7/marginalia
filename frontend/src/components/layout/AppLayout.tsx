import { ShieldCheck, CheckCircle2, RefreshCw, KeyRound, Sparkles } from 'lucide-react';
import { Header } from './Header';
import { useAuth } from '../../hooks/useAuth';

export function AppLayout() {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-paper-100 text-ink-900 flex flex-col selection:bg-amberAccent-100 selection:text-amberAccent-800">
      <Header />

      <main className="flex-1 max-w-5xl mx-auto w-full px-6 py-8">
        {/* Step 4.2 Success Banner */}
        <div className="mb-8 pb-6 border-b border-paper-300">
          <div className="flex items-center space-x-2 text-emerald-700 text-xs font-semibold uppercase tracking-wider mb-2">
            <CheckCircle2 className="w-4 h-4" />
            <span>Step 4.2 Verified — Authenticated Session Active</span>
          </div>
          <h2 className="font-serif text-3xl sm:text-4xl font-normal text-ink-900 tracking-tight">
            Welcome back, {user?.displayName || 'Reader'}
          </h2>
          <p className="mt-2 text-ink-600 text-sm sm:text-base max-w-2xl leading-relaxed">
            Your session has been validated through the Marginalia authentication gate.
            Session state is stored in MySQL via Spring Session JDBC and persists across page reloads.
          </p>
        </div>

        {/* User Session Profile Card */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <div className="p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-xs md:col-span-2">
            <h3 className="font-serif text-lg font-semibold text-ink-900 mb-3 flex items-center space-x-2">
              <KeyRound className="w-4 h-4 text-amberAccent-700" />
              <span>Current Session Credentials</span>
            </h3>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between py-2 border-b border-paper-200">
                <span className="text-ink-500">Authenticated Email:</span>
                <span className="font-mono font-semibold text-ink-900">{user?.email}</span>
              </div>
              <div className="flex justify-between py-2 border-b border-paper-200">
                <span className="text-ink-500">Display Name:</span>
                <span className="font-medium text-ink-800">{user?.displayName || '—'}</span>
              </div>
              <div className="flex justify-between py-2 border-b border-paper-200">
                <span className="text-ink-500">User ID (Database):</span>
                <span className="font-mono text-ink-700">#{user?.id}</span>
              </div>
              <div className="flex justify-between py-2">
                <span className="text-ink-500">Session Storage:</span>
                <span className="text-emerald-700 font-semibold flex items-center">
                  <ShieldCheck className="w-3.5 h-3.5 mr-1" />
                  MySQL Persistent Session (90-Day Cookie)
                </span>
              </div>
            </div>
          </div>

          {/* Persistence Test Card */}
          <div className="p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-xs flex flex-col justify-between">
            <div>
              <h3 className="font-serif text-lg font-semibold text-ink-900 mb-2 flex items-center space-x-2">
                <RefreshCw className="w-4 h-4 text-amberAccent-700" />
                <span>Persistence Check</span>
              </h3>
              <p className="text-xs text-ink-600 leading-relaxed mb-3">
                Reload this page (<kbd className="px-1 py-0.5 bg-paper-200 rounded font-mono text-[10px]">Cmd+R</kbd> or <kbd className="px-1 py-0.5 bg-paper-200 rounded font-mono text-[10px]">F5</kbd>).
                The authentication gate preserves your session without returning to the login view.
              </p>
            </div>
            <div className="p-2.5 rounded-lg bg-paper-100 border border-paper-200 text-[11px] text-ink-600 flex items-center space-x-2">
              <Sparkles className="w-3.5 h-3.5 text-amberAccent-700 shrink-0" />
              <span>Click "Log Out" in header to test returning to the login gate.</span>
            </div>
          </div>
        </div>

        {/* Roadmap Next Step Banner */}
        <div className="p-5 rounded-xl border border-paper-300 bg-paper-50/70 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 text-xs text-ink-600">
          <div>
            <span className="font-semibold text-ink-900 block mb-0.5">Ready for Step 4.3</span>
            <span>Navigation & Collapsible Sidebar (feeds, category accordions, unread counts, and subscription modal).</span>
          </div>
          <span className="px-3 py-1 rounded-full bg-paper-200 text-ink-700 font-mono text-[11px] shrink-0">
            Step 4.2 Complete
          </span>
        </div>
      </main>
    </div>
  );
}
