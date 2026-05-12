import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Sidebar from '../components/Sidebar';
import GlowBackground from '../components/GlowBackground';
import PromptBox from '../components/PromptBox';
import ResponseRenderer from '../components/renderers/ResponseRenderer';
import DashboardAnalytics from '../components/DashboardAnalytics';
import FeasibilityWarning from '../components/FeasibilityWarning';
import ContinuationBar from '../components/ContinuationBar';
import { Toast, useToast } from '../components/Toast';
import RateLimitBanner from '../components/RateLimitBanner';
import { BrainCircuit, LayoutGrid, Activity, Sparkles } from 'lucide-react';
import { getAllTasks, checkFeasibility, continueConversation } from '../services/api';
import { usePersistedWorkspace } from '../hooks/usePersistedWorkspace';
import { useStreamingGeneration } from '../hooks/useStreamingGeneration';
import type { TaskResponse, FeasibilityResult } from '../types';

// Intent-aware loading messages
const GENERATION_STATES: Record<string, string[]> = {
  CHAT:         ['Processing your question…', 'Generating response…'],
  CODING:       ['Analyzing code…', 'Building solution…', 'Formatting output…'],
  ANALYSIS:     ['Analyzing topic…', 'Evaluating options…', 'Building comparison…'],
  ROADMAP:      ['Analyzing your goal…', 'Detecting skill level…', 'Building personalized roadmap…', 'Structuring learning phases…', 'Finalizing your roadmap…'],
  LEARNING:     ['Analyzing your goal…', 'Structuring learning path…', 'Generating content…'],
  PRODUCTIVITY: ['Analyzing your goal…', 'Building execution plan…', 'Finalizing schedule…'],
  STARTUP:      ['Analyzing your idea…', 'Building GTM strategy…', 'Finalizing startup plan…'],
  default:      ['Analyzing your goal…', 'Detecting skill level…', 'Generating response…', 'Finalizing…'],
};

// Intents that should always show the continuation bar
const CONTINUATION_INTENTS = new Set(['ROADMAP', 'STARTUP', 'LEARNING', 'CODING', 'ANALYSIS']);

