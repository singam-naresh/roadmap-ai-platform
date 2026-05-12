import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, XCircle, Target, Lightbulb } from 'lucide-react';
import type { TaskResponse, AnalysisSection } from '../../types';

interface AnalysisRendererProps {
  response: TaskResponse;
  onRefine?: (mode: 'default' | 'detailed' | 'simplified') => void;
}

function ScoreBar({ score }: { score?: number | null }) {
  if (score == null) return null;
  const pct = (score / 10) * 100;
  const color = score >= 7 ? 'bg-emerald-500' : score >= 4 ? 'bg-amber-500' : 'bg-rose-500';
  return (
    <div className="flex items-center gap-2 mt-2">
      <div className="flex-1 h-1.5 bg-white/5 rounded-full overflow-hidden">
        <motion.div
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.8, delay: 0.2 }}
          className={`h-full rounded-full ${color}`}
        />
      </div>
      <span className="text-[10px] font-bold text-slate-400 w-6 text-right">{score}/10</span>
    </div>
  );
}

const AnalysisRenderer: React.FC<AnalysisRendererProps> = ({ response, onRefine }) => {
  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full space-y-8">

      {/* Header */}
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-[10px] font-bold uppercase tracking-widest">
            Analysis
          </span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight leading-tight">
          {response.userInput}
        </h1>
        {response.summary && (
          <p className="text-slate-400 text-sm leading-relaxed max-w-2xl">{response.summary}</p>
        )}
      </div>

      {/* Verdict */}
      {response.verdict && (
        <div className="p-5 rounded-2xl bg-purple-600/10 border border-purple-500/30">
          <p className="text-[10px] font-bold text-purple-400 uppercase tracking-widest mb-2">Verdict</p>
          <p className="text-white font-semibold text-base leading-relaxed">{response.verdict}</p>
        </div>
      )}

      {/* Refine bar */}
      {onRefine && (
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Refine:</span>
          {(['detailed', 'simplified', 'default'] as const).map(m => (
            <button key={m} onClick={() => onRefine(m)}
              className="px-3 py-1.5 rounded-lg bg-white/5 border border-white/10 text-[10px] font-bold text-slate-400 hover:text-white hover:border-white/20 transition-all capitalize">
              {m}
            </button>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Analysis sections */}
        <div className="lg:col-span-2 space-y-4">
          {response.sections && response.sections.length > 0 && (
            <>
              <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Analysis Breakdown</h3>
              {response.sections.map((section: AnalysisSection, i: number) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: i * 0.07 }}
                  className="p-5 rounded-2xl bg-white/5 border border-white/10 hover:border-white/20 transition-all"
                >
                  <div className="flex items-start justify-between mb-2">
                    <h4 className="font-bold text-white text-sm">{section.title}</h4>
                  </div>
                  <p className="text-sm text-slate-400 leading-relaxed">{section.content}</p>
                  <ScoreBar score={section.score} />
                </motion.div>
              ))}
            </>
          )}
        </div>

        {/* Sidebar: pros, cons, recommendations */}
        <div className="space-y-5">
          {response.pros && response.pros.length > 0 && (
            <div className="p-5 rounded-2xl bg-emerald-500/5 border border-emerald-500/20">
              <div className="flex items-center gap-2 mb-4 text-emerald-400">
                <CheckCircle2 size={16} />
                <h4 className="text-[10px] font-bold uppercase tracking-widest">Pros</h4>
              </div>
              <ul className="space-y-2">
                {response.pros.map((p, i) => (
                  <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                    <span className="text-emerald-400 flex-shrink-0 mt-0.5">+</span>{p}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {response.cons && response.cons.length > 0 && (
            <div className="p-5 rounded-2xl bg-rose-500/5 border border-rose-500/20">
              <div className="flex items-center gap-2 mb-4 text-rose-400">
                <XCircle size={16} />
                <h4 className="text-[10px] font-bold uppercase tracking-widest">Cons</h4>
              </div>
              <ul className="space-y-2">
                {response.cons.map((c, i) => (
                  <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                    <span className="text-rose-400 flex-shrink-0 mt-0.5">−</span>{c}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {response.recommendations && response.recommendations.length > 0 && (
            <div className="p-5 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
              <div className="flex items-center gap-2 mb-4 text-purple-400">
                <Target size={16} />
                <h4 className="text-[10px] font-bold uppercase tracking-widest">Recommendations</h4>
              </div>
              <ul className="space-y-2">
                {response.recommendations.map((r, i) => (
                  <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                    <span className="text-purple-400 flex-shrink-0 mt-0.5">→</span>{r}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {response.useCases && response.useCases.length > 0 && (
            <div className="p-5 rounded-2xl bg-white/5 border border-white/10">
              <div className="flex items-center gap-2 mb-4 text-amber-400">
                <Lightbulb size={16} />
                <h4 className="text-[10px] font-bold uppercase tracking-widest">Best For</h4>
              </div>
              <ul className="space-y-2">
                {response.useCases.map((u, i) => (
                  <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                    <span className="text-amber-400 flex-shrink-0 mt-0.5">✓</span>{u}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
};

export default AnalysisRenderer;
