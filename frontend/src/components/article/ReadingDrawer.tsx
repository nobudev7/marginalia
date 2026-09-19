import { useEffect, useMemo, useRef, useState } from 'react';
import { 
  X, 
  ChevronLeft, 
  ChevronRight, 
  Bookmark, 
  Check, 
  Circle, 
  ExternalLink, 
  Rss, 
  Clock, 
  BookOpen, 
  Keyboard,
  Share2,
  CheckCheck
} from 'lucide-react';
import DOMPurify from 'dompurify';
import type { ArticleDto } from '../../types';
import { formatRelativeTime, estimateReadingTime } from '../../utils/formatters';
import { useKeyboardShortcuts } from '../../hooks/useKeyboardShortcuts';

interface ReadingDrawerProps {
  article: ArticleDto | null;
  articles: ArticleDto[];
  onClose: () => void;
  onToggleRead: (id: number, currentRead: boolean) => void;
  onToggleSave: (id: number, currentSaved: boolean) => void;
  onSelectArticle: (article: ArticleDto) => void;
  onLoadMore?: () => void;
}

// Ensure external links in sanitized HTML open securely in a new tab
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank');
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

export function ReadingDrawer({
  article,
  articles,
  onClose,
  onToggleRead,
  onToggleSave,
  onSelectArticle,
  onLoadMore,
}: ReadingDrawerProps) {
  const contentRef = useRef<HTMLDivElement>(null);
  const autoMarkedIdsRef = useRef<Set<number>>(new Set());
  const [showShortcutsHelp, setShowShortcutsHelp] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);

  // Determine active article's position in stream for next/previous navigation
  const currentIndex = useMemo(() => {
    if (!article) return -1;
    return articles.findIndex((a) => a.id === article.id);
  }, [article, articles]);

  const hasNext = currentIndex !== -1 && currentIndex < articles.length - 1;
  const hasPrev = currentIndex > 0;

  const handleNext = () => {
    if (hasNext) {
      onSelectArticle(articles[currentIndex + 1]);
    } else if (onLoadMore) {
      onLoadMore();
    }
  };

  const handlePrev = () => {
    if (hasPrev) {
      onSelectArticle(articles[currentIndex - 1]);
    }
  };

  const handleToggleRead = () => {
    if (article) {
      onToggleRead(article.id, article.isRead);
    }
  };

  const handleToggleSave = () => {
    if (article) {
      onToggleSave(article.id, article.isSaved);
    }
  };

  const handleOpenOriginal = () => {
    if (article?.articleUrl) {
      window.open(article.articleUrl, '_blank', 'noopener,noreferrer');
    }
  };

  const handleCopyLink = async () => {
    if (!article?.articleUrl) return;
    try {
      await navigator.clipboard.writeText(article.articleUrl);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    } catch {
      // Fallback
    }
  };

  // Keyboard navigation shortcuts: j, k, m, s, v, Esc
  useKeyboardShortcuts({
    enabled: article !== null,
    handlers: {
      onNext: handleNext,
      onPrev: handlePrev,
      onToggleRead: handleToggleRead,
      onToggleSave: handleToggleSave,
      onOpenOriginal: handleOpenOriginal,
      onClose,
    },
  });

  // Automatically mark article as read ONCE when first opened in reading drawer
  useEffect(() => {
    if (article && !article.isRead && !autoMarkedIdsRef.current.has(article.id)) {
      autoMarkedIdsRef.current.add(article.id);
      onToggleRead(article.id, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [article?.id, onToggleRead]);

  // Reset scroll position to top whenever navigating to another article
  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scrollTop = 0;
    }
  }, [article?.id]);

  // Lock body scroll when drawer is open
  useEffect(() => {
    if (article) {
      document.body.style.overflow = 'hidden';
      return () => {
        document.body.style.overflow = '';
      };
    }
  }, [article]);

  // Sanitize article body HTML
  const sanitizedContent = useMemo(() => {
    if (!article) return '';
    const raw = article.content || article.summary || '';
    return DOMPurify.sanitize(raw, {
      ADD_ATTR: ['target', 'rel'],
    });
  }, [article]);

  if (!article) return null;

  const readingTime = estimateReadingTime(article.content || article.summary);
  const timeAgo = formatRelativeTime(article.publishedAt);
  const fullDate = new Date(article.publishedAt).toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  // Extract domain name for publisher attribution
  let domain = '';
  try {
    domain = new URL(article.articleUrl).hostname.replace(/^www\./, '');
  } catch {
    domain = article.feedTitle;
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* Dimmed Backdrop */}
      <div
        onClick={onClose}
        className="fixed inset-0 bg-ink-900/40 backdrop-blur-xs transition-opacity duration-300"
        aria-hidden="true"
      />

      {/* Slide-over Drawer Panel */}
      <div
        role="dialog"
        aria-modal="true"
        aria-label={article.title}
        className="relative w-full max-w-3xl bg-paper-100 shadow-2xl flex flex-col border-l border-paper-300 h-full z-10 transition-transform duration-300 overflow-hidden"
      >
        {/* Sticky Drawer Navigation Header */}
        <header className="sticky top-0 z-20 bg-paper-100/95 backdrop-blur-xs border-b border-paper-300 px-4 sm:px-6 py-3 flex items-center justify-between gap-3">
          {/* Left: Close & Feed info */}
          <div className="flex items-center space-x-3 min-w-0">
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-ink-500 hover:text-ink-900 hover:bg-paper-200 transition-colors cursor-pointer"
              title="Close drawer (Esc)"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center space-x-2 min-w-0">
              {article.feedIconUrl ? (
                <img
                  src={article.feedIconUrl}
                  alt=""
                  className="w-4 h-4 rounded object-contain shrink-0"
                  onError={(e) => {
                    (e.target as HTMLElement).style.display = 'none';
                  }}
                />
              ) : (
                <Rss className="w-3.5 h-3.5 text-amberAccent-700 shrink-0" />
              )}
              <span className="font-serif font-bold text-xs text-ink-800 truncate">
                {article.feedTitle}
              </span>
            </div>
          </div>

          {/* Right: Actions and Next/Prev Navigation */}
          <div className="flex items-center space-x-1 sm:space-x-1.5 shrink-0">
            {/* Previous Article (k) */}
            <button
              type="button"
              onClick={handlePrev}
              disabled={!hasPrev}
              className="p-1.5 rounded-lg text-ink-600 hover:text-ink-900 hover:bg-paper-200 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer disabled:cursor-not-allowed"
              title="Previous article (k)"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>

            {/* Position indicator */}
            {currentIndex !== -1 && (
              <span className="text-[11px] font-mono text-ink-400 px-1 hidden sm:inline">
                {currentIndex + 1} / {articles.length}
              </span>
            )}

            {/* Next Article (j) */}
            <button
              type="button"
              onClick={handleNext}
              disabled={!hasNext}
              className="p-1.5 rounded-lg text-ink-600 hover:text-ink-900 hover:bg-paper-200 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer disabled:cursor-not-allowed"
              title="Next article (j)"
            >
              <ChevronRight className="w-4 h-4" />
            </button>

            <div className="h-4 w-px bg-paper-300 mx-1" />

            {/* Mark Read/Unread (m) */}
            <button
              type="button"
              onClick={handleToggleRead}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                article.isRead
                  ? 'text-ink-400 hover:text-ink-900 hover:bg-paper-200'
                  : 'text-amberAccent-700 bg-amberAccent-100 hover:bg-amberAccent-200'
              }`}
              title={article.isRead ? 'Mark unread (m)' : 'Mark read (m)'}
            >
              {article.isRead ? (
                <Circle className="w-4 h-4" />
              ) : (
                <Check className="w-4 h-4" />
              )}
            </button>

            {/* Bookmark (s) */}
            <button
              type="button"
              onClick={handleToggleSave}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                article.isSaved
                  ? 'text-amber-800 bg-amber-100 hover:bg-amber-200'
                  : 'text-ink-500 hover:text-amber-700 hover:bg-paper-200'
              }`}
              title={article.isSaved ? 'Remove bookmark (s)' : 'Save bookmark (s)'}
            >
              <Bookmark
                className={`w-4 h-4 ${
                  article.isSaved ? 'fill-amber-600 text-amber-700' : ''
                }`}
              />
            </button>

            {/* Share / Copy link */}
            <button
              type="button"
              onClick={handleCopyLink}
              className="p-1.5 rounded-lg text-ink-500 hover:text-ink-900 hover:bg-paper-200 transition-colors cursor-pointer"
              title={copiedLink ? 'Link copied!' : 'Copy article URL'}
            >
              {copiedLink ? (
                <CheckCheck className="w-4 h-4 text-emerald-600" />
              ) : (
                <Share2 className="w-4 h-4" />
              )}
            </button>

            {/* Open Original (v) */}
            <button
              type="button"
              onClick={handleOpenOriginal}
              className="p-1.5 rounded-lg text-ink-500 hover:text-ink-900 hover:bg-paper-200 transition-colors cursor-pointer"
              title="Open original article in new tab (v)"
            >
              <ExternalLink className="w-4 h-4" />
            </button>

            <div className="h-4 w-px bg-paper-300 mx-0.5" />

            {/* Keyboard Shortcuts Help Toggle */}
            <button
              type="button"
              onClick={() => setShowShortcutsHelp((prev) => !prev)}
              className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                showShortcutsHelp
                  ? 'bg-paper-300 text-ink-900'
                  : 'text-ink-400 hover:text-ink-800 hover:bg-paper-200'
              }`}
              title="Keyboard shortcuts (j, k, m, s, v, Esc)"
            >
              <Keyboard className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Keyboard Shortcuts Guide Banner (Collapsible) */}
        {showShortcutsHelp && (
          <div className="bg-paper-200/90 border-b border-paper-300 px-4 py-2.5 text-xs text-ink-700 flex items-center justify-between gap-2 animate-in fade-in duration-150">
            <div className="flex items-center space-x-3 flex-wrap gap-y-1">
              <span className="font-semibold text-ink-900">Keyboard shortcuts:</span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">j</kbd>
                <span>Next</span>
              </span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">k</kbd>
                <span>Prev</span>
              </span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">m</kbd>
                <span>Toggle read</span>
              </span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">s</kbd>
                <span>Bookmark</span>
              </span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">v</kbd>
                <span>Open original</span>
              </span>
              <span className="inline-flex items-center space-x-1">
                <kbd className="px-1.5 py-0.5 rounded bg-paper-50 border border-paper-300 font-mono text-[10px] font-bold">Esc</kbd>
                <span>Close</span>
              </span>
            </div>
            <button
              type="button"
              onClick={() => setShowShortcutsHelp(false)}
              className="text-ink-400 hover:text-ink-700 cursor-pointer"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {/* Scrollable Article Content Body */}
        <div
          ref={contentRef}
          className="flex-1 overflow-y-auto px-6 sm:px-12 py-8 max-w-2xl mx-auto w-full selection:bg-amberAccent-100 selection:text-amberAccent-900"
        >
          {/* Metadata Row */}
          <div className="flex items-center space-x-2 text-xs text-ink-500 mb-4 flex-wrap gap-y-1">
            <span className="font-serif font-bold text-ink-800">
              {article.feedTitle}
            </span>

            {article.author && (
              <>
                <span className="text-paper-400">·</span>
                <span className="italic">By {article.author}</span>
              </>
            )}

            <span className="text-paper-400">·</span>

            <span className="flex items-center space-x-1" title={fullDate}>
              <Clock className="w-3 h-3 text-ink-400" />
              <span>{timeAgo}</span>
            </span>

            <span className="text-paper-400">·</span>

            <span className="flex items-center space-x-1 font-mono text-[11px]">
              <BookOpen className="w-3 h-3 text-ink-400" />
              <span>{readingTime}</span>
            </span>
          </div>

          {/* Article Headline */}
          <h1 className="font-serif text-3xl sm:text-4xl lg:text-[40px] font-bold tracking-tight text-ink-950 leading-tight mb-6">
            {article.title}
          </h1>

          {/* Hero Thumbnail / Feature Image */}
          {article.imageUrl && (
            <div className="mb-8 rounded-2xl overflow-hidden border border-paper-300 bg-paper-200">
              <img
                src={article.imageUrl}
                alt=""
                className="w-full max-h-[420px] object-cover"
                onError={(e) => {
                  (e.target as HTMLElement).style.display = 'none';
                }}
              />
            </div>
          )}

          {/* Sanitized Article Body Typography */}
          <div
            className="prose prose-stone prose-lg max-w-none font-serif text-ink-900 leading-relaxed prose-headings:font-serif prose-headings:font-bold prose-headings:text-ink-950 prose-a:text-amberAccent-700 hover:prose-a:text-amberAccent-900 prose-a:underline prose-img:rounded-xl prose-img:border prose-img:border-paper-300 prose-img:mx-auto prose-blockquote:border-l-4 prose-blockquote:border-amberAccent-600 prose-blockquote:font-serif prose-blockquote:italic prose-code:font-mono prose-code:bg-paper-200 prose-code:px-1.5 prose-code:py-0.5 prose-code:rounded"
            dangerouslySetInnerHTML={{ __html: sanitizedContent }}
          />

          {/* Publisher Attribution & External Link Footer */}
          <footer className="mt-12 pt-6 pb-8 pb-safe border-t border-paper-300">
            <div className="p-4 rounded-xl border border-paper-300 bg-paper-50 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
              <div>
                <p className="font-serif text-xs font-bold text-ink-800">
                  Published by {article.feedTitle}
                </p>
                <p className="text-[11px] font-mono text-ink-500 mt-0.5">
                  Original source: {domain}
                </p>
              </div>

              <a
                href={article.articleUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="px-3.5 py-1.5 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 text-xs font-medium rounded-lg transition-colors shadow-2xs inline-flex items-center space-x-1.5 shrink-0 self-start sm:self-auto cursor-pointer"
              >
                <span>Read Full Story</span>
                <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </div>

            {/* Bottom Keyboard Shortcut Hint */}
            <div className="mt-6 text-center text-[11px] font-mono text-ink-400">
              Press <kbd className="px-1 py-0.5 rounded bg-paper-200 text-ink-600">j</kbd> for next article,{' '}
              <kbd className="px-1 py-0.5 rounded bg-paper-200 text-ink-600">k</kbd> for previous,{' '}
              <kbd className="px-1 py-0.5 rounded bg-paper-200 text-ink-600">Esc</kbd> to close
            </div>
          </footer>
        </div>
      </div>
    </div>
  );
}
