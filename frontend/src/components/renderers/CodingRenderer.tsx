import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Copy, Check, Code2, Lightbulb, AlertCircle, BookOpen, HelpCircle } from 'lucide-react';
import type { TaskResponse, CodeBlock } from '../../types';

interface CodingRendererProps {
  response: TaskResponse;
  onRefine?: (mode: 'default' | 'detailed' | 'simplified') => void;
  onSuggestionClick?: (text: string) => void;
}

const LANG_COLORS: Record<string, string> = {
  java:       'text-orange-400',
  python:     'text-blue-400',
  javascript: 'text-yellow-400',
  typescript: 'text-blue-300',
  sql:        'text-emerald-400',
  bash:       'text-slate-300',
  json:       'text-purple-400',
  html:       'text-rose-400',
  css:        'text-cyan-400',
  default:    'text-slate-400',
};

function CodeBlockCard({ block }: { block: CodeBlock }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(block.code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  const langColor = LANG_COLORS[block.language?.toLowerCase()] ?? LANG_COLORS.default;

  return (
    <div className="rounded-2xl overflow-hidden border border-white/10 bg-[#0B1020]">
      {/* Header bar */}
      <div className="flex items-center justify-between px-4 py-2.5 bg-white/5 border-b border-white/5">
        <div className="flex items-center gap-2">
          <Code2 size={14} className="text-slate-500" />
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">{block.label}</span>
          {block.language && (
            <span className={`text-[10px] font-bold uppercase tracking-wider ${langColor}`}>
              {block.language}
            </span>
          )}
        </div>
        <button
          onClick={handleCopy}
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/5 border border-white/10 text-[10px] font-bold text-slate-400 hover:text-white transition-all"
        >
          {copied ? <><Check size={11} className="text-emerald-400" /> Copied</> : <><Copy size={11} /> Copy</>}
        </button>
      </div>
      {/* Code */}
      <pre className="p-5 overflow-x-auto text-sm text-slate-200 font-mono leading-relaxed whitespace-pre">
        <code>{block.code}</code>
      </pre>
    </div>
  );
}

const CodingRenderer: React.FC<CodingRendererProps> = ({ response, onRefine, onSuggestionClick }) => {
  // If the AI returned clarification questions, show them instead of code
  const hasClarifications = response.clarificationQuestions && response.clarificationQuestions.length > 0;
  const hasCode = response.codeBlocks && response.codeBlocks.length > 0;

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="w-full space-y-8">

      {/* Header */}
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          <span className="px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-blue-400 text-[10px] font-bold uppercase tracking-widest">
            {response.language || 'Code'}
          </span>
          <span className="px-3 py-1 rounded-full bg-white/5 border border-white/10 text-slate-400 text-[10px] font-bold uppercase tracking-widest">
            Coding
          </span>
        </div>
        <h1 className="text-3xl md:text-4xl font-bold text-white tracking-tight leading-tight">
          {response.userInput}
        </h1>
        {response.summary && (
          <p className="text-slate-400 text-sm leading-relaxed max-w-2xl">{response.summary}</p>
        )}
      </div>

      {/* Refine bar */}
      {onRefine && !hasClarifications && (
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

      {/* ── Clarification mode: show questions instead of code ── */}
      {hasClarifications && (
        <div className="p-6 rounded-2xl bg-amber-500/5 border border-amber-500/20 space-y-5">
          <div className="flex items-center gap-3 text-amber-400">
            <HelpCircle size={20} />
            <h3 className="font-bold text-sm">I need a bit more context</h3>
          </div>
          <p className="text-slate-400 text-sm">{response.explanation || 'To give you the most accurate help, please answer these questions:'}</p>
          <ul className="space-y-3">
            {response.clarificationQuestions!.map((q, i) => (
              <li key={i} className="flex items-start gap-3">
                <span className="w-5 h-5 rounded-full bg-amber-500/20 text-amber-400 text-xs font-bold flex items-center justify-center flex-shrink-0 mt-0.5">{i + 1}</span>
                <span className="text-sm text-slate-200">{q}</span>
              </li>
            ))}
          </ul>
          {onSuggestionClick && (
            <p className="text-[10px] text-slate-500 pt-2 border-t border-white/5">
              Tip: Re-submit your question with the details above for a complete solution.
            </p>
          )}
        </div>
      )}

      {/* ── Normal code output ── */}
      {!hasClarifications && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Main: explanation + code blocks */}
          <div className="lg:col-span-2 space-y-6">
            {response.explanation && (
              <div className="p-5 rounded-2xl bg-white/5 border border-white/10">
                <h3 className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-3">Explanation</h3>
                <p className="text-sm text-slate-300 leading-relaxed">{response.explanation}</p>
              </div>
            )}

            {hasCode && (
              <div className="space-y-4">
                {response.codeBlocks!.map((block, i) => (
                  <CodeBlockCard key={i} block={block} />
                ))}
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div className="space-y-5">
            {response.keyPoints && response.keyPoints.length > 0 && (
              <div className="p-5 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
                <div className="flex items-center gap-2 mb-4 text-purple-400">
                  <Lightbulb size={16} />
                  <h4 className="text-[10px] font-bold uppercase tracking-widest">Key Points</h4>
                </div>
                <ul className="space-y-2">
                  {response.keyPoints.map((p, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                      <span className="text-purple-400 font-bold flex-shrink-0 mt-0.5">→</span>
                      {p}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {response.commonMistakes && response.commonMistakes.length > 0 && (
              <div className="p-5 rounded-2xl bg-rose-500/5 border border-rose-500/20">
                <div className="flex items-center gap-2 mb-4 text-rose-400">
                  <AlertCircle size={16} />
                  <h4 className="text-[10px] font-bold uppercase tracking-widest">Common Mistakes</h4>
                </div>
                <ul className="space-y-2">
                  {response.commonMistakes.map((m, i) => (
                    <li key={i} className="flex items-start gap-2 text-xs text-slate-300">
                      <span className="text-rose-400 flex-shrink-0 mt-0.5">⚠</span>
                      {m}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {response.resources && response.resources.length > 0 && (
              <div className="p-5 rounded-2xl bg-white/5 border border-white/10">
                <div className="flex items-center gap-2 mb-4 text-slate-400">
                  <BookOpen size={16} />
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
        </div>
      )}
    </motion.div>
  );
};

export default CodingRenderer;
