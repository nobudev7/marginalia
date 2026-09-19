import { useState, type FormEvent, useEffect } from 'react';
import { Rss, Loader2, FolderPlus, X } from 'lucide-react';
import type { CategoryDto, FeedRequest } from '../../types';

interface AddFeedModalProps {
  isOpen: boolean;
  onClose: () => void;
  categories: CategoryDto[];
  onAddFeed: (request: FeedRequest) => Promise<unknown>;
  onAddCategory: (name: string) => Promise<CategoryDto>;
}

export function AddFeedModal({
  isOpen,
  onClose,
  categories,
  onAddFeed,
  onAddCategory,
}: AddFeedModalProps) {
  const [feedUrl, setFeedUrl] = useState('');
  const [title, setTitle] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('');
  const [isCreatingCategory, setIsCreatingCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset form when modal opens or closes
  useEffect(() => {
    if (isOpen) {
      setFeedUrl('');
      setTitle('');
      setSelectedCategoryId('');
      setIsCreatingCategory(false);
      setNewCategoryName('');
      setError(null);
    }
  }, [isOpen]);

  // Handle Escape key to close modal
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen && !submitting) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, submitting, onClose]);

  if (!isOpen) return null;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!feedUrl.trim()) return;

    try {
      setSubmitting(true);
      setError(null);

      let targetCategoryId: number | null = selectedCategoryId ? Number(selectedCategoryId) : null;

      // If user is adding a brand-new category
      if (isCreatingCategory && newCategoryName.trim()) {
        const createdCat = await onAddCategory(newCategoryName.trim());
        targetCategoryId = createdCat.id;
      }

      await onAddFeed({
        feedUrl: feedUrl.trim(),
        title: title.trim() || undefined,
        categoryId: targetCategoryId,
      });

      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to subscribe to feed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink-900/40 backdrop-blur-xs animate-in fade-in duration-200">
      <div 
        className="w-full max-w-lg bg-paper-50 border border-paper-300 rounded-2xl shadow-xl overflow-hidden animate-in zoom-in-95 duration-200"
        role="dialog"
        aria-modal="true"
      >
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-paper-200 flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-lg bg-amberAccent-100 text-amberAccent-800">
              <Rss className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-serif text-lg font-bold text-ink-900 leading-tight">
                Subscribe to Feed
              </h3>
              <p className="text-xs text-ink-500">
                Add an RSS or Atom stream to your reading library
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="p-1.5 text-ink-400 hover:text-ink-700 hover:bg-paper-200 rounded-lg transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Body / Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 leading-relaxed">
              {error}
            </div>
          )}

          {/* Feed URL */}
          <div>
            <label htmlFor="feed-url" className="block text-xs font-semibold text-ink-700 mb-1">
              Feed URL <span className="text-amberAccent-700">*</span>
            </label>
            <input
              id="feed-url"
              type="url"
              value={feedUrl}
              onChange={(e) => setFeedUrl(e.target.value)}
              placeholder="https://example.com/feed.xml"
              required
              autoFocus
              className="w-full px-3.5 py-2 bg-paper-50 border border-paper-300 rounded-xl text-xs font-mono text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
            />
          </div>

          {/* Optional Title Override */}
          <div>
            <label htmlFor="feed-title" className="block text-xs font-semibold text-ink-700 mb-1">
              Feed Title <span className="text-ink-400 font-normal">(Optional)</span>
            </label>
            <input
              id="feed-title"
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Leave blank to auto-detect from feed"
              className="w-full px-3.5 py-2 bg-paper-50 border border-paper-300 rounded-xl text-xs text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
            />
          </div>

          {/* Category Selector */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="category-select" className="text-xs font-semibold text-ink-700">
                Folder / Category
              </label>
              {!isCreatingCategory && (
                <button
                  type="button"
                  onClick={() => setIsCreatingCategory(true)}
                  className="text-[11px] text-amberAccent-700 hover:text-amberAccent-900 font-medium flex items-center space-x-1"
                >
                  <FolderPlus className="w-3 h-3" />
                  <span>New Folder</span>
                </button>
              )}
            </div>

            {isCreatingCategory ? (
              <div className="flex items-center space-x-2">
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="e.g. Technology, News, Science"
                  autoFocus
                  className="flex-1 px-3.5 py-2 bg-paper-50 border border-paper-300 rounded-xl text-xs text-ink-900 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
                />
                <button
                  type="button"
                  onClick={() => setIsCreatingCategory(false)}
                  className="px-2.5 py-2 text-xs text-ink-600 hover:text-ink-900 hover:bg-paper-200 rounded-lg transition-colors"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <select
                id="category-select"
                value={selectedCategoryId}
                onChange={(e) => setSelectedCategoryId(e.target.value)}
                className="w-full px-3.5 py-2 bg-paper-50 border border-paper-300 rounded-xl text-xs text-ink-800 focus:outline-none focus:ring-2 focus:ring-amberAccent-700/20 focus:border-amberAccent-700"
              >
                <option value="">Uncategorized</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Footer Actions */}
          <div className="pt-3 border-t border-paper-200 flex items-center justify-end space-x-3">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 text-xs font-medium text-ink-600 hover:text-ink-900 hover:bg-paper-200 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting || !feedUrl.trim()}
              className="px-5 py-2 bg-amberAccent-700 hover:bg-amberAccent-800 disabled:opacity-50 text-paper-50 font-medium text-xs rounded-xl transition-colors shadow-xs flex items-center space-x-2"
            >
              {submitting ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Fetching & Subscribing...</span>
                </>
              ) : (
                <span>Subscribe</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
