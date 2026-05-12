import { useState, useEffect, useCallback, useRef } from 'react';
import {
  getExecutionProgress,
  getAllStepStates,
  getExecutionSummary,
  getReadyAndBlockedSteps,
  markStepComplete,
  markStepIncomplete,
  transitionStepState,
  setStepNote,
  setStepPriority,
  type StepExecutionState,
  type ExecutionSummary,
  type StepState,
  type StepProgressRecord,
} from '../services/api';

interface UseExecutionStateOptions {
  taskId: number;
  totalSteps: number;
  /** If true, load state from backend on mount */
  persist?: boolean;
}

interface ExecutionStateResult {
  // Per-step state
  stepStates: Map<number, StepExecutionState>;
  stepProgress: Map<number, StepProgressRecord>;
  // Aggregate
  summary: ExecutionSummary | null;
  readySteps: number[];
  blockedSteps: number[];
  // Actions
  completeStep: (stepIndex: number) => Promise<void>;
  uncompleteStep: (stepIndex: number) => Promise<void>;
  setStepState: (stepIndex: number, state: StepState, notes?: string, blocker?: string) => Promise<void>;
  saveNote: (stepIndex: number, note: string) => Promise<void>;
  savePriority: (stepIndex: number, priority: string) => Promise<void>;
  // Derived
  isCompleted: (stepIndex: number) => boolean;
  isBlocked: (stepIndex: number) => boolean;
  isInProgress: (stepIndex: number) => boolean;
  getState: (stepIndex: number) => StepState;
  progressPercentage: number;
  isLoading: boolean;
  unlockedSteps: number[];
}

