import { useState } from 'react';
import {
  Inbox,
  BookOpen,
  Bookmark,
  ChevronRight,
  Folder,
  FolderOpen,
  Rss,
  Plus,
  Trash2,
  RotateCw,
  X,
  Settings,
} from 'lucide-react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  closestCenter,
  pointerWithin,
  rectIntersection,
  useDroppable,
  useSensor,
  useSensors,
  type CollisionDetection,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { CategoryDto, FeedDto, NavFilter } from '../../types';
import { cn } from '../../utils/cn';
import { groupFeedsByCategory } from '../../utils/feedOrder';

interface SidebarProps {
  currentFilter: NavFilter;
  onSelectFilter: (filter: NavFilter) => void;
  categories: CategoryDto[];
  feeds: FeedDto[];
  totalUnread: number;
  unreadByFeed: Record<number, number>;
  onOpenAddFeed: () => void;
  onOpenSettings?: () => void;
  onDeleteCategory?: (categoryId: number) => Promise<void>;
  onDeleteFeed: (feedId: number) => Promise<void>;
  onRefreshFeeds: () => Promise<void>;
  onReorderCategories?: (orderedIds: number[]) => Promise<void>;
  onReorderFeeds?: (
    categoryId: number | null,
    orderedFeedIds: number[],
    categoryName?: string | null
  ) => Promise<void>;
  isMobileOpen: boolean;
  onCloseMobile: () => void;
}

interface SortableFeedItemProps {
  feed: FeedDto;
  unread: number;
  isFeedSelected: boolean;
  onSelect: () => void;
  onDelete: () => Promise<void>;
}

function SortableFeedItem({
  feed,
  unread,
  isFeedSelected,
  onSelect,
  onDelete,
}: SortableFeedItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: `feed-${feed.id}`,
    data: {
      type: 'feed',
      feed,
      categoryId: feed.categoryId ?? null,
    },
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      onClick={onSelect}
      className={cn(
        'group/item flex items-center justify-between pl-1.5 pr-1.5 py-1.5 md:px-2 md:py-1 rounded-md text-sm md:text-xs cursor-pointer active:cursor-grabbing transition-colors [-webkit-touch-callout:none]',
        isDragging && 'opacity-35 bg-amberAccent-100/40',
        isFeedSelected
          ? 'bg-amberAccent-100/70 text-amberAccent-900 font-semibold'
          : 'text-ink-600 hover:bg-paper-200/50 hover:text-ink-900'
      )}
    >
      <div className="flex items-center space-x-1.5 md:space-x-2 flex-1 min-w-0 pr-1">
        {feed.iconUrl ? (
          <img
            src={feed.iconUrl}
            alt=""
            draggable={false}
            className="w-4 h-4 md:w-3.5 md:h-3.5 rounded shrink-0 object-contain pointer-events-none"
            onError={(e) => {
              (e.target as HTMLElement).style.display = 'none';
            }}
          />
        ) : (
          <Rss className="w-3.5 h-3.5 md:w-3 md:h-3 text-ink-400 shrink-0" />
        )}
        <span className="truncate text-sm md:text-[11px]">{feed.title}</span>
      </div>

      <div className="flex items-center space-x-1">
        {unread > 0 && (
          <span className="px-1.5 py-0.2 rounded-full text-[11px] md:text-[10px] font-bold bg-amberAccent-200 text-amberAccent-900">
            {unread}
          </span>
        )}
        <button
          type="button"
          onMouseDown={(e) => e.stopPropagation()}
          onTouchStart={(e) => e.stopPropagation()}
          onClick={async (e) => {
            e.stopPropagation();
            if (confirm(`Unsubscribe from "${feed.title}"?`)) {
              await onDelete();
            }
          }}
          className="[@media(hover:hover)]:opacity-0 group-hover/item:opacity-100 p-1 text-ink-400 hover:text-red-700 rounded transition-opacity"
          title="Unsubscribe"
        >
          <Trash2 className="w-3.5 h-3.5 md:w-3 md:h-3" />
        </button>
      </div>
    </div>
  );
}

