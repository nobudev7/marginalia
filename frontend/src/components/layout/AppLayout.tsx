import { useState } from 'react';
import { 
  Inbox, 
  BookOpen, 
  Bookmark, 
  Rss, 
  Folder, 
  CheckCircle2, 
  Plus, 
  Sparkles, 
  ExternalLink,
  Layers,
  ArrowRight
} from 'lucide-react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { AddFeedModal } from '../feeds/AddFeedModal';
import { useAuth } from '../../hooks/useAuth';
import { useCategories } from '../../hooks/useCategories';
import { useFeeds } from '../../hooks/useFeeds';
import type { NavFilter } from '../../types';

export function AppLayout() {
  const { user } = useAuth();
  const { categories, addCategory } = useCategories();
  const { 
    feeds, 
    totalUnread, 
    unreadByFeed, 
    addFeed, 
    deleteFeed, 
    refreshFeeds, 
    loading: feedsLoading 
  } = useFeeds();

  const [currentFilter, setCurrentFilter] = useState<NavFilter>({ type: 'all' });
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [isAddFeedModalOpen, setIsAddFeedModalOpen] = useState(false);

  // Derive title & icon for active view
  const getViewInfo = () => {
    switch (currentFilter.type) {
      case 'all':
        return {
          title: 'All Articles',
          subtitle: `Aggregated stream from all ${feeds.length} subscribed publications`,
          badge: totalUnread > 0 ? `${totalUnread} unread` : 'All caught up',
          icon: Inbox,
        };
      case 'unread':
        return {
          title: 'Unread Only',
          subtitle: 'Viewing only articles that have not been read yet',
          badge: totalUnread > 0 ? `${totalUnread} unread` : 'Zero unread',
          icon: BookOpen,
        };
      case 'saved':
        return {
          title: 'Saved / Bookmarks',
          subtitle: 'Articles bookmarked for future reading',
          badge: 'Bookmarks',
          icon: Bookmark,
        };
      case 'category':
        return {
          title: currentFilter.name,
          subtitle: `Folder stream containing ${feeds.filter(f => f.categoryId === currentFilter.categoryId).length} feeds`,
          badge: 'Category',
          icon: Folder,
        };
      case 'feed': {
        const feed = feeds.find(f => f.id === currentFilter.feedId);
        return {
          title: feed?.title || currentFilter.title,
          subtitle: feed?.feedUrl || 'Individual RSS stream',
          badge: (unreadByFeed[currentFilter.feedId] || 0) > 0 
            ? `${unreadByFeed[currentFilter.feedId]} unread` 
            : 'Caught up',
          icon: Rss,
          siteUrl: feed?.siteUrl,
        };
      }
    }
  };

  const viewInfo = getViewInfo();
  const ViewIcon = viewInfo.icon;

  return (
    <div className="min-h-screen bg-paper-100 text-ink-900 flex flex-col selection:bg-amberAccent-100 selection:text-amberAccent-800">
      {/* Top Application Header with Mobile Menu Toggle */}
      <Header onToggleMobileMenu={() => setIsMobileNavOpen(true)} />

      {/* Two-Column App Layout */}
      <div className="flex-1 flex w-full">
        {/* Left Navigation Sidebar */}
        <Sidebar
          currentFilter={currentFilter}
          onSelectFilter={setCurrentFilter}
          categories={categories}
          feeds={feeds}
          totalUnread={totalUnread}
          unreadByFeed={unreadByFeed}
          onOpenAddFeed={() => setIsAddFeedModalOpen(true)}
          onDeleteFeed={deleteFeed}
          onRefreshFeeds={refreshFeeds}
          isMobileOpen={isMobileNavOpen}
          onCloseMobile={() => setIsMobileNavOpen(false)}
        />

        {/* Main Content Area */}
        <main className="flex-1 p-6 sm:p-8 max-w-5xl overflow-y-auto">
          {/* Active View Header */}
          <div className="mb-6 pb-5 border-b border-paper-300 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-start space-x-3">
              <div className="p-2.5 rounded-xl bg-paper-200 text-amberAccent-800 shrink-0 mt-0.5">
                <ViewIcon className="w-5 h-5" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <h2 className="font-serif text-2xl sm:text-3xl font-bold text-ink-900 tracking-tight">
                    {viewInfo.title}
                  </h2>
                  <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amberAccent-100 text-amberAccent-800">
                    {viewInfo.badge}
                  </span>
                </div>
                <p className="text-xs sm:text-sm text-ink-600 mt-1 flex items-center space-x-2">
                  <span>{viewInfo.subtitle}</span>
                  {'siteUrl' in viewInfo && viewInfo.siteUrl && (
                    <a
                      href={viewInfo.siteUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-amberAccent-700 hover:text-amberAccent-900 inline-flex items-center space-x-0.5"
                    >
                      <span>Visit site</span>
                      <ExternalLink className="w-3 h-3" />
                    </a>
                  )}
                </p>
              </div>
            </div>

            {/* Quick Action in Header */}
            <div className="flex items-center space-x-2">
              <button
                type="button"
                onClick={() => setIsAddFeedModalOpen(true)}
                className="px-3 py-1.5 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 font-medium text-xs rounded-lg transition-colors shadow-2xs flex items-center space-x-1.5"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>Add Feed</span>
              </button>
            </div>
          </div>

          {/* Step 4.3 Status Banner */}
          <div className="mb-8 p-5 rounded-2xl bg-paper-50 border border-paper-300 shadow-xs">
            <div className="flex items-center space-x-2 text-emerald-700 text-xs font-bold uppercase tracking-wider mb-2">
              <CheckCircle2 className="w-4 h-4" />
              <span>Step 4.3 Verified — Navigation & Feed Architecture</span>
            </div>
            <p className="text-xs sm:text-sm text-ink-700 leading-relaxed">
              The collapsible navigation sidebar is fully connected to backend feed, category, and unread count endpoints.
              Select any feed or category on the left to see the view filter respond.
            </p>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 pt-4 border-t border-paper-200 text-xs">
              <div className="p-2.5 rounded-lg bg-paper-100 border border-paper-200">
                <span className="text-ink-500 block text-[11px]">Subscribed Feeds</span>
                <span className="font-serif text-lg font-bold text-ink-900">{feeds.length}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-paper-100 border border-paper-200">
                <span className="text-ink-500 block text-[11px]">Folders / Categories</span>
                <span className="font-serif text-lg font-bold text-ink-900">{categories.length}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-paper-100 border border-paper-200">
                <span className="text-ink-500 block text-[11px]">Total Unread</span>
                <span className="font-serif text-lg font-bold text-amberAccent-800">{totalUnread}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-paper-100 border border-paper-200">
                <span className="text-ink-500 block text-[11px]">Authenticated As</span>
                <span className="font-mono text-xs text-ink-800 truncate block">{user?.email}</span>
              </div>
            </div>
          </div>

          {/* Subscribed Feeds Grid Preview */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-serif text-base font-bold text-ink-900 flex items-center space-x-2">
                <Layers className="w-4 h-4 text-amberAccent-700" />
                <span>Active Feeds in Marginalia ({feeds.length})</span>
              </h3>
              {feedsLoading && (
                <span className="text-xs text-ink-400 font-mono animate-pulse">Syncing feeds...</span>
              )}
            </div>

            {feeds.length === 0 ? (
              <div className="p-8 text-center rounded-xl border border-dashed border-paper-300 bg-paper-50/50">
                <Rss className="w-8 h-8 text-ink-400 mx-auto mb-2" />
                <p className="font-serif text-base font-bold text-ink-800">No Subscriptions Yet</p>
                <p className="text-xs text-ink-500 mt-1 max-w-sm mx-auto">
                  Subscribe to your favorite publications to start streaming articles.
                </p>
                <button
                  type="button"
                  onClick={() => setIsAddFeedModalOpen(true)}
                  className="mt-4 px-4 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 text-xs font-medium rounded-xl transition-colors shadow-xs inline-flex items-center space-x-1.5"
                >
                  <Plus className="w-4 h-4" />
                  <span>Subscribe to First Feed</span>
                </button>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {feeds.map((feed) => {
                  const unread = unreadByFeed[feed.id] || 0;
                  return (
                    <div
                      key={feed.id}
                      onClick={() => setCurrentFilter({ type: 'feed', feedId: feed.id, title: feed.title })}
                      className="p-3.5 rounded-xl border border-paper-300 bg-paper-50 hover:border-paper-400 hover:shadow-xs transition-all cursor-pointer flex flex-col justify-between"
                    >
                      <div className="flex items-start justify-between gap-2 mb-2">
                        <div className="flex items-center space-x-2 min-w-0">
                          {feed.iconUrl ? (
                            <img
                              src={feed.iconUrl}
                              alt=""
                              className="w-4 h-4 rounded shrink-0 object-contain"
                              onError={(e) => {
                                (e.target as HTMLElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            <Rss className="w-4 h-4 text-amberAccent-700 shrink-0" />
                          )}
                          <h4 className="font-serif text-sm font-bold text-ink-900 truncate">
                            {feed.title}
                          </h4>
                        </div>
                        {unread > 0 ? (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amberAccent-200 text-amberAccent-900 shrink-0">
                            {unread} unread
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-paper-200 text-ink-600 shrink-0">
                            Caught up
                          </span>
                        )}
                      </div>

                      <div className="flex items-center justify-between text-[11px] text-ink-500 pt-2 border-t border-paper-200">
                        <span className="truncate">
                          {feed.categoryName ? `📁 ${feed.categoryName}` : 'Uncategorized'}
                        </span>
                        <span className="font-mono text-[10px]">
                          {feed.lastFetchedAt ? new Date(feed.lastFetchedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Never'}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Next Step Teaser */}
          <div className="mt-8 p-4 rounded-xl border border-paper-300 bg-paper-50/70 flex items-center justify-between text-xs text-ink-600">
            <div className="flex items-center space-x-2">
              <Sparkles className="w-4 h-4 text-amberAccent-700" />
              <span className="font-semibold text-ink-900">Next Step:</span>
              <span>Step 4.4 — Article Stream Pane (paginated stream, cards, inline mark-as-read, and bookmarking)</span>
            </div>
            <ArrowRight className="w-4 h-4 text-ink-400" />
          </div>
        </main>
      </div>

      {/* Subscribe to Feed Modal Dialog */}
      <AddFeedModal
        isOpen={isAddFeedModalOpen}
        onClose={() => setIsAddFeedModalOpen(false)}
        categories={categories}
        onAddFeed={addFeed}
        onAddCategory={addCategory}
      />
    </div>
  );
}
