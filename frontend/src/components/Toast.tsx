import React, { useEffect, useState, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle2, AlertCircle, Info, X, AlertTriangle } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastMessage {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  duration?: number; // ms, default 4000
}

interface ToastProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

const ICONS: Record<ToastType, React.ReactNode> = {
  success: <CheckCircle2 size={16} className="text-emerald-400" />,
  error:   <AlertCircle  size={16} className="text-red-400" />,
  info:    <Info         size={16} className="text-blue-400" />,
  warning: <AlertTriangle size={16} className="text-amber-400" />,
};

const COLORS: Record<ToastType, string> = {
  success: 'border-emerald-500/30 bg-emerald-500/10',
  error:   'border-red-500/30 bg-red-500/10',
  info:    'border-blue-500/30 bg-blue-500/10',
  warning: 'border-amber-500/30 bg-amber-500/10',
};

const TEXT_COLORS: Record<ToastType, string> = {
  success: 'text-emerald-300',
  error:   'text-red-300',
  info:    'text-blue-300',
  warning: 'text-amber-300',
};

function ToastItem({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: string) => void }) {
  useEffect(() => {
    const duration = toast.duration ?? 4000;
    const timer = setTimeout(() => onDismiss(toast.id), duration);
    return () => clearTimeout(timer);
  }, [toast.id, toast.duration, onDismiss]);

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 60, scale: 0.95 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 60, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      className={`flex items-start gap-3 p-4 rounded-xl border backdrop-blur-xl shadow-2xl max-w-sm w-full ${COLORS[toast.type]}`}
    >
      <div className="flex-shrink-0 mt-0.5">{ICONS[toast.type]}</div>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-semibold ${TEXT_COLORS[toast.type]}`}>{toast.title}</p>
        {toast.message && (
          <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">{toast.message}</p>
        )}
      </div>
      <button
        onClick={() => onDismiss(toast.id)}
        className="flex-shrink-0 text-slate-500 hover:text-slate-300 transition-colors"
      >
        <X size={14} />
      </button>
    </motion.div>
  );
}

export const Toast: React.FC<ToastProps> = ({ toasts, onDismiss }) => (
  <div className="fixed bottom-6 right-6 z-[100] flex flex-col gap-2 pointer-events-none">
    <AnimatePresence mode="popLayout">
      {toasts.map(t => (
        <div key={t.id} className="pointer-events-auto">
          <ToastItem toast={t} onDismiss={onDismiss} />
        </div>
      ))}
    </AnimatePresence>
  </div>
);

// ─── Hook ─────────────────────────────────────────────────────────────────────

let toastCounter = 0;

export function useToast() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  const show = useCallback((
    type: ToastType,
    title: string,
    message?: string,
    duration?: number
  ) => {
    const id = `toast-${++toastCounter}`;
    setToasts(prev => [...prev.slice(-4), { id, type, title, message, duration }]);
    return id;
  }, []);

  const success = useCallback((title: string, message?: string) => show('success', title, message), [show]);
  const error   = useCallback((title: string, message?: string) => show('error', title, message, 6000), [show]);
  const info    = useCallback((title: string, message?: string) => show('info', title, message), [show]);
  const warning = useCallback((title: string, message?: string) => show('warning', title, message), [show]);

  return { toasts, dismiss, show, success, error, info, warning };
}
