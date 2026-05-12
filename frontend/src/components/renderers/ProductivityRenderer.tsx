import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Clock, Zap, CheckSquare, Wrench, RotateCcw, AlertCircle } from 'lucide-react';
import type { TaskResponse, ScheduleBlock } from '../../types';

interface ProductivityRendererProps {
  response: TaskResponse;
  onRefine?: (mode: 'default' | 'detailed' | 'simplified') => void;
}

const PRIORITY_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
  High:   { bg: 'bg-rose-500/10',    text: 'text-rose-400',    dot: 'bg-rose-500' },
  Medium: { bg: 'bg-amber-500/10',   text: 'text-amber-400',   dot: 'bg-amber-500' },
  Low:    { bg: 'bg-emerald-500/10', text: 'text-emerald-400', dot: 'bg-emerald-500' },
};

function ScheduleCard({ block, index }: { block: ScheduleBlock; index: number }) {
  const style = PRIORITY_STYLES[block.priority] ?? PRIORITY_STYLES.Medium;

  return (
    <motion.div
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.06 }}
      className={`p-5 rounded-2xl border border-white/10 bg-white/5 hover:border-white/20 transition-all`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-start gap-3 flex-1 min-w-0">
          <div className={`w-2 h-2 rounded-full flex-shrink-0 mt-2 ${style.dot}`} />
          <div className="min-w-0">
            <p className="text-sm font-semibold text-white leading-snug">{block.activity}</p>
            {block.duration && (
              <p className="text-[10px] text-slate-500 mt-1 flex items-center gap-1">
                <Clock size={10} /> {block.duration}
              </p>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
          <span className="text-[10px] font-bold text-slate-400 bg-white/5 px-2 py-0.5 rounded-lg whitespace-nowrap">
            {block.timeBlock}
          </span>
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-lg ${style.bg} ${style.text}`}>
            {block.priority}
          </span>
        </div>
      </div>
    </motion.div>
  );
}

const ProductivityRenderer: React.FC<ProductivityRendererProps> = ({ response, onRefine }) => {
  const [activeTab, setActiveTab] = useState<'schedule' | 'system' | 'tools'>('schedule');

  const hasSchedule = response.schedule && response.schedule.length > 0;
  const hasPriorities = response.priorities && response.priorities.length > 0;
  const hasHabits = response.habits && response.habits.length > 0;
  const hasTools = response.tools && response.tools.length > 0;

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full space-y-8">

      {/* ── Header ── */}
      <div className="space-y-3">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="px-3 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 text-[10px] font-bold uppercase tracking-widest">
            Productivity
          </span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight leading-tight">
          {response.systemTitle || response.userInput}
        </h1>
        {response.summary && (
          <p className="text-slate-400 text-sm leading-relaxed max-w-2xl">{response.summary}</p>
        )}
        {response.overview && response.overview !== response.summary && (
          <p className="text-slate-500 text-sm leading-relaxed max-w-2xl">{response.overview}</p>
        )}
      </div>

      {/* ── Refine bar ── */}
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

      {/* ── Tabs ── */}
      <div className="flex items-center gap-1 p-1 bg-white/5 rounded-xl border border-white/5 w-fit">
        {[
          { id: 'schedule', label: '📅 Schedule' },
          { id: 'system',   label: '⚡ System' },
          { id: 'tools',    label: '🔧 Tools' },
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

      {/* ── Tab: Schedule ── */}
      {activeTab === 'schedule' && (
        <motion.div key="schedule" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="grid grid-cols-1 lg:grid-cols-3 gap-8">

          <div className="lg:col-span-2 space-y-3">
            <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Time Blocks</h3>
            {hasSchedule ? (
              response.schedule!.map((block, i) => (
                <ScheduleCard key={i} block={block} index={i} />
              ))
            ) : (
              <p className="text-slate-500 text-sm text-center py-12">No schedule blocks available.</p>
            )}
          </div>

          {/* Sidebar */}
          <div className="space-y-5">
            {hasPriorities && (
              <div className="p-5 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
                <div className="flex items-center gap-2 mb-4 text-purple-400">
                  <Zap size={16} />
                  <h4 className="text-[10px] font-bold uppercase tracking-widest">Top Priorities</h4>
                </div>
                <ul className="space-y-2.5">
                  {response.priorities!.map((p, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                      <span className="text-purple-400 font-bold flex-shrink-0 mt-0.5">{i + 1}.</span>
                      {p}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {response.tips && response.tips.length > 0 && (
              <div className="p-5 rounded-2xl bg-white/5 border border-white/10">
                <h4 className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-4">💡 Tips</h4>
                <ul className="space-y-2">
                  {response.tips.map((t, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                      <span className="text-amber-400 flex-shrink-0 mt-0.5">→</span>
                      {t}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {response.commonMistakes && response.commonMistakes.length > 0 && (
              <div className="p-5 rounded-2xl bg-rose-500/5 border border-rose-500/20">
                <div className="flex items-center gap-2 mb-4 text-rose-400">
                  <AlertCircle size={14} />
                  <h4 className="text-[10px] font-bold uppercase tracking-widest">Avoid These</h4>
                </div>
                <ul className="space-y-2">
                  {response.commonMistakes.map((m, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                      <span className="text-rose-400 flex-shrink-0 mt-0.5">✗</span>
                      {m}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </motion.div>
      )}

      {/* ── Tab: System ── */}
      {activeTab === 'system' && (
        <motion.div key="system" initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="grid grid-cols-1 lg:grid-cols-2 gap-6">

          {hasHabits && (
            <div className="space-y-3">
              <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Daily Habits</h3>
              {response.habits!.map((habit, i) => (
                <div key={i} className="p-4 rounded-2xl bg-white/5 border border-white/10 flex items-center gap-3">
                  <CheckSquare size={16} className="text-emerald-400 flex-shrink-0" />
                  <span className="text-sm text-slate-200">{habit}</span>
                </div>
              ))}
            </div>
          )}

          {response.weeklyReview && (
            <div className="p-5 rounded-2xl bg-purple-600/10 border border-purple-500/20">
              <div className="flex items-center gap-2 mb-4 text-purple-400">
                <RotateCcw size={16} />
                <h4 className="text-[10px] font-bold uppercase tracking-widest">Weekly Review</h4>
              </div>
              <p className="text-sm text-slate-300 leading-relaxed">{response.weeklyReview}</p>
            </div>
          )}
        </motion.div>
      )}

      {/* ── Tab: Tools ── */}
      {activeTab === 'tools' && (
        <motion.div key="tools" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          {hasTools ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {response.tools!.map((tool, i) => (
                <div key={i} className="p-5 rounded-2xl bg-white/5 border border-white/10 hover:border-purple-500/30 transition-all group">
                  <div className="flex items-start gap-3">
                    <Wrench size={16} className="text-cyan-400 flex-shrink-0 mt-0.5" />
                    <p className="text-sm text-slate-300 leading-relaxed group-hover:text-white transition-colors">{tool}</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-slate-500 text-sm text-center py-12">No tools recommended for this system.</p>
          )}
        </motion.div>
      )}

    </motion.div>
  );
};

export default ProductivityRenderer;
