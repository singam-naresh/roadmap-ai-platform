import React from 'react';
import type { TaskResponse } from '../../types';
import { toRoadmapViewModel } from '../../services/adapter';
import RoadmapCard from '../RoadmapCard';
import ChatRenderer from './ChatRenderer';
import CodingRenderer from './CodingRenderer';
import AnalysisRenderer from './AnalysisRenderer';
import LearningRenderer from './LearningRenderer';
import ProductivityRenderer from './ProductivityRenderer';
import EnhancedRoadmapRenderer from './EnhancedRoadmapRenderer';
import ErrorBoundary from '../ErrorBoundary';

interface ResponseRendererProps {
  response: TaskResponse;
  onRefine: (mode: 'default' | 'detailed' | 'simplified') => void;
  onSuggestionClick?: (text: string) => void;
  isLoading?: boolean;
  error?: string;
}

/**
 * Dynamic renderer dispatcher — TRUE isolation.
 *
 * Every intent type maps to exactly one dedicated renderer.
 * No renderer is reused across intent types.
 *
 * Fallback strategy (TASK 5 — history compatibility):
 * - null/undefined intentType → ROADMAP (legacy records before intent routing)
 * - unrecognised intentType string → CHAT (safe fallback for future intents)
 */
const ResponseRenderer: React.FC<ResponseRendererProps> = ({
  response,
  onRefine,
  onSuggestionClick,
  isLoading = false,
  error,
}) => {
  // Normalise: legacy records stored before intent routing had no intentType
  const intent = response.intentType ?? 'ROADMAP';

  return (
    <ErrorBoundary>
      {(() => {
        switch (intent) {

          // ── Conversational ────────────────────────────────────────────────────────
          case 'CHAT':
            return (
              <ChatRenderer
                response={response}
                onSuggestionClick={onSuggestionClick}
              />
            );

          // ── Technical / Code ──────────────────────────────────────────────────────
          case 'CODING':
            return (
              <CodingRenderer
                response={response}
                onRefine={onRefine}
                onSuggestionClick={onSuggestionClick}
              />
            );

          // ── Comparison / Evaluation ───────────────────────────────────────────────
          case 'ANALYSIS':
            return (
              <AnalysisRenderer
                response={response}
                onRefine={onRefine}
              />
            );

          // ── Concept teaching / tutorials ──────────────────────────────────────────
          case 'LEARNING':
            return (
              <LearningRenderer
                response={response}
                onSuggestionClick={onSuggestionClick}
              />
            );

          // ── Schedules / habits / time management ──────────────────────────────────
          case 'PRODUCTIVITY':
            return (
              <ProductivityRenderer
                response={response}
                onRefine={onRefine}
              />
            );

          // ── Structured execution plans ────────────────────────────────────────────
          case 'ROADMAP':
          case 'STARTUP':
            return (
              <EnhancedRoadmapRenderer
                response={response}
                onRefine={onRefine}
                onSuggestionClick={onSuggestionClick}
                isLoading={isLoading}
                error={error}
              />
            );

          // ── Fallback: unknown/future intent types ─────────────────────────────────
          default:
            // Safe fallback: render as chat so the user always sees something useful
            return (
              <ChatRenderer
                response={{
                  ...response,
                  message: response.aiOutput ?? response.summary ?? 'Response loaded.',
                }}
                onSuggestionClick={onSuggestionClick}
              />
            );
        }
      })()}
    </ErrorBoundary>
  );
};

export default ResponseRenderer;
