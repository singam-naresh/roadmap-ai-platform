import type { TaskRequest, TaskResponse } from '../types';
import { authService } from './authService';

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '');
const TASKS_BASE     = `${API_BASE}/api/tasks`;
const ANALYTICS_BASE = `${API_BASE}/api/analytics`;

class ApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public code?: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// Track ongoing refresh to prevent multiple simultaneous refreshes
let isRefreshing = false;
let refreshPromise: Promise<void> | null = null;

async function request<T>(url: string, options?: RequestInit, retries = 1): Promise<T> {
  try {
    const accessToken = authService.getAccessToken();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    if (accessToken) {
      headers['Authorization'] = `Bearer ${accessToken}`;
    }

    const res = await fetch(url, {
      headers,
      ...options,
    });

    // Handle 401 Unauthorized - try to refresh token
    if (res.status === 401 && accessToken && retries > 0) {
      try {
        // Prevent multiple simultaneous refresh attempts
        if (isRefreshing) {
          if (refreshPromise) {
            await refreshPromise;
          }
        } else {
          isRefreshing = true;
          refreshPromise = authService.refreshToken().then(() => {
            isRefreshing = false;
            refreshPromise = null;
          }).catch((error) => {
            isRefreshing = false;
            refreshPromise = null;
            throw error;
          });
          await refreshPromise;
        }
        
        // Update headers with new token
        const newToken = authService.getAccessToken();
        if (newToken) {
          headers['Authorization'] = `Bearer ${newToken}`;
          
          // Retry the request with new token
          return request<T>(url, { ...options, headers }, retries - 1);
        } else {
          throw new Error('No access token after refresh');
        }
      } catch (refreshError) {
        console.error('[api] Token refresh failed:', refreshError);
        // Refresh failed, clear auth state
        await authService.logout();
        throw new ApiError('Authentication failed', 401, 'AUTH_FAILED');
      }
    }

    if (!res.ok) {
      let message = `HTTP ${res.status}`;
      let errorCode = 'UNKNOWN_ERROR';
      
      try {
        const body = await res.json();
        message = body?.message ?? body?.error ?? message;
        errorCode = body?.code ?? errorCode;
      } catch {
        // ignore parse error — use status code message
        message = res.statusText || message;
      }
      
      throw new ApiError(message, res.status, errorCode);
    }

    // Handle 204 No Content (DELETE responses)
    if (res.status === 204) return undefined as T;

    return res.json() as Promise<T>;

  } catch (err: any) {
    // Don't retry on client errors (4xx) except 401 which is handled above
    if (err instanceof ApiError && err.status && err.status >= 400 && err.status < 500 && err.status !== 401) {
      throw err;
    }
    
    // Retry once on network failure or server errors (5xx)
    if (retries > 0 && (
      err?.message?.includes('Failed to fetch') ||
      err?.message?.includes('NetworkError') ||
      err?.name === 'TypeError' || // Network errors often show as TypeError
      (err instanceof ApiError && err.status && err.status >= 500)
    )) {
      console.log(`[api] Retrying request to ${url} (${retries} retries left)`);
      await new Promise(r => setTimeout(r, 1000 * (2 - retries))); // Exponential backoff
      return request<T>(url, options, retries - 1);
    }
    
    // Log error for debugging (but don't spam console)
    if (!(err instanceof ApiError && err.status === 401)) {
      console.error('[api] Request failed:', {
        url,
        error: err.message,
        status: err.status || 'unknown',
        code: err.code || 'unknown'
      });
    }
    
    throw err;
  }
}

