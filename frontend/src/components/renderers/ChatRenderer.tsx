import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { MessageSquare, Copy, Check, Sparkles } from 'lucide-react';
import type { TaskResponse } from '../../types';

interface ChatRendererProps {
  response: TaskResponse;
  onSuggestionClick?: (text: string) => void;
}

const ChatRenderer: React.FC<ChatRendererProps> = ({ response, onSuggestionClick }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(response.message ?? response.aiOutput ?? '').then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const message = response.message ?? response.aiOutput ?? '';

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      className="w-full max-w-3xl mx-auto space-y-6"
    >
      {/* User message bubble */}
      <div className="flex justify-end">
        <div className="max-w-[80%] px-5 py-3 rounded-2xl rounded-tr-sm bg-purple-600/20 border border-purple-500/30 text-white text-sm leading-relaxed">
          {response.userInput}
        </div>
      </div>

      {/* AI response bubble */}
      <div className="flex items-start gap-3">
        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-purple-500 to-indigo-600 flex items-center justify-center flex-shrink-0 mt-1 shadow-lg shadow-purple-500/20">
          <Sparkles size={14} className="text-white" />
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-[10px] font-bold text-purple-400 uppercase tracking-widest">Roadmap AI</span>
            <span className="text-[10px] text-slate-600">·</span>
            <span className="text-[10px] text-slate-600 font-mono">llama-3.3-70b</span>
          </div>

          <div className="relative group">
            <div className="px-5 py-4 rounded-2xl rounded-tl-sm bg-white/5 border border-white/10 text-slate-200 text-sm leading-relaxed whitespace-pre-wrap">
              {message}
            </div>

            {/* Copy button */}
            <button
              onClick={handleCopy}
              className="absolute top-3 right-3 p-1.5 rounded-lg bg-white/5 border border-white/10 text-slate-500 hover:text-white opacity-0 group-hover:opacity-100 transition-all"
              title="Copy response"
            >
              {copied ? <Check size={12} className="text-emerald-400" /> : <Copy size={12} />}
            </button>
          </div>

          {/* Follow-up suggestions */}
          {response.suggestions && response.suggestions.length > 0 && (
            <div className="mt-4 space-y-2">
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Try asking:</p>
              <div className="flex flex-wrap gap-2">
                {response.suggestions.map((s, i) => (
                  <button
                    key={i}
                    onClick={() => onSuggestionClick?.(s)}
                    className="px-3 py-1.5 rounded-xl bg-white/5 border border-white/10 text-xs text-slate-300 hover:text-white hover:border-purple-500/40 hover:bg-purple-500/10 transition-all text-left"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
};

export default ChatRenderer;
