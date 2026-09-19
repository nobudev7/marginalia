import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../api/client';
import type { FeedDto, FeedRequest, UnreadCountResponse, UnreadCountsResponse } from '../types';

export function useFeeds() {
  const [feeds, setFeeds] = useState<FeedDto[]>([]);
  const [totalUnread, setTotalUnread] = useState(0);
  const [unreadByFeed, setUnreadByFeed] = useState<Record<number, number>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFeedsAndCounts = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      // Fetch feeds and unread counts in parallel
      const [rawFeeds, countsRes] = await Promise.all([
        apiFetch<FeedDto[]>('/api/feeds'),
        apiFetch<UnreadCountsResponse | UnreadCountResponse>('/api/articles/unread-counts').catch(async () => {
          // Fallback to legacy single unread-count endpoint if unread-counts is not yet active
          return apiFetch<UnreadCountResponse>('/api/articles/unread-count');
        }),
      ]);

      let total = 0;
      const byFeedMap: Record<number, number> = {};

      if ('byFeed' in countsRes && countsRes.byFeed) {
        total = countsRes.total || 0;
        for (const [fId, cnt] of Object.entries(countsRes.byFeed)) {
          byFeedMap[Number(fId)] = cnt;
        }
      } else if ('unreadCount' in countsRes) {
        total = countsRes.unreadCount || 0;
      }

      setTotalUnread(total);
      setUnreadByFeed(byFeedMap);

      // Attach unread count to each feed object
      const enrichedFeeds = rawFeeds.map((f) => ({
        ...f,
        unreadCount: byFeedMap[f.id] || 0,
      }));

      setFeeds(enrichedFeeds);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load feeds');
    } finally {
      setLoading(false);
    }
  }, []);

  const addFeed = useCallback(async (request: FeedRequest): Promise<FeedDto> => {
    const created = await apiFetch<FeedDto>('/api/feeds', {
      method: 'POST',
      body: JSON.stringify(request),
    });

    // Proactively trigger initial crawler fetch in background so articles are parsed
    try {
      await apiFetch(`/api/feeds/${created.id}/refresh`, { method: 'POST' });
    } catch (crawlErr) {
      console.warn('Initial feed crawl background notice:', crawlErr);
    }

    // Refresh feeds and counts to reflect new articles
    await fetchFeedsAndCounts();
    return created;
  }, [fetchFeedsAndCounts]);

  const deleteFeed = useCallback(async (id: number) => {
    await apiFetch(`/api/feeds/${id}`, { method: 'DELETE' });
    setFeeds((prev) => prev.filter((f) => f.id !== id));
    setUnreadByFeed((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }, []);

  const crawlFeed = useCallback(async (id: number) => {
    await apiFetch(`/api/feeds/${id}/refresh`, { method: 'POST' });
    await fetchFeedsAndCounts();
  }, [fetchFeedsAndCounts]);

  useEffect(() => {
    fetchFeedsAndCounts();
  }, [fetchFeedsAndCounts]);

  return {
    feeds,
    totalUnread,
    unreadByFeed,
    loading,
    error,
    refreshFeeds: fetchFeedsAndCounts,
    addFeed,
    deleteFeed,
    crawlFeed,
  };
}