/** POST /api/tasks — generate a new response */
export async function createTask(payload: TaskRequest): Promise<TaskResponse> {
  return request<TaskResponse>(TASKS_BASE, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

/** GET /api/tasks — fetch full history, newest first */
export async function getAllTasks(): Promise<TaskResponse[]> {
  return request<TaskResponse[]>(TASKS_BASE);
}

/** GET /api/tasks/:id — fetch a single task */
export async function getTaskById(id: number): Promise<TaskResponse> {
  return request<TaskResponse>(`${TASKS_BASE}/${id}`);
}

/** DELETE /api/tasks/:id — delete a task from history */
export async function deleteTask(id: number): Promise<void> {
  await request<void>(`${TASKS_BASE}/${id}`, { method: 'DELETE' });
}

/** GET /api/tasks/search — paginated, searchable, filterable history */
export async function searchTasks(params: {
  q?: string;
  intent?: string;
  category?: string;
  page?: number;
  size?: number;
}): Promise<{ items: TaskResponse[]; totalItems: number; totalPages: number; page: number }> {
  const qs = new URLSearchParams();
  if (params.q)        qs.set('q',        params.q);
  if (params.intent)   qs.set('intent',   params.intent);
  if (params.category) qs.set('category', params.category);
  if (params.page  != null) qs.set('page', String(params.page));
  if (params.size  != null) qs.set('size', String(params.size));
  return request(`${TASKS_BASE}/search?${qs.toString()}`);
}

// ─── Analytics ────────────────────────────────────────────────────────────────

export interface AnalyticsSummary {
  totalGenerations:    number;
  thisWeekGenerations: number;
  learningCount:       number;
  codingCount:         number;
  roadmapCount:        number;
  currentStreak:       number;
  intentDistribution:  Record<string, number>;
  categoryDistribution: Record<string, number>;
  dailyActivity:       Array<{ date: string; count: number }>;
}

/** GET /api/analytics/summary — real DB-driven dashboard metrics */
export async function getAnalyticsSummary(): Promise<AnalyticsSummary> {
  return request<AnalyticsSummary>(`${ANALYTICS_BASE}/summary`);
}

// ─── Execution Tracking ───────────────────────────────────────────────────────

export interface StepProgressRecord {
  id: number;
  taskId: number;
  stepIndex: number;
  completed: boolean;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  note: string | null;
  updatedAt: string;
}

const EXEC_BASE = `${API_BASE}/api/executions`;

/** GET /api/executions/:taskId — load all step progress for a task */
export async function getExecutionProgress(taskId: number): Promise<Record<number, StepProgressRecord>> {
  return request(`${EXEC_BASE}/${taskId}`);
}

/** POST /api/executions/:taskId/steps/:stepIndex/complete */
export async function markStepComplete(taskId: number, stepIndex: number, totalSteps: number): Promise<{ progress: number }> {
  return request(`${EXEC_BASE}/${taskId}/steps/${stepIndex}/complete`, {
    method: 'POST',
    body: JSON.stringify({ totalSteps }),
  });
}

/** DELETE /api/executions/:taskId/steps/:stepIndex/complete */
export async function markStepIncomplete(taskId: number, stepIndex: number, totalSteps: number): Promise<{ progress: number }> {
  return request(`${EXEC_BASE}/${taskId}/steps/${stepIndex}/complete?totalSteps=${totalSteps}`, {
    method: 'DELETE',
  });
}

/** PUT /api/executions/:taskId/steps/:stepIndex/priority */
export async function setStepPriority(taskId: number, stepIndex: number, priority: string): Promise<void> {
  return request(`${EXEC_BASE}/${taskId}/steps/${stepIndex}/priority`, {
    method: 'PUT',
    body: JSON.stringify({ priority }),
  });
}

/** PUT /api/executions/:taskId/steps/:stepIndex/note */
export async function setStepNote(taskId: number, stepIndex: number, note: string): Promise<void> {
  return request(`${EXEC_BASE}/${taskId}/steps/${stepIndex}/note`, {
    method: 'PUT',
    body: JSON.stringify({ note }),
  });
}

// ─── Phase 4: Execution Intelligence API ─────────────────────────────────────

export type StepState = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'SKIPPED' | 'FAILED' | 'NEEDS_REVIEW';

export interface StepExecutionState {
  taskId: number;
  stepIndex: number;
  state: StepState;
  priority: string;
  notes: string | null;
  blocker: string | null;
  updatedAt: string | null;
}

export interface ExecutionSummary {
  taskId: number;
  totalSteps: number;
  completedSteps: number;
  blockedSteps: number;
  inProgressSteps: number;
  failedSteps: number;
  notStartedSteps: number;
  progressPercentage: number;
  recentCompletions: number;
}

export interface PrioritizedStep {
  stepIndex: number;
  title: string;
  score: number;
  priority: string;
  inProgress: boolean;
}

export interface AdaptationReport {
  roadmapId: number;
  taskId: number;
  signals: Array<{ key: string; value: string }>;
  stepsNeedingReinforcement: number[];
  recommendations: string[];
  mutations: Array<{ type: string; targetStepIndex: number; newContent: string | null; rationale: string }>;
}

export interface ExecutionEvent {
  type: string;
  taskId: number;
  stepIndex: number;
  message: string;
  occurredAt: string;
}

/** GET /api/executions/:taskId/state */
export async function getAllStepStates(taskId: number): Promise<StepExecutionState[]> {
  return request(`${EXEC_BASE}/${taskId}/state`);
}

/** POST /api/executions/:taskId/steps/:stepIndex/state */
export async function transitionStepState(
  taskId: number,
  stepIndex: number,
  state: StepState,
  notes?: string,
  blocker?: string
): Promise<StepExecutionState> {
  return request(`${EXEC_BASE}/${taskId}/steps/${stepIndex}/state`, {
    method: 'POST',
    body: JSON.stringify({ state, notes, blocker }),
  });
}

/** GET /api/executions/:taskId/summary?totalSteps=N */
export async function getExecutionSummary(taskId: number, totalSteps: number): Promise<ExecutionSummary> {
  return request(`${EXEC_BASE}/${taskId}/summary?totalSteps=${totalSteps}`);
}

/** GET /api/executions/:taskId/ready?totalSteps=N */
export async function getReadyAndBlockedSteps(
  taskId: number,
  totalSteps: number
): Promise<{ readySteps: number[]; blockedSteps: number[] }> {
  return request(`${EXEC_BASE}/${taskId}/ready?totalSteps=${totalSteps}`);
}

/** GET /api/executions/:taskId/adapt?roadmapId=N&totalSteps=N */
export async function getAdaptationReport(
  taskId: number,
  roadmapId: number,
  totalSteps: number
): Promise<AdaptationReport> {
  return request(`${EXEC_BASE}/${taskId}/adapt?roadmapId=${roadmapId}&totalSteps=${totalSteps}`);
}

/** GET /api/executions/:taskId/priority?roadmapId=N */
export async function getPrioritizedSteps(taskId: number, roadmapId: number): Promise<PrioritizedStep[]> {
  return request(`${EXEC_BASE}/${taskId}/priority?roadmapId=${roadmapId}`);
}

/** GET /api/executions/:taskId/events */
export async function getExecutionEvents(taskId: number): Promise<ExecutionEvent[]> {
  return request(`${EXEC_BASE}/${taskId}/events`);
}

// ─── Phase 4.5: Observability API ────────────────────────────────────────────

const OBS_BASE = `${API_BASE}/api/execution`;

export interface TimelineEntry {
  id: string;
  taskId: number;
  stepIndex: number;
  type: 'STATE_TRANSITION' | 'ROADMAP_MUTATION' | 'DEPENDENCY_UNLOCK' | 'PRIORITY_CHANGE' | 'ADAPTATION_INJECTION' | 'FAILURE' | 'RETRY';
  previousState: string | null;
  newState: string | null;
  notes: string | null;
  blocker: string | null;
  actor: string;
  occurredAt: string;
}

export interface AuditRecord {
  sequence: number;
  taskId: number;
  stepIndex: number;
  action: 'STATE_TRANSITION' | 'ROADMAP_MUTATION' | 'DEPENDENCY_CHANGE' | 'ADAPTATION_DECISION' | 'PRIORITY_CHANGE';
  previousValue: string | null;
  newValue: string | null;
  actor: string;
  reason: string | null;
  timestamp: string;
}

export interface ReplaySnapshot {
  taskId: number;
  pointInTime: string;
  lastSequence: number;
  stepStates: Record<number, string>;
  stepPriorities: Record<number, string>;
  appliedMutations: string[];
  completedCount: number;
  eventsReplayed: number;
}

export interface PlaybackFrame {
  frameIndex: number;
  timestamp: string;
  eventType: string;
  stepIndex: number;
  previousState: string;
  newState: string;
  notes: string | null;
  actor: string;
  allStatesAtFrame: Record<number, string>;
}

export interface GraphVisualization {
  taskId: number;
  totalSteps: number;
  nodes: Array<{
    id: string;
    stepIndex: number;
    title: string;
    status: 'COMPLETED' | 'BLOCKED' | 'READY' | 'NOT_STARTED';
    onCriticalPath: boolean;
    dependencies: number[];
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    status: 'BLOCKED' | 'UNLOCKED' | 'COMPLETED';
    onCriticalPath: boolean;
  }>;
  criticalPath: number[];
  readySteps: number[];
  blockedSteps: number[];
  completedSteps: number[];
  completedCount: number;
  blockedCount: number;
  readyCount: number;
}

export interface ExecutionMetrics {
  taskId: number;
  completionVelocity: number;
  blockedFrequency: number;
  mutationFrequency: number;
  totalMutations: number;
  retryFrequency: number;
  roadmapChurnRate: number;
  consistencyScore: number;
  failureHotspots: Record<number, number>;
  dependencyBottlenecks: number[];
  focusPatterns: Record<number, number>;
  burnDownData: Array<{ timestamp: string; remaining: number; completed: number }>;
}

export interface FailureDiagnosticReport {
  taskId: number;
  healthScore: number;
  overallStatus: 'HEALTHY' | 'DEGRADED' | 'CRITICAL';
  issues: Array<{
    type: string;
    description: string;
    suggestions: string[];
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  }>;
}

export interface InspectionReport {
  taskId: number;
  totalEvents: number;
  timelineLength: number;
  hasIssues: boolean;
  eventTypeCounts: Record<string, number>;
  storms: Array<{ start: string; end: string; eventCount: number; windowSeconds: number; description: string }>;
  loops: Array<{ eventType: string; stepIndex: number; repeatCount: number; spanSeconds: number; description: string }>;
  invalidTransitions: Array<{ stepIndex: number; fromState: string; toState: string; reason: string; occurredAt: string }>;
  anomalies: string[];
}

/** GET /api/execution/:taskId/timeline */
export async function getExecutionTimeline(taskId: number): Promise<TimelineEntry[]> {
  return request(`${OBS_BASE}/${taskId}/timeline`);
}

/** GET /api/execution/:taskId/audit */
export async function getAuditLog(taskId: number): Promise<AuditRecord[]> {
  return request(`${OBS_BASE}/${taskId}/audit`);
}

/** GET /api/execution/:taskId/replay?at=ISO_DATE */
export async function replayAtTime(taskId: number, at?: string): Promise<ReplaySnapshot> {
  const qs = at ? `?at=${encodeURIComponent(at)}` : '';
  return request(`${OBS_BASE}/${taskId}/replay${qs}`);
}

/** GET /api/execution/:taskId/playback */
export async function getPlaybackSequence(taskId: number): Promise<PlaybackFrame[]> {
  return request(`${OBS_BASE}/${taskId}/playback`);
}

/** GET /api/execution/:taskId/graph?totalSteps=N */
export async function getDependencyGraph(taskId: number, totalSteps: number): Promise<GraphVisualization> {
  return request(`${OBS_BASE}/${taskId}/graph?totalSteps=${totalSteps}`);
}

/** GET /api/execution/:taskId/metrics?totalSteps=N */
export async function getExecutionMetrics(taskId: number, totalSteps: number): Promise<ExecutionMetrics> {
  return request(`${OBS_BASE}/${taskId}/metrics?totalSteps=${totalSteps}`);
}

/** GET /api/execution/:taskId/failures?totalSteps=N */
export async function getFailureDiagnostics(taskId: number, totalSteps: number): Promise<FailureDiagnosticReport> {
  return request(`${OBS_BASE}/${taskId}/failures?totalSteps=${totalSteps}`);
}

/** GET /api/execution/:taskId/inspect */
export async function inspectEvents(taskId: number): Promise<InspectionReport> {
  return request(`${OBS_BASE}/${taskId}/inspect`);
}

/** GET /api/execution/:taskId/dashboard?totalSteps=N */
export async function getObservabilityDashboard(taskId: number, totalSteps: number): Promise<{
  timeline: TimelineEntry[];
  audit: AuditRecord[];
  metrics: ExecutionMetrics;
  failures: FailureDiagnosticReport;
  inspection: InspectionReport;
  graph: GraphVisualization;
  mutations: Array<{ sequence: number; timestamp: string; stepIndex: number; mutationType: string; description: string; actor: string }>;
}> {
  return request(`${OBS_BASE}/${taskId}/dashboard?totalSteps=${totalSteps}`);
}

// ─── Roadmap API ──────────────────────────────────────────────────────────────

export interface RoadmapStep {
  id: number;
  stepIndex: number;
  title: string;
  description?: string;
  completed: boolean;
  notes?: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  estimatedDuration?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RoadmapProgress {
  roadmapId: number;
  completedSteps: number;
  totalSteps: number;
  progressPercentage: number;
  lastUpdated: string;
}

const ROADMAP_BASE = `${API_BASE}/api/roadmaps`;

/** GET /api/roadmaps/task/{taskId} — get or create roadmap for task */
export async function getRoadmapByTaskId(taskId: number): Promise<any> {
  return request(`${ROADMAP_BASE}/task/${taskId}`);
}

/** GET /api/roadmaps/{roadmapId}/steps — get roadmap steps */
export async function getRoadmapSteps(roadmapId: number): Promise<RoadmapStep[]> {
  return request(`${ROADMAP_BASE}/${roadmapId}/steps`);
}

/** POST /api/roadmaps/{roadmapId}/steps/{stepIndex}/toggle — toggle step completion */
export async function toggleRoadmapStep(roadmapId: number, stepIndex: number): Promise<RoadmapStep> {
  return request(`${ROADMAP_BASE}/${roadmapId}/steps/${stepIndex}/toggle`, {
    method: 'POST',
  });
}

/** PUT /api/roadmaps/{roadmapId}/steps/{stepIndex}/note — update step note */
export async function updateRoadmapStepNote(roadmapId: number, stepIndex: number, note: string): Promise<RoadmapStep> {
  return request(`${ROADMAP_BASE}/${roadmapId}/steps/${stepIndex}/note`, {
    method: 'PUT',
    body: JSON.stringify({ note }),
  });
}

/** PUT /api/roadmaps/{roadmapId}/steps/{stepIndex}/priority — update step priority */
export async function updateRoadmapStepPriority(roadmapId: number, stepIndex: number, priority: string): Promise<RoadmapStep> {
  return request(`${ROADMAP_BASE}/${roadmapId}/steps/${stepIndex}/priority`, {
    method: 'PUT',
    body: JSON.stringify({ priority }),
  });
}

/** GET /api/roadmaps/{roadmapId}/progress — get roadmap progress */
export async function getRoadmapProgress(roadmapId: number): Promise<RoadmapProgress> {
  return request(`${ROADMAP_BASE}/${roadmapId}/progress`);
}

// Export the ApiError class for use in components
export { ApiError };

// ─── Phase 6: Conversation & Feasibility API ─────────────────────────────────

import type { Conversation, ConversationMessage, FeasibilityResult, ContinuationResponse } from '../types';

const CONV_BASE = `${API_BASE}/api/conversations`;

/** GET /api/conversations — list user's conversations */
export async function getConversations(): Promise<Conversation[]> {
  return request<Conversation[]>(CONV_BASE);
}

/** GET /api/conversations/:id/messages */
export async function getConversationMessages(id: number): Promise<ConversationMessage[]> {
  return request<ConversationMessage[]>(`${CONV_BASE}/${id}/messages`);
}

/** POST /api/conversations/:id/continue */
export async function continueConversation(id: number, userInput: string, mode = 'default'): Promise<import('../types').TaskResponse> {
  return request(`${CONV_BASE}/${id}/continue`, {
    method: 'POST',
    body: JSON.stringify({ userInput, mode, conversationId: id }),
  });
}

/** POST /api/conversations/:convId/roadmap/:roadmapId/action */
export async function roadmapAction(convId: number, roadmapId: number, userInput: string): Promise<ContinuationResponse> {
  // Phase 7.3: use direct endpoint when convId is 0 (no active conversation)
  if (convId === 0) {
    return request(`${CONV_BASE}/roadmap-actions/${roadmapId}`, {
      method: 'POST',
      body: JSON.stringify({ userInput }),
    });
  }
  return request(`${CONV_BASE}/${convId}/roadmap/${roadmapId}/action`, {
    method: 'POST',
    body: JSON.stringify({ userInput }),
  });
}

/** POST /api/conversations/feasibility */
export async function checkFeasibility(userInput: string): Promise<FeasibilityResult> {
  return request(`${CONV_BASE}/feasibility`, {
    method: 'POST',
    body: JSON.stringify({ userInput }),
  });
}