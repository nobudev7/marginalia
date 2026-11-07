import type { CategoryDto, FeedDto } from '../types';

/**
 * Group feeds by category ID, preserving the order of the given feed list.
 * Feeds without a category are returned in `uncategorized`.
 */
export function groupFeedsByCategory(feeds: FeedDto[]): {
  categorized: Record<number, FeedDto[]>;
  uncategorized: FeedDto[];
} {
  const categorized: Record<number, FeedDto[]> = {};
  const uncategorized: FeedDto[] = [];

  for (const feed of feeds) {
    if (feed.categoryId) {
      if (!categorized[feed.categoryId]) {
        categorized[feed.categoryId] = [];
      }
      categorized[feed.categoryId].push(feed);
    } else {
      uncategorized.push(feed);
    }
  }

  return { categorized, uncategorized };
}

/**
 * Flatten feeds into the same order as they appear in the sidebar:
 * folders in their display order (feeds in order within each folder), then Uncategorized.
 */
export function getOrderedFeeds(categories: CategoryDto[], feeds: FeedDto[]): FeedDto[] {
  const { categorized, uncategorized } = groupFeedsByCategory(feeds);
  const ordered: FeedDto[] = [];
  for (const category of categories) {
    ordered.push(...(categorized[category.id] || []));
  }
  ordered.push(...uncategorized);
  return ordered;
}

/**
 * Find the next feed after `currentFeedId` (in sidebar order) that has unread articles.
 * Does not wrap around; returns undefined when no later feed has unread articles.
 */
export function getNextUnreadFeed(
  categories: CategoryDto[],
  feeds: FeedDto[],
  unreadByFeed: Record<number, number>,
  currentFeedId: number
): FeedDto | undefined {
  const ordered = getOrderedFeeds(categories, feeds);
  const currentIndex = ordered.findIndex((f) => f.id === currentFeedId);
  if (currentIndex === -1) return undefined;
  return ordered.slice(currentIndex + 1).find((f) => (unreadByFeed[f.id] || 0) > 0);
}

/**
 * Find the next folder after `currentCategoryId` (in sidebar order) that has unread articles.
 * Does not wrap around; returns undefined when no later folder has unread articles.
 */
export function getNextUnreadCategory(
  categories: CategoryDto[],
  feeds: FeedDto[],
  unreadByFeed: Record<number, number>,
  currentCategoryId: number
): CategoryDto | undefined {
  const { categorized } = groupFeedsByCategory(feeds);
  const currentIndex = categories.findIndex((c) => c.id === currentCategoryId);
  if (currentIndex === -1) return undefined;
  return categories.slice(currentIndex + 1).find((c) =>
    (categorized[c.id] || []).some((f) => (unreadByFeed[f.id] || 0) > 0)
  );
}
