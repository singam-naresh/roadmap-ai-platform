import React, { useEffect, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, Code2, BookOpen, Map, Zap, Activity, RefreshCw } from 'lucide-react';
import { getAnalyticsSummary, type AnalyticsSummary } from '../services/api';

/**
 * DashboardAnalytics — fully real, DB-driven metrics.
 *
 * Fetches from GET /api/analytics/summary on mount and after each
 * new generation (via the `refreshTrigger` prop).
 * No hardcoded numbers. No fake chart heights.
 */
interface DashboardAnalyticsProps {
  refreshTrigger?: number; // increment to force a refresh after a new generation
}

const INTENT_ICONS: Record<string, React.ReactNode> = {
  ROADMAP:      <Map size={14} />,
  CODING:       <Code2 size={14} />,
  LEARNING:     <BookOpen size={14} />,
  PRODUCTIVITY: <Zap size={14} />,
  ANALYSIS:     <TrendingUp size={14} />,
  CHAT:         <Activity size={14} />,
  STARTUP:      <TrendingUp size={14} />,
};

const INTENT_COLORS: Record<string, string> = {
  ROADMAP:      'bg-purple-500/20 text-purple-400',
  CODING:       'bg-blue-500/20 text-blue-400',
  LEARNING:     'bg-emerald-500/20 text-emerald-400',
  PRODUCTIVITY: 'bg-cyan-500/20 text-cyan-400',
  ANALYSIS:     'bg-amber-500/20 text-amber-400',
  CHAT:         'bg-slate-500/20 text-slate-400',
  STARTUP:      'bg-rose-500/20 text-rose-400',
};

