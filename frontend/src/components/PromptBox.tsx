import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Send, Sparkles, Command, Zap, BookOpen, Briefcase, Target, GraduationCap, BrainCircuit } from 'lucide-react';

const PromptBox = ({ onGenerate, disabled = false }: { onGenerate: (prompt: string) => void; disabled?: boolean }) => {
  const [prompt, setPrompt] = useState('');
  const [isFocused, setIsFocused] = useState(false);

  const suggestions = [
    {
      icon: GraduationCap,
      label: "Beginner Java",
      color: "text-blue-400",
      cta: "Create my beginner Java roadmap",
      prompt: "I know absolutely nothing about Java or programming. Create a beginner roadmap starting from variables and loops.",
    },
    {
      icon: Briefcase,
      label: "Backend Engineer",
      color: "text-purple-400",
      cta: "Build my backend engineer path",
      prompt: "I know Java basics and OOP. Build a roadmap to become a backend engineer with Spring Boot, REST APIs, and databases.",
    },
    {
      icon: Target,
      label: "AI Engineer",
      color: "text-emerald-400",
      cta: "Build AI infra learning roadmap",
      prompt: "I know Python basics. Create a roadmap to become an AI/ML engineer with PyTorch, Hugging Face, and LLMs.",
    },
    {
      icon: BrainCircuit,
      label: "Distributed Systems",
      color: "text-amber-400",
      cta: "Generate advanced distributed systems roadmap",
      prompt: "I already know Spring Boot and system design basics. Generate an advanced distributed systems roadmap covering Kafka, scalability, and observability.",
    },
  ];

  return (
    <div className="w-full max-w-4xl mx-auto">
      <motion.div
        animate={{
          boxShadow: isFocused 
            ? "0 0 60px 5px rgba(124, 58, 237, 0.1)" 
            : "0 0 20px 0px rgba(0, 0, 0, 0.3)"
        }}
        className={`relative group rounded-3xl border transition-all duration-500 ${
          isFocused ? 'border-purple-500/50 bg-[#0B1020]' : 'border-white/10 bg-[#0B1020]/60'
        } backdrop-blur-2xl p-2`}
      >
        <div className="flex items-start gap-4 p-4">
          <div className={`mt-2 p-2.5 rounded-xl transition-all duration-500 ${isFocused ? 'bg-purple-500/20 text-purple-400 scale-110' : 'bg-white/5 text-slate-500'}`}>
            <Sparkles size={24} />
          </div>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey && prompt.trim()) {
                e.preventDefault();
                onGenerate(prompt);
              }
            }}
            placeholder="Describe your learning goal or skill level… e.g. 'I know nothing about Java, teach me from scratch' or 'I know Spring Boot, teach me distributed systems'"
            className="flex-1 bg-transparent border-none focus:ring-0 text-white placeholder:text-slate-600 resize-none py-2 text-lg min-h-[120px] font-medium"
            maxLength={1000}
          />
        </div>

        <div className="flex items-center justify-between p-3 border-t border-white/5">
          <div className="flex items-center gap-4 px-2">
            <div className="flex items-center gap-1.5 px-2 py-1 bg-white/5 rounded-md border border-white/5 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
              <Command size={12} />
              <span>K</span>
            </div>
            <div className="flex items-center gap-2 text-[10px] font-bold text-slate-600 uppercase tracking-widest">
              <BrainCircuit size={14} />
              <span>Adaptive AI</span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <span className="text-[10px] text-slate-600 font-mono font-bold">{prompt.length} / 1000</span>
            <button
              onClick={() => onGenerate(prompt)}
              disabled={!prompt.trim() || disabled}
              className={`flex items-center gap-2 px-8 py-3 rounded-xl font-bold text-xs uppercase tracking-widest transition-all ${
                prompt.trim() && !disabled
                  ? 'bg-purple-600 text-white shadow-lg shadow-purple-600/25 hover:scale-105 active:scale-95' 
                  : 'bg-white/5 text-slate-600 cursor-not-allowed'
              }`}
            >
              <span>{disabled ? 'Generating…' : 'Generate Roadmap'}</span>
              {disabled
                ? <div className="w-4 h-4 border-2 border-slate-600 border-t-transparent rounded-full animate-spin" />
                : <Zap size={16} />
              }
            </button>
          </div>
        </div>
      </motion.div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 mt-6">
        {suggestions.map((item, idx) => (
          <motion.button
            key={item.label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.1 }}
            onClick={() => setPrompt(item.prompt)}
            className="flex flex-col gap-2 p-4 rounded-2xl bg-white/5 border border-white/5 hover:bg-white/10 hover:border-purple-500/30 transition-all group text-left"
          >
            <div className="flex items-center gap-2">
              <div className={`p-2 rounded-lg bg-white/5 group-hover:bg-white/10 transition-colors ${item.color}`}>
                <item.icon size={16} />
              </div>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-tight">{item.label}</span>
            </div>
            <span className="text-xs text-slate-300 group-hover:text-white transition-colors leading-snug">
              {item.cta}
            </span>
          </motion.button>
        ))}
      </div>
    </div>
  );
};

export default PromptBox;