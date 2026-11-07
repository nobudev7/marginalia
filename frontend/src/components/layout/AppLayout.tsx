import { useState } from 'react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { MobileBottomNav } from './MobileBottomNav';
import { AddFeedModal } from '../feeds/AddFeedModal';
import { SettingsModal } from '../settings/SettingsModal';
import { ArticleStream } from '../stream/ArticleStream';
import { useCategories } from '../../hooks/useCategories';
import { useFeeds } from '../../hooks/useFeeds';
import type { NavFilter, ArticleDto } from '../../types';

export function AppLayout() {
  const { categories, addCategory, deleteCategory, refreshCategories, reorderCategories } = useCategories();
  const { 
    feeds, 
    totalUnread, 
    unreadByFeed, 
    addFeed, 
    updateFeed,
    deleteFeed, 
    refreshFeeds, 
    reorderFeeds,
  } = useFeeds();

  const [currentFilter, setCurrentFilter] = useState<NavFilter>({ type: 'all' });
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [isAddFeedModalOpen, setIsAddFeedModalOpen] = useState(false);
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [, setSelectedArticle] = useState<ArticleDto | null>(null);

  const handleSelectArticle = (article: ArticleDto) => {
    setSelectedArticle(article);
  };

  const handleDeleteCategory = async (categoryId: number) => {
    await deleteCategory(categoryId);
    await refreshFeeds();
    if (currentFilter.type === 'category' && currentFilter.categoryId === categoryId) {
      setCurrentFilter({ type: 'all' });
    }
  };

  return (
    <div className="min-h-screen bg-paper-100 text-ink-900 flex flex-col selection:bg-amberAccent-100 selection:text-amberAccent-800">
      {/* Top Application Header with Mobile Menu Toggle & Settings Button */}
      <Header 
        onToggleMobileMenu={() => setIsMobileNavOpen(true)} 
        onOpenSettings={() => setIsSettingsModalOpen(true)}
      />

      {/* Two-Column App Layout */}
      <div className="flex-1 flex w-full">
        {/* Left Navigation Sidebar */}
        <Sidebar
          currentFilter={currentFilter}
          onSelectFilter={setCurrentFilter}
          categories={categories}
          feeds={feeds}
          totalUnread={totalUnread}
          unreadByFeed={unreadByFeed}
          onOpenAddFeed={() => setIsAddFeedModalOpen(true)}
          onOpenSettings={() => setIsSettingsModalOpen(true)}
          onDeleteCategory={handleDeleteCategory}
          onDeleteFeed={deleteFeed}
          onRefreshFeeds={refreshFeeds}
          onReorderCategories={reorderCategories}
          onReorderFeeds={reorderFeeds}
          isMobileOpen={isMobileNavOpen}
          onCloseMobile={() => setIsMobileNavOpen(false)}
        />

        {/* Main Article Stream Pane */}
        <main className="flex-1 p-5 sm:p-8 pb-20 md:pb-8 max-w-4xl min-w-0">
          <ArticleStream
            filter={currentFilter}
            feeds={feeds}
            categories={categories}
            unreadByFeed={unreadByFeed}
            totalUnread={totalUnread}
            onSelectFilter={setCurrentFilter}
            onUnreadChanged={refreshFeeds}
            onOpenAddFeed={() => setIsAddFeedModalOpen(true)}
            onUpdateFeed={updateFeed}
            onSelectArticle={handleSelectArticle}
          />
        </main>
      </div>

      {/* Mobile Bottom Navigation Bar (< md screens) */}
      <MobileBottomNav
        currentFilter={currentFilter}
        onSelectFilter={setCurrentFilter}
        totalUnread={totalUnread}
        onOpenSidebar={() => setIsMobileNavOpen(true)}
        onOpenSettings={() => setIsSettingsModalOpen(true)}
      />

      {/* Subscribe to Feed Modal Dialog */}
      <AddFeedModal
        isOpen={isAddFeedModalOpen}
        onClose={() => setIsAddFeedModalOpen(false)}
        categories={categories}
        onAddFeed={addFeed}
        onAddCategory={addCategory}
      />

      {/* Settings, OPML Subscriptions & Whitelist Management Modal */}
      <SettingsModal
        isOpen={isSettingsModalOpen}
        onClose={() => setIsSettingsModalOpen(false)}
        onFeedsChanged={async () => {
          await Promise.all([refreshFeeds(), refreshCategories()]);
        }}
      />
    </div>
  );
}