const DashboardAnalytics: React.FC<DashboardAnalyticsProps> = ({ refreshTrigger }) => {
  const [data, setData]       = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setError(null);
      const summary = await getAnalyticsSummary();
      setData(summary);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load analytics');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load, refreshTrigger]);

  if (loading) {
    return (
      <div className="space-y-8">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="h-28 rounded-2xl bg-white/5 border border-white/10 animate-pulse" />
          ))}
        </div>
        <div className="h-48 rounded-2xl bg-white/5 border border-white/10 animate-pulse" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-6 rounded-2xl bg-rose-500/5 border border-rose-500/20 flex items-center justify-between">
        <span className="text-rose-400 text-sm">{error ?? 'Analytics unavailable'}</span>
        <button onClick={load} className="flex items-center gap-2 text-[10px] font-bold text-rose-400 hover:text-rose-300 transition-colors">
          <RefreshCw size={12} /> Retry
        </button>
      </div>
    );
  }

  const stats = [
    {
      icon: TrendingUp,
      label: 'Total Generations',
      value: String(data.totalGenerations),
      color: 'text-emerald-400',
      trend: data.totalGenerations > 0 ? `+${data.thisWeekGenerations} this week` : 'Start generating',
    },
    {
      icon: Code2,
      label: 'Coding Sessions',
      value: String(data.codingCount),
      color: 'text-blue-400',
      trend: data.totalGenerations > 0
        ? `${Math.round((data.codingCount / data.totalGenerations) * 100)}% of total`
        : 'None yet',
    },
    {
      icon: BookOpen,
      label: 'Learning Sessions',
      value: String(data.learningCount),
      color: 'text-purple-400',
      trend: data.totalGenerations > 0
        ? `${Math.round((data.learningCount / data.totalGenerations) * 100)}% of total`
        : 'None yet',
    },
    {
      icon: Zap,
      label: 'Day Streak',
      value: String(data.currentStreak),
      color: 'text-amber-400',
      trend: data.currentStreak > 0 ? 'Keep it going!' : 'Start today',
    },
  ];

  // Build real chart heights from dailyActivity (last 30 days, show last 12)
  const chartData = data.dailyActivity.slice(-12);
  const maxCount  = Math.max(...chartData.map(d => d.count), 1);

  // Intent distribution for the breakdown bar
  const totalIntents = Object.values(data.intentDistribution).reduce((a, b) => a + b, 0);

  return (
    <div className="space-y-8">
      {/* Stat cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat, idx) => (
          <motion.div
            key={stat.label}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: idx * 0.08 }}
            className="group relative p-6 rounded-2xl bg-white/5 border border-white/10 hover:border-purple-500/30 hover:bg-white/[0.07] transition-all"
          >
            <div className="flex items-start justify-between mb-4">
              <div className={`p-2 rounded-xl bg-white/5 ${stat.color}`}>
                <stat.icon size={20} />
              </div>
              <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider text-right max-w-[110px] truncate">
                {stat.trend}
              </span>
            </div>
            <div className="space-y-1">
              <h3 className="text-2xl font-bold text-white">{stat.value}</h3>
              <p className="text-xs text-slate-500 font-medium uppercase tracking-tight">{stat.label}</p>
            </div>
            <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-transparent via-purple-500/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
          </motion.div>
        ))}
      </div>

      {/* Activity chart + Intent breakdown */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Real activity chart */}
        <div className="lg:col-span-2 p-6 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-3">
              <Activity className="text-purple-400" size={20} />
              <h3 className="font-bold text-white">Daily Activity</h3>
            </div>
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Last 12 days</span>
          </div>

          {data.totalGenerations === 0 ? (
            <div className="h-48 flex items-center justify-center">
              <p className="text-slate-600 text-sm font-medium">Generate your first response to see activity</p>
            </div>
          ) : (
            <div className="h-48 flex items-end gap-1.5">
              {chartData.map((day, i) => {
                const heightPct = maxCount > 0 ? Math.max((day.count / maxCount) * 100, day.count > 0 ? 8 : 2) : 2;
                const label = new Date(day.date).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
                return (
                  <motion.div
                    key={day.date}
                    initial={{ height: 0 }}
                    animate={{ height: `${heightPct}%` }}
                    transition={{ delay: i * 0.04, duration: 0.6 }}
                    className={`flex-1 rounded-t-sm transition-all cursor-pointer relative group min-h-[4px] ${
                      day.count > 0
                        ? 'bg-gradient-to-t from-purple-600/40 to-purple-500/80 hover:to-purple-400'
                        : 'bg-white/5'
                    }`}
                  >
                    {day.count > 0 && (
                      <div className="absolute -top-9 left-1/2 -translate-x-1/2 px-2 py-1 bg-white text-black text-[10px] font-bold rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap z-10">
                        {day.count} · {label}
                      </div>
                    )}
                  </motion.div>
                );
              })}
            </div>
          )}
        </div>

        {/* Intent distribution */}
        <div className="p-6 rounded-2xl bg-[#0B1020]/60 border border-white/10 backdrop-blur-xl flex flex-col gap-4">
          <div className="flex items-center gap-3">
            <Zap className="text-amber-400" size={20} />
            <h3 className="font-bold text-white">Intent Breakdown</h3>
          </div>

          {totalIntents === 0 ? (
            <p className="text-sm text-slate-500 leading-relaxed flex-1 flex items-center">
              Generate responses to see your usage patterns here.
            </p>
          ) : (
            <div className="space-y-2.5 flex-1">
              {Object.entries(data.intentDistribution)
                .sort(([, a], [, b]) => b - a)
                .map(([intent, count]) => {
                  const pct = Math.round((count / totalIntents) * 100);
                  const colorClass = INTENT_COLORS[intent] ?? 'bg-slate-500/20 text-slate-400';
                  return (
                    <div key={intent} className="space-y-1">
                      <div className="flex items-center justify-between">
                        <div className={`flex items-center gap-1.5 px-2 py-0.5 rounded-lg text-[10px] font-bold ${colorClass}`}>
                          {INTENT_ICONS[intent]}
                          {intent}
                        </div>
                        <span className="text-[10px] font-bold text-slate-400">{count} · {pct}%</span>
                      </div>
                      <div className="h-1.5 bg-white/5 rounded-full overflow-hidden">
                        <motion.div
                          initial={{ width: 0 }}
                          animate={{ width: `${pct}%` }}
                          transition={{ duration: 0.8, delay: 0.1 }}
                          className="h-full bg-purple-500/60 rounded-full"
                        />
                      </div>
                    </div>
                  );
                })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardAnalytics;
