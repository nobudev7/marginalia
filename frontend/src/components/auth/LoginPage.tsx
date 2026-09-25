import { useState, type FormEvent } from 'react';
import { ShieldAlert, Terminal, ArrowRight, Loader2, Sparkles } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

export function LoginPage() {
  const { error, clearError, loginWithDev, loading } = useAuth();
  const [devEmail, setDevEmail] = useState('test@example.com');
  const [devLoggingIn, setDevLoggingIn] = useState(false);

  const handleDevSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!devEmail.trim()) return;
    try {
      setDevLoggingIn(true);
      await loginWithDev(devEmail.trim());
    } finally {
      setDevLoggingIn(false);
    }
  };

  return (
    <div className="min-h-screen bg-paper-100 flex flex-col justify-center items-center px-4 py-12 selection:bg-amberAccent-100 selection:text-amberAccent-800">
      <div className="w-full max-w-md">
        {/* Brand Emblem & Headline */}
        <div className="text-center mb-8">
          <img
            src="/favicon.svg"
            alt="Marginalia"
            className="inline-flex w-14 h-14 rounded-2xl shadow-sm mb-4 object-cover"
          />
          <h1 className="font-serif text-3xl font-bold tracking-tight text-ink-900">
            Marginalia
          </h1>
          <p className="mt-1.5 text-sm text-ink-600 font-sans">
            Personal Reader & Editorial Feed Stream
          </p>
        </div>

        {/* Card Container */}
        <div className="bg-paper-50 border border-paper-300 rounded-2xl p-7 shadow-sm">
          {/* Unauthorized / Alert Message */}
          {error && (
            <div className="mb-6 p-4 rounded-xl bg-amberAccent-50 border border-amberAccent-200 text-amberAccent-800 text-xs flex items-start space-x-3">
              <ShieldAlert className="w-5 h-5 text-amberAccent-700 shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="font-semibold text-amberAccent-900 mb-0.5">Authentication Notice</p>
                <p className="leading-relaxed">{error}</p>
              </div>
              <button
                type="button"
                onClick={clearError}
                className="text-amberAccent-600 hover:text-amberAccent-900 font-bold ml-1 text-sm leading-none"
                title="Dismiss"
              >
                ✕
              </button>
            </div>
          )}

          {/* Social / OAuth2 Sign-In Buttons */}
          <div className="space-y-3">
            <a
              href="/oauth2/authorization/google"
              className="w-full flex items-center justify-center px-4 py-2.5 border border-paper-300 rounded-xl bg-paper-50 hover:bg-paper-100 text-ink-800 text-sm font-medium transition-colors shadow-xs group"
            >
              {/* Google SVG Icon */}
              <svg className="w-4 h-4 mr-3" viewBox="0 0 24 24">
                <path
                  fill="#4285F4"
                  d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17z"
                />
                <path
                  fill="#34A853"
                  d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.26v3.15C3.25 21.36 7.33 24 12 24z"
                />
                <path
                  fill="#FBBC05"
                  d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.14-1.55.38-2.27V6.58H1.26C.46 8.16 0 9.94 0 12s.46 3.84 1.26 5.42l4.02-3.15z"
                />
                <path
                  fill="#EA4335"
                  d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.25 2.64 1.26 6.58l4.02 3.15c.95-2.83 3.6-4.98 6.72-4.98z"
                />
              </svg>
              <span>Continue with Google</span>
            </a>

            <a
              href="/oauth2/authorization/github"
              className="w-full flex items-center justify-center px-4 py-2.5 border border-paper-300 rounded-xl bg-paper-50 hover:bg-paper-100 text-ink-800 text-sm font-medium transition-colors shadow-xs group"
            >
              {/* GitHub SVG Icon */}
              <svg className="w-4 h-4 mr-3 fill-current text-ink-900" viewBox="0 0 24 24">
                <path fillRule="evenodd" clipRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" />
              </svg>
              <span>Continue with GitHub</span>
            </a>
          </div>

          {/* Privacy & Whitelist Note */}
          <p className="mt-5 text-center text-xs text-ink-500 leading-relaxed">
            Marginalia is private. Only email addresses pre-authorized on the access whitelist can sign in.
          </p>

          {/* Divider */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-paper-300" />
            </div>
            <div className="relative flex justify-center text-xs">
              <span className="bg-paper-50 px-3 text-ink-400 font-mono text-[11px]">
                LOCAL DEVELOPMENT
              </span>
            </div>
          </div>

          {/* Dev Mode Login Helper */}
          <form onSubmit={handleDevSubmit} className="space-y-3 bg-paper-100/70 p-4 rounded-xl border border-paper-200">
            <div className="flex items-center space-x-2 text-xs font-semibold text-ink-700">
              <Terminal className="w-3.5 h-3.5 text-amberAccent-700" />
              <span>Dev Login Shortcut</span>
            </div>

            <div>
              <label htmlFor="dev-email" className="block text-[11px] font-medium text-ink-500 mb-1">
                Whitelisted Email Address
              </label>
              <input
                id="dev-email"
                type="email"
                value={devEmail}
                onChange={(e) => setDevEmail(e.target.value)}
                placeholder="test@example.com"
                required
                className="w-full px-3 py-1.5 bg-paper-50 border border-paper-300 rounded-lg text-xs text-ink-900 focus:outline-none focus:ring-1 focus:ring-amberAccent-700 focus:border-amberAccent-700 font-mono"
              />
            </div>

            <button
              type="submit"
              disabled={devLoggingIn || loading}
              className="w-full flex items-center justify-center px-3 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 disabled:opacity-50 text-paper-50 rounded-lg text-xs font-medium transition-colors shadow-xs"
            >
              {devLoggingIn ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
                  <span>Authenticating...</span>
                </>
              ) : (
                <>
                  <span>Sign in as {devEmail || 'Developer'}</span>
                  <ArrowRight className="w-3.5 h-3.5 ml-1.5" />
                </>
              )}
            </button>

            <p className="text-[11px] text-ink-500 leading-snug">
              Directly seeds an authenticated session in MySQL without requiring OAuth2 provider credentials.
            </p>
          </form>
        </div>

        {/* Footer info */}
        <div className="mt-8 text-center text-xs text-ink-500 flex items-center justify-center space-x-1.5">
          <Sparkles className="w-3 h-3 text-amberAccent-700" />
          <span>Session persisted in MySQL via Spring Session JDBC (90 days)</span>
        </div>
      </div>
    </div>
  );
}
