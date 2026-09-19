import { apiFetch } from './client';
import type { FeedDto } from '../types';

/**
 * Downloads the user's subscribed feeds and categories as a standard OPML 2.0 XML file.
 */
export async function exportOpml(): Promise<void> {
  const response = await fetch('/api/opml/export', {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Export failed with HTTP ${response.status}`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'marginalia-subscriptions.opml';
  document.body.appendChild(anchor);
  anchor.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(anchor);
}

/**
 * Uploads an OPML file to the server and imports feeds and folders.
 * Returns the list of imported feed responses.
 */
export async function importOpml(file: File): Promise<FeedDto[]> {
  const formData = new FormData();
  formData.append('file', file);

  return await apiFetch<FeedDto[]>('/api/opml/import', {
    method: 'POST',
    body: formData,
  });
}
