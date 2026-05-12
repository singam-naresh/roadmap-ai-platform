import React from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, Clock, Zap, X } from 'lucide-react';
import type { FeasibilityResult } from '../types';

interface FeasibilityWarningProps {
  result: FeasibilityResult;
  onAcceleratedPlan: () => void;
  onDismiss: () => void;
}

const FeasibilityWarning: React.FC<FeasibilityWarningProps> = ({
  result,
  onAcceleratedPlan,
  onDismiss,
}) => {
  if (result.feasible) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className="w-full max-w-4xl mx-auto mb-6"
    >
      <div className="p-5 rounded-2xl bg-amber-500/10 border border-amber-500/30 space-y-4">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-amber-500/20">
              <AlertTriangle className="text-amber-400" size={20} />
            </div>
            <div>
              <h3 className="text-sm font-bold text-amber-300">Unrealistic Timeline Detected</h3>
              <p className="text-xs text-amber-400/70 mt-0.5">
                Minimum realistic estimate:{' '}
                <span className="font-bold text-amber-300">{result.minimumRealisticEstimate}</span>
              </p>
            </div>
          </div>
          <button
            onClick={onDismiss}
            className="text-amber-400/50 hover:text-amber-400 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {/* Explanation */}
        {result.explanation && (
          <p className="text-sm text-amber-200/80 leading-relaxed whitespace-pre-line">
            {result.explanation}
          </p>
        )}

        {/* Actions */}
        <div className="flex items-center gap-3 pt-1">
          <button
            onClick={onAcceleratedPlan}
            className="flex items-center gap-2 px-4 py-2 bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/30 text-amber-300 rounded-xl text-xs font-bold transition-all"
          >
            <Zap size={14} />
            Generate Accelerated Plan
          </button>
          <div className="flex items-center gap-2 text-xs text-amber-400/60">
            <Clock size={12} />
            <span>Minimum: {result.minimumRealisticEstimate}</span>
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default FeasibilityWarning;
