import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useArticles } from './useArticles';
import type { ArticleDto, NavFilter, PageResponse } from '../types';

// Hoisted so its identity is stable across renders: buildUrl (and the
// initial-load effect that depends on it) is keyed on the filter object,
// and a fresh literal on every render would re-fetch in a loop.
const feedFilter: NavFilter = { type: 'feed', feedId: 1, title: 'MacRumors' };

function makeArticle(id: number): ArticleDto {
  return {
    id,
    feedId: 1,
    feedTitle: 'MacRumors',
    feedIconUrl: null,
    guid: `guid-${id}`,
    title: `Article ${id}`,
    author: 'Author',
    content: null,
    summary: null,
    articleUrl: `https://example.com/${id}`,
    imageUrl: null,
    publishedAt: '2026-11-19T10:00:00Z',
    isRead: false,
    isSaved: false,
  };
}

function pageResponse(
  ids: number[],
  page: number,
  totalElements: number,
  totalPages: number
): PageResponse<ArticleDto> {
  return {
    content: ids.map(makeArticle),
    totalElements,
    totalPages,
    size: 20,
    number: page,
    first: page === 0,
    last: page === totalPages - 1,
    empty: ids.length === 0,
  };
}

function range(from: number, to: number): number[] {
  return Array.from({ length: to - from + 1 }, (_, i) => from + i);
}

const fetchMock = vi.fn();

function mockFetchJson(body: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
  });
}

describe('useArticles loadMore', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('re-syncs instead of showing more loaded articles than the total when the unread set shrinks between pages', async () => {
    // Regression test for "Showing 20 of 16 articles": in an unread-only
    // feed, page 0 loads 20 of 36 unread articles. The user then reads some
    // of them, so by the time "More" fetches page 1 the unread set has
    // shrunk to 16 and the next page is empty. The stream must re-sync to
    // the current set instead of keeping 20 loaded articles against a
    // total of 16.
    let pageZeroCalls = 0;
    fetchMock.mockImplementation((url: string) => {
      const page = new URL(url, 'http://localhost').searchParams.get('page');
      if (page === '0') {
        pageZeroCalls += 1;
        // First load: 20 of 36. Refresh after the stale page: the 16 that
        // are still unread.
        return pageZeroCalls === 1
          ? mockFetchJson(pageResponse(range(1, 20), 0, 36, 2))
          : mockFetchJson(pageResponse(range(1, 16), 0, 16, 1));
      }
      // Page 1 of the shrunken unread set: nothing left to show.
      return mockFetchJson(pageResponse([], 1, 16, 1));
    });

    const { result } = renderHook(() =>
      useArticles({
        filter: feedFilter,
        unreadOnly: true,
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.articles).toHaveLength(20);
    expect(result.current.totalElements).toBe(36);
    expect(result.current.hasMore).toBe(true);

    await act(async () => {
      await result.current.loadMore();
    });
    await waitFor(() => expect(result.current.loadingMore).toBe(false));

    // The list and the total now describe the same current set, so the
    // footer can never read "Showing 20 of 16 articles".
    expect(result.current.articles.length).toBeLessThanOrEqual(
      result.current.totalElements
    );
    expect(result.current.articles).toHaveLength(16);
    expect(result.current.totalElements).toBe(16);
    expect(result.current.hasMore).toBe(false);
    // The stale page was followed by a page-0 re-sync.
    expect(pageZeroCalls).toBe(2);
  });

  it('appends the next page normally when it contains new articles', async () => {
    fetchMock.mockImplementation((url: string) => {
      const page = new URL(url, 'http://localhost').searchParams.get('page');
      if (page === '0') {
        return mockFetchJson(pageResponse(range(1, 20), 0, 36, 2));
      }
      return mockFetchJson(pageResponse(range(21, 36), 1, 36, 2));
    });

    const { result } = renderHook(() =>
      useArticles({
        filter: feedFilter,
        unreadOnly: true,
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.articles).toHaveLength(20);

    await act(async () => {
      await result.current.loadMore();
    });
    await waitFor(() => expect(result.current.loadingMore).toBe(false));

    expect(result.current.articles).toHaveLength(36);
    expect(result.current.totalElements).toBe(36);
    expect(result.current.hasMore).toBe(false);
  });
});