interface SortableCategorySectionProps {
  category: CategoryDto;
  catFeeds: FeedDto[];
  isExpanded: boolean;
  catUnread: number;
  isCatSelected: boolean;
  isFeedDropTarget: boolean;
  currentFilter: NavFilter;
  unreadByFeed: Record<number, number>;
  onToggleExpand: (e: React.MouseEvent) => void;
  onSelectCategory: () => void;
  onDeleteCategory?: () => Promise<void>;
  onSelectFeed: (feed: FeedDto) => void;
  onDeleteFeed: (feedId: number) => Promise<void>;
}

function SortableCategorySection({
  category,
  catFeeds,
  isExpanded,
  catUnread,
  isCatSelected,
  isFeedDropTarget,
  currentFilter,
  unreadByFeed,
  onToggleExpand,
  onSelectCategory,
  onDeleteCategory,
  onSelectFeed,
  onDeleteFeed,
}: SortableCategorySectionProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: `cat-${category.id}`,
    data: {
      type: 'category',
      category,
      categoryId: category.id,
    },
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        'space-y-0.5 rounded-lg transition-colors',
        isDragging && 'opacity-40 bg-paper-200/60',
        isFeedDropTarget && 'ring-1 ring-amberAccent-500/70 bg-amberAccent-50/40'
      )}
    >
      {/* Category Header */}
      <div
        {...attributes}
        {...listeners}
        onClick={onSelectCategory}
        className={cn(
          'group flex items-center justify-between pl-1 pr-1.5 py-2 md:px-2 md:py-1.5 rounded-lg text-[15px] md:text-xs cursor-pointer active:cursor-grabbing transition-colors [-webkit-touch-callout:none]',
          isCatSelected
            ? 'bg-paper-200 text-ink-900 font-semibold'
            : 'text-ink-700 hover:bg-paper-200/50 hover:text-ink-900'
        )}
      >
        <div className="flex items-center space-x-1 md:space-x-1.5 flex-1 min-w-0 pr-1">
          <button
            type="button"
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={onToggleExpand}
            className="p-0.5 text-ink-400 hover:text-ink-800 rounded shrink-0"
          >
            <ChevronRight className={cn('w-3.5 h-3.5 md:w-3 md:h-3 transition-transform', isExpanded && 'rotate-90')} />
          </button>
          {isExpanded ? (
            <FolderOpen className="w-4 h-4 md:w-3.5 md:h-3.5 text-amberAccent-700 shrink-0" />
          ) : (
            <Folder className="w-4 h-4 md:w-3.5 md:h-3.5 text-amberAccent-700 shrink-0" />
          )}
          <span className="truncate">{category.name}</span>
        </div>

        <div className="flex items-center space-x-1">
          {catUnread > 0 && (
            <span className="px-1.5 py-0.2 rounded-full text-xs md:text-[10px] font-medium bg-paper-300/80 text-ink-700">
              {catUnread}
            </span>
          )}
          {onDeleteCategory && (
            <button
              type="button"
              onMouseDown={(e) => e.stopPropagation()}
              onTouchStart={(e) => e.stopPropagation()}
              onClick={async (e) => {
                e.stopPropagation();
                const confirmMsg =
                  catFeeds.length > 0
                    ? `Delete folder "${category.name}"? The ${catFeeds.length === 1 ? '1 feed' : `${catFeeds.length} feeds`} under this folder will be moved to Uncategorized.`
                    : `Delete folder "${category.name}"?`;
                if (confirm(confirmMsg)) {
                  await onDeleteCategory();
                }
              }}
              className="[@media(hover:hover)]:opacity-0 group-hover:opacity-100 p-1 text-ink-400 hover:text-red-700 rounded transition-opacity"
              title="Delete folder"
            >
              <Trash2 className="w-3.5 h-3.5 md:w-3 md:h-3" />
            </button>
          )}
        </div>
      </div>

      {/* Feed items in this category */}
      {isExpanded && (
        <SortableContext
          items={catFeeds.map((f) => `feed-${f.id}`)}
          strategy={verticalListSortingStrategy}
        >
          <div className="ml-2 pl-2 md:ml-3.5 md:pl-6 space-y-0.5 border-l border-paper-200/80">
            {catFeeds.map((feed) => {
              const unread = unreadByFeed[feed.id] || 0;
              const isFeedSelected = currentFilter.type === 'feed' && currentFilter.feedId === feed.id;

              return (
                <SortableFeedItem
                  key={feed.id}
                  feed={feed}
                  unread={unread}
                  isFeedSelected={isFeedSelected}
                  onSelect={() => onSelectFeed(feed)}
                  onDelete={() => onDeleteFeed(feed.id)}
                />
              );
            })}
            {catFeeds.length === 0 && (
              <div className="text-xs md:text-[10px] text-ink-400 py-1.5 pl-2 italic">
                Drop feed here or empty folder
              </div>
            )}
          </div>
        </SortableContext>
      )}
    </div>
  );
}

