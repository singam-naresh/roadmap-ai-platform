import type { TaskResponse, RoadmapViewModel, RoadmapStep } from '../types';

const DIFFICULTY_MAP: Record<string, 'Beginner' | 'Intermediate' | 'Advanced'> = {
  beginner: 'Beginner',
  intermediate: 'Intermediate',
  advanced: 'Advanced',
};

/**
 * Maps a step difficulty string to the UI step difficulty.
 * Backend sends overall difficulty; we distribute it across steps.
 */
function stepDifficulty(
  overallDifficulty: string,
  index: number,
  total: number
): 'Easy' | 'Medium' | 'Hard' {
  const norm = overallDifficulty?.toLowerCase() ?? 'intermediate';
  if (norm === 'beginner') {
    return index < total * 0.6 ? 'Easy' : 'Medium';
  }
  if (norm === 'advanced') {
    return index < total * 0.3 ? 'Medium' : 'Hard';
  }
  // intermediate
  if (index < total * 0.33) return 'Easy';
  if (index < total * 0.66) return 'Medium';
  return 'Hard';
}

/**
 * Converts a flat step string into a structured RoadmapStep.
 * The backend returns steps as plain sentences; we parse them into
 * title + description by splitting on the first period or dash.
 */
function parseStep(
  raw: string,
  index: number,
  total: number,
  overallDifficulty: string,
  estimatedTime: string
): RoadmapStep {
  // Try to split "Title. Description" or "Title — Description"
  const separatorMatch = raw.match(/^([^.—–\-]{10,60})[.—–\-]\s*(.+)$/s);

  let title: string;
  let description: string;

  if (separatorMatch) {
    title = separatorMatch[1].trim();
    description = separatorMatch[2].trim();
  } else {
    // Fallback: use first 60 chars as title, full text as description
    title = raw.length > 60 ? raw.slice(0, 57) + '…' : raw;
    description = raw;
  }

  // Derive a per-step duration from the overall estimated time
  const duration = deriveStepDuration(estimatedTime, total);

  return {
    id: String(index + 1),
    title,
    description,
    duration,
    difficulty: stepDifficulty(overallDifficulty, index, total),
  };
}

/**
 * Derives a per-step duration estimate from the overall plan time.
 */
function deriveStepDuration(estimatedTime: string, totalSteps: number): string {
  if (!estimatedTime || totalSteps === 0) return '1–2 Weeks';

  const lower = estimatedTime.toLowerCase();

  // Extract first number found
  const match = lower.match(/(\d+)/);
  if (!match) return '1–2 Weeks';

  const totalNum = parseInt(match[1], 10);

  // Determine unit
  const isMonths = lower.includes('month');
  const isWeeks  = lower.includes('week');
  const isDays   = lower.includes('day');

  let totalWeeks: number;
  if (isMonths)      totalWeeks = totalNum * 4;
  else if (isWeeks)  totalWeeks = totalNum;
  else if (isDays)   totalWeeks = Math.ceil(totalNum / 7);
  else               totalWeeks = totalNum * 4; // assume months

  const weeksPerStep = Math.max(1, Math.round(totalWeeks / totalSteps));
  return weeksPerStep === 1 ? '1 Week' : `${weeksPerStep} Weeks`;
}

/**
 * Converts a raw TaskResponse from the backend into the RoadmapViewModel
 * that the UI components consume.
 */
export function toRoadmapViewModel(task: TaskResponse): RoadmapViewModel {
  const steps: RoadmapStep[] = (task.steps ?? []).map((raw, i) =>
    parseStep(raw, i, task.steps?.length ?? 0, task.difficulty ?? 'Intermediate', task.estimatedTime ?? '')
  );

  return {
    id: task.id,
    title: task.userInput,
    category: capitalize(task.category ?? 'General'),
    estimatedTime: task.estimatedTime ?? 'Varies',
    difficulty: DIFFICULTY_MAP[task.difficulty?.toLowerCase()] ?? task.difficulty ?? 'Intermediate',
    skillLevel: capitalize(task.skillLevel ?? 'Intermediate'),
    summary: task.summary ?? '',
    prerequisites: task.prerequisites ?? [],
    steps,
    tips: task.tips ?? [],
    mistakesToAvoid: task.mistakesToAvoid ?? [],
    resources: task.resources ?? [],
    mode: task.mode ?? 'default',
    createdAt: task.createdAt ?? '',
    userInput: task.userInput,
    intentType: task.intentType ?? 'ROADMAP',
  };
}

function capitalize(s: string): string {
  if (!s) return s;
  return s.charAt(0).toUpperCase() + s.slice(1);
}
