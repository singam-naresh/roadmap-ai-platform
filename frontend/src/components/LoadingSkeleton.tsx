import React from 'react';
import { motion } from 'framer-motion';

interface LoadingSkeletonProps {
  type?: 'roadmap' | 'card' | 'list' | 'text';
  count?: number;
  className?: string;
}

const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({ 
  type = 'card', 
  count = 1, 
  className = '' 
}) => {
  const skeletonVariants = {
    loading: {
      opacity: [0.4, 0.8, 0.4],
      transition: {
        duration: 1.5,
        repeat: Infinity,
        ease: "easeInOut"
      }
    }
  };

  const renderSkeleton = () => {
    switch (type) {
      case 'roadmap':
        return (
          <div className={`space-y-6 ${className}`}>
            {/* Header skeleton */}
            <div className="space-y-3">
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-8 bg-white/10 rounded-lg w-3/4"
              />
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/5 rounded w-full"
              />
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/5 rounded w-2/3"
              />
            </div>

            {/* Steps skeleton */}
            <div className="space-y-4">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="flex items-start gap-4 p-4 bg-white/5 rounded-xl">
                  <motion.div 
                    variants={skeletonVariants}
                    animate="loading"
                    className="w-8 h-8 bg-white/10 rounded-full flex-shrink-0"
                  />
                  <div className="flex-1 space-y-2">
                    <motion.div 
                      variants={skeletonVariants}
                      animate="loading"
                      className="h-5 bg-white/10 rounded w-full"
                    />
                    <motion.div 
                      variants={skeletonVariants}
                      animate="loading"
                      className="h-4 bg-white/5 rounded w-3/4"
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        );

      case 'card':
        return (
          <div className={`p-6 bg-white/5 rounded-xl space-y-4 ${className}`}>
            <motion.div 
              variants={skeletonVariants}
              animate="loading"
              className="h-6 bg-white/10 rounded w-3/4"
            />
            <div className="space-y-2">
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/5 rounded w-full"
              />
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/5 rounded w-5/6"
              />
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/5 rounded w-2/3"
              />
            </div>
            <div className="flex gap-2">
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-8 bg-white/10 rounded-lg w-20"
              />
              <motion.div 
                variants={skeletonVariants}
                animate="loading"
                className="h-8 bg-white/10 rounded-lg w-16"
              />
            </div>
          </div>
        );

      case 'list':
        return (
          <div className={`space-y-3 ${className}`}>
            {Array.from({ length: count }).map((_, i) => (
              <div key={i} className="flex items-center gap-3 p-3 bg-white/5 rounded-lg">
                <motion.div 
                  variants={skeletonVariants}
                  animate="loading"
                  className="w-4 h-4 bg-white/10 rounded"
                />
                <motion.div 
                  variants={skeletonVariants}
                  animate="loading"
                  className="h-4 bg-white/10 rounded flex-1"
                />
                <motion.div 
                  variants={skeletonVariants}
                  animate="loading"
                  className="w-16 h-4 bg-white/5 rounded"
                />
              </div>
            ))}
          </div>
        );

      case 'text':
        return (
          <div className={`space-y-2 ${className}`}>
            {Array.from({ length: count }).map((_, i) => (
              <motion.div 
                key={i}
                variants={skeletonVariants}
                animate="loading"
                className="h-4 bg-white/10 rounded"
                style={{ width: `${Math.random() * 40 + 60}%` }}
              />
            ))}
          </div>
        );

      default:
        return (
          <motion.div 
            variants={skeletonVariants}
            animate="loading"
            className={`h-20 bg-white/10 rounded-lg ${className}`}
          />
        );
    }
  };

  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i}>
          {renderSkeleton()}
        </div>
      ))}
    </>
  );
};

export default LoadingSkeleton;