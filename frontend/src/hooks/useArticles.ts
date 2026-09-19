import { useState, useEffect, useCallback, useRef } from 'react';
import type { ArticleDto, NavFilter, PageResponse } from '../types';

interface UseArticlesOptions {
  filter: NavFilter;
  pageSize?: number;
  unreadOnly?: boolean;
  onUnreadChanged?: () => void;
}

export function useArticles({ filter, pageSize = 20, unreadOnly = false, onUnreadChanged }: UseArticlesOptions) {
  const [articles, setArticles] = useState<ArticleDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);

  // Build query string based on active navigation filter, unread toggle, and page index
  const buildUrl = useCallback(
    (pageIndex: number) => {
      const params = new URLSearchParams();
      params.set('page', pageIndex.toString());
      params.set('size', pageSize.toString());
      params.set('sort', 'publishedAt,desc');

      switch (filter.type) {
        case 'unread':
          params.set('unreadOnly', 'true');
          break;
        case 'saved':
          params.set('saved', 'true');
          break;
        case 'feed':
          params.set('feedId', filter.feedId.toString());
          if (unreadOnly) {
            params.set('unreadOnly', 'true');
          }
          break;
        case 'category':
          params.set('categoryId', filter.categoryId.toString());
          if (unreadOnly) {
            params.set('unreadOnly', 'true');
          }
          break;
        case 'all':
        default:
          if (unreadOnly) {
            params.set('unreadOnly', 'true');
          }
          break;
      }

      return `/api/articles?${params.toString()}`;
    },
    [filter, pageSize, unreadOnly]
  );

  // Fetch initial page 0 when filter changes
  useEffect(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setLoading(true);
    setError(null);
    setPage(0);

    const fetchInitialPage = async () => {
      try {
        const url = buildUrl(0);
        const res = await fetch(url, {
          credentials: 'include',
          signal: controller.signal,
        });

        if (!res.ok) {
          throw new Error(`Failed to load articles (HTTP ${res.status})`);
        }

        const data: PageResponse<ArticleDto> = await res.json();
        setArticles(data.content || []);
        setPage(data.number || 0);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
      } catch (err: unknown) {
        if ((err as Error).name !== 'AbortError') {
          setError(err instanceof Error ? err.message : 'Failed to load articles');
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    };

    fetchInitialPage();

    return () => {
      controller.abort();
    };
  }, [buildUrl]);

  // Load next page
  const loadMore = useCallback(async () => {
    if (loading || loadingMore || page + 1 >= totalPages) {
      return;
    }

    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const url = buildUrl(nextPage);
      const res = await fetch(url, { credentials: 'include' });

      if (!res.ok) {
        throw new Error(`Failed to load more articles (HTTP ${res.status})`);
      }

      const data: PageResponse<ArticleDto> = await res.json();
      setArticles((prev) => {
        // Prevent duplicate article IDs if new items were ingested
        const existingIds = new Set(prev.map((a) => a.id));
        const newArticles = (data.content || []).filter((a) => !existingIds.has(a.id));
        return [...prev, ...newArticles];
      });
      setPage(data.number);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load more articles');
    } finally {
      setLoadingMore(false);
    }
  }, [loading, loadingMore, page, totalPages, buildUrl]);

  // Refresh current view from page 0
  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const url = buildUrl(0);
      const res = await fetch(url, { credentials: 'include' });

      if (!res.ok) {
        throw new Error(`Failed to refresh articles (HTTP ${res.status})`);
      }

      const data: PageResponse<ArticleDto> = await res.json();
      setArticles(data.content || []);
      setPage(data.number || 0);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
      onUnreadChanged?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to refresh articles');
    } finally {
      setLoading(false);
    }
  }, [buildUrl, onUnreadChanged]);

  // Optimistically toggle read status
  const toggleRead = useCallback(
    async (articleId: number, currentRead: boolean) => {
      const targetRead = !currentRead;

      // Optimistic update
      setArticles((prev) =>
        prev.map((a) => (a.id === articleId ? { ...a, isRead: targetRead } : a))
      );

      try {
        const res = await fetch(`/api/articles/${articleId}/read?read=${targetRead}`, {
          method: 'PUT',
          credentials: 'include',
        });

        if (!res.ok) {
          throw new Error('Failed to update article read state');
        }

        onUnreadChanged?.();
      } catch (err: unknown) {
        // Rollback optimistic update
        setArticles((prev) =>
          prev.map((a) => (a.id === articleId ? { ...a, isRead: currentRead } : a))
        );
        console.error('Failed to toggle article read state:', err);
      }
    },
    [onUnreadChanged]
  );

  // Optimistically toggle bookmark status
  const toggleSave = useCallback(
    async (articleId: number, currentSaved: boolean) => {
      const targetSaved = !currentSaved;

      // Optimistic update
      setArticles((prev) =>
        prev.map((a) => (a.id === articleId ? { ...a, isSaved: targetSaved } : a))
      );

      try {
        const res = await fetch(`/api/articles/${articleId}/save?saved=${targetSaved}`, {
          method: 'PUT',
          credentials: 'include',
        });

        if (!res.ok) {
          throw new Error('Failed to update bookmark state');
        }
      } catch (err: unknown) {
        // Rollback optimistic update
        setArticles((prev) =>
          prev.map((a) => (a.id === articleId ? { ...a, isSaved: currentSaved } : a))
        );
        console.error('Failed to toggle bookmark state:', err);
      }
    },
    []
  );

  // Mark all unread articles as read in active view
  const markAllAsRead = useCallback(async () => {
    // Optimistically mark visible items read
    setArticles((prev) => prev.map((a) => ({ ...a, isRead: true })));

    try {
      const params = new URLSearchParams();
      if (filter.type === 'feed') {
        params.set('feedId', filter.feedId.toString());
      } else if (filter.type === 'category') {
        params.set('categoryId', filter.categoryId.toString());
      }

      const queryString = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`/api/articles/mark-all-read${queryString}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify([]),
        credentials: 'include',
      });

      if (!res.ok) {
        throw new Error('Failed to mark all articles as read');
      }

      onUnreadChanged?.();
    } catch (err: unknown) {
      console.error('Failed to mark all as read:', err);
      // Refresh to restore accurate server state
      refresh();
    }
  }, [filter, onUnreadChanged, refresh]);

  const hasMore = page + 1 < totalPages;

  return {
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
  };
}
