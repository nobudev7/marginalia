import { useState } from 'react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { AddFeedModal } from '../feeds/AddFeedModal';
import { ArticleStream } from '../stream/ArticleStream';
import { useCategories } from '../../hooks/useCategories';
import { useFeeds } from '../../hooks/useFeeds';
import type { NavFilter, ArticleDto } from '../../types';

export function AppLayout() {
  const { categories, addCategory } = useCategories();
  const { 
    feeds, 
    totalUnread, 
    unreadByFeed, 
    addFeed, 
    deleteFeed, 
    refreshFeeds, 
  } = useFeeds();

  const [currentFilter, setCurrentFilter] = useState<NavFilter>({ type: 'all' });
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [isAddFeedModalOpen, setIsAddFeedModalOpen] = useState(false);
  const [, setSelectedArticle] = useState<ArticleDto | null>(null);

  const handleSelectArticle = (article: ArticleDto) => {
    setSelectedArticle(article);
  };

  return (
    <div className="min-h-screen bg-paper-100 text-ink-900 flex flex-col selection:bg-amberAccent-100 selection:text-amberAccent-800">
      {/* Top Application Header with Mobile Menu Toggle */}
      <Header onToggleMobileMenu={() => setIsMobileNavOpen(true)} />

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
          onDeleteFeed={deleteFeed}
          onRefreshFeeds={refreshFeeds}
          isMobileOpen={isMobileNavOpen}
          onCloseMobile={() => setIsMobileNavOpen(false)}
        />

        {/* Main Article Stream Pane */}
        <main className="flex-1 p-5 sm:p-8 max-w-4xl overflow-y-auto">
          <ArticleStream
            filter={currentFilter}
            feeds={feeds}
            unreadByFeed={unreadByFeed}
            totalUnread={totalUnread}
            onUnreadChanged={refreshFeeds}
            onOpenAddFeed={() => setIsAddFeedModalOpen(true)}
            onSelectArticle={handleSelectArticle}
          />
        </main>
      </div>

      {/* Subscribe to Feed Modal Dialog */}
      <AddFeedModal
        isOpen={isAddFeedModalOpen}
        onClose={() => setIsAddFeedModalOpen(false)}
        categories={categories}
        onAddFeed={addFeed}
        onAddCategory={addCategory}
      />
    </div>
  );
}
