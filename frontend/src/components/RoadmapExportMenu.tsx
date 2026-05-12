import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Download, Copy, Check, ChevronDown, FileText, Code2, Share2 } from 'lucide-react';

interface RoadmapExportMenuProps {
  roadmapId: number;
  taskId: number;
  title: string;
}

const API_BASE = (import.meta as any).env?.VITE_API_BASE_URL ?? '';

const RoadmapExportMenu: React.FC<RoadmapExportMenuProps> = ({ roadmapId, taskId, title }) => {
  const [open, setOpen]       = useState(false);
  const [copied, setCopied]   = useState(false);
  const [loading, setLoading] = useState<string | null>(null);

  const downloadExport = async (format: 'markdown' | 'json') => {
    setLoading(format);
    setOpen(false);
    try {
      const token = localStorage.getItem('aura_access_token') ?? '';
      const res = await fetch(`${API_BASE}/api/roadmaps/${roadmapId}/export?format=${format}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('Export failed');
      const blob = await res.blob();
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href     = url;
      a.download = `roadmap-${roadmapId}.${format === 'markdown' ? 'md' : 'json'}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('[export]', err);
    } finally {
      setLoading(null);
    }
  };

  const copyLink = async () => {
    const url = `${window.location.origin}/?task=${taskId}`;
    await navigator.clipboard.writeText(url).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg transition-all"
      >
        {loading ? (
          <div className="w-3 h-3 border border-slate-400 border-t-transparent rounded-full animate-spin" />
        ) : (
          <Download size={13} />
        )}
        Export
        <ChevronDown size={10} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      <AnimatePresence>
        {open && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: -4 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: -4 }}
              transition={{ duration: 0.1 }}
              className="absolute right-0 top-full mt-1 z-50 w-48 bg-[#0B1020] border border-white/10 rounded-xl shadow-2xl overflow-hidden"
            >
              <div className="p-1">
                <button
                  onClick={() => downloadExport('markdown')}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-xs text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-all"
                >
                  <FileText size={14} className="text-purple-400" />
                  Download Markdown
                </button>
                <button
                  onClick={() => downloadExport('json')}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-xs text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-all"
                >
                  <Code2 size={14} className="text-blue-400" />
                  Download JSON
                </button>
                <div className="my-1 border-t border-white/5" />
                <button
                  onClick={copyLink}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-xs text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-all"
                >
                  {copied ? (
                    <Check size={14} className="text-green-400" />
                  ) : (
                    <Share2 size={14} className="text-emerald-400" />
                  )}
                  {copied ? 'Link Copied!' : 'Copy Share Link'}
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

export default RoadmapExportMenu;
