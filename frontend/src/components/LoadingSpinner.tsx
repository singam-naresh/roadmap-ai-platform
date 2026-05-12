import React from 'react';
import { motion } from 'framer-motion';
import { Sparkles } from 'lucide-react';

interface LoadingSpinnerProps {
  message?: string;
  size?: 'sm' | 'md' | 'lg';
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ 
  message = 'Loading...', 
  size = 'md' 
}) => {
  const sizeClasses = {
    sm: 'w-6 h-6',
    md: 'w-8 h-8', 
    lg: 'w-12 h-12'
  };

  const iconSizes = {
    sm: 16,
    md: 20,
    lg: 24
  };

  return (
    <div className="flex items-center gap-3 text-slate-400">
      <div className="relative">
        <div className={`${sizeClasses[size]} border-2 border-purple-500 border-t-transparent rounded-full animate-spin`}></div>
        <div className="absolute inset-0 flex items-center justify-center">
          <Sparkles className="text-purple-400 animate-pulse" size={iconSizes[size] / 2} />
        </div>
      </div>
      <span className="text-lg font-medium">{message}</span>
    </div>
  );
};

export default LoadingSpinner;