export function useExecutionState({
  taskId,
  totalSteps,
  persist = true,
}: UseExecutionStateOptions): ExecutionStateResult {
  const [stepStates, setStepStates] = useState<Map<number, StepExecutionState>>(new Map());
  const [stepProgress, setStepProgress] = useState<Map<number, StepProgressRecord>>(new Map());
  const [summary, setSummary] = useState<ExecutionSummary | null>(null);
  const [readySteps, setReadySteps] = useState<number[]>([]);
  const [blockedSteps, setBlockedSteps] = useState<number[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [unlockedSteps, setUnlockedSteps] = useState<number[]>([]);

  // Load state from backend on mount
  useEffect(() => {
    if (!persist || !taskId || totalSteps === 0) return;
    loadState();
  }, [taskId, totalSteps, persist]);

  const loadState = useCallback(async () => {
    if (!taskId) return;
    setIsLoading(true);
    try {
      const [progressMap, stateList, summaryData, readyData] = await Promise.all([
        getExecutionProgress(taskId),
        getAllStepStates(taskId),
        getExecutionSummary(taskId, totalSteps),
        totalSteps > 0 ? getReadyAndBlockedSteps(taskId, totalSteps) : Promise.resolve({ readySteps: [], blockedSteps: [] }),
      ]);

      setStepProgress(new Map(Object.entries(progressMap).map(([k, v]) => [Number(k), v])));
      setStepStates(new Map(stateList.map(s => [s.stepIndex, s])));
      setSummary(summaryData);
      setReadySteps(readyData.readySteps);
      setBlockedSteps(readyData.blockedSteps);
    } catch (err) {
      // Non-fatal — fall back to local state
      console.warn('[useExecutionState] Failed to load state:', err);
    } finally {
      setIsLoading(false);
    }
  }, [taskId, totalSteps]);

  const completeStep = useCallback(async (stepIndex: number) => {
    // Optimistic update
    setStepProgress(prev => {
      const next = new Map(prev);
      const existing = next.get(stepIndex);
      next.set(stepIndex, { ...(existing ?? { id: 0, taskId, stepIndex, priority: 'MEDIUM', note: null, updatedAt: new Date().toISOString() }), completed: true });
      return next;
    });

    try {
      const result = await markStepComplete(taskId, stepIndex, totalSteps);
      // Update unlocked steps
      if ((result as any).unlockedSteps?.length > 0) {
        setUnlockedSteps((result as any).unlockedSteps);
        setTimeout(() => setUnlockedSteps([]), 3000); // clear after 3s
      }
      // Refresh summary
      const newSummary = await getExecutionSummary(taskId, totalSteps);
      setSummary(newSummary);
      // Refresh ready/blocked
      if (totalSteps > 0) {
        const readyData = await getReadyAndBlockedSteps(taskId, totalSteps);
        setReadySteps(readyData.readySteps);
        setBlockedSteps(readyData.blockedSteps);
      }
    } catch (err) {
      // Revert optimistic update
      setStepProgress(prev => {
        const next = new Map(prev);
        const existing = next.get(stepIndex);
        if (existing) next.set(stepIndex, { ...existing, completed: false });
        return next;
      });
      console.error('[useExecutionState] Failed to complete step:', err);
    }
  }, [taskId, stepIndex => stepIndex, totalSteps]);

  const uncompleteStep = useCallback(async (stepIndex: number) => {
    setStepProgress(prev => {
      const next = new Map(prev);
      const existing = next.get(stepIndex);
      if (existing) next.set(stepIndex, { ...existing, completed: false });
      return next;
    });

    try {
      await markStepIncomplete(taskId, stepIndex, totalSteps);
      const newSummary = await getExecutionSummary(taskId, totalSteps);
      setSummary(newSummary);
    } catch (err) {
      console.error('[useExecutionState] Failed to uncomplete step:', err);
    }
  }, [taskId, totalSteps]);

  const setStepState = useCallback(async (
    stepIndex: number,
    state: StepState,
    notes?: string,
    blocker?: string
  ) => {
    try {
      const newState = await transitionStepState(taskId, stepIndex, state, notes, blocker);
      setStepStates(prev => new Map(prev).set(stepIndex, newState));

      // If completing via state machine, also update progress
      if (state === 'COMPLETED') {
        await completeStep(stepIndex);
      }
    } catch (err) {
      console.error('[useExecutionState] Failed to set step state:', err);
    }
  }, [taskId, completeStep]);

  const saveNote = useCallback(async (stepIndex: number, note: string) => {
    try {
      await setStepNote(taskId, stepIndex, note);
    } catch (err) {
      console.error('[useExecutionState] Failed to save note:', err);
    }
  }, [taskId]);

  const savePriority = useCallback(async (stepIndex: number, priority: string) => {
    try {
      await setStepPriority(taskId, stepIndex, priority);
    } catch (err) {
      console.error('[useExecutionState] Failed to save priority:', err);
    }
  }, [taskId]);

  // Derived helpers
  const isCompleted = useCallback((stepIndex: number): boolean => {
    return stepProgress.get(stepIndex)?.completed === true;
  }, [stepProgress]);

  const isBlocked = useCallback((stepIndex: number): boolean => {
    const state = stepStates.get(stepIndex);
    return state?.state === 'BLOCKED' || blockedSteps.includes(stepIndex);
  }, [stepStates, blockedSteps]);

  const isInProgress = useCallback((stepIndex: number): boolean => {
    return stepStates.get(stepIndex)?.state === 'IN_PROGRESS';
  }, [stepStates]);

  const getState = useCallback((stepIndex: number): StepState => {
    if (isCompleted(stepIndex)) return 'COMPLETED';
    return stepStates.get(stepIndex)?.state ?? 'NOT_STARTED';
  }, [isCompleted, stepStates]);

  const completedCount = Array.from(stepProgress.values()).filter(p => p.completed).length;
  const progressPercentage = totalSteps > 0 ? Math.round((completedCount / totalSteps) * 100) : 0;

  return {
    stepStates,
    stepProgress,
    summary,
    readySteps,
    blockedSteps,
    completeStep,
    uncompleteStep,
    setStepState,
    saveNote,
    savePriority,
    isCompleted,
    isBlocked,
    isInProgress,
    getState,
    progressPercentage,
    isLoading,
    unlockedSteps,
  };
}