interface UncategorizedSectionProps {
  feeds: FeedDto[];
  isExpanded: boolean;
  unreadTotal: number;
  isFeedDropTarget: boolean;
  currentFilter: NavFilter;
  unreadByFeed: Record<number, number>;
  onToggleExpand: () => void;
  onSelectFeed: (feed: FeedDto) => void;
  onDeleteFeed: (feedId: number) => Promise<void>;
}

function UncategorizedSection({
  feeds,
  isExpanded,
  unreadTotal,
  isFeedDropTarget,
  currentFilter,
  unreadByFeed,
  onToggleExpand,
  onSelectFeed,
  onDeleteFeed,
}: UncategorizedSectionProps) {
  const { setNodeRef } = useDroppable({
    id: 'cat-uncategorized',
    data: {
      type: 'category-drop',
      categoryId: null,
    },
  });

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'space-y-0.5 pt-1 rounded-lg transition-colors',
        isFeedDropTarget && 'ring-1 ring-amberAccent-500/70 bg-amberAccent-50/40'
      )}
    >
      <div
        className="flex items-center justify-between pl-1 pr-1.5 py-2 md:px-2 md:py-1.5 rounded-lg text-[15px] md:text-xs cursor-pointer text-ink-700 hover:bg-paper-200/50"
        onClick={onToggleExpand}
      >
        <div className="flex items-center space-x-1 md:space-x-1.5 flex-1 min-w-0">
          <ChevronRight
            className={cn('w-3.5 h-3.5 md:w-3 md:h-3 text-ink-400 transition-transform', isExpanded && 'rotate-90')}
          />
          <Folder className="w-4 h-4 md:w-3.5 md:h-3.5 text-ink-500" />
          <span className="truncate text-ink-600">Uncategorized</span>
        </div>
        {unreadTotal > 0 && (
          <span className="px-1.5 py-0.2 rounded-full text-xs md:text-[10px] font-medium bg-paper-300/80 text-ink-700">
            {unreadTotal}
          </span>
        )}
      </div>

      {isExpanded && (
        <SortableContext
          items={feeds.map((f) => `feed-${f.id}`)}
          strategy={verticalListSortingStrategy}
        >
          <div className="ml-2 pl-2 md:ml-3.5 md:pl-6 space-y-0.5 border-l border-paper-200/80">
            {feeds.map((feed) => {
              const unread = unreadByFeed[feed.id] || 0;
              const isFeedSelected = currentFilter.type === 'feed' && currentFilter.feedId === feed.id;

              return (
                <SortableFeedItem
                  key={feed.id}
                  feed={feed}
                  unread={unread}
                  isFeedSelected={isFeedSelected}
                  onSelect={() => onSelectFeed(feed)}
                  onDelete={() => onDeleteFeed(feed.id)}
                />
              );
            })}
            {feeds.length === 0 && (
              <div className="text-xs md:text-[10px] text-ink-400 py-1.5 pl-2 italic">
                Drop here for Uncategorized
              </div>
            )}
          </div>
        </SortableContext>
      )}
    </div>
  );
}

