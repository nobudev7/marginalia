import { LogOut, User as UserIcon } from 'lucide-react';
import { useAuth } from '../../hooks/useAuth';

export function Header() {
  const { user, logout } = useAuth();

  return (
    <header className="border-b border-paper-300 bg-paper-50 sticky top-0 z-30 px-6 py-3 shadow-xs">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Logo & Brand */}
        <div className="flex items-center space-x-3">
          <div className="w-8 h-8 rounded-lg bg-amberAccent-700 text-paper-50 flex items-center justify-center font-serif font-bold text-lg shadow-xs">
            M
          </div>
          <div>
            <h1 className="font-serif text-lg font-bold tracking-tight text-ink-900 leading-none">
              Marginalia
            </h1>
            <p className="text-[11px] text-ink-500 font-sans mt-0.5 hidden sm:block">
              Personal Reader & Editorial Feed Stream
            </p>
          </div>
        </div>

        {/* User Badge & Actions */}
        <div className="flex items-center space-x-3">
          {user && (
            <div className="flex items-center space-x-2.5 px-3 py-1.5 rounded-full border border-paper-300 bg-paper-100">
              {user.avatarUrl ? (
                <img
                  src={user.avatarUrl}
                  alt={user.displayName || user.email}
                  className="w-5 h-5 rounded-full object-cover border border-paper-300"
                />
              ) : (
                <div className="w-5 h-5 rounded-full bg-paper-300 text-ink-700 flex items-center justify-center text-[10px] font-bold">
                  <UserIcon className="w-3 h-3 text-ink-600" />
                </div>
              )}
              <span className="text-xs font-medium text-ink-800 hidden sm:inline max-w-[150px] truncate">
                {user.displayName || user.email}
              </span>
              <span className="text-[11px] text-ink-500 hidden md:inline font-mono border-l border-paper-300 pl-2">
                {user.email}
              </span>
            </div>
          )}

          <button
            type="button"
            onClick={logout}
            className="flex items-center space-x-1.5 px-3 py-1.5 text-xs text-ink-600 hover:text-ink-900 hover:bg-paper-200 border border-transparent hover:border-paper-300 rounded-lg transition-colors font-medium"
            title="Log Out"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Log Out</span>
          </button>
        </div>
      </div>
    </header>
  );
}
