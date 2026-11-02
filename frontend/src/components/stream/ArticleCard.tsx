import React, { useState, useRef, useEffect } from 'react';
import { 
  Bookmark, 
  Check, 
  Circle, 
  ExternalLink, 
  Rss, 
  Clock,
  BookOpen,
  ArrowUpToLine,
  MoreHorizontal
} from 'lucide-react';
import type { ArticleDto } from '../../types';
import { formatRelativeTime, stripHtml, estimateReadingTime } from '../../utils/formatters';

interface ArticleCardProps {
  article: ArticleDto;
  hasUnreadAbove?: boolean;
  onToggleRead: (id: number, currentRead: boolean) => void;
  onToggleSave: (id: number, currentSaved: boolean) => void;
  onMarkAboveAsRead?: (id: number) => void;
  onSelectArticle?: (article: ArticleDto) => void;
  onLongPress?: (article: ArticleDto) => void;
}

export function ArticleCard({
  article,
  hasUnreadAbove = false,
  onToggleRead,
  onToggleSave,
  onMarkAboveAsRead,
  onSelectArticle,
  onLongPress,
}: ArticleCardProps) {
  const [imageError, setImageError] = useState(false);
  const longPressTimerRef = useRef<number | null>(null);
  const touchStartPosRef = useRef<{ x: number; y: number } | null>(null);
  const longPressTriggeredRef = useRef(false);

  const snippet = stripHtml(article.summary || article.content);
  const readingTime = estimateReadingTime(article.content || article.summary);
  const timeAgo = formatRelativeTime(article.publishedAt);

  const clearLongPressTimer = () => {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      if (longPressTimerRef.current !== null) {
        window.clearTimeout(longPressTimerRef.current);
      }
    };
  }, []);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (!onLongPress) return;
    if ((e.target as HTMLElement).closest('button, a')) return;

    const touch = e.touches[0];
    if (!touch) return;

    touchStartPosRef.current = { x: touch.clientX, y: touch.clientY };
    longPressTriggeredRef.current = false;
    clearLongPressTimer();

    longPressTimerRef.current = window.setTimeout(() => {
      longPressTriggeredRef.current = true;
      longPressTimerRef.current = null;
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        navigator.vibrate(15);
      }
      onLongPress(article);
    }, 400);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (longPressTimerRef.current === null || !touchStartPosRef.current) return;
    const touch = e.touches[0];
    if (!touch) return;

    const dx = touch.clientX - touchStartPosRef.current.x;
    const dy = touch.clientY - touchStartPosRef.current.y;
    if (Math.hypot(dx, dy) > 15) {
      clearLongPressTimer();
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    clearLongPressTimer();
    if (longPressTriggeredRef.current) {
      // Prevent iOS Safari from synthesizing a click event on the newly mounted
      // modal backdrop when the user lifts their finger after a long press
      if (e.cancelable) {
        e.preventDefault();
      }
    }
  };

  const handleTouchCancel = () => {
    clearLongPressTimer();
  };

  const handleContextMenu = (e: React.MouseEvent) => {
    if (!onLongPress) return;
    if ((e.target as HTMLElement).closest('button, a')) return;
    if (window.matchMedia('(max-width: 767px)').matches) {
      e.preventDefault();
      longPressTriggeredRef.current = true;
      clearLongPressTimer();
      onLongPress(article);
    }
  };

  const handleCardClick = (e: React.MouseEvent) => {
    if (longPressTriggeredRef.current) {
      longPressTriggeredRef.current = false;
      e.preventDefault();
      return;
    }
    // Don't trigger article selection if clicking interactive buttons or links
    if ((e.target as HTMLElement).closest('button, a')) {
      return;
    }
    onSelectArticle?.(article);
  };

  return (
    <article
      onClick={handleCardClick}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      onTouchCancel={handleTouchCancel}
      onContextMenu={handleContextMenu}
      className={`group relative p-4 sm:p-5 rounded-2xl border transition-all duration-200 cursor-pointer select-none sm:select-auto [-webkit-touch-callout:none] ${
        article.isRead
          ? 'bg-paper-50/70 border-paper-200 hover:border-paper-300 hover:bg-paper-50'
          : 'bg-paper-50 border-paper-300/90 shadow-2xs hover:shadow-xs hover:border-amberAccent-400/60'
      }`}
    >
      <div className="flex items-start gap-4">
        {/* Main Content Column */}
        <div className="flex-1 min-w-0">
          {/* Publication Metadata Row */}
          <div className="flex items-center space-x-2 text-xs text-ink-500 mb-2 flex-wrap gap-y-1">
            {/* Unread Accent Dot */}
            {!article.isRead && (
              <span
                className="w-2 h-2 rounded-full bg-amberAccent-600 shrink-0"
                title="Unread article"
              />
            )}

            {/* Feed Icon & Publication Title */}
            <div className="flex items-center space-x-1.5 font-medium text-ink-700 max-w-[200px] truncate">
              {article.feedIconUrl ? (
                <img
                  src={article.feedIconUrl}
                  alt=""
                  className="w-3.5 h-3.5 rounded object-contain shrink-0"
                  onError={(e) => {
                    (e.target as HTMLElement).style.display = 'none';
                  }}
                />
              ) : (
                <Rss className="w-3 h-3 text-amberAccent-700 shrink-0" />
              )}
              <span className="truncate">{article.feedTitle}</span>
            </div>

            <span className="text-paper-400">·</span>

            {/* Relative Timestamp */}
            {timeAgo && (
              <span className="flex items-center space-x-1 shrink-0 font-sans">
                <Clock className="w-3 h-3 text-ink-400" />
                <span>{timeAgo}</span>
              </span>
            )}

            {/* Author (if available) */}
            {article.author && (
              <>
                <span className="text-paper-400">·</span>
                <span className="truncate max-w-[140px] italic">by {article.author}</span>
              </>
            )}

            <span className="text-paper-400">·</span>

            {/* Reading Time */}
            <span className="flex items-center space-x-1 shrink-0 font-mono text-[11px] text-ink-400">
              <BookOpen className="w-3 h-3 text-ink-300" />
              <span>{readingTime}</span>
            </span>
          </div>

          {/* Article Title */}
          <h3
            className={`font-serif text-lg sm:text-xl tracking-tight leading-snug line-clamp-2 transition-colors ${
              article.isRead
                ? 'font-medium text-ink-600 group-hover:text-ink-900'
                : 'font-bold text-ink-950 group-hover:text-amberAccent-900'
            }`}
          >
            {article.title}
          </h3>

          {/* Summary Snippet */}
          {snippet && (
            <p
              className={`font-sans text-xs sm:text-sm leading-relaxed mt-2 line-clamp-2 ${
                article.isRead ? 'text-ink-400' : 'text-ink-600'
              }`}
            >
              {snippet}
            </p>
          )}

          {/* Card Action Controls Footer */}
          <div className="flex items-center justify-between mt-3.5 pt-3 border-t border-paper-200/70 text-xs">
            <div className="flex items-center space-x-1 sm:space-x-2">
              {/* Toggle Read / Unread */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleRead(article.id, article.isRead);
                }}
                className={`px-2.5 py-1 rounded-lg text-xs font-medium flex items-center space-x-1.5 transition-colors ${
                  article.isRead
                    ? 'text-ink-500 hover:text-ink-800 hover:bg-paper-200/60'
                    : 'text-amberAccent-800 bg-amberAccent-100/70 hover:bg-amberAccent-200/80 font-semibold'
                }`}
                title={article.isRead ? 'Mark as unread' : 'Mark as read'}
              >
                {article.isRead ? (
                  <>
                    <Circle className="w-3.5 h-3.5 text-ink-400" />
                    <span className="hidden sm:inline">Mark unread</span>
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5 text-amberAccent-700" />
                    <span>Unread</span>
                  </>
                )}
              </button>

              {/* Desktop: Make above as read */}
              {onMarkAboveAsRead && (
                <button
                  type="button"
                  disabled={!hasUnreadAbove}
                  onClick={(e) => {
                    e.stopPropagation();
                    onMarkAboveAsRead(article.id);
                  }}
                  className="hidden sm:flex px-2.5 py-1 rounded-lg text-xs font-medium items-center space-x-1.5 text-ink-500 hover:text-amberAccent-800 hover:bg-paper-200/60 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                  title="Make above as read"
                >
                  <ArrowUpToLine className="w-3.5 h-3.5 text-ink-400" />
                  <span>Make above as read</span>
                </button>
              )}

              {/* Bookmark Star Toggle */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleSave(article.id, article.isSaved);
                }}
                className={`px-2.5 py-1 rounded-lg text-xs font-medium flex items-center space-x-1.5 transition-colors ${
                  article.isSaved
                    ? 'text-amber-800 bg-amber-100 hover:bg-amber-200'
                    : 'text-ink-500 hover:text-amber-700 hover:bg-paper-200/60'
                }`}
                title={article.isSaved ? 'Remove bookmark' : 'Save for later'}
              >
                <Bookmark
                  className={`w-3.5 h-3.5 ${
                    article.isSaved ? 'fill-amber-600 text-amber-700' : 'text-ink-400'
                  }`}
                />
                <span className="hidden sm:inline">
                  {article.isSaved ? 'Saved' : 'Save'}
                </span>
              </button>
            </div>

            <div className="flex items-center space-x-1">
              {/* Mobile Actions Menu Trigger (also activated via long-press) */}
              {onLongPress && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onLongPress(article);
                  }}
                  className="md:hidden p-1.5 text-ink-400 hover:text-ink-800 active:bg-paper-200/70 rounded-lg transition-colors"
                  title="Article actions"
                  aria-label="Article actions"
                >
                  <MoreHorizontal className="w-4 h-4" />
                </button>
              )}

              {/* External Original Link */}
              <a
                href={article.articleUrl}
                target="_blank"
                rel="noreferrer"
                onClick={(e) => e.stopPropagation()}
                className="p-1.5 text-ink-400 hover:text-ink-800 hover:bg-paper-200/70 rounded-lg transition-colors flex items-center space-x-1"
                title="Open original article in new tab"
              >
                <span className="text-[11px] hidden sm:inline text-ink-500">Original</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>
        </div>

        {/* Thumbnail Image (Right Column) */}
        {article.imageUrl && !imageError && (
          <div className="shrink-0 hidden xs:block sm:block">
            <img
              src={article.imageUrl}
              alt=""
              loading="lazy"
              onError={() => setImageError(true)}
              className="w-20 h-20 sm:w-28 sm:h-28 rounded-xl object-cover border border-paper-200/90 bg-paper-100 group-hover:border-paper-300 transition-colors"
            />
          </div>
        )}
      </div>
    </article>
  );
}
