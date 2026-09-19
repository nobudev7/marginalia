import { useState, useCallback, useEffect } from 'react';
import { apiFetch, ApiError } from '../api/client';
import type { WhitelistEntryDto } from '../types';

export function useWhitelist(enabled = false) {
  const [entries, setEntries] = useState<WhitelistEntryDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchEntries = useCallback(async () => {
    if (!enabled) return;
    try {
      setLoading(true);
      setError(null);
      const data = await apiFetch<WhitelistEntryDto[]>('/api/admin/whitelist');
      setEntries(data);
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        setError('Administrative privileges required to manage whitelist.');
      } else {
        setError(extractErrorMessage(err, 'Failed to load whitelist entries'));
      }
    } finally {
      setLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    fetchEntries();
  }, [fetchEntries]);

  const addEntry = async (email: string, note?: string): Promise<WhitelistEntryDto> => {
    try {
      setError(null);
      const newEntry = await apiFetch<WhitelistEntryDto>('/api/admin/whitelist', {
        method: 'POST',
        body: JSON.stringify({ email: email.trim(), note: note?.trim() || null }),
      });
      setEntries((prev) => [newEntry, ...prev.filter((e) => e.id !== newEntry.id)]);
      return newEntry;
    } catch (err) {
      const msg = extractErrorMessage(err, 'Failed to add email to whitelist');
      setError(msg);
      throw new Error(msg);
    }
  };

  const removeEntry = async (id: number): Promise<void> => {
    try {
      setError(null);
      await apiFetch<void>(`/api/admin/whitelist/${id}`, {
        method: 'DELETE',
      });
      setEntries((prev) => prev.filter((e) => e.id !== id));
    } catch (err) {
      const msg = extractErrorMessage(err, 'Failed to remove email from whitelist');
      setError(msg);
      throw new Error(msg);
    }
  };

  return {
    entries,
    loading,
    error,
    refreshEntries: fetchEntries,
    addEntry,
    removeEntry,
    setError,
  };
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.data && typeof err.data === 'object') {
    const data = err.data as Record<string, unknown>;
    if (typeof data.error === 'string') return data.error;
    if (typeof data.message === 'string') return data.message;
  }
  if (err instanceof Error) return err.message;
  return fallback;
}
