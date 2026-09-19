import { useState } from 'react';
import { 
  Inbox, 
  BookOpen, 
  Bookmark, 
  ChevronRight, 
  Folder, 
  FolderOpen, 
  Rss, 
  Plus, 
  Trash2, 
  RotateCw,
  X
} from 'lucide-react';
import type { CategoryDto, FeedDto, NavFilter } from '../../types';
import { cn } from '../../utils/cn';

interface SidebarProps {
  currentFilter: NavFilter;
  onSelectFilter: (filter: NavFilter) => void;
  categories: CategoryDto[];
  feeds: FeedDto[];
  totalUnread: number;
  unreadByFeed: Record<number, number>;
  onOpenAddFeed: () => void;
  onDeleteFeed: (feedId: number) => Promise<void>;
  onRefreshFeeds: () => Promise<void>;
  isMobileOpen: boolean;
  onCloseMobile: () => void;
}

export function Sidebar({
  currentFilter,
  onSelectFilter,
  categories,
  feeds,
  totalUnread,
  unreadByFeed,
  onOpenAddFeed,
  onDeleteFeed,
  onRefreshFeeds,
  isMobileOpen,
  onCloseMobile,
}: SidebarProps) {
  // Map of open category IDs (expanded accordions)
  const [expandedCategories, setExpandedCategories] = useState<Record<string, boolean>>({
    uncategorized: true,
  });
  const [refreshing, setRefreshing] = useState(false);

  const toggleCategory = (catKey: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setExpandedCategories((prev) => ({
      ...prev,
      [catKey]: !prev[catKey],
    }));
  };

  const handleRefresh = async () => {
    try {
      setRefreshing(true);
      await onRefreshFeeds();
    } finally {
      setRefreshing(false);
    }
  };

  const handleSelect = (filter: NavFilter) => {
    onSelectFilter(filter);
    onCloseMobile();
  };

  // Group feeds by category ID
  const categorizedFeeds: Record<number, FeedDto[]> = {};
  const uncategorizedFeeds: FeedDto[] = [];

  for (const feed of feeds) {
    if (feed.categoryId) {
      if (!categorizedFeeds[feed.categoryId]) {
        categorizedFeeds[feed.categoryId] = [];
      }
      categorizedFeeds[feed.categoryId].push(feed);
    } else {
      uncategorizedFeeds.push(feed);
    }
  }

  // Calculate unread count per category
  const getCategoryUnread = (catFeeds: FeedDto[] = []) => {
    return catFeeds.reduce((acc, f) => acc + (unreadByFeed[f.id] || 0), 0);
  };

  const sidebarContent = (
    <aside className="w-72 bg-paper-50 border-r border-paper-300 flex flex-col h-full select-none">
      {/* Top Mobile Header (only visible on mobile drawer) */}
      <div className="md:hidden px-4 py-3 border-b border-paper-300 flex items-center justify-between">
        <span className="font-serif font-bold text-ink-900 text-sm">Navigation</span>
        <button
          type="button"
          onClick={onCloseMobile}
          className="p-1.5 text-ink-500 hover:text-ink-800 rounded-lg"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Primary Navigation Views */}
      <div className="p-3 space-y-1 border-b border-paper-200">
        <button
          type="button"
          onClick={() => handleSelect({ type: 'all' })}
          className={cn(
            'w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-colors',
            currentFilter.type === 'all'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2.5">
            <Inbox className="w-4 h-4 text-amberAccent-700" />
            <span>All Articles</span>
          </div>
          {totalUnread > 0 && (
            <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-amberAccent-200 text-amberAccent-900">
              {totalUnread}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => handleSelect({ type: 'unread' })}
          className={cn(
            'w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-colors',
            currentFilter.type === 'unread'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2.5">
            <BookOpen className="w-4 h-4 text-emerald-700" />
            <span>Unread Only</span>
          </div>
          {totalUnread > 0 && (
            <span className="px-2 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800">
              {totalUnread}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => handleSelect({ type: 'saved' })}
          className={cn(
            'w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium transition-colors',
            currentFilter.type === 'saved'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2.5">
            <Bookmark className="w-4 h-4 text-amber-600" />
            <span>Saved / Bookmarks</span>
          </div>
        </button>
      </div>

      {/* Subscriptions Section Header */}
      <div className="px-4 pt-4 pb-2 flex items-center justify-between">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-ink-400 font-mono">
          Subscriptions ({feeds.length})
        </span>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          className="p-1 text-ink-400 hover:text-ink-800 rounded transition-colors"
          title="Refresh All Feeds"
        >
          <RotateCw className={cn('w-3.5 h-3.5', refreshing && 'animate-spin text-amberAccent-700')} />
        </button>
      </div>

      {/* Category Folders & Feeds Accordion Scroll Area */}
      <div className="flex-1 overflow-y-auto px-3 py-1 space-y-1">
        {/* Categories */}
        {categories.map((cat) => {
          const catFeeds = categorizedFeeds[cat.id] || [];
          const catKey = String(cat.id);
          const isExpanded = expandedCategories[catKey] ?? true;
          const catUnread = getCategoryUnread(catFeeds);
          const isCatSelected = currentFilter.type === 'category' && currentFilter.categoryId === cat.id;

          return (
            <div key={cat.id} className="space-y-0.5">
              {/* Category Header */}
              <div
                onClick={() => handleSelect({ type: 'category', categoryId: cat.id, name: cat.name })}
                className={cn(
                  'group flex items-center justify-between px-2 py-1.5 rounded-lg text-xs cursor-pointer transition-colors',
                  isCatSelected
                    ? 'bg-paper-200 text-ink-900 font-semibold'
                    : 'text-ink-700 hover:bg-paper-200/50 hover:text-ink-900'
                )}
              >
                <div className="flex items-center space-x-1.5 flex-1 min-w-0 pr-1">
                  <button
                    type="button"
                    onClick={(e) => toggleCategory(catKey, e)}
                    className="p-0.5 text-ink-400 hover:text-ink-800 rounded"
                  >
                    <ChevronRight className={cn('w-3 h-3 transition-transform', isExpanded && 'rotate-90')} />
                  </button>
                  {isExpanded ? (
                    <FolderOpen className="w-3.5 h-3.5 text-amberAccent-700 shrink-0" />
                  ) : (
                    <Folder className="w-3.5 h-3.5 text-amberAccent-700 shrink-0" />
                  )}
                  <span className="truncate">{cat.name}</span>
                </div>

                <div className="flex items-center space-x-1.5">
                  {catUnread > 0 && (
                    <span className="px-1.5 py-0.2 rounded-full text-[10px] font-medium bg-paper-300/80 text-ink-700">
                      {catUnread}
                    </span>
                  )}
                </div>
              </div>

              {/* Feed items in this category */}
              {isExpanded && (
                <div className="pl-6 space-y-0.5 border-l border-paper-200/80 ml-3.5">
                  {catFeeds.map((feed) => {
                    const unread = unreadByFeed[feed.id] || 0;
                    const isFeedSelected = currentFilter.type === 'feed' && currentFilter.feedId === feed.id;

                    return (
                      <div
                        key={feed.id}
                        onClick={() => handleSelect({ type: 'feed', feedId: feed.id, title: feed.title })}
                        className={cn(
                          'group/item flex items-center justify-between px-2 py-1 rounded-md text-xs cursor-pointer transition-colors',
                          isFeedSelected
                            ? 'bg-amberAccent-100/70 text-amberAccent-900 font-semibold'
                            : 'text-ink-600 hover:bg-paper-200/50 hover:text-ink-900'
                        )}
                      >
                        <div className="flex items-center space-x-2 flex-1 min-w-0 pr-1">
                          {feed.iconUrl ? (
                            <img
                              src={feed.iconUrl}
                              alt=""
                              className="w-3.5 h-3.5 rounded shrink-0 object-contain"
                              onError={(e) => {
                                (e.target as HTMLElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            <Rss className="w-3 h-3 text-ink-400 shrink-0" />
                          )}
                          <span className="truncate text-[11px]">{feed.title}</span>
                        </div>

                        <div className="flex items-center space-x-1">
                          {unread > 0 && (
                            <span className="px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-amberAccent-200 text-amberAccent-900">
                              {unread}
                            </span>
                          )}
                          <button
                            type="button"
                            onClick={async (e) => {
                              e.stopPropagation();
                              if (confirm(`Unsubscribe from "${feed.title}"?`)) {
                                await onDeleteFeed(feed.id);
                              }
                            }}
                            className="opacity-0 group-hover/item:opacity-100 p-1 text-ink-400 hover:text-red-700 rounded transition-opacity"
                            title="Unsubscribe"
                          >
                            <Trash2 className="w-3 h-3" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                  {catFeeds.length === 0 && (
                    <div className="text-[10px] text-ink-400 py-1 pl-2 italic">
                      No feeds in this folder
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}

        {/* Uncategorized Feeds Section */}
        {uncategorizedFeeds.length > 0 && (
          <div className="space-y-0.5 pt-1">
            <div
              className="flex items-center justify-between px-2 py-1.5 rounded-lg text-xs cursor-pointer text-ink-700 hover:bg-paper-200/50"
              onClick={() => toggleCategory('uncategorized')}
            >
              <div className="flex items-center space-x-1.5 flex-1 min-w-0">
                <ChevronRight className={cn('w-3 h-3 text-ink-400 transition-transform', expandedCategories.uncategorized && 'rotate-90')} />
                <Folder className="w-3.5 h-3.5 text-ink-500" />
                <span className="truncate text-ink-600">Uncategorized</span>
              </div>
              {getCategoryUnread(uncategorizedFeeds) > 0 && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] font-medium bg-paper-300/80 text-ink-700">
                  {getCategoryUnread(uncategorizedFeeds)}
                </span>
              )}
            </div>

            {expandedCategories.uncategorized && (
              <div className="pl-6 space-y-0.5 border-l border-paper-200/80 ml-3.5">
                {uncategorizedFeeds.map((feed) => {
                  const unread = unreadByFeed[feed.id] || 0;
                  const isFeedSelected = currentFilter.type === 'feed' && currentFilter.feedId === feed.id;

                  return (
                    <div
                      key={feed.id}
                      onClick={() => handleSelect({ type: 'feed', feedId: feed.id, title: feed.title })}
                      className={cn(
                        'group/item flex items-center justify-between px-2 py-1 rounded-md text-xs cursor-pointer transition-colors',
                        isFeedSelected
                          ? 'bg-amberAccent-100/70 text-amberAccent-900 font-semibold'
                          : 'text-ink-600 hover:bg-paper-200/50 hover:text-ink-900'
                      )}
                    >
                      <div className="flex items-center space-x-2 flex-1 min-w-0 pr-1">
                        {feed.iconUrl ? (
                          <img
                            src={feed.iconUrl}
                            alt=""
                            className="w-3.5 h-3.5 rounded shrink-0 object-contain"
                            onError={(e) => {
                              (e.target as HTMLElement).style.display = 'none';
                            }}
                          />
                        ) : (
                          <Rss className="w-3 h-3 text-ink-400 shrink-0" />
                        )}
                        <span className="truncate text-[11px]">{feed.title}</span>
                      </div>

                      <div className="flex items-center space-x-1">
                        {unread > 0 && (
                          <span className="px-1.5 py-0.2 rounded-full text-[10px] font-bold bg-amberAccent-200 text-amberAccent-900">
                            {unread}
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={async (e) => {
                            e.stopPropagation();
                            if (confirm(`Unsubscribe from "${feed.title}"?`)) {
                              await onDeleteFeed(feed.id);
                            }
                          }}
                          className="opacity-0 group-hover/item:opacity-100 p-1 text-ink-400 hover:text-red-700 rounded transition-opacity"
                          title="Unsubscribe"
                        >
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {feeds.length === 0 && (
          <div className="p-4 text-center text-xs text-ink-400">
            No feeds yet. Click &ldquo;Add Feed&rdquo; to subscribe to your first publication.
          </div>
        )}
      </div>

      {/* Bottom Subscribe Button */}
      <div className="p-3 border-t border-paper-300 bg-paper-100/50">
        <button
          type="button"
          onClick={onOpenAddFeed}
          className="w-full flex items-center justify-center space-x-2 px-3 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 text-xs font-medium rounded-xl transition-colors shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>Add Feed</span>
        </button>
      </div>
    </aside>
  );

  return (
    <>
      {/* Desktop Sidebar (hidden on mobile, permanent left column on >= md screens) */}
      <div className="hidden md:flex shrink-0 h-[calc(100vh-53px)] sticky top-[53px]">
        {sidebarContent}
      </div>

      {/* Mobile Slide-Out Drawer with Backdrop Overlay */}
      {isMobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden flex">
          {/* Backdrop overlay */}
          <div
            className="fixed inset-0 bg-ink-900/40 backdrop-blur-xs transition-opacity"
            onClick={onCloseMobile}
          />
          {/* Drawer container */}
          <div className="relative z-50 flex flex-col w-72 max-w-full bg-paper-50 shadow-xl h-full animate-in slide-in-from-left duration-200">
            {sidebarContent}
          </div>
        </div>
      )}
    </>
  );
}
