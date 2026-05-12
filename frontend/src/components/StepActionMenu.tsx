import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ChevronDown, Expand, Code, BookOpen, HelpCircle,
  MessageSquare, Briefcase, Zap
} from 'lucide-react';

interface StepAction {
  id: string;
  label: string;
  icon: React.ReactNode;
  prompt: string;
}

const STEP_ACTIONS: StepAction[] = [
  { id: 'expand',    label: 'Expand Step',           icon: <Expand size={14} />,      prompt: 'expand step' },
  { id: 'project',   label: 'Generate Project',       icon: <Briefcase size={14} />,   prompt: 'generate a project for this step' },
  { id: 'explain',   label: 'Explain Concept',        icon: <BookOpen size={14} />,    prompt: 'explain the concept in this step' },
  { id: 'quiz',      label: 'Generate Quiz',          icon: <HelpCircle size={14} />,  prompt: 'generate a quiz for this step' },
  { id: 'interview', label: 'Interview Questions',    icon: <MessageSquare size={14} />, prompt: 'generate interview questions for this step' },
  { id: 'code',      label: 'Code Template',          icon: <Code size={14} />,        prompt: 'generate a code template for this step' },
];

interface StepActionMenuProps {
  stepIndex: number;
  stepTitle: string;
  onAction: (prompt: string) => void;
}

const StepActionMenu: React.FC<StepActionMenuProps> = ({ stepIndex, stepTitle, onAction }) => {
  const [open, setOpen] = useState(false);

  const handleAction = (action: StepAction) => {
    setOpen(false);
    onAction(`${action.prompt} ${stepIndex + 1}: "${stepTitle}"`);
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 px-2 py-1 text-xs text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-all"
      >
        <Zap size={12} />
        <span className="hidden group-hover:inline">Actions</span>
        <ChevronDown size={10} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      <AnimatePresence>
        {open && (
          <>
            {/* Backdrop */}
            <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />

            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: -5 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: -5 }}
              transition={{ duration: 0.1 }}
              className="absolute right-0 top-full mt-1 z-50 w-52 bg-[#0B1020] border border-white/10 rounded-xl shadow-2xl overflow-hidden"
            >
              <div className="p-1">
                {STEP_ACTIONS.map(action => (
                  <button
                    key={action.id}
                    onClick={() => handleAction(action)}
                    className="w-full flex items-center gap-3 px-3 py-2.5 text-xs text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-all text-left"
                  >
                    <span className="text-purple-400">{action.icon}</span>
                    {action.label}
                  </button>
                ))}
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

export default StepActionMenu;
