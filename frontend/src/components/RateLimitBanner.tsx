import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Clock, X } from 'lucide-react';

interface RateLimitBannerProps {
  message: string;
  retryAfterSeconds: number;
  remainingRequests?: number;
  onDismiss: () => void;
}

const RateLimitBanner: React.FC<RateLimitBannerProps> = ({ message, retryAfterSeconds, remainingRequests, onDismiss }) => {
  const [remaining, setRemaining] = useState(retryAfterSeconds);

  useEffect(() => {
    if (remaining <= 0) { onDismiss(); return; }
    const timer = setInterval(() => {
      setRemaining(r => {
        if (r <= 1) { clearInterval(timer); onDismiss(); return 0; }
        return r - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [remaining, onDismiss]);

  return (
    <motion.div
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className="w-full max-w-4xl mx-auto mb-4"
    >
      <div className="flex items-center gap-3 p-4 rounded-xl bg-orange-500/10 border border-orange-500/30">
        <Clock className="text-orange-400 flex-shrink-0" size={18} />
        <div className="flex-1">
          <p className="text-sm text-orange-300 font-medium">{message}</p>
          {remaining > 0 && (
            <p className="text-xs text-orange-400/70 mt-0.5">
              Ready in <span className="font-bold text-orange-300">{remaining}s</span>
              {remainingRequests !== undefined && remainingRequests >= 0 && (
                <span className="ml-2">· {remainingRequests} requests remaining</span>
              )}
            </p>
          )}
        </div>
        <button onClick={onDismiss} className="text-orange-400/50 hover:text-orange-400 transition-colors">
          <X size={16} />
        </button>
      </div>
    </motion.div>
  );
};

export default RateLimitBanner;
