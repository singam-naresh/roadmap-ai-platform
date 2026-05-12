import React, { useState, useMemo } from 'react';
import { motion } from 'framer-motion';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  History, Search, ChevronLeft, ChevronRight,
  LayoutDashboard, Plus, MessageSquare, Zap, Filter, Clock, LogOut,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import type { TaskResponse } from '../types';

interface SidebarProps {
  history: TaskResponse[];
  onSelectHistory: (task: TaskResponse) => void;
  onNewExecution: () => void;
  activeId?: number;
}

const CATEGORY_COLORS: Record<string, string> = {
  coding:       'bg-blue-500',
  career:       'bg-purple-500',
  learning:     'bg-emerald-500',
  fitness:      'bg-rose-500',
  business:     'bg-amber-500',
  content:      'bg-pink-500',
  productivity: 'bg-cyan-500',
  general:      'bg-slate-500',
};

function relativeTime(dateStr: string): string {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  if (diff < 60_000)     return 'just now';
  if (diff < 3_600_000)  return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  return `${Math.floor(diff / 86_400_000)}d ago`;
}

const Sidebar: React.FC<SidebarProps> = ({ history, onSelectHistory, onNewExecution, activeId }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const filtered = useMemo(() => {
    if (!searchQuery.trim()) return history;
    const q = searchQuery.toLowerCase();
    return history.filter(t => t.userInput?.toLowerCase().includes(q));
  }, [history, searchQuery]);

  const navItems = [
    { icon: LayoutDashboard, label: 'Dashboard', path: '/' },
    { icon: MessageSquare,   label: 'Chat',      path: '/chat' },
    { icon: History,         label: 'History',   path: '/history' },
  ];

  return (
    <motion.aside
      initial={false}
      animate={{ width: isCollapsed ? 80 : 320 }}
      className="relative h-screen bg-[#0B1020]/80 backdrop-blur-2xl border-r border-white/5 flex flex-col z-40 flex-shrink-0"
    >
      {/* Brand + collapse */}
      <div className="p-6 flex items-center justify-between">
        {!isCollapsed && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex items-center gap-3">
            <div className="w-8 h-8 bg-gradient-to-br from-purple-500 to-indigo-600 rounded-lg flex items-center justify-center shadow-lg shadow-purple-500/20">
              <Zap className="text-white w-5 h-5" />
            </div>
            <span className="font-bold text-xl tracking-tight text-white">Roadmap AI</span>
          </motion.div>
        )}
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="p-2 hover:bg-white/5 rounded-lg transition-colors text-slate-400"
        >
          {isCollapsed ? <ChevronRight size={20} /> : <ChevronLeft size={20} />}
        </button>
      </div>

      {/* New Roadmap */}
      <div className="px-4 mb-6">
        <button
          onClick={onNewExecution}
          className="w-full py-3 px-4 bg-purple-600 hover:bg-purple-500 text-white rounded-xl flex items-center gap-3 transition-all group shadow-lg shadow-purple-600/20"
        >
          <Plus className="group-hover:rotate-90 transition-transform" size={20} />
          {!isCollapsed && <span className="text-sm font-bold">New Roadmap</span>}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 space-y-8">
        {/* Navigation */}
        <section>
          {!isCollapsed && (
            <div className="flex items-center justify-between mb-4 px-2">
              <h3 className="text-[10px] uppercase tracking-[0.2em] text-slate-500 font-bold">Navigation</h3>
            </div>
          )}
          <div className="space-y-1">
            {navItems.map(item => {
              const isActive = location.pathname === item.path ||
                (item.path === '/' && location.pathname === '/dashboard');
              return (
                <button
                  key={item.label}
                  onClick={() => navigate(item.path)}
                  className={`w-full p-3 flex items-center gap-3 rounded-xl transition-all group ${
                    isActive
                      ? 'bg-purple-500/10 text-purple-400 border border-purple-500/20'
                      : 'text-slate-400 hover:text-white hover:bg-white/5 border border-transparent'
                  }`}
                >
                  <item.icon size={18} className={isActive ? 'text-purple-400' : 'group-hover:text-purple-400 transition-colors'} />
                  {!isCollapsed && <span className="text-xs font-bold uppercase tracking-wider">{item.label}</span>}
                </button>
              );
            })}
          </div>
        </section>

        {/* Recent history */}
        <section>
          {!isCollapsed && (
            <div className="space-y-4 mb-4 px-2">
              <div className="flex items-center justify-between">
                <h3 className="text-[10px] uppercase tracking-[0.2em] text-slate-500 font-bold">
                  Recent {history.length > 0 && `(${history.length})`}
                </h3>
                <Filter size={14} className="text-slate-500 cursor-pointer hover:text-white" />
              </div>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={14} />
                <input
                  type="text"
                  placeholder="Search history"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="w-full bg-white/5 border border-white/5 rounded-lg py-2 pl-9 pr-4 text-xs text-white placeholder:text-slate-600 focus:outline-none focus:border-purple-500/50 transition-all"
                />
              </div>
            </div>
          )}

          <div className="space-y-2">
            {filtered.length === 0 && !isCollapsed && (
              <p className="text-[10px] text-slate-600 text-center py-4 uppercase tracking-widest">
                {history.length === 0 ? 'No roadmaps yet' : 'No results'}
              </p>
            )}
            {filtered.map(task => {
              const catColor = CATEGORY_COLORS[task.category?.toLowerCase()] ?? 'bg-slate-500';
              const isActive = task.id === activeId;
              return (
                <button
                  key={task.id}
                  onClick={() => onSelectHistory(task)}
                  className={`w-full p-3 flex flex-col gap-2 text-left rounded-xl transition-all group border ${
                    isActive
                      ? 'bg-purple-500/10 border-purple-500/30'
                      : 'border-transparent hover:bg-white/5 hover:border-white/5'
                  }`}
                >
                  <div className="flex items-center gap-2 overflow-hidden">
                    <div className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${catColor}`} />
                    {!isCollapsed && (
                      <span className="text-xs font-bold text-slate-300 truncate uppercase tracking-tight">
                        {task.userInput}
                      </span>
                    )}
                  </div>
                  {!isCollapsed && (
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Clock size={10} className="text-slate-600" />
                        <span className="text-[10px] text-slate-500 font-medium">{relativeTime(task.createdAt)}</span>
                      </div>
                      <span className="text-[10px] px-1.5 py-0.5 bg-white/5 rounded text-slate-400 font-bold capitalize">
                        {task.category ?? 'general'}
                      </span>
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </section>
      </div>

      {/* Footer */}
      <div className="p-4 border-t border-white/5 space-y-2">
        <div className="flex items-center gap-3 p-2 rounded-xl hover:bg-white/5 transition-colors cursor-pointer group">
          <div className="relative">
            <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-purple-600 to-blue-600 border border-white/10 flex items-center justify-center">
              <span className="text-white font-bold text-sm">
                {user?.firstName?.[0]}{user?.lastName?.[0]}
              </span>
            </div>
            <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-emerald-500 border-2 border-[#0B1020] rounded-full" />
          </div>
          {!isCollapsed && (
            <div className="flex flex-col flex-1">
              <span className="text-sm font-bold text-white group-hover:text-purple-400 transition-colors">
                {user?.firstName} {user?.lastName}
              </span>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                {user?.role?.toLowerCase() === 'admin' ? 'Admin' : 'Pro User'}
              </span>
            </div>
          )}
        </div>
        <button
          onClick={logout}
          className="w-full flex items-center gap-3 p-2 rounded-xl hover:bg-red-500/10 border border-transparent hover:border-red-500/20 transition-all group text-slate-400 hover:text-red-400"
        >
          <LogOut size={18} className="group-hover:text-red-400 transition-colors" />
          {!isCollapsed && <span className="text-xs font-bold uppercase tracking-wider">Logout</span>}
        </button>
      </div>
    </motion.aside>
  );
};

export default Sidebar;