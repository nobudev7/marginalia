import { useState, useEffect, useCallback } from 'react';
import { 
  Inbox, 
  BookOpen, 
  Bookmark, 
  Rss, 
  Folder, 
  CheckCheck, 
  RotateCw, 
  AlertCircle, 
  Plus, 
  ExternalLink,
  ChevronDown,
  Eye,
  EyeOff
} from 'lucide-react';
import type { NavFilter, FeedDto, ArticleDto } from '../../types';
import { useArticles } from '../../hooks/useArticles';
import { ArticleCard } from './ArticleCard';
import { ReadingDrawer } from '../article/ReadingDrawer';

interface ArticleStreamProps {
  filter: NavFilter;
  feeds: FeedDto[];
  unreadByFeed: Record<string, number>;
  totalUnread: number;
  onUnreadChanged: () => void;
  onOpenAddFeed: () => void;
  onSelectArticle?: (article: ArticleDto) => void;
}

export function ArticleStream({
  filter,
  feeds,
  unreadByFeed,
  totalUnread,
  onUnreadChanged,
  onOpenAddFeed,
  onSelectArticle,
}: ArticleStreamProps) {
  // Each subscription (feed) and category defaults to showing only unread articles.
  const [unreadOnly, setUnreadOnly] = useState<boolean>(() => {
    return filter.type === 'feed' || filter.type === 'category' || filter.type === 'unread';
  });

  const currentFeedId = filter.type === 'feed' ? filter.feedId : undefined;
  const currentCategoryId = filter.type === 'category' ? filter.categoryId : undefined;

  // Whenever the user switches to a different subscription or filter, reset to default state (unread only)
  useEffect(() => {
    if (filter.type === 'feed' || filter.type === 'category' || filter.type === 'unread') {
      setUnreadOnly(true);
    } else {
      setUnreadOnly(false);
    }
  }, [filter.type, currentFeedId, currentCategoryId]);

  const {
    articles,
    loading,
    loadingMore,
    hasMore,
    totalElements,
    error,
    loadMore,
    refresh,
    toggleRead,
    toggleSave,
    markAllAsRead,
  } = useArticles({ filter, unreadOnly, onUnreadChanged });

  const [selectedArticle, setSelectedArticle] = useState<ArticleDto | null>(null);

  // Close reading drawer when switching navigation filters/feeds
  useEffect(() => {
    setSelectedArticle(null);
  }, [filter.type, currentFeedId, currentCategoryId]);

  const handleSelectArticle = useCallback((article: ArticleDto) => {
    setSelectedArticle(article);
    onSelectArticle?.(article);
  }, [onSelectArticle]);

  const handleCloseDrawer = () => {
    setSelectedArticle(null);
  };

  // Synchronize drawer article state with live optimistic updates in the stream
  const activeArticle = selectedArticle
    ? articles.find((a) => a.id === selectedArticle.id) || selectedArticle
    : null;

  // Global shortcut: press 'j' to open the first article when stream is populated and drawer is closed
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (selectedArticle !== null) return;
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      if ((e.key === 'j' || e.key === 'J') && articles.length > 0) {
        e.preventDefault();
        handleSelectArticle(articles[0]);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedArticle, articles, handleSelectArticle]);

  // Derive header title, subtitle, and icon
  const getHeaderInfo = () => {
    switch (filter.type) {
      case 'all':
        return {
          title: 'All Articles',
          subtitle: `Aggregated stream across ${feeds.length} publications`,
          icon: Inbox,
          unreadCount: totalUnread,
        };
      case 'unread':
        return {
          title: 'Unread Only',
          subtitle: 'Viewing unread articles across all subscriptions',
          icon: BookOpen,
          unreadCount: totalUnread,
        };
      case 'saved':
        return {
          title: 'Saved / Bookmarks',
          subtitle: 'Articles saved for later reading and reference',
          icon: Bookmark,
          unreadCount: 0,
        };
      case 'category': {
        const categoryFeeds = feeds.filter((f) => f.categoryId === filter.categoryId);
        const catUnread = categoryFeeds.reduce(
          (acc, f) => acc + (unreadByFeed[f.id] || 0),
          0
        );
        return {
          title: filter.name,
          subtitle: `Folder containing ${categoryFeeds.length} publication${
            categoryFeeds.length === 1 ? '' : 's'
          }`,
          icon: Folder,
          unreadCount: catUnread,
        };
      }
      case 'feed': {
        const feed = feeds.find((f) => f.id === filter.feedId);
        const feedUnread = unreadByFeed[filter.feedId] || 0;
        return {
          title: feed?.title || filter.title,
          subtitle: feed?.siteUrl || feed?.feedUrl || 'Publication stream',
          icon: Rss,
          siteUrl: feed?.siteUrl,
          unreadCount: feedUnread,
        };
      }
    }
  };

  const headerInfo = getHeaderInfo();
  const HeaderIcon = headerInfo.icon;
  const unreadInStream = articles.filter((a) => !a.isRead).length;

  return (
    <div className="w-full">
      {/* Stream Header */}
      <header className="mb-6 pb-5 border-b border-paper-300 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-start space-x-3.5">
          <div className="p-2.5 rounded-xl bg-paper-200 text-amberAccent-800 shrink-0 mt-0.5 shadow-2xs">
            <HeaderIcon className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center space-x-2.5 flex-wrap gap-y-1">
              <h2 className="font-serif text-2xl sm:text-3xl font-bold text-ink-950 tracking-tight">
                {headerInfo.title}
              </h2>
              {filter.type !== 'saved' && (
                <span
                  className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                    headerInfo.unreadCount > 0
                      ? 'bg-amberAccent-100 text-amberAccent-900 border border-amberAccent-200'
                      : 'bg-paper-200 text-ink-600'
                  }`}
                >
                  {unreadOnly
                    ? headerInfo.unreadCount > 0
                      ? `${headerInfo.unreadCount} unread`
                      : 'All read'
                    : `${totalElements} articles${
                        headerInfo.unreadCount > 0 ? ` (${headerInfo.unreadCount} unread)` : ''
                      }`}
                </span>
              )}
            </div>
            <p className="text-xs sm:text-sm text-ink-600 mt-1 flex items-center space-x-2">
              <span>{headerInfo.subtitle}</span>
              {'siteUrl' in headerInfo && headerInfo.siteUrl && (
                <a
                  href={headerInfo.siteUrl}
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

        {/* Stream Actions on Top of Stream */}
        <div className="flex items-center space-x-2 self-start sm:self-auto flex-wrap gap-y-2">
          {/* Button to toggle seeing read articles as well vs unread only */}
          {(filter.type === 'feed' || filter.type === 'category' || filter.type === 'all') && (
            <button
              type="button"
              onClick={() => setUnreadOnly((prev) => !prev)}
              className={`px-3 py-1.5 rounded-xl border text-xs font-medium transition-colors shadow-2xs flex items-center space-x-1.5 cursor-pointer ${
                unreadOnly
                  ? 'border-paper-300 hover:border-paper-400 bg-paper-50 hover:bg-paper-200/50 text-ink-700'
                  : 'border-amberAccent-300 bg-amberAccent-100 hover:bg-amberAccent-200/80 text-amberAccent-900 font-semibold'
              }`}
              title={unreadOnly ? 'Show read articles as well' : 'Show only unread articles'}
            >
              {unreadOnly ? (
                <>
                  <Eye className="w-3.5 h-3.5 text-ink-500" />
                  <span>Show Read Articles</span>
                </>
              ) : (
                <>
                  <EyeOff className="w-3.5 h-3.5 text-amberAccent-700" />
                  <span>Show Unread Only</span>
                </>
              )}
            </button>
          )}

          {/* Refresh Button */}
          <button
            type="button"
            onClick={refresh}
            disabled={loading}
            className="p-2 rounded-xl border border-paper-300 hover:border-paper-400 bg-paper-50 text-ink-600 hover:text-ink-900 transition-colors shadow-2xs disabled:opacity-50 cursor-pointer"
            title="Refresh articles"
          >
            <RotateCw className={`w-4 h-4 ${loading ? 'animate-spin text-amberAccent-700' : ''}`} />
          </button>

          {/* Mark All as Read Button */}
          <button
            type="button"
            onClick={markAllAsRead}
            disabled={loading || (headerInfo.unreadCount === 0 && unreadInStream === 0)}
            className="px-3 py-1.5 rounded-xl border border-paper-300 hover:border-paper-400 bg-paper-50 hover:bg-paper-200/50 text-ink-700 font-medium text-xs transition-colors shadow-2xs flex items-center space-x-1.5 disabled:opacity-40 disabled:hover:bg-paper-50 cursor-pointer disabled:cursor-not-allowed"
            title="Mark all articles in view as read"
          >
            <CheckCheck className="w-4 h-4 text-emerald-700" />
            <span>Mark All Read</span>
          </button>
        </div>
      </header>

      {/* Error Alert */}
      {error && (
        <div className="mb-6 p-4 rounded-xl border border-rose-200 bg-rose-50/70 text-rose-800 text-xs flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
            <span>{error}</span>
          </div>
          <button
            type="button"
            onClick={refresh}
            className="px-2.5 py-1 bg-rose-100 hover:bg-rose-200 text-rose-900 rounded font-medium cursor-pointer"
          >
            Retry
          </button>
        </div>
      )}

      {/* Loading Skeleton View */}
      {loading && (
        <div className="space-y-4">
          {[1, 2, 3, 4].map((n) => (
            <div
              key={n}
              className="p-5 rounded-2xl border border-paper-200 bg-paper-50/70 animate-pulse flex items-start gap-4"
            >
              <div className="flex-1 space-y-3">
                <div className="flex items-center space-x-2">
                  <div className="w-4 h-4 rounded bg-paper-300" />
                  <div className="w-24 h-3 rounded bg-paper-300" />
                  <div className="w-12 h-3 rounded bg-paper-200" />
                </div>
                <div className="w-3/4 h-5 rounded bg-paper-300" />
                <div className="space-y-1.5">
                  <div className="w-full h-3 rounded bg-paper-200" />
                  <div className="w-5/6 h-3 rounded bg-paper-200" />
                </div>
                <div className="w-32 h-4 rounded bg-paper-200 pt-2" />
              </div>
              <div className="w-24 h-24 rounded-xl bg-paper-200 shrink-0 hidden sm:block" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State Views */}
      {!loading && articles.length === 0 && (
        <div className="py-14 px-6 text-center rounded-2xl border border-dashed border-paper-300 bg-paper-50/50 my-6">
          {filter.type === 'saved' ? (
            <div>
              <Bookmark className="w-10 h-10 text-paper-400 mx-auto mb-3" />
              <h3 className="font-serif text-lg font-bold text-ink-800">No Bookmarks Saved</h3>
              <p className="text-xs sm:text-sm text-ink-500 mt-1 max-w-sm mx-auto leading-relaxed">
                Click the bookmark icon on any article in your stream to save it here for future reading.
              </p>
            </div>
          ) : unreadOnly && (filter.type === 'feed' || filter.type === 'category') ? (
            /* Simple message telling all is read for this subscription */
            <div>
              <CheckCheck className="w-10 h-10 text-emerald-600 mx-auto mb-3" />
              <h3 className="font-serif text-lg font-bold text-ink-900">All read</h3>
              <p className="text-xs sm:text-sm text-ink-500 mt-1 max-w-sm mx-auto leading-relaxed">
                All articles in this subscription have been read.
              </p>
              <button
                type="button"
                onClick={() => setUnreadOnly(false)}
                className="mt-4 px-4 py-2 bg-paper-200 hover:bg-paper-300 text-ink-800 text-xs font-medium rounded-xl transition-colors shadow-2xs inline-flex items-center space-x-1.5 cursor-pointer"
              >
                <Eye className="w-3.5 h-3.5 text-ink-600" />
                <span>Show read articles</span>
              </button>
            </div>
          ) : filter.type === 'unread' ? (
            <div>
              <CheckCheck className="w-10 h-10 text-emerald-600 mx-auto mb-3" />
              <h3 className="font-serif text-lg font-bold text-ink-900">All read</h3>
              <p className="text-xs sm:text-sm text-ink-500 mt-1 max-w-sm mx-auto leading-relaxed">
                You have read every article across your publications.
              </p>
            </div>
          ) : feeds.length === 0 ? (
            <div>
              <Rss className="w-10 h-10 text-amberAccent-600 mx-auto mb-3" />
              <h3 className="font-serif text-lg font-bold text-ink-800">No Subscriptions Yet</h3>
              <p className="text-xs sm:text-sm text-ink-500 mt-1 max-w-sm mx-auto leading-relaxed">
                Subscribe to your favorite RSS and Atom publications to start streaming articles in Marginalia.
              </p>
              <button
                type="button"
                onClick={onOpenAddFeed}
                className="mt-4 px-4 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 text-xs font-medium rounded-xl transition-colors shadow-xs inline-flex items-center space-x-1.5 cursor-pointer"
              >
                <Plus className="w-4 h-4" />
                <span>Subscribe to First Feed</span>
              </button>
            </div>
          ) : (
            <div>
              <Inbox className="w-10 h-10 text-paper-400 mx-auto mb-3" />
              <h3 className="font-serif text-lg font-bold text-ink-800">No Articles Ingested Yet</h3>
              <p className="text-xs sm:text-sm text-ink-500 mt-1 max-w-sm mx-auto leading-relaxed">
                Marginalia is currently synchronizing with this feed. Dispatches will appear shortly.
              </p>
            </div>
          )}
        </div>
      )}

      {/* Populated Articles Stream */}
      {!loading && articles.length > 0 && (
        <div className="space-y-4">
          {articles.map((article) => (
            <ArticleCard
              key={article.id}
              article={article}
              onToggleRead={toggleRead}
              onToggleSave={toggleSave}
              onSelectArticle={handleSelectArticle}
            />
          ))}

          {/* Pagination & Load More Footer */}
          <div className="pt-6 pb-12 flex flex-col items-center justify-center space-y-3">
            {hasMore ? (
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="px-5 py-2.5 rounded-xl border border-paper-300 hover:border-paper-400 bg-paper-50 hover:bg-paper-200/70 text-ink-800 font-medium text-xs transition-colors shadow-2xs flex items-center space-x-2 disabled:opacity-50 cursor-pointer"
              >
                {loadingMore ? (
                  <>
                    <RotateCw className="w-3.5 h-3.5 animate-spin text-amberAccent-700" />
                    <span>Loading more dispatches...</span>
                  </>
                ) : (
                  <>
                    <span>Load More Articles</span>
                    <ChevronDown className="w-3.5 h-3.5 text-ink-500" />
                  </>
                )}
              </button>
            ) : (
              <div className="text-center">
                <span className="text-paper-400 text-sm">❧</span>
                <p className="text-xs text-ink-400 font-serif italic mt-1">
                  You have reached the end of this stream
                </p>
              </div>
            )}

            <p className="text-[11px] font-mono text-ink-400">
              Showing {articles.length} of {totalElements} articles
            </p>
          </div>
        </div>
      )}

      {/* Distraction-free Reading Drawer with Keyboard Shortcuts */}
      {activeArticle && (
        <ReadingDrawer
          article={activeArticle}
          articles={articles}
          onClose={handleCloseDrawer}
          onToggleRead={toggleRead}
          onToggleSave={toggleSave}
          onSelectArticle={setSelectedArticle}
          onLoadMore={hasMore ? loadMore : undefined}
        />
      )}
    </div>
  );
}
