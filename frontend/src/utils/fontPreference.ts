export type FontPreference = 'sans' | 'serif';

const STORAGE_KEY = 'marginalia.fontFamily';

export function getFontPreference(): FontPreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'serif' || stored === 'sans') {
      return stored;
    }
  } catch {
    // Ignore localStorage access errors
  }
  return 'sans';
}

export function applyFontPreference(pref: FontPreference): void {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.font = pref;
  }
}

export function setFontPreference(pref: FontPreference): void {
  try {
    localStorage.setItem(STORAGE_KEY, pref);
  } catch {
    // Ignore localStorage access errors
  }
  applyFontPreference(pref);
}

export function initFontPreference(): void {
  applyFontPreference(getFontPreference());
}
