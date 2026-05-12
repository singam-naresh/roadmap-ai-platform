// ─── Conversation types (Phase 6) ────────────────────────────────────────────
export interface ConversationMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  intentType?: string;
  taskId?: number;
  createdAt: string;
}

export interface Conversation {
  id: number;
  title: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  lastMessageAt: string;
}

export interface FeasibilityResult {
  feasible: boolean;
  explanation: string | null;
  minimumRealisticEstimate: string | null;
  acceleratedAlternative: string | null;
  domain: string | null;
  level: string | null;
  feasibilityScore: number;
}

export interface ContinuationResponse {
  actionType: string;
  content: string;
  summary: string;
  roadmapId: number;
}

// ─── Intent Types ─────────────────────────────────────────────────────────────
export type AIIntentType =
  | 'ROADMAP'
  | 'CHAT'
  | 'CODING'
  | 'ANALYSIS'
  | 'PRODUCTIVITY'
  | 'STARTUP'
  | 'LEARNING';

// ─── Backend API Response ─────────────────────────────────────────────────────
// Single unified response from the backend — fields populated based on intentType.
export interface TaskResponse {
  id: number;
  userInput: string;
  intentType: AIIntentType;
  conversationId?: number;

  // Shared
  summary: string;
  category: string;
  skillLevel: string;
  mode: 'default' | 'detailed' | 'simplified';
  aiOutput: string;
  createdAt: string;

  // ROADMAP / LEARNING / PRODUCTIVITY / STARTUP
  estimatedTime?: string;
  difficulty?: 'Beginner' | 'Intermediate' | 'Advanced';
  prerequisites?: string[];
  steps?: string[];
  tips?: string[];
  mistakesToAvoid?: string[];
  resources?: string[];

  // CHAT
  message?: string;
  suggestions?: string[];

  // CODING
  language?: string;
  explanation?: string;
  codeBlocks?: CodeBlock[];
  keyPoints?: string[];
  commonMistakes?: string[];
  clarificationQuestions?: string[]; // populated when prompt is too vague

  // ANALYSIS
  verdict?: string;
  sections?: AnalysisSection[];
  pros?: string[];
  cons?: string[];
  recommendations?: string[];
  useCases?: string[];

  // LEARNING
  conceptTitle?: string;
  conceptExplanation?: string;
  examples?: LearningExample[];
  commonMisconceptions?: string[];
  practiceExercises?: string[];
  bestPractices?: string[];
  nextTopics?: string[];

  // PRODUCTIVITY
  systemTitle?: string;
  overview?: string;
  schedule?: ScheduleBlock[];
  priorities?: string[];
  habits?: string[];
  tools?: string[];
  weeklyReview?: string;
}

export interface CodeBlock {
  label: string;
  language: string;
  code: string;
}

export interface AnalysisSection {
  title: string;
  content: string;
  score?: number | null;
}

export interface LearningExample {
  title: string;
  description: string;
  code: string;
}

export interface ScheduleBlock {
  timeBlock: string;
  activity: string;
  priority: 'High' | 'Medium' | 'Low';
  duration: string;
}

// ─── Request ──────────────────────────────────────────────────────────────────
export interface TaskRequest {
  userInput: string;
  mode: 'default' | 'detailed' | 'simplified';
  /** Optional: continue an existing conversation thread */
  conversationId?: number;
}

// ─── UI-layer Step (enriched from flat string for RoadmapRenderer) ────────────
export interface RoadmapStep {
  id: string;
  title: string;
  duration: string;
  difficulty: 'Easy' | 'Medium' | 'Hard';
  description: string;
}

// ─── UI-layer Roadmap ViewModel ───────────────────────────────────────────────
export interface RoadmapViewModel {
  id: number;
  title: string;
  category: string;
  estimatedTime: string;
  difficulty: string;
  skillLevel: string;
  summary: string;
  prerequisites: string[];
  steps: RoadmapStep[];
  tips: string[];
  mistakesToAvoid: string[];
  resources: string[];
  mode: string;
  createdAt: string;
  userInput: string;
  intentType: AIIntentType;
}
