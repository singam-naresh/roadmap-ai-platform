/**
 * PHASE 7 — Persisted Workspace Hook
 *
 * Persists and restores:
 *   - Active task ID (survives refresh)
 *   - Active conversation ID
 *   - Active roadmap ID
 *   - Selected mode
 *
 * Uses localStorage with safe JSON parsing and version tagging.
 */

import { useState, useEffect, useCallback } from 'react';

const STORAGE_KEY = 'roadmap_workspace_v1';

interface WorkspaceState {
  activeTaskId:         number | null;
  activeConversationId: number | null;
  activeRoadmapId:      number | null;
  selectedMode:         'default' | 'detailed' | 'simplified';
  lastView:             'dashboard' | 'response';
}

const DEFAULT_STATE: WorkspaceState = {
  activeTaskId:         null,
  activeConversationId: null,
  activeRoadmapId:      null,
  selectedMode:         'default',
  lastView:             'dashboard', // always default to dashboard on fresh session
};

function loadFromStorage(): WorkspaceState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_STATE;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_STATE, ...parsed };
  } catch {
    return DEFAULT_STATE;
  }
}

function saveToStorage(state: WorkspaceState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Storage full or unavailable — fail silently
  }
}

export function usePersistedWorkspace() {
  const [workspace, setWorkspaceState] = useState<WorkspaceState>(loadFromStorage);

  // Persist on every change
  useEffect(() => {
    saveToStorage(workspace);
  }, [workspace]);

  const setActiveTask = useCallback((taskId: number | null) => {
    setWorkspaceState(prev => ({ ...prev, activeTaskId: taskId }));
  }, []);

  const setActiveConversation = useCallback((convId: number | null) => {
    setWorkspaceState(prev => ({ ...prev, activeConversationId: convId }));
  }, []);

  const setActiveRoadmap = useCallback((roadmapId: number | null) => {
    setWorkspaceState(prev => ({ ...prev, activeRoadmapId: roadmapId }));
  }, []);

  const setSelectedMode = useCallback((mode: 'default' | 'detailed' | 'simplified') => {
    setWorkspaceState(prev => ({ ...prev, selectedMode: mode }));
  }, []);

  const setLastView = useCallback((view: 'dashboard' | 'response') => {
    setWorkspaceState(prev => ({ ...prev, lastView: view }));
  }, []);

  const clearWorkspace = useCallback(() => {
    setWorkspaceState(DEFAULT_STATE);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  return {
    ...workspace,
    setActiveTask,
    setActiveConversation,
    setActiveRoadmap,
    setSelectedMode,
    setLastView,
    clearWorkspace,
  };
}
