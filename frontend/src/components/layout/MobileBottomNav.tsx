import { Inbox, BookOpen, Bookmark, Folder, Settings } from 'lucide-react';
import type { NavFilter } from '../../types';
import { cn } from '../../utils/cn';

interface MobileBottomNavProps {
  currentFilter: NavFilter;
  onSelectFilter: (filter: NavFilter) => void;
  totalUnread: number;
  onOpenSidebar: () => void;
  onOpenSettings: () => void;
}

export function MobileBottomNav({
  currentFilter,
  onSelectFilter,
  totalUnread,
  onOpenSidebar,
  onOpenSettings,
}: MobileBottomNavProps) {
  const isAll = currentFilter.type === 'all';
  const isUnread = currentFilter.type === 'unread';
  const isSaved = currentFilter.type === 'saved';
  const isFeedsOrFolders = currentFilter.type === 'category' || currentFilter.type === 'feed';

  return (
    <nav
      aria-label="Mobile Navigation"
      className="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-paper-50/95 backdrop-blur-md border-t border-paper-300 pb-safe shadow-md select-none"
    >
      <div className="grid grid-cols-5 h-14 items-stretch">
        {/* 1. All Articles */}
        <button
          type="button"
          onClick={() => onSelectFilter({ type: 'all' })}
          className={cn(
            'flex flex-col items-center justify-center py-1 text-[10px] font-medium transition-colors relative',
            isAll ? 'text-amberAccent-800 font-bold' : 'text-ink-600 hover:text-ink-900'
          )}
        >
          <div className="relative">
            <Inbox className="w-5 h-5 mb-0.5" />
            {totalUnread > 0 && !isAll && (
              <span className="absolute -top-1 -right-2 px-1 py-0.2 bg-amberAccent-700 text-paper-50 text-[9px] font-bold rounded-full min-w-[14px] text-center leading-tight">
                {totalUnread > 99 ? '99+' : totalUnread}
              </span>
            )}
          </div>
          <span>All</span>
        </button>

        {/* 2. Unread Only */}
        <button
          type="button"
          onClick={() => onSelectFilter({ type: 'unread' })}
          className={cn(
            'flex flex-col items-center justify-center py-1 text-[10px] font-medium transition-colors relative',
            isUnread ? 'text-amberAccent-800 font-bold' : 'text-ink-600 hover:text-ink-900'
          )}
        >
          <div className="relative">
            <BookOpen className="w-5 h-5 mb-0.5" />
            {totalUnread > 0 && (
              <span className="absolute -top-1 -right-2 px-1 py-0.2 bg-emerald-600 text-paper-50 text-[9px] font-bold rounded-full min-w-[14px] text-center leading-tight">
                {totalUnread > 99 ? '99+' : totalUnread}
              </span>
            )}
          </div>
          <span>Unread</span>
        </button>

        {/* 3. Saved */}
        <button
          type="button"
          onClick={() => onSelectFilter({ type: 'saved' })}
          className={cn(
            'flex flex-col items-center justify-center py-1 text-[10px] font-medium transition-colors',
            isSaved ? 'text-amberAccent-800 font-bold' : 'text-ink-600 hover:text-ink-900'
          )}
        >
          <Bookmark className="w-5 h-5 mb-0.5" />
          <span>Saved</span>
        </button>

        {/* 4. Feeds & Folders (Drawer Toggle) */}
        <button
          type="button"
          onClick={onOpenSidebar}
          className={cn(
            'flex flex-col items-center justify-center py-1 text-[10px] font-medium transition-colors',
            isFeedsOrFolders ? 'text-amberAccent-800 font-bold' : 'text-ink-600 hover:text-ink-900'
          )}
        >
          <Folder className="w-5 h-5 mb-0.5" />
          <span>Feeds</span>
        </button>

        {/* 5. Settings */}
        <button
          type="button"
          onClick={onOpenSettings}
          className="flex flex-col items-center justify-center py-1 text-[10px] font-medium text-ink-600 hover:text-ink-900 transition-colors"
        >
          <Settings className="w-5 h-5 mb-0.5" />
          <span>Settings</span>
        </button>
      </div>
    </nav>
  );
}
