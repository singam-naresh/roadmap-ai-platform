import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Search, Filter, Clock, Activity, ChevronDown, ChevronUp } from 'lucide-react';
import { getAllTasks, searchTasks } from '../services/api';
import type { TaskResponse } from '../types';
import GlowBackground from '../components/GlowBackground';

const CATEGORY_COLORS: Record<string, string> = {
  coding: 'bg-blue-500',
  career: 'bg-purple-500',
  learning: 'bg-emerald-500',
  fitness: 'bg-rose-500',
  business: 'bg-amber-500',
  content: 'bg-pink-500',
  productivity: 'bg-cyan-500',
  general: 'bg-slate-500',
};

const INTENT_LABELS: Record<string, string> = {
  ROADMAP: 'Roadmap',
  CODING: 'Code',
  ANALYSIS: 'Analysis',
  LEARNING: 'Learning',
  PRODUCTIVITY: 'Productivity',
  STARTUP: 'Startup',
  CHAT: 'Chat',
};

function relativeTime(dateStr: string): string {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  if (diff < 60_000) return 'just now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  return `${Math.floor(diff / 86_400_000)}d ago`;
}

const ExecutionHistory: React.FC = () => {
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [filteredTasks, setFilteredTasks] = useState<TaskResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIntent, setSelectedIntent] = useState<string>('');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [sortBy, setSortBy] = useState<'date' | 'intent' | 'category'>('date');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [expandedTask, setExpandedTask] = useState<number | null>(null);

  useEffect(() => {
    loadTasks();
  }, []);

  useEffect(() => {
    filterAndSortTasks();
  }, [tasks, searchQuery, selectedIntent, selectedCategory, sortBy, sortOrder]);

  const loadTasks = async () => {
    try {
      setIsLoading(true);
      const allTasks = await getAllTasks();
      setTasks(allTasks);
    } catch (error) {
      console.error('Failed to load tasks:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const filterAndSortTasks = () => {
    let filtered = [...tasks];

    // Apply search filter
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      filtered = filtered.filter(task => 
        task.userInput?.toLowerCase().includes(query) ||
        task.summary?.toLowerCase().includes(query)
      );
    }

    // Apply intent filter
    if (selectedIntent) {
      filtered = filtered.filter(task => task.intentType === selectedIntent);
    }

    // Apply category filter
    if (selectedCategory) {
      filtered = filtered.filter(task => task.category === selectedCategory);
    }

    // Apply sorting
    filtered.sort((a, b) => {
      let comparison = 0;
      
      switch (sortBy) {
        case 'date':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
        case 'intent':
          comparison = (a.intentType || '').localeCompare(b.intentType || '');
          break;
        case 'category':
          comparison = (a.category || '').localeCompare(b.category || '');
          break;
      }

      return sortOrder === 'desc' ? -comparison : comparison;
    });

    setFilteredTasks(filtered);
  };

  const uniqueIntents = [...new Set(tasks.map(t => t.intentType).filter(Boolean))];
  const uniqueCategories = [...new Set(tasks.map(t => t.category).filter(Boolean))];

  const toggleSort = (newSortBy: 'date' | 'intent' | 'category') => {
    if (sortBy === newSortBy) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(newSortBy);
      setSortOrder('desc');
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#050816] flex items-center justify-center">
        <div className="flex items-center gap-3 text-slate-400">
          <div className="w-8 h-8 border-2 border-purple-500 border-t-transparent rounded-full animate-spin"></div>
          <span className="text-lg font-medium">Loading history...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#050816] text-slate-200">
      <GlowBackground />
      
      <div className="relative z-10 max-w-6xl mx-auto px-8 py-12">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-white mb-2">Roadmap History</h1>
          <p className="text-slate-400">
            View and manage all your AI-generated roadmaps and responses
          </p>
        </div>

        {/* Filters and Search */}
        <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 mb-8">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
              <input
                type="text"
                placeholder="Search executions..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-10 pr-4 py-3 bg-white/5 border border-white/10 rounded-xl text-white placeholder:text-slate-500 focus:outline-none focus:border-purple-500/50 transition-all"
              />
            </div>

            {/* Intent Filter */}
            <select
              value={selectedIntent}
              onChange={(e) => setSelectedIntent(e.target.value)}
              className="px-4 py-3 bg-white/5 border border-white/10 rounded-xl text-white focus:outline-none focus:border-purple-500/50 transition-all"
            >
              <option value="">All Intents</option>
              {uniqueIntents.map(intent => (
                <option key={intent} value={intent}>
                  {INTENT_LABELS[intent] || intent}
                </option>
              ))}
            </select>

            {/* Category Filter */}
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="px-4 py-3 bg-white/5 border border-white/10 rounded-xl text-white focus:outline-none focus:border-purple-500/50 transition-all"
            >
              <option value="">All Categories</option>
              {uniqueCategories.map(category => (
                <option key={category} value={category}>
                  {category.charAt(0).toUpperCase() + category.slice(1)}
                </option>
              ))}
            </select>

            {/* Sort */}
            <div className="flex gap-2">
              <button
                onClick={() => toggleSort('date')}
                className={`flex-1 px-4 py-3 rounded-xl font-medium transition-all flex items-center justify-center gap-2 ${
                  sortBy === 'date' ? 'bg-purple-600 text-white' : 'bg-white/5 text-slate-400 hover:text-white'
                }`}
              >
                Date {sortBy === 'date' && (sortOrder === 'desc' ? <ChevronDown size={16} /> : <ChevronUp size={16} />)}
              </button>
            </div>
          </div>
        </div>

        {/* Results */}
        <div className="space-y-4">
          {filteredTasks.length === 0 ? (
            <div className="text-center py-12">
              <Activity className="mx-auto mb-4 text-slate-600" size={48} />
              <h3 className="text-xl font-bold text-slate-400 mb-2">
                {tasks.length === 0 ? 'No executions yet' : 'No results found'}
              </h3>
              <p className="text-slate-500">
                {tasks.length === 0 
                  ? 'Start by creating your first AI execution from the dashboard'
                  : 'Try adjusting your search criteria'
                }
              </p>
            </div>
          ) : (
            filteredTasks.map((task) => {
              const catColor = CATEGORY_COLORS[task.category?.toLowerCase()] ?? 'bg-slate-500';
              const isExpanded = expandedTask === task.id;
              
              return (
                <motion.div
                  key={task.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 hover:border-white/20 transition-all"
                >
                  <div className="flex items-start justify-between mb-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <div className={`w-3 h-3 rounded-full ${catColor}`} />
                        <span className="text-sm font-bold text-purple-400 uppercase tracking-wider">
                          {INTENT_LABELS[task.intentType] || task.intentType}
                        </span>
                        <span className="text-xs text-slate-500 px-2 py-1 bg-white/5 rounded-full">
                          {task.category}
                        </span>
                      </div>
                      <h3 className="text-lg font-bold text-white mb-2 line-clamp-2">
                        {task.userInput}
                      </h3>
                      {task.summary && (
                        <p className="text-slate-400 text-sm line-clamp-2">
                          {task.summary}
                        </p>
                      )}
                    </div>
                    <div className="flex items-center gap-3 text-slate-500">
                      <Clock size={16} />
                      <span className="text-sm">{relativeTime(task.createdAt)}</span>
                    </div>
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4 text-xs text-slate-500">
                      <span>Mode: {task.mode || 'default'}</span>
                      {task.difficulty && <span>Difficulty: {task.difficulty}</span>}
                      {task.estimatedTime && <span>Est. Time: {task.estimatedTime}</span>}
                    </div>
                    <button
                      onClick={() => setExpandedTask(isExpanded ? null : task.id)}
                      className="px-4 py-2 bg-white/5 hover:bg-white/10 rounded-lg text-sm font-medium transition-all flex items-center gap-2"
                    >
                      {isExpanded ? 'Collapse' : 'View Details'}
                      {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                  </div>

                  {isExpanded && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      className="mt-6 pt-6 border-t border-white/10"
                    >
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        {task.steps && task.steps.length > 0 && (
                          <div>
                            <h4 className="font-bold text-white mb-3">Steps ({task.steps.length})</h4>
                            <div className="space-y-2">
                              {task.steps.slice(0, 5).map((step, idx) => (
                                <div key={idx} className="flex items-start gap-3 text-sm">
                                  <div className="w-6 h-6 bg-purple-600/20 rounded-full flex items-center justify-center text-purple-400 font-bold text-xs flex-shrink-0 mt-0.5">
                                    {idx + 1}
                                  </div>
                                  <span className="text-slate-300">{step}</span>
                                </div>
                              ))}
                              {task.steps.length > 5 && (
                                <p className="text-xs text-slate-500 ml-9">
                                  +{task.steps.length - 5} more steps...
                                </p>
                              )}
                            </div>
                          </div>
                        )}

                        {task.keyPoints && task.keyPoints.length > 0 && (
                          <div>
                            <h4 className="font-bold text-white mb-3">Key Points</h4>
                            <div className="space-y-2">
                              {task.keyPoints.slice(0, 4).map((point, idx) => (
                                <div key={idx} className="flex items-start gap-2 text-sm">
                                  <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full flex-shrink-0 mt-2" />
                                  <span className="text-slate-300">{point}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </motion.div>
              );
            })
          )}
        </div>

        {/* Stats */}
        <div className="mt-12 grid grid-cols-1 md:grid-cols-4 gap-6">
          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 text-center">
            <div className="text-2xl font-bold text-white mb-1">{tasks.length}</div>
            <div className="text-sm text-slate-400">Total Roadmaps</div>
          </div>
          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 text-center">
            <div className="text-2xl font-bold text-white mb-1">{uniqueIntents.length}</div>
            <div className="text-sm text-slate-400">Intent Types</div>
          </div>
          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 text-center">
            <div className="text-2xl font-bold text-white mb-1">{uniqueCategories.length}</div>
            <div className="text-sm text-slate-400">Categories</div>
          </div>
          <div className="bg-white/5 backdrop-blur-xl border border-white/10 rounded-2xl p-6 text-center">
            <div className="text-2xl font-bold text-white mb-1">{filteredTasks.length}</div>
            <div className="text-sm text-slate-400">Filtered Results</div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExecutionHistory;