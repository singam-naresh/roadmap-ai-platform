import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BookOpen, Lightbulb, Code2, Target, ArrowRight, Copy, Check, ChevronDown, ChevronUp } from 'lucide-react';
import type { TaskResponse, LearningExample } from '../../types';

interface LearningRendererProps {
  response: TaskResponse;
  onSuggestionClick?: (text: string) => void;
}

function ExampleCard({ example, index }: { example: LearningExample; index: number }) {
  const [expanded, setExpanded] = useState(index === 0); // first example open by default
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    if (!example.code) return;
    navigator.clipboard.writeText(example.code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 overflow-hidden">
      <button
        onClick={() => setExpanded(p => !p)}
        className="w-full flex items-center justify-between px-5 py-4 text-left hover:bg-white/[0.03] transition-colors"
      >
        <div className="flex items-center gap-3">
          <span className="w-6 h-6 rounded-full bg-purple-500/20 text-purple-400 text-xs font-bold flex items-center justify-center flex-shrink-0">
            {index + 1}
          </span>
          <span className="text-sm font-semibold text-white">{example.title}</span>
        </div>
        {expanded ? <ChevronUp size={16} className="text-slate-500" /> : <ChevronDown size={16} className="text-slate-500" />}
      </button>

      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="px-5 pb-5 space-y-4 border-t border-white/5">
              {example.description && (
                <p className="text-sm text-slate-400 leading-relaxed pt-4">{example.description}</p>
              )}
              {example.code && (
                <div className="rounded-xl overflow-hidden border border-white/10 bg-[#0B1020]">
                  <div className="flex items-center justify-between px-4 py-2 bg-white/5 border-b border-white/5">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Code</span>
                    <button
                      onClick={handleCopy}
                      className="flex items-center gap-1.5 px-2 py-1 rounded-lg bg-white/5 text-[10px] font-bold text-slate-400 hover:text-white transition-all"
                    >
                      {copied ? <><Check size={10} className="text-emerald-400" /> Copied</> : <><Copy size={10} /> Copy</>}
                    </button>
                  </div>
                  <pre className="p-4 text-sm text-slate-200 font-mono leading-relaxed overflow-x-auto whitespace-pre">
                    <code>{example.code}</code>
                  </pre>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

const LearningRenderer: React.FC<LearningRendererProps> = ({ response, onSuggestionClick }) => {
  const [activeTab, setActiveTab] = useState<'learn' | 'practice' | 'next'>('learn');

  const hasExamples   = response.examples && response.examples.length > 0;
  const hasPractice   = response.practiceExercises && response.practiceExercises.length > 0;
  const hasNext       = response.nextTopics && response.nextTopics.length > 0;

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full space-y-8">

      {/* ── Header ── */}
      <div className="space-y-3">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-[10px] font-bold uppercase tracking-widest">
            Learning
          </span>
          {response.skillLevel && (
            <span className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-slate-400 text-[10px] font-bold uppercase tracking-widest">
              {response.skillLevel}
            </span>
          )}
        </div>
        <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight leading-tight">
          {response.conceptTitle || response.userInput}
        </h1>
        {response.summary && (
          <p className="text-slate-400 text-sm leading-relaxed max-w-2xl">{response.summary}</p>
        )}
      </div>

      {/* ── Tabs ── */}
      <div className="flex items-center gap-1 p-1 bg-white/5 rounded-xl border border-white/5 w-fit">
        {[
          { id: 'learn',    label: '📖 Learn' },
          { id: 'practice', label: '✏️ Practice' },
          { id: 'next',     label: '→ What\'s Next' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as typeof activeTab)}
            className={`px-4 py-2 rounded-lg text-xs font-bold transition-all ${
              activeTab === tab.id ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20' : 'text-slate-400 hover:text-white'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">

        {/* ── Tab: Learn ── */}
        {activeTab === 'learn' && (
          <motion.div key="learn" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="grid grid-cols-1 lg:grid-cols-3 gap-8">

            <div className="lg:col-span-2 space-y-6">
              {/* Core explanation */}
              {response.conceptExplanation && (
                <div className="p-6 rounded-2xl bg-white/5 border border-white/10">
                  <div className="flex items-center gap-2 mb-4 text-emerald-400">
                    <BookOpen size={16} />
                    <h3 className="text-[10px] font-bold uppercase tracking-widest">Explanation</h3>
                  </div>
                  <p className="text-sm text-slate-200 leading-relaxed">{response.conceptExplanation}</p>
                </div>
              )}

              {/* Examples */}
              {hasExamples && (
                <div className="space-y-3">
                  <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Examples</h3>
                  {response.examples!.map((ex, i) => (
                    <ExampleCard key={i} example={ex} index={i} />
                  ))}
                </div>
              )}
            </div>

            {/* Sidebar */}
            <div className="space-y-5">
              {/* Key points */}
              {response.keyPoints && response.keyPoints.length > 0 && (
                <div className="p-5 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
                  <div className="flex items-center gap-2 mb-4 text-purple-400">
                    <Lightbulb size={16} />
                    <h4 className="text-[10px] font-bold uppercase tracking-widest">Key Points</h4>
                  </div>
                  <ul className="space-y-2.5">
                    {response.keyPoints.map((p, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                        <span className="text-purple-400 font-bold flex-shrink-0 mt-0.5">→</span>
                        {p}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Common misconceptions */}
              {response.commonMisconceptions && response.commonMisconceptions.length > 0 && (
                <div className="p-5 rounded-2xl bg-amber-500/5 border border-amber-500/20">
                  <h4 className="text-[10px] font-bold uppercase tracking-widest text-amber-400 mb-4">⚠ Common Misconceptions</h4>
                  <ul className="space-y-2">
                    {response.commonMisconceptions.map((m, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                        <span className="text-amber-400 flex-shrink-0 mt-0.5">✗</span>
                        {m}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Best practices */}
              {response.bestPractices && response.bestPractices.length > 0 && (
                <div className="p-5 rounded-2xl bg-emerald-500/5 border border-emerald-500/20">
                  <h4 className="text-[10px] font-bold uppercase tracking-widest text-emerald-400 mb-4">✓ Best Practices</h4>
                  <ul className="space-y-2">
                    {response.bestPractices.map((b, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                        <span className="text-emerald-400 flex-shrink-0 mt-0.5">✓</span>
                        {b}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Resources */}
              {response.resources && response.resources.length > 0 && (
                <div className="p-5 rounded-2xl bg-white/5 border border-white/10">
                  <div className="flex items-center gap-2 mb-4 text-slate-400">
                    <BookOpen size={14} />
                    <h4 className="text-[10px] font-bold uppercase tracking-widest">Resources</h4>
                  </div>
                  <ul className="space-y-2">
                    {response.resources.map((r, i) => (
                      <li key={i} className="text-xs text-slate-300 flex items-start gap-2">
                        <span className="text-purple-400 flex-shrink-0">🔗</span>{r}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </motion.div>
        )}

        {/* ── Tab: Practice ── */}
        {activeTab === 'practice' && (
          <motion.div key="practice" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="space-y-4">
            <p className="text-slate-500 text-sm">Complete these exercises to reinforce your understanding.</p>
            {hasPractice ? (
              response.practiceExercises!.map((ex, i) => (
                <div key={i} className="p-5 rounded-2xl bg-white/5 border border-white/10 flex items-start gap-4 hover:border-purple-500/30 transition-all group">
                  <span className="w-8 h-8 rounded-full bg-purple-500/10 border border-purple-500/20 text-purple-400 text-sm font-bold flex items-center justify-center flex-shrink-0">
                    {i + 1}
                  </span>
                  <div className="flex-1">
                    <p className="text-sm text-slate-200 leading-relaxed">{ex}</p>
                  </div>
                  <Target size={16} className="text-slate-600 group-hover:text-purple-400 transition-colors flex-shrink-0 mt-0.5" />
                </div>
              ))
            ) : (
              <p className="text-slate-500 text-sm text-center py-12">No practice exercises available for this topic.</p>
            )}
          </motion.div>
        )}

        {/* ── Tab: What's Next ── */}
        {activeTab === 'next' && (
          <motion.div key="next" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="space-y-4">
            <p className="text-slate-500 text-sm">Continue your learning journey with these related topics.</p>
            {hasNext ? (
              response.nextTopics!.map((topic, i) => (
                <button
                  key={i}
                  onClick={() => onSuggestionClick?.(topic)}
                  className="w-full p-5 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-between hover:border-purple-500/30 hover:bg-purple-500/5 transition-all group text-left"
                >
                  <div className="flex items-center gap-4">
                    <span className="w-8 h-8 rounded-full bg-white/5 text-slate-400 text-sm font-bold flex items-center justify-center flex-shrink-0 group-hover:bg-purple-500/20 group-hover:text-purple-400 transition-all">
                      {i + 1}
                    </span>
                    <span className="text-sm text-slate-200 group-hover:text-white transition-colors">{topic}</span>
                  </div>
                  <ArrowRight size={16} className="text-slate-600 group-hover:text-purple-400 transition-colors flex-shrink-0" />
                </button>
              ))
            ) : (
              <p className="text-slate-500 text-sm text-center py-12">No next topics available.</p>
            )}
          </motion.div>
        )}

      </AnimatePresence>
    </motion.div>
  );
};

export default LearningRenderer;
