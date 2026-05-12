import React, { useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  CheckCircle2, Circle, Clock, Target, Lightbulb, AlertTriangle, 
  ExternalLink, ChevronDown, ChevronRight, RefreshCw, AlertCircle,
  Lock, Play, SkipForward, Zap, Bug, Loader2
} from 'lucide-react';
import type { TaskResponse } from '../../types';
import LoadingSkeleton from '../LoadingSkeleton';
import ErrorBoundary from '../ErrorBoundary';
import { useExecutionState } from '../../hooks/useExecutionState';
import ExecutionDebugPanel from '../ExecutionDebugPanel';
import StepActionMenu from '../StepActionMenu';
import { roadmapAction } from '../../services/api';
import type { StepState } from '../../services/api';

const STATE_CONFIG: Record<StepState, { label: string; color: string; icon: React.ReactNode }> = {
  NOT_STARTED:  { label: 'Not started',  color: 'text-slate-500',  icon: <Circle size={20} /> },
  IN_PROGRESS:  { label: 'In progress',  color: 'text-blue-400',   icon: <Play size={20} /> },
  BLOCKED:      { label: 'Blocked',      color: 'text-amber-400',  icon: <Lock size={20} /> },
  COMPLETED:    { label: 'Completed',    color: 'text-green-400',  icon: <CheckCircle2 size={20} /> },
  SKIPPED:      { label: 'Skipped',      color: 'text-slate-400',  icon: <SkipForward size={20} /> },
  FAILED:       { label: 'Failed',       color: 'text-red-400',    icon: <AlertCircle size={20} /> },
  NEEDS_REVIEW: { label: 'Needs review', color: 'text-purple-400', icon: <AlertTriangle size={20} /> },
};

interface EnhancedRoadmapRendererProps {
  response: TaskResponse;
  onRefine?: (mode: 'default' | 'detailed' | 'simplified') => void;
  onSuggestionClick?: (text: string) => void;
  isLoading?: boolean;
  error?: string;
}

