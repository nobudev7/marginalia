/**
 * TypeScript definitions mirroring backend Marginalia DTOs and API contracts.
 */

export interface ArticleDto {
  id: number;
  feedId: number;
  feedTitle: string;
  feedIconUrl: string | null;
  guid: string;
  title: string;
  author: string | null;
  content: string | null;
  summary: string | null;
  articleUrl: string;
  imageUrl: string | null;
  publishedAt: string;
  isRead: boolean;
  isSaved: boolean;
}

export interface FeedDto {
  id: number;
  feedUrl: string;
  siteUrl: string;
  title: string;
  description: string | null;
  iconUrl: string | null;
  categoryId: number | null;
  categoryName: string | null;
  lastFetchedAt: string | null;
  fetchErrorCount: number;
  lastErrorMessage: string | null;
  createdAt: string;
  unreadCount?: number;
}

export interface CategoryDto {
  id: number;
  name: string;
  sortOrder: number;
  createdAt: string;
  unreadCount?: number;
}

export interface UserDto {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  isAdmin: boolean;
}

export interface AuthStatus {
  authenticated: boolean;
  user?: UserDto;
}

export interface WhitelistEntryDto {
  id: number;
  email: string;
  note: string | null;
  addedBy: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface UnreadCountsResponse {
  total: number;
  byFeed: Record<string, number>;
}

export interface FeedRequest {
  feedUrl: string;
  title?: string;
  categoryId?: number | null;
}

export interface CategoryRequest {
  name: string;
  sortOrder?: number;
}

export type NavFilter =
  | { type: 'all' }
  | { type: 'unread' }
  | { type: 'saved' }
  | { type: 'category'; categoryId: number; name: string }
  | { type: 'feed'; feedId: number; title: string };

export interface ApiStatus {
  online: boolean;
  statusCode?: number;
  message?: string;
}