export function Sidebar({
  currentFilter,
  onSelectFilter,
  categories,
  feeds,
  totalUnread,
  unreadByFeed,
  onOpenAddFeed,
  onOpenSettings,
  onDeleteCategory,
  onDeleteFeed,
  onRefreshFeeds,
  onReorderCategories,
  onReorderFeeds,
  isMobileOpen,
  onCloseMobile,
}: SidebarProps) {
  // Map of open category IDs (expanded accordions)
  const [expandedCategories, setExpandedCategories] = useState<Record<string, boolean>>({
    uncategorized: true,
  });
  const [refreshing, setRefreshing] = useState(false);

  // Local feeds state used during active feed dragging for real-time cross-folder preview
  const [localFeeds, setLocalFeeds] = useState<FeedDto[]>(feeds);
  const [activeCategory, setActiveCategory] = useState<CategoryDto | null>(null);
  const [activeFeed, setActiveFeed] = useState<FeedDto | null>(null);
  const [overCategoryId, setOverCategoryId] = useState<number | 'uncategorized' | null>(null);

  const sensors = useSensors(
    useSensor(MouseSensor, {
      activationConstraint: { distance: 6 },
    }),
    useSensor(TouchSensor, {
      activationConstraint: { delay: 220, tolerance: 6 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  // Custom collision detection:
  // - When dragging a category folder, only collide with other category folders.
  // - When dragging a feed, use pointerWithin / rectIntersection / closestCenter across feeds and folders.
  const collisionDetection: CollisionDetection = (args) => {
    const activeType = args.active.data.current?.type;
    if (activeType === 'category') {
      const categoryContainers = args.droppableContainers.filter(
        (container) =>
          String(container.id).startsWith('cat-') && container.id !== 'cat-uncategorized'
      );
      return closestCenter({
        ...args,
        droppableContainers: categoryContainers,
      });
    }

    const pointerCollisions = pointerWithin(args);
    if (pointerCollisions.length > 0) {
      // Prefer specific feed items over outer category containers when pointer is inside both
      const feedCollision = pointerCollisions.find((c) => String(c.id).startsWith('feed-'));
      if (feedCollision) {
        return [feedCollision];
      }
      return pointerCollisions;
    }

    const rectCollisions = rectIntersection(args);
    if (rectCollisions.length > 0) {
      return rectCollisions;
    }

    return closestCenter(args);
  };

  const toggleCategory = (catKey: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    setExpandedCategories((prev) => ({
      ...prev,
      [catKey]: !(prev[catKey] ?? true),
    }));
  };

  const handleRefresh = async () => {
    try {
      setRefreshing(true);
      await onRefreshFeeds();
    } finally {
      setRefreshing(false);
    }
  };

  const handleSelect = (filter: NavFilter) => {
    onSelectFilter(filter);
    onCloseMobile();
  };

  const displayedFeeds = activeFeed ? localFeeds : feeds;

  // Group feeds by category ID preserving their list order
  const { categorized: categorizedFeeds, uncategorized: uncategorizedFeeds } =
    groupFeedsByCategory(displayedFeeds);

  // Calculate unread count per category
  const getCategoryUnread = (catFeeds: FeedDto[] = []) => {
    return catFeeds.reduce((acc, f) => acc + (unreadByFeed[f.id] || 0), 0);
  };

  const resolveOverCategoryId = (overIdStr: string, feedList: FeedDto[]): number | null | undefined => {
    if (overIdStr === 'cat-uncategorized') {
      return null;
    }
    if (overIdStr.startsWith('cat-')) {
      const parsed = Number(overIdStr.replace('cat-', ''));
      return Number.isNaN(parsed) ? undefined : parsed;
    }
    if (overIdStr.startsWith('feed-')) {
      const feedId = Number(overIdStr.replace('feed-', ''));
      const found = feedList.find((f) => f.id === feedId);
      return found ? (found.categoryId ?? null) : undefined;
    }
    return undefined;
  };

  const handleDragStart = (event: DragStartEvent) => {
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate(10);
    }
    const data = event.active.data.current;
    if (data?.type === 'category') {
      setActiveCategory(data.category as CategoryDto);
      setActiveFeed(null);
      setOverCategoryId(null);
    } else if (data?.type === 'feed') {
      setLocalFeeds(feeds);
      setActiveFeed(data.feed as FeedDto);
      setActiveCategory(null);
      const startCatId = (data.feed as FeedDto).categoryId;
      setOverCategoryId(startCatId ?? 'uncategorized');
    }
  };

  const handleDragOver = (event: DragOverEvent) => {
    const { active, over } = event;
    if (!over || active.data.current?.type !== 'feed') {
      return;
    }

    const activeIdStr = String(active.id);
    const overIdStr = String(over.id);
    if (activeIdStr === overIdStr) {
      return;
    }

    const activeFeedId = Number(activeIdStr.replace('feed-', ''));

    setLocalFeeds((prev) => {
      const activeIdx = prev.findIndex((f) => f.id === activeFeedId);
      if (activeIdx === -1) return prev;

      const currentFeed = prev[activeIdx];
      const targetCatId = resolveOverCategoryId(overIdStr, prev);
      if (targetCatId === undefined) return prev;

      setOverCategoryId(targetCatId ?? 'uncategorized');

      // Auto-expand collapsed target folder when hovering over it with a feed
      const catKey = targetCatId === null ? 'uncategorized' : String(targetCatId);
      setExpandedCategories((exp) => (exp[catKey] === false ? { ...exp, [catKey]: true } : exp));

      const currentCatId = currentFeed.categoryId ?? null;

      if (overIdStr.startsWith('feed-')) {
        const overFeedId = Number(overIdStr.replace('feed-', ''));
        const overIdx = prev.findIndex((f) => f.id === overFeedId);
        if (overIdx === -1) return prev;

        if (currentCatId !== targetCatId) {
          const updatedFeed: FeedDto = { ...currentFeed, categoryId: targetCatId };
          const next = [...prev];
          next[activeIdx] = updatedFeed;
          return arrayMove(next, activeIdx, overIdx);
        }
        return arrayMove(prev, activeIdx, overIdx);
      }

      // Hovering directly over a category header / empty category container
      if (currentCatId !== targetCatId) {
        const updatedFeed: FeedDto = { ...currentFeed, categoryId: targetCatId };
        const next = prev.filter((f) => f.id !== activeFeedId);
        next.push(updatedFeed);
        return next;
      }

      return prev;
    });
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    const activeType = active.data.current?.type;

    setActiveCategory(null);
    setActiveFeed(null);
    setOverCategoryId(null);

    if (!over) {
      setLocalFeeds(feeds);
      return;
    }

    if (activeType === 'category') {
      const activeCatId = Number(String(active.id).replace('cat-', ''));
      const overCatId = Number(String(over.id).replace('cat-', ''));
      if (!Number.isNaN(activeCatId) && !Number.isNaN(overCatId) && activeCatId !== overCatId) {
        const oldIndex = categories.findIndex((c) => c.id === activeCatId);
        const newIndex = categories.findIndex((c) => c.id === overCatId);
        if (oldIndex !== -1 && newIndex !== -1 && onReorderCategories) {
          const reordered = arrayMove(categories, oldIndex, newIndex);
          await onReorderCategories(reordered.map((c) => c.id));
        }
      }
      return;
    }

    if (activeType === 'feed') {
      const activeFeedId = Number(String(active.id).replace('feed-', ''));
      let finalFeeds = localFeeds;

      const overIdStr = String(over.id);
      if (overIdStr.startsWith('feed-')) {
        const overFeedId = Number(overIdStr.replace('feed-', ''));
        const activeIdx = finalFeeds.findIndex((f) => f.id === activeFeedId);
        const overIdx = finalFeeds.findIndex((f) => f.id === overFeedId);
        if (activeIdx !== -1 && overIdx !== -1 && activeIdx !== overIdx) {
          finalFeeds = arrayMove(finalFeeds, activeIdx, overIdx);
        }
      }

      const movedFeed = finalFeeds.find((f) => f.id === activeFeedId);
      if (!movedFeed || !onReorderFeeds) {
        return;
      }

      const targetCategoryId = movedFeed.categoryId ?? null;
      const targetCatFeeds = finalFeeds.filter(
        (f) => (f.categoryId ?? null) === targetCategoryId
      );
      const targetCategoryName =
        targetCategoryId !== null
          ? (categories.find((c) => c.id === targetCategoryId)?.name ?? null)
          : null;

      await onReorderFeeds(
        targetCategoryId,
        targetCatFeeds.map((f) => f.id),
        targetCategoryName
      );
    }
  };

  const handleDragCancel = () => {
    setActiveCategory(null);
    setActiveFeed(null);
    setOverCategoryId(null);
    setLocalFeeds(feeds);
  };

  const sidebarContent = (
    <aside className="w-full md:w-72 bg-paper-50 border-r border-paper-300 flex flex-col h-full select-none">
      {/* Top Mobile Header (only visible on mobile drawer) */}
      <div className="md:hidden px-3 py-3 border-b border-paper-300 flex items-center justify-between">
        <span className="font-serif font-bold text-ink-900 text-base">Navigation</span>
        <button
          type="button"
          onClick={onCloseMobile}
          className="p-1.5 text-ink-500 hover:text-ink-800 rounded-lg"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Primary Navigation Views */}
      <div className="p-2 md:p-3 space-y-1 border-b border-paper-200">
        <button
          type="button"
          onClick={() => handleSelect({ type: 'all' })}
          className={cn(
            'w-full flex items-center justify-between px-2.5 md:px-3 py-2 rounded-xl text-sm md:text-xs font-medium transition-colors',
            currentFilter.type === 'all'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2 md:space-x-2.5">
            <Inbox className="w-4 h-4 text-amberAccent-700" />
            <span>All Articles</span>
          </div>
          {totalUnread > 0 && (
            <span className="px-2 py-0.5 rounded-full text-xs md:text-[11px] font-bold bg-amberAccent-200 text-amberAccent-900">
              {totalUnread}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => handleSelect({ type: 'unread' })}
          className={cn(
            'w-full flex items-center justify-between px-2.5 md:px-3 py-2 rounded-xl text-sm md:text-xs font-medium transition-colors',
            currentFilter.type === 'unread'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2 md:space-x-2.5">
            <BookOpen className="w-4 h-4 text-emerald-700" />
            <span>Unread Only</span>
          </div>
          {totalUnread > 0 && (
            <span className="px-2 py-0.5 rounded-full text-xs md:text-[11px] font-bold bg-emerald-100 text-emerald-800">
              {totalUnread}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => handleSelect({ type: 'saved' })}
          className={cn(
            'w-full flex items-center justify-between px-2.5 md:px-3 py-2 rounded-xl text-sm md:text-xs font-medium transition-colors',
            currentFilter.type === 'saved'
              ? 'bg-amberAccent-100/80 text-amberAccent-900 font-semibold shadow-2xs'
              : 'text-ink-700 hover:bg-paper-200/70 hover:text-ink-900'
          )}
        >
          <div className="flex items-center space-x-2 md:space-x-2.5">
            <Bookmark className="w-4 h-4 text-amberAccent-600" />
            <span>Saved / Bookmarks</span>
          </div>
        </button>
      </div>

      {/* Subscriptions Section Header */}
      <div className="px-2.5 md:px-4 pt-3.5 md:pt-4 pb-2 flex items-center justify-between">
        <span className="text-xs md:text-[11px] font-semibold uppercase tracking-wider text-ink-400 font-mono">
          Subscriptions ({feeds.length})
        </span>
        <div className="flex items-center space-x-1">
          <button
            type="button"
            onClick={handleRefresh}
            disabled={refreshing}
            className="p-1 text-ink-400 hover:text-ink-800 rounded transition-colors"
            title="Refresh All Feeds"
          >
            <RotateCw className={cn('w-4 h-4 md:w-3.5 md:h-3.5', refreshing && 'animate-spin text-amberAccent-700')} />
          </button>
          {onOpenSettings && (
            <button
              type="button"
              onClick={onOpenSettings}
              className="p-1 text-ink-400 hover:text-ink-800 rounded transition-colors"
              title="Settings & OPML Subscriptions"
            >
              <Settings className="w-4 h-4 md:w-3.5 md:h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Category Folders & Feeds Accordion Scroll Area */}
      <div className="flex-1 overflow-y-auto px-1.5 md:px-3 py-1 space-y-1">
        <DndContext
          sensors={sensors}
          collisionDetection={collisionDetection}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDragEnd={handleDragEnd}
          onDragCancel={handleDragCancel}
        >
          {/* Sortable Categories */}
          <SortableContext
            items={categories.map((c) => `cat-${c.id}`)}
            strategy={verticalListSortingStrategy}
          >
            {categories.map((cat) => {
              const catFeeds = categorizedFeeds[cat.id] || [];
              const catKey = String(cat.id);
              const isExpanded = expandedCategories[catKey] ?? true;
              const catUnread = getCategoryUnread(catFeeds);
              const isCatSelected =
                currentFilter.type === 'category' && currentFilter.categoryId === cat.id;

              return (
                <SortableCategorySection
                  key={cat.id}
                  category={cat}
                  catFeeds={catFeeds}
                  isExpanded={isExpanded}
                  catUnread={catUnread}
                  isCatSelected={isCatSelected}
                  isFeedDropTarget={activeFeed !== null && overCategoryId === cat.id}
                  currentFilter={currentFilter}
                  unreadByFeed={unreadByFeed}
                  onToggleExpand={(e) => toggleCategory(catKey, e)}
                  onSelectCategory={() =>
                    handleSelect({ type: 'category', categoryId: cat.id, name: cat.name })
                  }
                  onDeleteCategory={
                    onDeleteCategory ? () => onDeleteCategory(cat.id) : undefined
                  }
                  onSelectFeed={(feed) =>
                    handleSelect({ type: 'feed', feedId: feed.id, title: feed.title })
                  }
                  onDeleteFeed={onDeleteFeed}
                />
              );
            })}
          </SortableContext>

          {/* Uncategorized Feeds Section (also shown while dragging a feed so user can drop into Uncategorized) */}
          {(uncategorizedFeeds.length > 0 || activeFeed !== null) && (
            <UncategorizedSection
              feeds={uncategorizedFeeds}
              isExpanded={expandedCategories.uncategorized ?? true}
              unreadTotal={getCategoryUnread(uncategorizedFeeds)}
              isFeedDropTarget={activeFeed !== null && overCategoryId === 'uncategorized'}
              currentFilter={currentFilter}
              unreadByFeed={unreadByFeed}
              onToggleExpand={() => toggleCategory('uncategorized')}
              onSelectFeed={(feed) =>
                handleSelect({ type: 'feed', feedId: feed.id, title: feed.title })
              }
              onDeleteFeed={onDeleteFeed}
            />
          )}

          <DragOverlay>
            {activeCategory ? (
              <div className="flex items-center space-x-2 px-2.5 py-1.5 rounded-lg text-[15px] md:text-xs font-semibold bg-paper-50 text-ink-900 border border-amberAccent-400 shadow-lg">
                <Folder className="w-4 h-4 md:w-3.5 md:h-3.5 text-amberAccent-700 shrink-0" />
                <span className="truncate">{activeCategory.name}</span>
              </div>
            ) : activeFeed ? (
              <div className="flex items-center space-x-2 px-2.5 py-1 rounded-md text-sm md:text-xs font-semibold bg-paper-50 text-ink-900 border border-amberAccent-400 shadow-lg">
                {activeFeed.iconUrl ? (
                  <img
                    src={activeFeed.iconUrl}
                    alt=""
                    className="w-4 h-4 md:w-3.5 md:h-3.5 rounded shrink-0 object-contain"
                  />
                ) : (
                  <Rss className="w-3.5 h-3.5 md:w-3 md:h-3 text-ink-400 shrink-0" />
                )}
                <span className="truncate text-sm md:text-[11px]">{activeFeed.title}</span>
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>

        {feeds.length === 0 && (
          <div className="p-4 text-center text-sm md:text-xs text-ink-400">
            No feeds yet. Click &ldquo;Add Feed&rdquo; to subscribe to your first publication.
          </div>
        )}
      </div>

      {/* Bottom Subscribe Button */}
      <div className="p-3 border-t border-paper-300 bg-paper-100/50">
        <button
          type="button"
          onClick={onOpenAddFeed}
          className="w-full flex items-center justify-center space-x-2 px-3 py-2.5 md:py-2 bg-amberAccent-700 hover:bg-amberAccent-800 text-paper-50 text-sm md:text-xs font-medium rounded-xl transition-colors shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>Add Feed</span>
        </button>
      </div>
    </aside>
  );

  return (
    <>
      {/* Mobile Backdrop overlay */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-ink-900/40 backdrop-blur-xs transition-opacity md:hidden"
          onClick={onCloseMobile}
        />
      )}

      {/* Single Mounted Sidebar Instance (Desktop sticky column on >= md, Mobile drawer on < md) */}
      <div
        className={cn(
          'shrink-0 md:flex md:sticky md:top-[53px] md:h-[calc(100vh-53px)] md:z-auto md:shadow-none',
          isMobileOpen
            ? 'fixed inset-y-0 left-0 z-50 flex w-[88vw] max-w-[88vw] md:w-72 md:max-w-full bg-paper-50 shadow-xl h-full animate-in slide-in-from-left duration-200'
            : 'hidden'
        )}
      >
        {sidebarContent}
      </div>
    </>
  );
}
