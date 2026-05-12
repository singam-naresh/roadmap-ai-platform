/**
 * PHASE 7.2 — Stable Generation Hook
 *
 * The SSE streaming endpoint causes ERR_INCOMPLETE_CHUNKED_ENCODING due to
 * Spring Boot's SseEmitter lifecycle conflicts with the async thread pool.
 *
 * This hook uses the stable POST /api/tasks endpoint with:
 *   - Single active request guard (no concurrent calls)
 *   - AbortController for clean cancellation
 *   - Rate-limit 429 detection and structured error types
 *   - Simulated progressive text reveal for perceived responsiveness
 *   - Proper cleanup on unmount
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { createTask } from '../services/api';
import type { TaskResponse } from '../types';
import { authService } from '../services/authService';

// ─── Types ────────────────────────────────────────────────────────────────────

export type StreamFailureReason =
  | 'RATE_LIMITED'
  | 'CONCURRENT'
  | 'COOLDOWN'
  | 'BACKEND_FAILURE'
  | 'CLIENT_ABORT'
  | 'NETWORK_ERROR';

export interface StreamingState {
  isStreaming:        boolean;
  streamedText:       string;
  isComplete:         boolean;
  error:              string | null;
  retryAfter:         number | null;
  remainingRequests:  number | null;
  failureReason:      StreamFailureReason | null;
}

const INITIAL_STATE: StreamingState = {
  isStreaming:       false,
  streamedText:      '',
  isComplete:        false,
  error:             null,
  retryAfter:        null,
  remainingRequests: null,
  failureReason:     null,
};

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useStreamingGeneration() {
  const [state, setState] = useState<StreamingState>(INITIAL_STATE);

  // Single active request guard
  const isActiveRef  = useRef(false);
  // AbortController for the current request
  const abortCtrlRef = useRef<AbortController | null>(null);
  // Mounted guard
  const mountedRef   = useRef(true);
  // Text reveal interval
  const revealRef    = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      abortCtrlRef.current?.abort('unmount');
      if (revealRef.current) clearInterval(revealRef.current);
    };
  }, []);

  const safeSet = useCallback((updater: Partial<StreamingState> | ((p: StreamingState) => StreamingState)) => {
    if (!mountedRef.current) return;
    setState(prev => typeof updater === 'function' ? updater(prev) : { ...prev, ...updater });
  }, []);

  const generate = useCallback(async (
    userInput:       string,
    mode:            'default' | 'detailed' | 'simplified' = 'default',
    conversationId?: number,
    onComplete?:     (task: TaskResponse) => void,
    onError?:        (error: string, retryAfter?: number, reason?: StreamFailureReason) => void
  ) => {
    // Block concurrent calls
    if (isActiveRef.current) {
      console.warn('[gen] Blocked duplicate request — already active');
      return;
    }

    // Cancel any previous request
    abortCtrlRef.current?.abort('new-request');
    if (revealRef.current) { clearInterval(revealRef.current); revealRef.current = null; }

    const abortCtrl = new AbortController();
    abortCtrlRef.current = abortCtrl;
    isActiveRef.current  = true;

    safeSet({ ...INITIAL_STATE, isStreaming: true });
    console.debug('[gen] Starting:', userInput.substring(0, 60));

    try {
      const token = authService.getAccessToken();

      // Use the stable non-streaming endpoint
      const res = await fetch(`${(import.meta as any).env?.VITE_API_BASE_URL ?? ''}/api/tasks`, {
        method:  'POST',
        signal:  abortCtrl.signal,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ userInput, mode, conversationId }),
      });

      // Handle rate limiting
      if (res.status === 429) {
        const retryAfter = parseInt(res.headers.get('Retry-After') ?? '30', 10);
        let body: any = {};
        try { body = await res.json(); } catch {}
        const msg = 'The AI engine is busy. Please wait ' + retryAfter + ' seconds before trying again.';
        const remaining = body.remainingRequests ?? 0;
        safeSet({ isStreaming: false, error: msg, retryAfter, remainingRequests: remaining, failureReason: 'RATE_LIMITED' });
        onError?.(msg, retryAfter, 'RATE_LIMITED');
        return;
      }

      if (!res.ok) {
        let errMsg = `HTTP ${res.status}`;
        try { const b = await res.json(); errMsg = b.message ?? b.error ?? errMsg; } catch {}
        throw Object.assign(new Error(errMsg), { status: res.status });
      }

      const task: TaskResponse = await res.json();
      console.debug('[gen] Received task:', task.id);

      // Simulate progressive text reveal for perceived responsiveness
      const fullText = task.aiOutput ?? '';
      if (fullText && mountedRef.current) {
        let pos = 0;
        const chunkSize = 60;
        revealRef.current = setInterval(() => {
          if (!mountedRef.current) { clearInterval(revealRef.current!); return; }
          pos = Math.min(pos + chunkSize, fullText.length);
          safeSet({ streamedText: fullText.substring(0, pos) });
          if (pos >= fullText.length) {
            clearInterval(revealRef.current!);
            revealRef.current = null;
          }
        }, 16); // ~60fps
      }

      safeSet({ isStreaming: false, isComplete: true });
      console.debug('[gen] Complete, taskId:', task.id);
      onComplete?.(task);

    } catch (err: any) {
      if (err?.name === 'AbortError') {
        console.debug('[gen] Aborted:', err.message);
        safeSet({ isStreaming: false, failureReason: 'CLIENT_ABORT' });
        return;
      }

      const status = err?.status;
      if (status === 429) {
        const msg = err.message ?? 'Rate limit exceeded.';
        safeSet({ isStreaming: false, error: msg, retryAfter: 60, failureReason: 'RATE_LIMITED' });
        onError?.(msg, 60, 'RATE_LIMITED');
        return;
      }

      const isNetwork = err?.message?.includes('Failed to fetch') ||
                        err?.message?.includes('NetworkError') ||
                        err?.name === 'TypeError';
      const reason: StreamFailureReason = isNetwork ? 'NETWORK_ERROR' : 'BACKEND_FAILURE';

      // User-friendly error messages — never expose raw API errors
      let msg: string;
      if (isNetwork) {
        msg = 'Connection lost. Check your internet and try again.';
      } else if (err?.status === 500 || err?.status === 503) {
        msg = 'The AI engine is temporarily busy. Please wait a moment and try again.';
      } else if (err?.status === 429) {
        msg = 'Too many requests. Please wait a few seconds before generating again.';
      } else {
        msg = 'Generation failed. Please try again.';
      }

      console.error('[gen] Failed:', msg, reason);
      safeSet({ isStreaming: false, error: msg, failureReason: reason });
      onError?.(msg, undefined, reason);

    } finally {
      isActiveRef.current  = false;
      abortCtrlRef.current = null;
    }
  }, [safeSet]);

  const abort = useCallback(() => {
    abortCtrlRef.current?.abort('user-abort');
    abortCtrlRef.current = null;
    isActiveRef.current  = false;
    if (revealRef.current) { clearInterval(revealRef.current); revealRef.current = null; }
    safeSet({ isStreaming: false, failureReason: 'CLIENT_ABORT' });
    console.debug('[gen] Aborted by user');
  }, [safeSet]);

  const reset = useCallback(() => {
    if (revealRef.current) { clearInterval(revealRef.current); revealRef.current = null; }
    safeSet(INITIAL_STATE);
  }, [safeSet]);

  return { ...state, generate, abort, reset };
}