const Home: React.FC = () => {
  const [view, setView]                         = useState<'dashboard' | 'response'>('dashboard');
  const [isGenerating, setIsGenerating]         = useState(false);
  const [generationState, setGenerationState]   = useState('');
  const [currentResponse, setCurrentResponse]   = useState<TaskResponse | null>(null);
  const [history, setHistory]                   = useState<TaskResponse[]>([]);
  const [analyticsRefresh, setAnalyticsRefresh] = useState(0);
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const [feasibilityResult, setFeasibilityResult]       = useState<FeasibilityResult | null>(null);
  const [isContinuing, setIsContinuing]         = useState(false);
  const [rateLimitInfo, setRateLimitInfo]       = useState<{ message: string; retryAfter: number } | null>(null);
  const [layoutReady, setLayoutReady]           = useState(false);

  // Debounce guard — prevents double-submit
  const lastSubmitRef = useRef<number>(0);

  const workspace = usePersistedWorkspace();
  const streaming = useStreamingGeneration();
  const toast     = useToast();

  // Layout ready gate — prevents hydration layout shift
  useEffect(() => {
    const t = requestAnimationFrame(() => setLayoutReady(true));
    return () => cancelAnimationFrame(t);
  }, []);

  // Restore workspace on mount
  useEffect(() => {
    getAllTasks()
      .then(tasks => {
        setHistory(tasks);
        const shouldRestore =
          workspace.lastView === 'response' &&
          workspace.activeTaskId !== null;

        if (shouldRestore) {
          const saved = tasks.find(t => t.id === workspace.activeTaskId);
          if (saved) {
            setCurrentResponse(saved);
            setView('response');
            if (saved.conversationId) setActiveConversationId(saved.conversationId);
          }
        }
      })
      .catch(() => toast.error('Failed to load history', 'Check your connection and refresh.'));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Main generation handler ──────────────────────────────────────────────

  const handleGenerate = useCallback(async (
    prompt: string,
    mode: 'default' | 'detailed' | 'simplified' = workspace.selectedMode
  ) => {
    const trimmed = prompt.trim();
    if (!trimmed) return;

    // Debounce: ignore if called within 500ms of last submit
    const now = Date.now();
    if (now - lastSubmitRef.current < 500) return;
    lastSubmitRef.current = now;

    // Abort any in-flight request
    if (streaming.isStreaming) {
      streaming.abort();
      await new Promise(r => setTimeout(r, 100));
    }

    // Feasibility check
    try {
      const feasibility = await checkFeasibility(trimmed);
      if (!feasibility.feasible) {
        setFeasibilityResult(feasibility);
        setView('response');
        setIsGenerating(false);
        return;
      }
    } catch {
      // Non-fatal — proceed if feasibility check fails
    }
    setFeasibilityResult(null);

    setIsGenerating(true);
    setCurrentResponse(null);
    setView('response');
    workspace.setLastView('response');

    // Cycle loading messages
    const states = GENERATION_STATES.default;
    let idx = 0;
    setGenerationState(states[0]);
    const stateInterval = setInterval(() => {
      idx = Math.min(idx + 1, states.length - 1);
      setGenerationState(states[idx]);
    }, 900);

    streaming.generate(
      trimmed,
      mode,
      activeConversationId ?? undefined,
      (task) => {
        clearInterval(stateInterval);
        setCurrentResponse(task);
        setHistory(prev => [task, ...prev.filter(t => t.id !== task.id)]);
        setAnalyticsRefresh(n => n + 1);
        setIsGenerating(false);
        workspace.setActiveTask(task.id);
        if (task.conversationId) {
          setActiveConversationId(task.conversationId);
          workspace.setActiveConversation(task.conversationId);
        }
      },
      (errMsg, retryAfter, reason) => {
        clearInterval(stateInterval);
        setIsGenerating(false);
        if (reason === 'RATE_LIMITED' || reason === 'CONCURRENT' || reason === 'COOLDOWN') {
          setRateLimitInfo({ message: errMsg, retryAfter: retryAfter ?? 30 });
          setView('dashboard');
        } else if (reason === 'CLIENT_ABORT') {
          setView('dashboard');
        } else {
          toast.error('Generation failed', errMsg);
          setView('dashboard');
        }
      }
    );
  }, [workspace, activeConversationId, streaming, toast]);

  // ── Continuation handler ─────────────────────────────────────────────────

  const handleContinuation = useCallback(async (prompt: string) => {
    if (!prompt.trim()) return;

    // If no active conversation, start a new generation instead
    if (!activeConversationId) {
      handleGenerate(prompt);
      return;
    }

    setIsContinuing(true);
    try {
      const response = await continueConversation(
        activeConversationId,
        prompt,
        workspace.selectedMode
      );
      setCurrentResponse(response);
      setHistory(prev => [response, ...prev.filter(t => t.id !== response.id)]);
      workspace.setActiveTask(response.id);
      // Keep the same conversationId — context is preserved
    } catch (err: any) {
      const msg = err?.message ?? 'Continuation failed';
      // Friendly error messages
      if (msg.includes('429') || msg.includes('rate')) {
        toast.warning('Slow down', 'Too many requests. Wait a moment and try again.');
      } else if (msg.includes('401') || msg.includes('auth')) {
        toast.error('Session expired', 'Please refresh the page and log in again.');
      } else {
        toast.error('Follow-up failed', msg);
      }
    } finally {
      setIsContinuing(false);
    }
  }, [activeConversationId, workspace, handleGenerate, toast]);

  // ── Other handlers ───────────────────────────────────────────────────────

  const handleSelectHistory = useCallback((task: TaskResponse) => {
    // Clear first to prevent stale state leakage between roadmaps
    setCurrentResponse(null);
    setFeasibilityResult(null);
    // Small tick to let React flush the null state before setting new response
    requestAnimationFrame(() => {
      setCurrentResponse(task);
      setView('response');
      workspace.setActiveTask(task.id);
      workspace.setLastView('response');
      if (task.conversationId) {
        setActiveConversationId(task.conversationId);
        workspace.setActiveConversation(task.conversationId);
      } else {
        setActiveConversationId(null);
      }
    });
  }, [workspace]);

  const handleRefine = useCallback((mode: 'default' | 'detailed' | 'simplified') => {
    if (currentResponse) handleGenerate(currentResponse.userInput, mode);
  }, [currentResponse, handleGenerate]);

  const handleSuggestionClick = useCallback((text: string) => {
    handleGenerate(text, workspace.selectedMode);
  }, [handleGenerate, workspace.selectedMode]);

  const handleNewRoadmap = useCallback(() => {
    setView('dashboard');
    setCurrentResponse(null);
    setFeasibilityResult(null);
    setActiveConversationId(null);
    workspace.setActiveTask(null);
    workspace.setLastView('dashboard');
  }, [workspace]);

  // Determine if continuation bar should be shown
  const showContinuation = !!(
    currentResponse &&
    CONTINUATION_INTENTS.has(currentResponse.intentType)
  );

  const activeTabLabel = currentResponse
    ? ({ CHAT: 'RESPONSE', CODING: 'CODE', ANALYSIS: 'ANALYSIS' } as Record<string, string>)[currentResponse.intentType] ?? 'ROADMAP'
    : 'ROADMAP';

  return (
    <div className="flex h-screen text-slate-200 selection:bg-purple-500/30 overflow-hidden">
      <GlowBackground />

      {/* Toast notifications */}
      <Toast toasts={toast.toasts} onDismiss={toast.dismiss} />

      <Sidebar
        history={history}
        onSelectHistory={handleSelectHistory}
        onNewExecution={handleNewRoadmap}
        activeId={currentResponse?.id}
      />

      <main className="flex-1 flex flex-col min-h-0">
        {/* ── Header ── */}
        <header className="flex-shrink-0 w-full px-8 py-4 bg-[#050816]/60 backdrop-blur-xl border-b border-white/5 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 cursor-pointer hover:bg-white/10 transition-colors"
              onClick={handleNewRoadmap}
            >
              <BrainCircuit size={16} className="text-purple-400" />
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Adaptive Learning</span>
            </div>
            <nav className="hidden md:flex items-center gap-1 p-1 bg-white/5 rounded-xl border border-white/5">
              <button
                onClick={() => setView('dashboard')}
                className={`px-4 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-2 ${
                  view === 'dashboard' ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                <LayoutGrid size={14} /> DASHBOARD
              </button>
              <button
                onClick={() => setView('response')}
                disabled={!currentResponse && !isGenerating}
                className={`px-4 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-2 disabled:opacity-30 disabled:cursor-not-allowed ${
                  view === 'response' ? 'bg-white/10 text-white' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                <Activity size={14} /> {activeTabLabel}
              </button>
            </nav>
          </div>

          <div className="flex items-center gap-4">
            {isGenerating && (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-purple-500/10 border border-purple-500/20">
                <div className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse" />
                <span className="text-[10px] font-bold text-purple-400 uppercase tracking-widest">Generating…</span>
              </div>
            )}
          </div>
        </header>

        {/* ── Main Content ── */}
        <div className="flex-1 flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-white/10 hover:scrollbar-thumb-white/20">
            <div className="w-full max-w-6xl mx-auto px-8 py-12">

              {!layoutReady && (
                <div className="space-y-6 animate-pulse">
                  <div className="h-8 bg-white/5 rounded-xl w-48" />
                  <div className="h-4 bg-white/5 rounded-xl w-96" />
                  <div className="grid grid-cols-2 gap-4">
                    {[1,2,3,4].map(i => <div key={i} className="h-24 bg-white/5 rounded-2xl" />)}
                  </div>
                </div>
              )}

              {layoutReady && (
                <AnimatePresence mode="wait">

                  {/* ── Dashboard ── */}
                  {view === 'dashboard' && !isGenerating && (
                    <motion.div
                      key="dashboard"
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.98 }}
                      className="space-y-12"
                    >
                      <div className="space-y-2">
                        <h2 className="text-sm font-bold text-purple-400 uppercase tracking-[0.3em]">Your Learning Hub</h2>
                        <h1 className="text-4xl font-bold text-white tracking-tight">
                          {history.length > 0 ? 'Welcome back.' : 'Build your learning roadmap.'}
                        </h1>
                        <p className="text-slate-400 text-sm">
                          Describe your goal and get an adaptive roadmap tailored to your skill level.
                        </p>
                      </div>

                      <AnimatePresence>
                        {rateLimitInfo && (
                          <RateLimitBanner
                            message={rateLimitInfo.message}
                            retryAfterSeconds={rateLimitInfo.retryAfter}
                            remainingRequests={streaming.remainingRequests ?? undefined}
                            onDismiss={() => setRateLimitInfo(null)}
                          />
                        )}
                      </AnimatePresence>

                      <DashboardAnalytics refreshTrigger={analyticsRefresh} />

                      <div className="space-y-6">
                        <div className="flex items-center justify-between">
                          <h3 className="text-lg font-bold text-white">What do you want to learn?</h3>
                          <div className="flex items-center gap-2">
                            {(['default', 'detailed', 'simplified'] as const).map(m => (
                              <button
                                key={m}
                                onClick={() => workspace.setSelectedMode(m)}
                                className={`px-3 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all ${
                                  workspace.selectedMode === m
                                    ? 'bg-purple-600 text-white'
                                    : 'bg-white/5 text-slate-500 hover:text-white'
                                }`}
                              >
                                {m}
                              </button>
                            ))}
                          </div>
                        </div>
                        <PromptBox
                          onGenerate={(p) => handleGenerate(p, workspace.selectedMode)}
                          disabled={isGenerating || streaming.isStreaming}
                        />
                      </div>
                    </motion.div>
                  )}

                  {/* ── Generating ── */}
                  {isGenerating && (
                    <motion.div
                      key="generating"
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      className="flex flex-col items-center justify-center py-32 space-y-12"
                    >
                      <div className="relative">
                        <motion.div
                          animate={{ rotate: 360 }}
                          transition={{ duration: 8, repeat: Infinity, ease: 'linear' }}
                          className="w-48 h-48 rounded-full border border-purple-500/20 border-t-purple-500 border-r-purple-500/40"
                        />
                        <div className="absolute inset-0 flex items-center justify-center">
                          <Sparkles className="text-purple-400 animate-pulse" size={48} />
                        </div>
                      </div>

                      <div className="text-center space-y-4">
                        <motion.h2
                          key={generationState}
                          initial={{ opacity: 0, y: 10 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-2xl font-bold text-white tracking-tight"
                        >
                          {generationState}
                        </motion.h2>
                        <div className="flex items-center justify-center gap-1">
                          {[0, 1, 2].map(i => (
                            <motion.div
                              key={i}
                              animate={{ scale: [1, 1.5, 1], opacity: [0.3, 1, 0.3] }}
                              transition={{ duration: 1, repeat: Infinity, delay: i * 0.2 }}
                              className="w-1.5 h-1.5 rounded-full bg-purple-500"
                            />
                          ))}
                        </div>
                      </div>

                      <div className="w-full max-w-2xl grid grid-cols-1 md:grid-cols-2 gap-4">
                        {[1, 2, 3, 4].map(i => (
                          <div
                            key={i}
                            className="h-24 bg-white/5 rounded-2xl border border-white/5 animate-pulse"
                            style={{ animationDelay: `${i * 0.1}s` }}
                          />
                        ))}
                      </div>

                      {streaming.streamedText && (
                        <motion.div
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          className="w-full max-w-2xl p-4 bg-white/5 border border-white/10 rounded-2xl"
                        >
                          <p className="text-xs text-slate-400 font-mono leading-relaxed line-clamp-6">
                            {streaming.streamedText}
                            <span className="inline-block w-1.5 h-3 bg-purple-400 ml-0.5 animate-pulse" />
                          </p>
                        </motion.div>
                      )}
                    </motion.div>
                  )}

                  {/* ── Response View ── */}
                  {view === 'response' && !isGenerating && (
                    <motion.div
                      key={`response-${currentResponse?.id ?? 'feasibility'}`}
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      className="space-y-8"
                    >
                      <button
                        onClick={handleNewRoadmap}
                        className="text-[10px] font-bold text-slate-500 hover:text-white transition-colors flex items-center gap-2 tracking-widest"
                      >
                        ← BACK TO DASHBOARD
                      </button>

                      <AnimatePresence>
                        {feasibilityResult && !feasibilityResult.feasible && (
                          <FeasibilityWarning
                            result={feasibilityResult}
                            onAcceleratedPlan={() => {
                              setFeasibilityResult(null);
                              const domain = feasibilityResult.domain ?? 'learning';
                              handleGenerate(`Generate an accelerated ${domain} roadmap focusing on the most critical skills`);
                            }}
                            onDismiss={() => setFeasibilityResult(null)}
                          />
                        )}
                      </AnimatePresence>

                      {currentResponse && (
                        <ResponseRenderer
                          response={currentResponse}
                          onRefine={handleRefine}
                          onSuggestionClick={handleSuggestionClick}
                        />
                      )}

                      {/* Persistent continuation bar — shown for all non-chat intents */}
                      {showContinuation && (
                        <ContinuationBar
                          conversationId={activeConversationId}
                          taskId={currentResponse!.id}
                          intentType={currentResponse!.intentType}
                          isLoading={isContinuing}
                          onSubmit={handleContinuation}
                        />
                      )}
                    </motion.div>
                  )}

                </AnimatePresence>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Home;
