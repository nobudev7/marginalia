import { useCallback, useEffect, useState } from 'react';
import { apiFetch } from '../api/client';
import type { CategoryDto, CategoryRequest } from '../types';

export function useCategories() {
  const [categories, setCategories] = useState<CategoryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiFetch<CategoryDto[]>('/api/categories');
      // Sort by sortOrder then name
      const sorted = [...data].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
      setCategories(sorted);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load categories');
    } finally {
      setLoading(false);
    }
  }, []);

  const addCategory = useCallback(async (name: string, sortOrder = 0): Promise<CategoryDto> => {
    const payload: CategoryRequest = { name: name.trim(), sortOrder };
    const created = await apiFetch<CategoryDto>('/api/categories', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    setCategories((prev) => [...prev, created].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)));
    return created;
  }, []);

  const deleteCategory = useCallback(async (id: number) => {
    await apiFetch(`/api/categories/${id}`, { method: 'DELETE' });
    setCategories((prev) => prev.filter((c) => c.id !== id));
  }, []);

  const reorderCategories = useCallback(async (orderedIds: number[]) => {
    setCategories((prev) => {
      const byId = new Map(prev.map((c) => [c.id, c]));
      const next: CategoryDto[] = [];
      orderedIds.forEach((id, idx) => {
        const cat = byId.get(id);
        if (cat) {
          next.push({ ...cat, sortOrder: idx });
          byId.delete(id);
        }
      });
      byId.forEach((cat) => {
        next.push({ ...cat, sortOrder: next.length });
      });
      return next;
    });

    try {
      await apiFetch<CategoryDto[]>('/api/categories/reorder', {
        method: 'PUT',
        body: JSON.stringify({ categoryIds: orderedIds }),
      });
    } catch (err) {
      console.error('Failed to reorder categories:', err);
      await fetchCategories();
    }
  }, [fetchCategories]);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  return {
    categories,
    loading,
    error,
    refreshCategories: fetchCategories,
    addCategory,
    deleteCategory,
    reorderCategories,
  };
}
