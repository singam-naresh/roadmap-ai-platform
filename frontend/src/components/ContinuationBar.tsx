import React, { useState, useRef, useCallback, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Send, Loader2, Zap, BookOpen, Code2, HelpCircle, Briefcase, ChevronDown } from 'lucide-react';

interface QuickAction {
  label: string;
  icon: React.ReactNode;
  prompt: string;
}

const QUICK_ACTIONS: QuickAction[] = [
  { label: 'Expand a step',      icon: <Zap size={13} />,       prompt: 'Expand step 1 with more detail and sub-steps' },
  { label: 'Generate project',   icon: <Briefcase size={13} />, prompt: 'Generate a hands-on project idea for this roadmap' },
  { label: 'Interview prep',     icon: <HelpCircle size={13} />,prompt: 'Generate interview questions for this topic' },
  { label: 'Quiz me',            icon: <BookOpen size={13} />,  prompt: 'Generate a quiz to test my knowledge of this roadmap' },
  { label: 'Code template',      icon: <Code2 size={13} />,     prompt: 'Generate a starter code template for this roadmap' },
  { label: 'Simplify roadmap',   icon: <Zap size={13} />,       prompt: 'Simplify this roadmap for a complete beginner' },
  { label: 'Make weekly plan',   icon: <Briefcase size={13} />, prompt: 'Convert this roadmap into a week-by-week study plan' },
];

interface ContinuationBarProps {
  conversationId: number | null;
  taskId: number;
  intentType: string;
  isLoading: boolean;
  onSubmit: (prompt: string) => void;
  placeholder?: string;
}

const ContinuationBar: React.FC<ContinuationBarProps> = ({
  conversationId,
  taskId,
  intentType,
  isLoading,
  onSubmit,
  placeholder,
}) => {
  const [input, setInput] = useState('');
  const [showActions, setShowActions] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const actionsRef = useRef<HTMLDivElement>(null);

  // Close actions dropdown when clicking outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (actionsRef.current && !actionsRef.current.contains(e.target as Node)) {
        setShowActions(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleSubmit = useCallback(() => {
    const trimmed = input.trim();
    if (!trimmed || isLoading) return;
    onSubmit(trimmed);
    setInput('');
  }, [input, isLoading, onSubmit]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  }, [handleSubmit]);

  const handleQuickAction = useCallback((action: QuickAction) => {
    setShowActions(false);
    onSubmit(action.prompt);
  }, [onSubmit]);

  const defaultPlaceholder = placeholder ?? (
    intentType === 'ROADMAP' || intentType === 'STARTUP'
      ? 'Ask a follow-up: expand a step, generate a project, quiz me…'
      : intentType === 'LEARNING'
      ? 'Ask a follow-up: give me an example, quiz me, what\'s next…'
      : 'Continue the conversation…'
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="sticky bottom-0 pt-4 pb-2"
    >
      <div className="bg-[#0B1020]/95 backdrop-blur-xl border border-white/10 rounded-2xl overflow-visible shadow-2xl">
        {/* Context indicator */}
        {conversationId && (
          <div className="px-4 pt-2.5 pb-0 flex items-center gap-2">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-[10px] text-slate-500 font-medium">
              Session active · Context preserved · Conv #{conversationId}
            </span>
          </div>
        )}

        {/* Input row */}
        <div className="flex items-center gap-2 p-3">
          {/* Quick actions dropdown */}
          <div className="relative flex-shrink-0" ref={actionsRef}>
            <button
              onClick={() => setShowActions(v => !v)}
              disabled={isLoading}
              className="flex items-center gap-1 px-2.5 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-400 hover:text-white transition-all text-xs font-medium disabled:opacity-40"
              title="Quick actions"
            >
              <Zap size={13} className="text-purple-400" />
              <ChevronDown size={11} className={`transition-transform ${showActions ? 'rotate-180' : ''}`} />
            </button>

            {showActions && (
              <motion.div
                initial={{ opacity: 0, y: 4, scale: 0.97 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                className="absolute bottom-full left-0 mb-2 w-52 bg-[#0B1020] border border-white/10 rounded-xl shadow-2xl z-50 overflow-hidden"
              >
                <div className="p-1">
                  {QUICK_ACTIONS.map(action => (
                    <button
                      key={action.label}
                      onClick={() => handleQuickAction(action)}
                      className="w-full flex items-center gap-2.5 px-3 py-2.5 text-xs text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-all text-left"
                    >
                      <span className="text-purple-400 flex-shrink-0">{action.icon}</span>
                      {action.label}
                    </button>
                  ))}
                </div>
              </motion.div>
            )}
          </div>

          {/* Text input */}
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={defaultPlaceholder}
            disabled={isLoading}
            className="flex-1 bg-transparent text-sm text-white placeholder:text-slate-600 focus:outline-none disabled:opacity-50"
          />

          {/* Send button */}
          <button
            onClick={handleSubmit}
            disabled={!input.trim() || isLoading}
            className="flex items-center gap-1.5 px-4 py-2 bg-purple-600 hover:bg-purple-500 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-xl text-xs font-bold transition-all flex-shrink-0"
          >
            {isLoading ? (
              <Loader2 size={14} className="animate-spin" />
            ) : (
              <Send size={14} />
            )}
          </button>
        </div>
      </div>
    </motion.div>
  );
};

export default ContinuationBar;