const EnhancedRoadmapRenderer: React.FC<EnhancedRoadmapRendererProps> = ({
  response,
  onRefine,
  onSuggestionClick,
  isLoading = false,
  error
}) => {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['steps']));
  const [showDebugPanel, setShowDebugPanel] = useState(false);
  // Per-step action state: stepIndex → { loading, result, error }
  const [stepActionState, setStepActionState] = useState<Record<number, {
    loading: boolean;
    result: string | null;
    error: string | null;
  }>>({});
  const isDev = import.meta.env.DEV;

  const steps = response.steps || [];

  // Phase 4: Persistent execution state
  const exec = useExecutionState({
    taskId: response.id,
    totalSteps: steps.length,
    persist: true,
  });

  const toggleSection = useCallback((section: string) => {
    setExpandedSections(prev => {
      const newSet = new Set(prev);
      if (newSet.has(section)) newSet.delete(section);
      else newSet.add(section);
      return newSet;
    });
  }, []);

  const handleStepClick = useCallback(async (stepIndex: number) => {
    if (exec.isBlocked(stepIndex)) return; // can't toggle blocked steps
    if (exec.isCompleted(stepIndex)) {
      await exec.uncompleteStep(stepIndex);
    } else {
      await exec.completeStep(stepIndex);
    }
  }, [exec]);

  // Phase 7.3: Step action handler — calls continuation API and shows result inline
  const handleStepAction = useCallback(async (stepIndex: number, prompt: string) => {
    // Clear action result
    if (prompt === '__clear__') {
      setStepActionState(prev => {
        const next = { ...prev };
        delete next[stepIndex];
        return next;
      });
      return;
    }

    const taskId = response.id;

    setStepActionState(prev => ({
      ...prev,
      [stepIndex]: { loading: true, result: null, error: null },
    }));

    try {
      const result = await roadmapAction(0, taskId, prompt);
      setStepActionState(prev => ({
        ...prev,
        [stepIndex]: { loading: false, result: result.content, error: null },
      }));
    } catch (err: any) {
      setStepActionState(prev => ({
        ...prev,
        [stepIndex]: { loading: false, result: null, error: err?.message ?? 'Action failed. Please try again.' },
      }));
    }
  }, [response.id]);

  if (isLoading) {
    return (
      <div className="space-y-8">
        <LoadingSkeleton type="roadmap" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 bg-red-500/10 border border-red-500/20 rounded-xl">
        <div className="flex items-center gap-3 mb-4">
          <AlertCircle className="text-red-400" size={20} />
          <h3 className="text-lg font-bold text-red-400">Generation Failed</h3>
        </div>
        <p className="text-red-300 mb-4">{error}</p>
        {onRefine && (
          <button
            onClick={() => onRefine('default')}
            className="inline-flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg transition-colors"
          >
            <RefreshCw size={16} />
            Try Again
          </button>
        )}
      </div>
    );
  }

  const tips = response.tips || [];
  const mistakesToAvoid = response.mistakesToAvoid || [];
  const resources = response.resources || [];
  const progressPercentage = exec.progressPercentage;

  // Unlocked step notification
  const hasUnlocked = exec.unlockedSteps.length > 0;

  return (
    <ErrorBoundary>
      <div className="space-y-8">

        {/* Debug panel (dev mode only) */}
        {isDev && showDebugPanel && (
          <ExecutionDebugPanel
            taskId={response.id}
            totalSteps={steps.length}
            onClose={() => setShowDebugPanel(false)}
          />
        )}

        {/* Unlocked step notification */}
        <AnimatePresence>
          {hasUnlocked && (
            <motion.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="flex items-center gap-3 p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl"
            >
              <Zap className="text-emerald-400 flex-shrink-0" size={18} />
              <span className="text-emerald-300 text-sm font-medium">
                Step{exec.unlockedSteps.length > 1 ? 's' : ''} {exec.unlockedSteps.map(i => i + 1).join(', ')} unlocked!
              </span>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Execution summary bar */}
        {exec.summary && exec.summary.totalSteps > 0 && (
          <div className="flex items-center gap-6 text-xs text-slate-500 flex-wrap">
            <span className="text-green-400 font-medium">{exec.summary.completedSteps} done</span>
            {exec.summary.inProgressSteps > 0 && <span className="text-blue-400">{exec.summary.inProgressSteps} in progress</span>}
            {exec.summary.blockedSteps > 0 && <span className="text-amber-400">{exec.summary.blockedSteps} blocked</span>}
            {exec.summary.failedSteps > 0 && <span className="text-red-400">{exec.summary.failedSteps} failed</span>}
            <span>{exec.summary.notStartedSteps} remaining</span>
          </div>
        )}
        {/* Header Section */}
        <div className="space-y-6">
          <div className="flex items-start justify-between">
            <div className="space-y-2 flex-1">
              <div className="flex items-center gap-3">
                <Target className="text-purple-400" size={24} />
                <h1 className="text-2xl font-bold text-white tracking-tight">
                  {response.userInput}
                </h1>
              </div>
              
              {response.summary && (
                <p className="text-slate-300 text-lg leading-relaxed max-w-4xl">
                  {response.summary}
                </p>
              )}
            </div>

            {onRefine && (
              <div className="flex items-center gap-2 ml-6">
                <span className="text-xs text-slate-500 uppercase tracking-wider">Refine</span>
                {(['simplified', 'default', 'detailed'] as const).map(mode => (
                  <button
                    key={mode}
                    onClick={() => onRefine(mode)}
                    className="px-3 py-1.5 text-xs font-bold uppercase tracking-wider rounded-lg transition-all hover:bg-white/10 text-slate-400 hover:text-white"
                  >
                    {mode}
                  </button>
                ))}
                {isDev && (
                  <button
                    onClick={() => setShowDebugPanel(true)}
                    title="Open Execution Debug Panel (dev only)"
                    className="px-2 py-1.5 text-xs rounded-lg transition-all hover:bg-white/10 text-slate-600 hover:text-slate-300 flex items-center gap-1"
                  >
                    <Bug size={12} />
                    Debug
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Metadata */}
          <div className="flex items-center gap-6 text-sm flex-wrap">
            {response.difficulty && (
              <div className="flex items-center gap-2">
                <span className="text-slate-500 text-xs">Difficulty:</span>
                <span className={`px-3 py-1 rounded-full text-xs font-bold border ${
                  response.difficulty === 'Beginner'
                    ? 'bg-green-500/15 text-green-400 border-green-500/30'
                    : response.difficulty === 'Intermediate'
                    ? 'bg-yellow-500/15 text-yellow-400 border-yellow-500/30'
                    : 'bg-red-500/15 text-red-400 border-red-500/30'
                }`}>
                  {response.difficulty === 'Beginner' ? '🟢 ' : response.difficulty === 'Intermediate' ? '🟡 ' : '🔴 '}
                  {response.difficulty}
                </span>
              </div>
            )}

            {response.estimatedTime && (
              <div className="flex items-center gap-2">
                <Clock className="text-slate-500" size={14} />
                <span className="text-slate-300 text-xs">{response.estimatedTime}</span>
              </div>
            )}

            {response.skillLevel && response.skillLevel !== 'general' && (
              <div className="flex items-center gap-2">
                <span className="text-slate-500 text-xs">Level:</span>
                <span className="text-slate-300 text-xs capitalize">{response.skillLevel}</span>
              </div>
            )}

            <div className="flex items-center gap-2">
              <span className="text-slate-500 text-xs">Progress:</span>
              <span className="text-purple-400 font-bold text-xs">{progressPercentage}%</span>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-white/10 rounded-full h-2 overflow-hidden">
            <motion.div
              className="h-full bg-gradient-to-r from-purple-500 to-blue-500"
              initial={{ width: 0 }}
              animate={{ width: `${progressPercentage}%` }}
              transition={{ duration: 0.5, ease: "easeOut" }}
            />
          </div>
        </div>

        {/* Prerequisites */}
        {response.prerequisites && response.prerequisites.length > 0 && (
          <CollapsibleSection
            title="Prerequisites"
            isExpanded={expandedSections.has('prerequisites')}
            onToggle={() => toggleSection('prerequisites')}
            icon={<AlertTriangle size={18} />}
          >
            <div className="space-y-2">
              {response.prerequisites.map((prereq, index) => (
                <div key={index} className="flex items-start gap-3 p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
                  <AlertTriangle className="text-amber-400 mt-0.5 flex-shrink-0" size={16} />
                  <span className="text-amber-200">{prereq}</span>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}

        {/* Steps Section */}
        <CollapsibleSection
          title={`Implementation Steps (${steps.length})`}
          isExpanded={expandedSections.has('steps')}
          onToggle={() => toggleSection('steps')}
          icon={<Target size={18} />}
        >
          <div className="space-y-3">
            {steps.length === 0 ? (
              <div className="text-center py-8 text-slate-500">
                <Target className="mx-auto mb-3 opacity-50" size={32} />
                <p>No steps available</p>
              </div>
            ) : (
              steps.map((step, index) => (
                <StepCard
                  key={index}
                  step={step}
                  index={index}
                  state={exec.getState(index)}
                  isReady={exec.readySteps.includes(index) || index === 0}
                  onToggle={() => handleStepClick(index)}
                  onStateChange={(s) => exec.setStepState(index, s)}
                  onAction={(prompt) => handleStepAction(index, prompt)}
                  actionState={stepActionState[index] ?? null}
                />
              ))
            )}
          </div>
        </CollapsibleSection>

        {/* Tips Section */}
        {tips.length > 0 && (
          <CollapsibleSection
            title={`Pro Tips (${tips.length})`}
            isExpanded={expandedSections.has('tips')}
            onToggle={() => toggleSection('tips')}
            icon={<Lightbulb size={18} />}
          >
            <div className="grid gap-3 md:grid-cols-2">
              {tips.map((tip, index) => (
                <div key={index} className="p-4 bg-blue-500/10 border border-blue-500/20 rounded-lg">
                  <div className="flex items-start gap-3">
                    <Lightbulb className="text-blue-400 mt-0.5 flex-shrink-0" size={16} />
                    <span className="text-blue-200">{tip}</span>
                  </div>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}

        {/* Mistakes to Avoid */}
        {mistakesToAvoid.length > 0 && (
          <CollapsibleSection
            title={`Common Pitfalls (${mistakesToAvoid.length})`}
            isExpanded={expandedSections.has('mistakes')}
            onToggle={() => toggleSection('mistakes')}
            icon={<AlertTriangle size={18} />}
          >
            <div className="space-y-3">
              {mistakesToAvoid.map((mistake, index) => (
                <div key={index} className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
                  <div className="flex items-start gap-3">
                    <AlertTriangle className="text-red-400 mt-0.5 flex-shrink-0" size={16} />
                    <span className="text-red-200">{mistake}</span>
                  </div>
                </div>
              ))}
            </div>
          </CollapsibleSection>
        )}

        {/* Resources */}
        {resources.length > 0 && (
          <CollapsibleSection
            title={`Resources (${resources.length})`}
            isExpanded={expandedSections.has('resources')}
            onToggle={() => toggleSection('resources')}
            icon={<ExternalLink size={18} />}
          >
            <div className="grid gap-3 md:grid-cols-2">
              {resources.map((resource, index) => (
                <ResourceCard key={index} resource={resource} />
              ))}
            </div>
          </CollapsibleSection>
        )}
      </div>
    </ErrorBoundary>
  );
};

// Helper Components
interface CollapsibleSectionProps {
  title: string;
  children: React.ReactNode;
  isExpanded: boolean;
  onToggle: () => void;
  icon?: React.ReactNode;
}

const CollapsibleSection: React.FC<CollapsibleSectionProps> = ({
  title,
  children,
  isExpanded,
  onToggle,
  icon
}) => (
  <div className="space-y-4">
    <button
      onClick={onToggle}
      className="flex items-center gap-3 w-full text-left group"
    >
      <div className="flex items-center gap-2 text-purple-400">
        {icon}
        <h2 className="text-xl font-bold">{title}</h2>
      </div>
      <motion.div
        animate={{ rotate: isExpanded ? 90 : 0 }}
        transition={{ duration: 0.2 }}
      >
        <ChevronRight className="text-slate-500 group-hover:text-slate-300" size={20} />
      </motion.div>
    </button>
    
    <AnimatePresence>
      {isExpanded && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: 0 }}
          transition={{ duration: 0.3, ease: "easeInOut" }}
          className="overflow-hidden"
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  </div>
);

interface StepCardProps {
  step: string;
  index: number;
  state: StepState;
  isReady: boolean;
  onToggle: () => void;
  onStateChange: (state: StepState) => void;
  onAction: (prompt: string) => void;
  actionState: { loading: boolean; result: string | null; error: string | null } | null;
}

const StepCard: React.FC<StepCardProps> = ({
  step, index, state, isReady, onToggle, onStateChange, onAction, actionState
}) => {
  const cfg = STATE_CONFIG[state];
  const isCompleted = state === 'COMPLETED';
  const isBlocked   = state === 'BLOCKED';

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05 }}
      className={`rounded-xl border transition-all ${
        isCompleted ? 'bg-green-500/10 border-green-500/30' :
        isBlocked   ? 'bg-amber-500/10 border-amber-500/30 opacity-70' :
        state === 'IN_PROGRESS' ? 'bg-blue-500/10 border-blue-500/30' :
        state === 'FAILED' ? 'bg-red-500/10 border-red-500/30' :
        'bg-white/5 border-white/10 hover:border-white/20'
      }`}
    >
      {/* Main step row */}
      <div
        className={`group p-4 ${isBlocked ? 'cursor-not-allowed' : 'cursor-pointer'}`}
        onClick={isBlocked ? undefined : onToggle}
      >
        <div className="flex items-start gap-4">
          <div className={`flex-shrink-0 mt-1 ${cfg.color}`}>
            {cfg.icon}
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-2 flex-wrap">
              <span className="text-xs font-bold text-purple-400 bg-purple-500/20 px-2 py-1 rounded">
                STEP {index + 1}
              </span>
              {state !== 'NOT_STARTED' && (
                <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${cfg.color} bg-white/5`}>
                  {cfg.label}
                </span>
              )}
              {isReady && state === 'NOT_STARTED' && (
                <span className="text-xs text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full">
                  Ready
                </span>
              )}
            </div>

            <p className={`text-sm leading-relaxed ${
              isCompleted ? 'text-green-200 line-through' :
              isBlocked   ? 'text-amber-200' :
              'text-slate-200'
            }`}>
              {step}
            </p>
          </div>

          {/* Phase 7.3: StepActionMenu — always visible, top-right of card */}
          <div
            className="flex-shrink-0 flex items-center gap-2"
            onClick={e => e.stopPropagation()} // prevent card toggle
          >
            {/* Quick state actions */}
            {!isCompleted && !isBlocked && (
              <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {state !== 'IN_PROGRESS' && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onStateChange('IN_PROGRESS'); }}
                    className="text-xs px-2 py-1 bg-blue-500/20 text-blue-400 rounded hover:bg-blue-500/30 transition-colors"
                  >
                    Start
                  </button>
                )}
                <button
                  onClick={(e) => { e.stopPropagation(); onStateChange('COMPLETED'); }}
                  className="text-xs px-2 py-1 bg-green-500/20 text-green-400 rounded hover:bg-green-500/30 transition-colors"
                >
                  Complete
                </button>
              </div>
            )}

            {/* Action menu — always visible */}
            <StepActionMenu
              stepIndex={index}
              stepTitle={step.substring(0, 60)}
              onAction={onAction}
            />

            {/* Loading spinner for active action */}
            {actionState?.loading && (
              <Loader2 size={14} className="text-purple-400 animate-spin flex-shrink-0" />
            )}
          </div>
        </div>
      </div>

      {/* Inline action result */}
      <AnimatePresence>
        {actionState?.result && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div className="mx-4 mb-4 p-4 bg-purple-500/10 border border-purple-500/20 rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-bold text-purple-400 uppercase tracking-wider">AI Response</span>
                <button
                  onClick={() => onAction('__clear__')}
                  className="text-xs text-slate-500 hover:text-slate-300 transition-colors"
                >
                  ✕
                </button>
              </div>
              <p className="text-sm text-slate-300 leading-relaxed whitespace-pre-wrap">
                {actionState.result}
              </p>
            </div>
          </motion.div>
        )}
        {actionState?.error && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div className="mx-4 mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
              <p className="text-xs text-red-400">{actionState.error}</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

interface ResourceCardProps {
  resource: string;
}

const ResourceCard: React.FC<ResourceCardProps> = ({ resource }) => {
  // Detect URLs — support http/https and common patterns like "docs.spring.io"
  const urlPattern = /https?:\/\/[^\s)]+|www\.[^\s)]+\.[a-z]{2,}[^\s)]*/i;
  const urlMatch = resource.match(urlPattern);
  const url = urlMatch ? (urlMatch[0].startsWith('http') ? urlMatch[0] : `https://${urlMatch[0]}`) : null;
  const displayText = resource;

  return (
    <div className="p-4 bg-slate-500/10 border border-slate-500/20 rounded-lg group hover:border-purple-500/30 transition-colors">
      <div className="flex items-start gap-3">
        <ExternalLink className="text-slate-400 mt-0.5 flex-shrink-0 group-hover:text-purple-400 transition-colors" size={16} />
        {url ? (
          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-slate-200 hover:text-purple-300 transition-colors flex-1 break-words underline underline-offset-2 decoration-slate-600 hover:decoration-purple-400"
          >
            {displayText}
          </a>
        ) : (
          <span className="text-slate-200 flex-1">{displayText}</span>
        )}
      </div>
    </div>
  );
};

export default EnhancedRoadmapRenderer;