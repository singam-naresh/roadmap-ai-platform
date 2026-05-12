import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { toast } from 'react-toastify';
import {
  Clock, BarChart, CheckCircle2, MoreHorizontal, AlertCircle,
  Lightbulb, RefreshCw, Calendar, ArrowRight, CheckSquare,
  StickyNote, Flag, ChevronDown, ChevronUp, BookOpen, ExternalLink,
} from 'lucide-react';
import type { RoadmapViewModel } from '../types';
import { 
  getRoadmapByTaskId,
  getRoadmapSteps,
  toggleRoadmapStep,
  updateRoadmapStepNote,
  updateRoadmapStepPriority,
  getRoadmapProgress,
  type RoadmapStep,
  type RoadmapProgress
} from '../services/api';

interface RoadmapCardProps {
  roadmap: RoadmapViewModel;
  onRefine?: (mode: 'default' | 'detailed' | 'simplified') => void;
}

const REFINEMENT_MODES: { label: string; mode: 'default' | 'detailed' | 'simplified' }[] = [
  { label: 'Make Detailed',  mode: 'detailed'   },
  { label: 'Quick Version',  mode: 'simplified' },
  { label: 'Balanced Plan',  mode: 'default'    },
];

const RoadmapCard: React.FC<RoadmapCardProps> = ({ roadmap, onRefine }) => {
  const [roadmapData, setRoadmapData] = useState<any>(null);
  const [roadmapSteps, setRoadmapSteps] = useState<RoadmapStep[]>([]);
  const [roadmapProgress, setRoadmapProgress] = useState<RoadmapProgress | null>(null);
  const [expandedSteps, setExpandedSteps] = useState<Set<string>>(new Set());
  const [activeTab, setActiveTab] = useState<'roadmap' | 'insights' | 'resources'>('roadmap');
  const [copiedMd, setCopiedMd] = useState(false);
  const [loading, setLoading] = useState(true);
  const [editingNote, setEditingNote] = useState<string | null>(null);
  const [noteText, setNoteText] = useState('');

  // Load roadmap data on mount
  useEffect(() => {
    const loadRoadmapData = async () => {
      try {
        setLoading(true);
        
        // Get or create roadmap from task
        const roadmapEntity = await getRoadmapByTaskId(roadmap.id);
        setRoadmapData(roadmapEntity);
        
        // Get roadmap steps
        const steps = await getRoadmapSteps(roadmapEntity.id);
        setRoadmapSteps(steps);
        
        // Get progress
        const progress = await getRoadmapProgress(roadmapEntity.id);
        setRoadmapProgress(progress);
        
      } catch (error) {
        console.error('Failed to load roadmap data:', error);
        toast.error('Failed to load roadmap progress');
      } finally {
        setLoading(false);
      }
    };

    loadRoadmapData();
  }, [roadmap.id]);

  const toggleStep = async (stepIndex: number) => {
    if (!roadmapData) return;
    
    try {
      const updatedStep = await toggleRoadmapStep(roadmapData.id, stepIndex);
      
      // Update local state
      setRoadmapSteps(prev => prev.map(step => 
        step.stepIndex === stepIndex ? updatedStep : step
      ));
      
      // Refresh progress
      const progress = await getRoadmapProgress(roadmapData.id);
      setRoadmapProgress(progress);
      
      toast.success(updatedStep.completed ? 'Step completed! 🎉' : 'Step marked as incomplete');
    } catch (error) {
      console.error('Failed to toggle step:', error);
      toast.error('Failed to update step progress');
    }
  };

  const handleSetPriority = async (stepIndex: number, priority: string) => {
    if (!roadmapData) return;
    
    try {
      const updatedStep = await updateRoadmapStepPriority(roadmapData.id, stepIndex, priority);
      
      setRoadmapSteps(prev => prev.map(step => 
        step.stepIndex === stepIndex ? updatedStep : step
      ));
      
      toast.success(`Priority set to ${priority.toLowerCase()}`);
    } catch (error) {
      console.error('Failed to set priority:', error);
      toast.error('Failed to update priority');
    }
  };

  const handleSaveNote = async (stepIndex: number, note: string) => {
    if (!roadmapData) return;
    
    try {
      const updatedStep = await updateRoadmapStepNote(roadmapData.id, stepIndex, note);
      
      setRoadmapSteps(prev => prev.map(step => 
        step.stepIndex === stepIndex ? updatedStep : step
      ));
      
      setEditingNote(null);
      setNoteText('');
      toast.success('Note saved');
    } catch (error) {
      console.error('Failed to save note:', error);
      toast.error('Failed to save note');
    }
  };

  const startEditingNote = (stepIndex: number) => {
    const step = roadmapSteps.find(s => s.stepIndex === stepIndex);
    const currentNote = step?.notes || '';
    setNoteText(currentNote);
    setEditingNote(String(stepIndex));
  };

  const toggleExpand = (id: string) => {
    setExpandedSteps(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const progress = roadmapProgress?.progressPercentage || 0;

  const handleCopyMarkdown = () => {
    const lines = [
      `# ${roadmap.title}`,
      '',
      roadmap.summary ? `> ${roadmap.summary}` : '',
      '',
      `**Estimated Time:** ${roadmap.estimatedTime}`,
      `**Difficulty:** ${roadmap.difficulty}`,
      `**Skill Level:** ${roadmap.skillLevel}`,
      '',
    ];
    if (roadmap.prerequisites.length) {
      lines.push('## Prerequisites', ...roadmap.prerequisites.map(p => `- ${p}`), '');
    }
    if (roadmap.steps.length) {
      lines.push('## Roadmap');
      roadmap.steps.forEach((s, i) => lines.push(`${i + 1}. **${s.title}** (${s.duration}) — ${s.description}`));
      lines.push('');
    }
    if (roadmap.tips.length) {
      lines.push('## Tips', ...roadmap.tips.map(t => `- 💡 ${t}`), '');
    }
    if (roadmap.mistakesToAvoid.length) {
      lines.push('## Mistakes to Avoid', ...roadmap.mistakesToAvoid.map(m => `- ⚠️ ${m}`), '');
    }
    if (roadmap.resources.length) {
      lines.push('## Resources', ...roadmap.resources.map(r => `- 🔗 ${r}`), '');
    }
    navigator.clipboard.writeText(lines.join('\n')).then(() => {
      setCopiedMd(true);
      setTimeout(() => setCopiedMd(false), 2000);
    });
  };

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full space-y-8">

      {/* ── Header ── */}
      <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-6">
        <div className="space-y-4">
          <div className="flex items-center gap-3 flex-wrap">
            <span className="px-3 py-1 rounded-full bg-purple-500/10 border border-purple-500/20 text-purple-400 text-[10px] font-bold uppercase tracking-widest">
              {roadmap.category}
            </span>
            <span className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-slate-400 text-[10px] font-bold uppercase tracking-widest">
              {roadmap.skillLevel}
            </span>
            <div className="flex items-center gap-4 text-slate-500 text-xs font-medium">
              <div className="flex items-center gap-1.5"><Clock size={14} /> {roadmap.estimatedTime}</div>
              <div className="flex items-center gap-1.5"><BarChart size={14} /> {roadmap.difficulty}</div>
            </div>
          </div>
          <h1 className="text-3xl md:text-5xl font-bold text-white tracking-tight leading-tight">
            {roadmap.title}
          </h1>
          {roadmap.summary && (
            <p className="text-slate-400 text-sm leading-relaxed max-w-2xl">{roadmap.summary}</p>
          )}
        </div>

        <div className="flex items-center gap-3 flex-shrink-0">
          <div className="flex bg-white/5 p-1 rounded-xl border border-white/10">
            {(['roadmap', 'insights', 'resources'] as const).map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2 rounded-lg text-xs font-bold transition-all ${
                  activeTab === tab ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/20' : 'text-slate-400 hover:text-white'
                }`}
              >
                {tab.toUpperCase()}
              </button>
            ))}
          </div>
          <button
            onClick={handleCopyMarkdown}
            className="p-2.5 rounded-xl bg-white/5 border border-white/10 text-slate-300 hover:bg-white/10 transition-colors text-xs font-bold"
            title="Copy as Markdown"
          >
            {copiedMd ? '✓' : '⎘'}
          </button>
          <button className="p-2.5 rounded-xl bg-white/5 border border-white/10 text-slate-300 hover:bg-white/10 transition-colors">
            <MoreHorizontal size={20} />
          </button>
        </div>
      </div>

      {/* ── Refinement Bar ── */}
      {onRefine && (
        <div className="flex items-center gap-2 overflow-x-auto pb-2">
          <div className="flex items-center gap-2 px-3 py-2 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400 text-[10px] font-bold whitespace-nowrap">
            <RefreshCw size={12} />
            REFINE PLAN
          </div>
          {REFINEMENT_MODES.map(({ label, mode }) => (
            <button
              key={mode}
              onClick={() => onRefine(mode)}
              className="px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-slate-400 text-[10px] font-bold hover:text-white hover:border-white/20 transition-all whitespace-nowrap"
            >
              {label.toUpperCase()}
            </button>
          ))}
        </div>
      )}

      {/* ── Tab: Roadmap ── */}
      <AnimatePresence mode="wait">
        {activeTab === 'roadmap' && (
          <motion.div key="roadmap" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="grid grid-cols-1 lg:grid-cols-3 gap-8">

            {loading ? (
              <div className="lg:col-span-2 flex items-center justify-center py-12">
                <div className="flex items-center gap-3 text-slate-400">
                  <div className="w-5 h-5 border-2 border-purple-500 border-t-transparent rounded-full animate-spin"></div>
                  <span className="text-sm font-medium">Loading execution progress...</span>
                </div>
              </div>
            ) : (
              <>
                {/* Steps timeline */}
                <div className="lg:col-span-2 space-y-6">
              {/* Prerequisites */}
              {roadmap.prerequisites.length > 0 && (
                <div className="p-4 rounded-2xl bg-white/5 border border-white/10">
                  <h4 className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-3">Prerequisites</h4>
                  <div className="flex flex-wrap gap-2">
                    {roadmap.prerequisites.map((p, i) => (
                      <span key={i} className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-xs text-slate-300">
                        ✓ {p}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Timeline steps */}
              {roadmap.steps.map((step, idx) => {
                const stepIndex = idx;
                const roadmapStep = roadmapSteps.find(s => s.stepIndex === stepIndex);
                const done = roadmapStep?.completed || false;
                const priority = roadmapStep?.priority || 'MEDIUM';
                const note = roadmapStep?.notes || '';
                const expanded = expandedSteps.has(step.id);
                const isEditingThisNote = editingNote === String(stepIndex);
                
                return (
                  <motion.div
                    key={step.id}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    className="group relative pl-12 pb-8 last:pb-0"
                  >
                    {/* Connector line */}
                    <div className={`absolute left-[19px] top-0 bottom-0 w-[2px] transition-colors duration-500 ${done ? 'bg-purple-500' : 'bg-white/10'} group-last:h-10`} />

                    {/* Node */}
                    <button
                      onClick={() => toggleStep(stepIndex)}
                      disabled={loading}
                      className={`absolute left-0 top-0 w-10 h-10 rounded-full border-2 flex items-center justify-center z-10 transition-all duration-500 ${
                        done
                          ? 'bg-purple-600 border-purple-400 shadow-[0_0_20px_rgba(124,58,237,0.5)]'
                          : 'bg-[#0B1020] border-white/10 hover:border-purple-500/50'
                      } ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                      title={done ? 'Mark incomplete' : 'Mark complete'}
                    >
                      {done
                        ? <CheckCircle2 size={20} className="text-white" />
                        : <span className="text-sm font-bold text-slate-500 group-hover:text-purple-400">{idx + 1}</span>
                      }
                    </button>

                    {/* Card */}
                    <div className={`p-6 rounded-2xl border transition-all duration-500 ${
                      done ? 'bg-purple-500/5 border-purple-500/20' : 'bg-white/5 border-white/5 hover:border-white/10 hover:bg-white/[0.07]'
                    }`}>
                      <div className="flex items-start justify-between mb-4">
                        <div className="space-y-1">
                          <h3 className={`text-xl font-bold transition-colors break-words ${done ? 'text-purple-300 line-through' : 'text-white'}`}>
                            {step.title}
                          </h3>
                          <div className="flex items-center gap-3">
                            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">{step.duration}</span>
                            <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded ${
                              step.difficulty === 'Easy'   ? 'bg-emerald-500/10 text-emerald-400' :
                              step.difficulty === 'Medium' ? 'bg-amber-500/10 text-amber-400' :
                                                             'bg-rose-500/10 text-rose-400'
                            }`}>
                              {step.difficulty}
                            </span>
                            <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded ${
                              priority === 'CRITICAL' ? 'bg-red-500/10 text-red-400' :
                              priority === 'HIGH' ? 'bg-orange-500/10 text-orange-400' :
                              priority === 'MEDIUM' ? 'bg-blue-500/10 text-blue-400' :
                                                     'bg-gray-500/10 text-gray-400'
                            }`}>
                              {priority}
                            </span>
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <button
                            onClick={() => toggleExpand(step.id)}
                            className="p-2 rounded-lg bg-white/5 text-slate-500 hover:text-white transition-colors"
                            title={expanded ? 'Collapse' : 'Expand'}
                          >
                            {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                          </button>
                          <button 
                            onClick={() => startEditingNote(stepIndex)}
                            className={`p-2 rounded-lg bg-white/5 transition-colors ${
                              note ? 'text-yellow-400 hover:text-yellow-300' : 'text-slate-500 hover:text-white'
                            }`}
                            title={note ? 'Edit note' : 'Add note'}
                          >
                            <StickyNote size={16} />
                          </button>
                          <div className="relative">
                            <select
                              value={priority}
                              onChange={(e) => handleSetPriority(stepIndex, e.target.value)}
                              className="p-2 rounded-lg bg-white/5 text-slate-500 hover:text-white transition-colors text-xs border-none outline-none appearance-none cursor-pointer"
                              title="Set priority"
                            >
                              <option value="LOW">Low</option>
                              <option value="MEDIUM">Medium</option>
                              <option value="HIGH">High</option>
                              <option value="CRITICAL">Critical</option>
                            </select>
                          </div>
                        </div>
                      </div>

                      <AnimatePresence>
                        {expanded && (
                          <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: 'auto', opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            className="overflow-hidden"
                          >
                            <p className="text-slate-400 text-sm leading-relaxed mb-6">{step.description}</p>
                            
                            {/* Note section */}
                            {(note || isEditingThisNote) && (
                              <div className="mb-4 p-3 rounded-lg bg-white/5 border border-white/10">
                                {isEditingThisNote ? (
                                  <div className="space-y-2">
                                    <textarea
                                      value={noteText}
                                      onChange={(e) => setNoteText(e.target.value)}
                                      placeholder="Add a note for this step..."
                                      className="w-full p-2 rounded bg-white/5 border border-white/10 text-white text-sm resize-none"
                                      rows={3}
                                    />
                                    <div className="flex gap-2">
                                      <button
                                        onClick={() => handleSaveNote(stepIndex, noteText)}
                                        className="px-3 py-1 rounded bg-purple-600 text-white text-xs font-bold hover:bg-purple-700 transition-colors"
                                      >
                                        Save
                                      </button>
                                      <button
                                        onClick={() => setEditingNote(null)}
                                        className="px-3 py-1 rounded bg-white/10 text-slate-400 text-xs font-bold hover:bg-white/20 transition-colors"
                                      >
                                        Cancel
                                      </button>
                                    </div>
                                  </div>
                                ) : (
                                  <div className="flex items-start gap-2">
                                    <StickyNote size={14} className="text-yellow-400 mt-0.5 flex-shrink-0" />
                                    <p className="text-slate-300 text-sm">{note}</p>
                                  </div>
                                )}
                              </div>
                            )}
                          </motion.div>
                        )}
                      </AnimatePresence>

                      <div className="flex flex-wrap items-center gap-4 pt-4 border-t border-white/5">
                        <button className="flex items-center gap-2 text-[10px] font-bold text-purple-400 hover:text-purple-300 transition-colors">
                          <ExternalLink size={14} /> RESOURCES
                        </button>
                        <button 
                          onClick={() => startEditingNote(stepIndex)}
                          className="flex items-center gap-2 text-[10px] font-bold text-slate-500 hover:text-slate-400 transition-colors"
                        >
                          <StickyNote size={14} /> {note ? 'EDIT NOTE' : 'ADD NOTE'}
                        </button>
                        <div className="ml-auto flex items-center gap-2 text-[10px] font-bold text-slate-600">
                          <CheckSquare size={14} />
                          {done ? 'COMPLETED' : 'MARK AS DONE'}
                        </div>
                      </div>
                    </div>
                  </motion.div>
                );
              })}
                </div>
              </>
            )}

            {/* Sidebar widgets */}
            <div className="space-y-6">
              {/* Progress */}
              <div className="p-6 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
                <div className="flex items-center justify-between mb-6">
                  <h4 className="font-bold text-white text-sm">Overall Progress</h4>
                  <span className="text-purple-400 font-bold text-lg">{progress}%</span>
                </div>
                <div className="w-full h-2 bg-white/5 rounded-full overflow-hidden mb-6">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: `${progress}%` }}
                    className="h-full bg-gradient-to-r from-purple-600 to-indigo-500 shadow-[0_0_10px_rgba(124,58,237,0.5)]"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                    <div className="text-xl font-bold text-white">{roadmapProgress?.completedSteps || 0}</div>
                    <div className="text-[10px] text-slate-500 font-bold uppercase">Done</div>
                  </div>
                  <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                    <div className="text-xl font-bold text-white">{(roadmapProgress?.totalSteps || roadmap.steps.length) - (roadmapProgress?.completedSteps || 0)}</div>
                    <div className="text-[10px] text-slate-500 font-bold uppercase">Left</div>
                  </div>
                </div>
              </div>

              {/* AI Context Memory */}
              <div className="p-6 rounded-2xl bg-purple-600/10 border border-purple-500/20">
                <div className="flex items-center gap-3 mb-4 text-purple-400">
                  <Lightbulb size={18} />
                  <h4 className="font-bold uppercase tracking-widest text-[10px]">AI Context Memory</h4>
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  This roadmap was generated using <span className="text-white">llama-3.3-70b-versatile</span> with{' '}
                  <span className="text-purple-400">{roadmap.mode}</span> mode for a{' '}
                  <span className="text-white">{roadmap.skillLevel}</span> learner.
                </p>
              </div>

              {/* Quick Actions */}
              <div className="space-y-2">
                <button className="w-full p-4 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between group hover:bg-white/10 transition-all">
                  <div className="flex items-center gap-3">
                    <Calendar className="text-blue-400" size={18} />
                    <span className="text-xs font-bold text-slate-300">Sync to Calendar</span>
                  </div>
                  <ArrowRight size={16} className="text-slate-600 group-hover:text-white transition-colors" />
                </button>
                <button
                  onClick={() => setActiveTab('insights')}
                  className="w-full p-4 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between group hover:bg-white/10 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <AlertCircle className="text-rose-400" size={18} />
                    <span className="text-xs font-bold text-slate-300">Common Pitfalls</span>
                  </div>
                  <ArrowRight size={16} className="text-slate-600 group-hover:text-white transition-colors" />
                </button>
              </div>
            </div>
          </motion.div>
        )}

        {/* ── Tab: Insights ── */}
        {activeTab === 'insights' && (
          <motion.div key="insights" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* Tips */}
            {roadmap.tips.length > 0 && (
              <div className="space-y-4">
                <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">💡 Pro Tips</h3>
                {roadmap.tips.map((tip, i) => (
                  <div key={i} className="p-4 rounded-2xl bg-white/5 border border-white/10 flex gap-4">
                    <span className="w-6 h-6 rounded-full bg-purple-500/20 text-purple-400 text-xs font-bold flex items-center justify-center flex-shrink-0">{i + 1}</span>
                    <p className="text-sm text-slate-300 leading-relaxed">{tip}</p>
                  </div>
                ))}
              </div>
            )}
            {/* Mistakes */}
            {roadmap.mistakesToAvoid.length > 0 && (
              <div className="space-y-4">
                <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">⚠️ Mistakes to Avoid</h3>
                {roadmap.mistakesToAvoid.map((m, i) => (
                  <div key={i} className="p-4 rounded-2xl bg-rose-500/5 border border-rose-500/20 flex gap-4">
                    <AlertCircle size={18} className="text-rose-400 flex-shrink-0 mt-0.5" />
                    <p className="text-sm text-slate-300 leading-relaxed">{m}</p>
                  </div>
                ))}
              </div>
            )}
            {roadmap.tips.length === 0 && roadmap.mistakesToAvoid.length === 0 && (
              <p className="text-slate-500 text-sm col-span-2 text-center py-12">No insights available for this plan.</p>
            )}
          </motion.div>
        )}

        {/* ── Tab: Resources ── */}
        {activeTab === 'resources' && (
          <motion.div key="resources" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
            {roadmap.resources.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {roadmap.resources.map((r, i) => (
                  <div key={i} className="p-5 rounded-2xl bg-white/5 border border-white/10 hover:border-purple-500/30 transition-all group">
                    <div className="flex items-start gap-3">
                      <BookOpen size={18} className="text-purple-400 flex-shrink-0 mt-0.5" />
                      <p className="text-sm text-slate-300 leading-relaxed group-hover:text-white transition-colors">{r}</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-slate-500 text-sm text-center py-12">No resources available for this plan.</p>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

export default RoadmapCard;
