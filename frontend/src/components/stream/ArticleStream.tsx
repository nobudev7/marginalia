import { useState, useEffect, useCallback, useRef, type FormEvent } from 'react';
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
  EyeOff,
  ArrowUpToLine,
  Filter,
  Loader2,
  X
} from 'lucide-react';
import type { NavFilter, FeedDto, FeedRequest, ArticleDto } from '../../types';
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
  onUpdateFeed?: (id: number, request: FeedRequest) => Promise<FeedDto>;
  onSelectArticle?: (article: ArticleDto) => void;
}

export function ArticleStream({
  filter,
  feeds,
  unreadByFeed,
  totalUnread,
  onUnreadChanged,
  onOpenAddFeed,
  onUpdateFeed,
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
    markAboveAsRead,
    markAllAsRead,
  } = useArticles({ filter, unreadOnly, onUnreadChanged });

  const [selectedArticle, setSelectedArticle] = useState<ArticleDto | null>(null);
  const [longPressArticle, setLongPressArticle] = useState<ArticleDto | null>(null);
  const longPressOpenedAtRef = useRef<number>(0);

  // Feed Title Filter Modal state
  const [isFilterModalOpen, setIsFilterModalOpen] = useState(false);
  const [draftKeywords, setDraftKeywords] = useState<string[]>([]);
  const [newKeywordInput, setNewKeywordInput] = useState('');
  const [savingFilters, setSavingFilters] = useState(false);
  const [filterError, setFilterError] = useState<string | null>(null);

  const activeFeed = currentFeedId !== undefined ? feeds.find((f) => f.id === currentFeedId) : undefined;
  const activeFilterKeywords = activeFeed?.filterKeywords || [];
  const activeFilteredCount = activeFeed?.filteredCount || 0;

  const handleOpenFilterModal = () => {
    if (!activeFeed) return;
    setDraftKeywords(activeFeed.filterKeywords ? [...activeFeed.filterKeywords] : []);
    setNewKeywordInput('');
    setFilterError(null);
    setIsFilterModalOpen(true);
  };

  const handleAddDraftKeyword = () => {
    const trimmed = newKeywordInput.trim();
    if (!trimmed) return;
    const exists = draftKeywords.some((k) => k.toLowerCase() === trimmed.toLowerCase());
    if (!exists) {
      setDraftKeywords((prev) => [...prev, trimmed]);
    }
    setNewKeywordInput('');
  };

  const handleRemoveDraftKeyword = (keywordToRemove: string) => {
    setDraftKeywords((prev) => prev.filter((k) => k !== keywordToRemove));
  };

  const handleSaveFilters = async (e: FormEvent) => {
    e.preventDefault();
    if (!activeFeed || !onUpdateFeed) return;

    // Also include any pending text in the input box if user clicked Save directly
    let finalKeywords = [...draftKeywords];
    const pending = newKeywordInput.trim();
    if (pending && !finalKeywords.some((k) => k.toLowerCase() === pending.toLowerCase())) {
      finalKeywords.push(pending);
    }

    try {
      setSavingFilters(true);
      setFilterError(null);
      await onUpdateFeed(activeFeed.id, {
        feedUrl: activeFeed.feedUrl,
        title: activeFeed.title,
        categoryId: activeFeed.categoryId,
        filterKeywords: finalKeywords,
      });
      await refresh();
      setIsFilterModalOpen(false);
    } catch (err) {
      setFilterError(err instanceof Error ? err.message : 'Failed to update feed filters');
    } finally {
      setSavingFilters(false);
    }
  };

  const handleOpenLongPressMenu = useCallback((article: ArticleDto) => {
    longPressOpenedAtRef.current = Date.now();
    setLongPressArticle(article);
  }, []);

  // Close reading drawer, filter modal, and long-press menu when switching navigation filters/feeds
  useEffect(() => {
    setSelectedArticle(null);
    setLongPressArticle(null);
    setIsFilterModalOpen(false);
  }, [filter.type, currentFeedId, currentCategoryId]);

  // Refresh article stream when a subscription is added or removed
  const prevFeedCountRef = useRef<number | null>(null);
  useEffect(() => {
    if (loading) return;
    if (prevFeedCountRef.current !== null && prevFeedCountRef.current !== feeds.length) {
      refresh();
    }
    prevFeedCountRef.current = feeds.length;
  }, [feeds.length, loading, refresh]);

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
  const firstUnreadIndex = articles.findIndex((a) => !a.isRead);
  const longPressIndex = longPressArticle
    ? articles.findIndex((a) => a.id === longPressArticle.id)
    : -1;
  const longPressHasUnreadAbove =
    firstUnreadIndex !== -1 && longPressIndex > firstUnreadIndex;

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
              {filter.type === 'feed' && activeFilteredCount > 0 && (
                <button
                  type="button"
                  onClick={handleOpenFilterModal}
                  className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-paper-200/80 hover:bg-paper-300/80 text-ink-600 border border-paper-300 inline-flex items-center space-x-1 transition-colors cursor-pointer"
                  title="Articles hidden by title filter — click to edit filter rules"
                >
                  <Filter className="w-3 h-3 text-amberAccent-700" />
                  <span>{activeFilteredCount} filtered</span>
                </button>
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
          {/* Feed Title Filter Button */}
          {filter.type === 'feed' && activeFeed && onUpdateFeed && (
            <button
              type="button"
              onClick={handleOpenFilterModal}
              className={`px-3 py-1.5 rounded-xl border text-xs font-medium transition-colors shadow-2xs flex items-center space-x-1.5 cursor-pointer ${
                activeFilterKeywords.length > 0
                  ? 'border-amberAccent-300 bg-amberAccent-100 hover:bg-amberAccent-200/80 text-amberAccent-900 font-semibold'
                  : 'border-paper-300 hover:border-paper-400 bg-paper-50 hover:bg-paper-200/50 text-ink-700'
              }`}
              title="Filter out articles in this feed by title words or phrases"
            >
              <Filter
                className={`w-3.5 h-3.5 ${
                  activeFilterKeywords.length > 0 ? 'text-amberAccent-700' : 'text-ink-500'
                }`}
              />
              <span>
                {activeFilterKeywords.length > 0
                  ? `Filter (${activeFilterKeywords.length})`
                  : 'Filter'}
              </span>
            </button>
          )}

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
              {filter.type === 'feed' && activeFilteredCount > 0 && (
                <p className="text-xs text-ink-400 mt-1">
                  ({activeFilteredCount} article{activeFilteredCount === 1 ? '' : 's'} hidden by title filter)
                </p>
              )}
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
          {articles.map((article, index) => (
            <ArticleCard
              key={article.id}
              article={article}
              hasUnreadAbove={firstUnreadIndex !== -1 && firstUnreadIndex < index}
              onToggleRead={toggleRead}
              onToggleSave={toggleSave}
              onMarkAboveAsRead={markAboveAsRead}
              onSelectArticle={handleSelectArticle}
              onLongPress={handleOpenLongPressMenu}
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

      {/* Mobile Long-Press Context Menu */}
      {longPressArticle && (
        <div
          className="md:hidden fixed inset-0 z-50 flex items-end justify-center p-4 pb-20 bg-ink-900/40 backdrop-blur-xs"
          onClick={() => {
            if (Date.now() - longPressOpenedAtRef.current < 400) {
              return;
            }
            setLongPressArticle(null);
          }}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-paper-50 border border-paper-300 shadow-xl overflow-hidden select-none"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="px-4 py-3 border-b border-paper-200 flex items-center justify-between gap-2 bg-paper-100/60">
              <p className="font-serif text-xs font-semibold text-ink-800 truncate">
                {longPressArticle.title}
              </p>
              <button
                type="button"
                onClick={() => setLongPressArticle(null)}
                className="p-1 text-ink-400 hover:text-ink-800 rounded-lg shrink-0"
                aria-label="Close menu"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-1.5 space-y-0.5">
              <button
                type="button"
                disabled={!longPressHasUnreadAbove}
                onClick={() => {
                  const id = longPressArticle.id;
                  setLongPressArticle(null);
                  markAboveAsRead(id);
                }}
                className="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-ink-800 active:bg-paper-200 disabled:opacity-40 disabled:pointer-events-none transition-colors text-left"
              >
                <ArrowUpToLine className="w-4 h-4 text-amberAccent-700 shrink-0" />
                <span>Make above as read</span>
              </button>

              <button
                type="button"
                disabled={loading || (headerInfo.unreadCount === 0 && unreadInStream === 0)}
                onClick={() => {
                  setLongPressArticle(null);
                  markAllAsRead();
                }}
                className="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-ink-800 active:bg-paper-200 disabled:opacity-40 disabled:pointer-events-none transition-colors text-left"
              >
                <CheckCheck className="w-4 h-4 text-emerald-700 shrink-0" />
                <span>Mark all read</span>
              </button>

              <button
                type="button"
                disabled={loading}
                onClick={() => {
                  setLongPressArticle(null);
                  refresh();
                }}
                className="w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-xl text-xs font-medium text-ink-800 active:bg-paper-200 disabled:opacity-40 disabled:pointer-events-none transition-colors text-left"
              >
                <RotateCw className="w-4 h-4 text-ink-600 shrink-0" />
                <span>Refresh</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Feed Title Filter Modal */}
      {isFilterModalOpen && activeFeed && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink-900/40 backdrop-blur-xs animate-in fade-in duration-200"
          onClick={() => !savingFilters && setIsFilterModalOpen(false)}
        >
          <div
            className="w-full max-w-lg bg-paper-50 border border-paper-300 rounded-2xl shadow-xl overflow-hidden animate-in zoom-in-95 duration-200"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="px-6 py-4 border-b border-paper-200 flex items-center justify-between">
              <div className="flex items-center space-x-2.5 min-w-0">
                <div className="p-2 rounded-lg bg-amberAccent-100 text-amberAccent-800 shrink-0">
                  <Filter className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <h3 className="font-serif text-lg font-bold text-ink-900 leading-tight">
                    Filter Articles by Title
                  </h3>
                  <p className="text-xs text-ink-500 truncate">
                    {activeFeed.title}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsFilterModalOpen(false)}
                disabled={savingFilters}
                className="p-1.5 text-ink-400 hover:text-ink-700 hover:bg-paper-200 rounded-lg transition-colors cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSaveFilters} className="p-6 space-y-4">
              {filterError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 leading-relaxed">
                  {filterError}
                </div>
              )}

              <p className="text-xs text-ink-600 leading-relaxed">
                Articles in this feed whose title contains any of the words or phrases below (case-insensitive) will be excluded from your stream.
              </p>

              {activeFilteredCount > 0 && (
                <div className="px-3.5 py-2.5 rounded-xl bg-amberAccent-100/60 border border-amberAccent-200 text-xs text-amberAccent-900 flex items-center space-x-2">
                  <Filter className="w-3.5 h-3.5 text-amberAccent-700 shrink-0" />
                  <span>
                    Currently hiding <strong>{activeFilteredCount}</strong> article{activeFilteredCount === 1 ? '' : 's'} in this feed.
                  </span>
                </div>
              )}

              <div>
                <label htmlFor="filter-keyword-input" className="block text-xs font-semibold text-ink-700 mb-1.5">
                  Add Filter Word or Phrase
                </label>
                <div className="flex items-center space-x-2">
                  <input
                    id="filter-keyword-input"
                    type="text"
                    value={newKeywordInput}
                    onChange={(e) => setNewKeywordInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleAddDraftKeyword();
                      }
                    }}
                    placeholder="e.g. [PR], Sponsored, Advertisement"
                    autoFocus
                    className="flex-1 px-3.5 py-2 bg-paper-50 border border-paper-300 rounded-xl text-xs text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
                  />
                  <button
                    type="button"
                    onClick={handleAddDraftKeyword}
                    disabled={!newKeywordInput.trim()}
                    className="px-3.5 py-2 bg-paper-200 hover:bg-paper-300 disabled:opacity-40 text-ink-800 text-xs font-semibold rounded-xl transition-colors cursor-pointer"
                  >
                    Add
                  </button>
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-semibold text-ink-700">
                    Active Filter Phrases ({draftKeywords.length})
                  </span>
                  {draftKeywords.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setDraftKeywords([])}
                      className="text-[11px] text-ink-500 hover:text-rose-700 transition-colors cursor-pointer"
                    >
                      Clear all
                    </button>
                  )}
                </div>

                {draftKeywords.length === 0 ? (
                  <div className="p-4 rounded-xl border border-dashed border-paper-300 bg-paper-100/40 text-center text-xs text-ink-400">
                    No filter words configured for this feed.
                  </div>
                ) : (
                  <div className="flex flex-wrap gap-2 p-3 rounded-xl border border-paper-300 bg-paper-100/40 max-h-44 overflow-y-auto">
                    {draftKeywords.map((kw) => (
                      <span
                        key={kw}
                        className="inline-flex items-center space-x-1.5 pl-2.5 pr-1.5 py-1 rounded-lg bg-paper-50 border border-paper-300 text-xs font-medium text-ink-800 shadow-2xs"
                      >
                        <span>{kw}</span>
                        <button
                          type="button"
                          onClick={() => handleRemoveDraftKeyword(kw)}
                          className="p-0.5 text-ink-400 hover:text-rose-700 rounded transition-colors cursor-pointer"
                          title={`Remove "${kw}"`}
                        >
                          <X className="w-3 h-3" />
                        </button>
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div className="pt-3 border-t border-paper-200 flex items-center justify-end space-x-3">
                <button
                  type="button"
                  onClick={() => setIsFilterModalOpen(false)}
                  disabled={savingFilters}
                  className="px-4 py-2 text-xs font-medium text-ink-600 hover:text-ink-900 hover:bg-paper-200 rounded-xl transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={savingFilters}
                  className="px-5 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 disabled:opacity-50 text-paper-50 font-medium text-xs rounded-xl transition-colors shadow-xs flex items-center space-x-2 cursor-pointer"
                >
                  {savingFilters ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      <span>Saving...</span>
                    </>
                  ) : (
                    <span>Save Filters</span>
                  )}
                </button>
              </div>
            </form>
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
