import { useEffect, useState } from 'react';
import { 
  BookOpen, 
  Bookmark, 
  Rss, 
  Settings, 
  CheckCircle2, 
  ExternalLink, 
  ShieldCheck, 
  Server,
  Layers,
  ChevronRight,
  Sparkles
} from 'lucide-react';
import { apiFetch } from './api/client';
import type { AuthStatus } from './types';

export default function App() {
  const [proxyStatus, setProxyStatus] = useState<{
    loading: boolean;
    connected: boolean;
    data?: unknown;
    error?: string;
  }>({
    loading: true,
    connected: false,
  });

  useEffect(() => {
    async function testBackendConnection() {
      try {
        const data = await apiFetch<AuthStatus>('/api/auth/status');
        setProxyStatus({
          loading: false,
          connected: true,
          data,
        });
      } catch (err) {
        setProxyStatus({
          loading: false,
          connected: false,
          error: err instanceof Error ? err.message : 'Failed to connect to backend',
        });
      }
    }

    testBackendConnection();
  }, []);

  return (
    <div className="min-h-screen bg-paper-100 text-ink-900 flex flex-col selection:bg-amberAccent-100 selection:text-amberAccent-800">
      {/* Top Application Header */}
      <header className="border-b border-paper-300 bg-paper-50 sticky top-0 z-30 px-6 py-3.5 shadow-sm">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-lg bg-amberAccent-700 text-paper-50 flex items-center justify-center font-serif font-bold text-xl shadow-sm">
              M
            </div>
            <div>
              <h1 className="font-serif text-xl font-bold tracking-tight text-ink-900 leading-none">
                Marginalia
              </h1>
              <p className="text-xs text-ink-500 font-sans mt-0.5">
                Personal Reader & Editorial Feed Stream
              </p>
            </div>
          </div>

          {/* Right Status Badge & Actions */}
          <div className="flex items-center space-x-3">
            <div className="flex items-center space-x-2 text-xs px-3 py-1.5 rounded-full border border-paper-300 bg-paper-100 font-medium">
              <Server className="w-3.5 h-3.5 text-ink-500" />
              <span>Vite Proxy:</span>
              {proxyStatus.loading ? (
                <span className="text-ink-500 animate-pulse">Testing connection...</span>
              ) : proxyStatus.connected ? (
                <span className="flex items-center text-emerald-700 font-semibold">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 mr-1.5 animate-ping" />
                  Spring Boot Connected (:8080)
                </span>
              ) : (
                <span className="flex items-center text-amber-700 font-semibold">
                  <span className="w-2 h-2 rounded-full bg-amber-500 mr-1.5" />
                  Backend Offline / Standby
                </span>
              )}
            </div>

            <button
              type="button"
              className="p-2 text-ink-600 hover:text-ink-900 hover:bg-paper-200 rounded-lg transition-colors"
              title="Settings"
            >
              <Settings className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Showcase & Foundation Shell */}
      <main className="flex-1 max-w-6xl mx-auto w-full px-6 py-8">
        {/* Welcome Banner */}
        <div className="mb-8 pb-6 border-b border-paper-300">
          <div className="flex items-center space-x-2 text-amberAccent-700 text-xs font-semibold uppercase tracking-wider mb-2">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Step 4.1 Foundation Complete</span>
          </div>
          <h2 className="font-serif text-3xl sm:text-4xl font-normal text-ink-900 tracking-tight">
            Design System & Scaffolding
          </h2>
          <p className="mt-2 text-ink-600 text-sm sm:text-base max-w-2xl leading-relaxed">
            Welcome to the Marginalia frontend foundation. Built with Vite, React, TypeScript, and Tailwind CSS.
            The environment below demonstrates our editorial typography, warm paper color tokens, and live backend proxy integration.
          </p>
        </div>

        {/* Live Proxy Connection Card */}
        <div className="mb-8 p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-sm">
          <div className="flex items-start justify-between">
            <div className="flex items-center space-x-3">
              <div className={`p-2 rounded-lg ${proxyStatus.connected ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-amberAccent-50 text-amberAccent-700 border border-amberAccent-200'}`}>
                <ShieldCheck className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-serif text-lg font-semibold text-ink-900">
                  Development Reverse-Proxy Validation
                </h3>
                <p className="text-xs text-ink-500 mt-0.5">
                  Request to <code className="px-1.5 py-0.5 bg-paper-200 rounded text-ink-800 font-mono text-xs">/api/auth/status</code> forwarded to <code className="px-1.5 py-0.5 bg-paper-200 rounded text-ink-800 font-mono text-xs">http://localhost:8080</code>
                </p>
              </div>
            </div>
            <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${proxyStatus.connected ? 'bg-emerald-100 text-emerald-800' : 'bg-paper-200 text-ink-600'}`}>
              {proxyStatus.connected ? 'HTTP 200 OK' : 'Checking...'}
            </span>
          </div>

          <div className="mt-4 p-3 bg-paper-100 rounded-lg border border-paper-200 font-mono text-xs text-ink-700 overflow-x-auto">
            {proxyStatus.loading ? (
              <span className="text-ink-400">Pinging backend endpoint...</span>
            ) : proxyStatus.connected ? (
              <pre className="text-ink-800">{JSON.stringify(proxyStatus.data, null, 2)}</pre>
            ) : (
              <div className="text-amber-800">
                <p className="font-sans font-medium text-xs mb-1">Backend connection notice:</p>
                <p className="font-mono text-xs">{proxyStatus.error || 'Backend not reachable on port 8080'}</p>
                <p className="font-sans text-xs text-ink-500 mt-2">
                  Tip: Ensure the Spring Boot backend is running (<code className="font-mono">./mvnw spring-boot:run</code>).
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Design System & Component Preview Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {/* Column 1: Typography & Palette */}
          <div className="p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-sm flex flex-col">
            <h3 className="font-serif text-lg font-semibold text-ink-900 mb-3 flex items-center space-x-2">
              <Layers className="w-4 h-4 text-amberAccent-700" />
              <span>Editorial Palette</span>
            </h3>
            <p className="text-xs text-ink-600 mb-4">
              Natural paper hues paired with warm charcoal ink for high legibility and long reading sessions.
            </p>

            <div className="space-y-2 mt-auto">
              <div className="flex items-center justify-between p-2 rounded bg-paper-50 border border-paper-200 text-xs">
                <span className="font-medium text-ink-800">paper-50</span>
                <span className="font-mono text-ink-500 text-[11px]">#FDFCFB</span>
              </div>
              <div className="flex items-center justify-between p-2 rounded bg-paper-100 border border-paper-300 text-xs">
                <span className="font-medium text-ink-800">paper-100 (Body)</span>
                <span className="font-mono text-ink-500 text-[11px]">#FAF8F5</span>
              </div>
              <div className="flex items-center justify-between p-2 rounded bg-paper-200 border border-paper-300 text-xs">
                <span className="font-medium text-ink-800">paper-200</span>
                <span className="font-mono text-ink-500 text-[11px]">#F4EFEA</span>
              </div>
              <div className="flex items-center justify-between p-2 rounded bg-amberAccent-100 border border-amberAccent-200 text-xs text-amberAccent-800">
                <span className="font-medium">amberAccent-100</span>
                <span className="font-mono text-[11px]">#FEF3C7</span>
              </div>
            </div>
          </div>

          {/* Column 2: Article Card Sample */}
          <div className="p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-sm flex flex-col">
            <h3 className="font-serif text-lg font-semibold text-ink-900 mb-3 flex items-center space-x-2">
              <BookOpen className="w-4 h-4 text-amberAccent-700" />
              <span>Article Card Sample</span>
            </h3>

            <div className="p-4 rounded-lg border border-paper-300 bg-paper-100 hover:border-paper-400 transition-all flex flex-col flex-1">
              <div className="flex items-center justify-between text-xs text-ink-500 mb-2">
                <div className="flex items-center space-x-1.5">
                  <Rss className="w-3.5 h-3.5 text-amberAccent-700" />
                  <span className="font-semibold text-ink-700">Ars Technica</span>
                </div>
                <span>3h ago</span>
              </div>

              <h4 className="font-serif text-base font-bold text-ink-900 leading-snug mb-2 hover:text-amberAccent-800 cursor-pointer">
                The craft of writing distraction-free editorial tools
              </h4>

              <p className="text-xs text-ink-600 line-clamp-3 leading-relaxed mb-4">
                Personal reading tools prioritize calm design over noise. Clean typography and responsive ergonomics elevate the daily reading experience.
              </p>

              <div className="mt-auto pt-3 border-t border-paper-200 flex items-center justify-between text-xs text-ink-500">
                <span className="flex items-center space-x-1 text-emerald-700 font-medium">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Unread</span>
                </span>
                <div className="flex items-center space-x-2">
                  <button type="button" className="p-1 hover:text-amberAccent-700 rounded transition-colors" title="Bookmark">
                    <Bookmark className="w-3.5 h-3.5" />
                  </button>
                  <button type="button" className="p-1 hover:text-ink-900 rounded transition-colors" title="Open Link">
                    <ExternalLink className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Column 3: Navigation Badges & Buttons */}
          <div className="p-5 rounded-xl border border-paper-300 bg-paper-50 shadow-sm flex flex-col">
            <h3 className="font-serif text-lg font-semibold text-ink-900 mb-3 flex items-center space-x-2">
              <Rss className="w-4 h-4 text-amberAccent-700" />
              <span>Interactive Elements</span>
            </h3>
            <p className="text-xs text-ink-600 mb-4">
              Buttons, pills, and badges for the upcoming sidebar and stream panes.
            </p>

            <div className="space-y-3 mt-auto">
              <button 
                type="button"
                className="w-full px-4 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 font-medium text-xs rounded-lg shadow-sm transition-colors flex items-center justify-center space-x-2"
              >
                <span>Subscribe to New Feed</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>

              <div className="flex items-center justify-between p-2 rounded-lg bg-paper-100 border border-paper-200 text-xs">
                <span className="text-ink-700 font-medium">All Stories</span>
                <span className="px-2 py-0.5 rounded-full bg-amberAccent-100 text-amberAccent-800 font-semibold text-[11px]">
                  42
                </span>
              </div>

              <div className="flex items-center justify-between p-2 rounded-lg bg-paper-100 border border-paper-200 text-xs">
                <span className="text-ink-700 font-medium">Saved Bookmarks</span>
                <span className="px-2 py-0.5 rounded-full bg-paper-200 text-ink-600 font-semibold text-[11px]">
                  8
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Next Steps Roadmap Footer */}
        <div className="p-4 rounded-xl border border-paper-200 bg-paper-200/50 flex items-center justify-between text-xs text-ink-600">
          <div className="flex items-center space-x-2">
            <span className="font-semibold text-ink-800">Next Step:</span>
            <span>Step 4.2 — Authentication & Session Gate (Login view, AuthContext, dev-login)</span>
          </div>
          <span className="font-mono text-[11px] text-ink-500">marginalia-web v0.1.0</span>
        </div>
      </main>
    </div>
  );
}
