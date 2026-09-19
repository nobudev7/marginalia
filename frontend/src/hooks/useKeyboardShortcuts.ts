import { useEffect, useRef } from 'react';

export interface KeyboardShortcutsHandlers {
  onNext?: () => void;
  onPrev?: () => void;
  onToggleRead?: () => void;
  onToggleSave?: () => void;
  onOpenOriginal?: () => void;
  onClose?: () => void;
}

interface UseKeyboardShortcutsOptions {
  enabled?: boolean;
  handlers: KeyboardShortcutsHandlers;
}

/**
 * Global keyboard shortcuts handler for Marginalia reading navigation.
 * Ignored when typing inside input elements, textareas, or contentEditable fields.
 */
export function useKeyboardShortcuts({ enabled = true, handlers }: UseKeyboardShortcutsOptions) {
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      // Do not trigger shortcuts when focus is inside text input controls
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }

      // Ignore if modifier keys (Meta, Ctrl, Alt) are pressed (e.g. Cmd+R, Ctrl+C)
      if (e.metaKey || e.ctrlKey || e.altKey) {
        return;
      }

      switch (e.key) {
        case 'j':
        case 'J':
          e.preventDefault();
          handlersRef.current.onNext?.();
          break;
        case 'k':
        case 'K':
          e.preventDefault();
          handlersRef.current.onPrev?.();
          break;
        case 'm':
        case 'M':
          e.preventDefault();
          handlersRef.current.onToggleRead?.();
          break;
        case 's':
        case 'S':
          e.preventDefault();
          handlersRef.current.onToggleSave?.();
          break;
        case 'v':
        case 'V':
          e.preventDefault();
          handlersRef.current.onOpenOriginal?.();
          break;
        case 'Escape':
          e.preventDefault();
          handlersRef.current.onClose?.();
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [enabled]);
}
