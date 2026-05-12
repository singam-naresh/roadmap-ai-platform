import React from 'react';
import { motion } from 'framer-motion';

const GlowBackground = () => {
  return (
    <div className="fixed inset-0 -z-10 overflow-hidden bg-[#050816]">
      <motion.div
        animate={{
          scale: [1, 1.2, 1],
          opacity: [0.3, 0.5, 0.3],
          x: [0, 100, 0],
          y: [0, 50, 0],
        }}
        transition={{
          duration: 20,
          repeat: Infinity,
          ease: "linear"
        }}
        className="absolute -top-[10%] -left-[10%] w-[70%] h-[70%] rounded-full bg-purple-600/20 blur-[120px]"
      />
      <motion.div
        animate={{
          scale: [1, 1.3, 1],
          opacity: [0.2, 0.4, 0.2],
          x: [0, -100, 0],
          y: [0, -50, 0],
        }}
        transition={{
          duration: 25,
          repeat: Infinity,
          ease: "linear"
        }}
        className="absolute -bottom-[10%] -right-[10%] w-[60%] h-[60%] rounded-full bg-indigo-600/20 blur-[120px]"
      />
      <div className="absolute inset-0 bg-noise opacity-20 mix-blend-overlay pointer-events-none" />
    </div>
  );
};

export default GlowBackground;