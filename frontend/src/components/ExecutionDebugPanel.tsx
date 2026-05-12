import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Activity, Clock, AlertTriangle, CheckCircle2, GitBranch,
  BarChart2, RefreshCw, ChevronDown, ChevronRight, Zap,
  Shield, Eye, Play, List, X
} from 'lucide-react';
import {
  getObservabilityDashboard,
  getPlaybackSequence,
  type TimelineEntry,
  type AuditRecord,
  type ExecutionMetrics,
  type FailureDiagnosticReport,
  type InspectionReport,
  type GraphVisualization,
  type PlaybackFrame,
} from '../services/api';

interface ExecutionDebugPanelProps {
  taskId: number;
  totalSteps: number;
  onClose: () => void;
}

type Tab = 'timeline' | 'audit' | 'metrics' | 'graph' | 'failures' | 'replay' | 'inspect';

const TAB_CONFIG: Array<{ id: Tab; label: string; icon: React.ReactNode }> = [
  { id: 'timeline',  label: 'Timeline',  icon: <Clock size={14} /> },
  { id: 'audit',     label: 'Audit Log', icon: <Shield size={14} /> },
  { id: 'metrics',   label: 'Metrics',   icon: <BarChart2 size={14} /> },
  { id: 'graph',     label: 'Dep Graph', icon: <GitBranch size={14} /> },
  { id: 'failures',  label: 'Failures',  icon: <AlertTriangle size={14} /> },
  { id: 'replay',    label: 'Replay',    icon: <Play size={14} /> },
  { id: 'inspect',   label: 'Inspector', icon: <Eye size={14} /> },
];

const SEVERITY_COLORS: Record<string, string> = {
  LOW:      'text-slate-400 bg-slate-500/10',
  MEDIUM:   'text-amber-400 bg-amber-500/10',
  HIGH:     'text-orange-400 bg-orange-500/10',
  CRITICAL: 'text-red-400 bg-red-500/10',
};

const STATE_COLORS: Record<string, string> = {
  COMPLETED:    'text-green-400',
  IN_PROGRESS:  'text-blue-400',
  BLOCKED:      'text-amber-400',
  FAILED:       'text-red-400',
  NOT_STARTED:  'text-slate-500',
  SKIPPED:      'text-slate-400',
  NEEDS_REVIEW: 'text-purple-400',
};

function relTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  if (diff < 60_000) return 'just now';
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  return `${Math.floor(diff / 86_400_000)}d ago`;
}

const ExecutionDebugPanel: React.FC<ExecutionDebugPanelProps> = ({ taskId, totalSteps, onClose }) => {
  const [activeTab, setActiveTab] = useState<Tab>('timeline');
  const [isLoading, setIsLoading] = useState(true);
  const [dashboard, setDashboard] = useState<Awaited<ReturnType<typeof getObservabilityDashboard>> | null>(null);
  const [playback, setPlayback] = useState<PlaybackFrame[]>([]);
  const [playbackFrame, setPlaybackFrame] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const [dash, pb] = await Promise.all([
        getObservabilityDashboard(taskId, totalSteps),
        getPlaybackSequence(taskId),
      ]);
      setDashboard(dash);
      setPlayback(pb);
    } catch (err) {
      console.error('[debug-panel] Failed to load:', err);
    } finally {
      setIsLoading(false);
    }
  }, [taskId, totalSteps]);

  useEffect(() => { load(); }, [load]);

  // Auto-advance playback
  useEffect(() => {
    if (!isPlaying || playback.length === 0) return;
    if (playbackFrame >= playback.length - 1) { setIsPlaying(false); return; }
    const timer = setTimeout(() => setPlaybackFrame(f => f + 1), 600);
    return () => clearTimeout(timer);
  }, [isPlaying, playbackFrame, playback.length]);

  if (isLoading) {
    return (
      <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center">
        <div className="flex items-center gap-3 text-slate-400">
          <div className="w-6 h-6 border-2 border-purple-500 border-t-transparent rounded-full animate-spin" />
          <span>Loading observability data...</span>
        </div>
      </div>
    );
  }

  if (!dashboard) return null;

  const healthColor = dashboard.failures.overallStatus === 'HEALTHY' ? 'text-green-400'
    : dashboard.failures.overallStatus === 'DEGRADED' ? 'text-amber-400' : 'text-red-400';

  return (
    <div className="fixed inset-0 z-50 bg-black/90 flex flex-col font-mono text-xs">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 bg-slate-900 border-b border-white/10">
        <div className="flex items-center gap-3">
          <Activity className="text-purple-400" size={16} />
          <span className="text-white font-bold text-sm">Execution Debug Panel</span>
          <span className="text-slate-500">task #{taskId}</span>
          <span className={`px-2 py-0.5 rounded text-xs font-bold ${healthColor} bg-white/5`}>
            {dashboard.failures.overallStatus}
          </span>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={load} className="text-slate-400 hover:text-white transition-colors">
            <RefreshCw size={14} />
          </button>
          <button onClick={onClose} className="text-slate-400 hover:text-white transition-colors">
            <X size={16} />
          </button>
        </div>
      </div>

      {/* Quick stats bar */}
      <div className="flex items-center gap-6 px-4 py-2 bg-slate-900/50 border-b border-white/5 text-slate-400">
        <span>Timeline: <span className="text-white">{dashboard.timeline.length}</span></span>
        <span>Audit: <span className="text-white">{dashboard.audit.length}</span></span>
        <span>Velocity: <span className="text-white">{dashboard.metrics.completionVelocity.toFixed(2)}</span>/day</span>
        <span>Consistency: <span className="text-white">{(dashboard.metrics.consistencyScore * 100).toFixed(0)}%</span></span>
        <span>Mutations: <span className="text-white">{dashboard.metrics.totalMutations}</span></span>
        {dashboard.inspection.hasIssues && (
          <span className="text-amber-400 flex items-center gap-1">
            <AlertTriangle size={12} /> Issues detected
          </span>
        )}
      </div>

      {/* Tabs */}
      <div className="flex border-b border-white/10 bg-slate-900/30">
        {TAB_CONFIG.map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-1.5 px-4 py-2.5 transition-colors ${
              activeTab === tab.id
                ? 'text-purple-400 border-b-2 border-purple-400 bg-purple-500/5'
                : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-4">
        {activeTab === 'timeline' && <TimelineTab entries={dashboard.timeline} />}
        {activeTab === 'audit'    && <AuditTab records={dashboard.audit} />}
        {activeTab === 'metrics'  && <MetricsTab metrics={dashboard.metrics} />}
        {activeTab === 'graph'    && <GraphTab graph={dashboard.graph} />}
        {activeTab === 'failures' && <FailuresTab report={dashboard.failures} />}
        {activeTab === 'replay'   && (
          <ReplayTab
            frames={playback}
            currentFrame={playbackFrame}
            isPlaying={isPlaying}
            onPlay={() => { setPlaybackFrame(0); setIsPlaying(true); }}
            onPause={() => setIsPlaying(false)}
            onStep={(f) => setPlaybackFrame(f)}
          />
        )}
        {activeTab === 'inspect'  && <InspectTab report={dashboard.inspection} />}
      </div>
    </div>
  );
};

// ─── Tab components ───────────────────────────────────────────────────────────

const TimelineTab: React.FC<{ entries: TimelineEntry[] }> = ({ entries }) => (
  <div className="space-y-1">
    {entries.length === 0 && <p className="text-slate-500">No timeline entries yet.</p>}
    {[...entries].reverse().map((e, i) => (
      <div key={e.id ?? i} className="flex items-start gap-3 p-2 rounded hover:bg-white/5">
        <span className="text-slate-600 w-20 flex-shrink-0">{relTime(e.occurredAt)}</span>
        <span className={`w-28 flex-shrink-0 ${
          e.type === 'STATE_TRANSITION' ? 'text-blue-400' :
          e.type === 'ROADMAP_MUTATION' ? 'text-amber-400' :
          e.type === 'DEPENDENCY_UNLOCK' ? 'text-green-400' :
          'text-slate-400'
        }`}>{e.type.replace('_', ' ')}</span>
        <span className="text-slate-500 w-8 flex-shrink-0">s{e.stepIndex}</span>
        <span className={STATE_COLORS[e.previousState ?? ''] ?? 'text-slate-500'}>{e.previousState ?? '—'}</span>
        <span className="text-slate-600 mx-1">→</span>
        <span className={STATE_COLORS[e.newState ?? ''] ?? 'text-slate-400'}>{e.newState ?? '—'}</span>
        {e.notes && <span className="text-slate-600 truncate ml-2">{e.notes}</span>}
        <span className="text-slate-700 ml-auto flex-shrink-0">{e.actor}</span>
      </div>
    ))}
  </div>
);

const AuditTab: React.FC<{ records: AuditRecord[] }> = ({ records }) => (
  <div className="space-y-1">
    {records.length === 0 && <p className="text-slate-500">No audit records yet.</p>}
    {[...records].reverse().map((r, i) => (
      <div key={r.sequence ?? i} className="flex items-start gap-3 p-2 rounded hover:bg-white/5">
        <span className="text-slate-700 w-10 flex-shrink-0">#{r.sequence}</span>
        <span className="text-slate-600 w-20 flex-shrink-0">{relTime(r.timestamp)}</span>
        <span className="text-purple-400 w-28 flex-shrink-0">{r.action.replace('_', ' ')}</span>
        <span className="text-slate-500 w-8 flex-shrink-0">s{r.stepIndex}</span>
        <span className="text-slate-500">{r.previousValue ?? '—'}</span>
        <span className="text-slate-600 mx-1">→</span>
        <span className="text-white">{r.newValue ?? '—'}</span>
        {r.reason && <span className="text-slate-600 truncate ml-2">{r.reason}</span>}
        <span className="text-slate-700 ml-auto flex-shrink-0">{r.actor}</span>
      </div>
    ))}
  </div>
);

const MetricsTab: React.FC<{ metrics: ExecutionMetrics }> = ({ metrics }) => (
  <div className="grid grid-cols-2 gap-4">
    <MetricCard label="Completion Velocity" value={`${metrics.completionVelocity.toFixed(2)} steps/day`} />
    <MetricCard label="Consistency Score" value={`${(metrics.consistencyScore * 100).toFixed(0)}%`} />
    <MetricCard label="Blocked Frequency" value={`${(metrics.blockedFrequency * 100).toFixed(1)}%`} />
    <MetricCard label="Total Mutations" value={String(metrics.totalMutations)} />
    <MetricCard label="Churn Rate" value={`${(metrics.roadmapChurnRate * 100).toFixed(1)}%`} />
    <MetricCard label="Retry Frequency" value={`${(metrics.retryFrequency * 100).toFixed(1)}%`} />

    {Object.keys(metrics.failureHotspots).length > 0 && (
      <div className="col-span-2 p-3 bg-red-500/5 border border-red-500/20 rounded">
        <p className="text-red-400 font-bold mb-2">Failure Hotspots</p>
        {Object.entries(metrics.failureHotspots).map(([step, count]) => (
          <div key={step} className="flex justify-between text-slate-400">
            <span>Step {step}</span>
            <span className="text-red-400">{count} failures</span>
          </div>
        ))}
      </div>
    )}

    {metrics.burnDownData.length > 0 && (
      <div className="col-span-2 p-3 bg-white/5 border border-white/10 rounded">
        <p className="text-slate-300 font-bold mb-2">Burn-Down</p>
        <div className="space-y-1">
          {metrics.burnDownData.slice(-10).map((pt, i) => (
            <div key={i} className="flex justify-between text-slate-500">
              <span>{relTime(pt.timestamp)}</span>
              <span>{pt.completed} done / {pt.remaining} remaining</span>
            </div>
          ))}
        </div>
      </div>
    )}
  </div>
);

const MetricCard: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="p-3 bg-white/5 border border-white/10 rounded">
    <p className="text-slate-500 mb-1">{label}</p>
    <p className="text-white font-bold text-sm">{value}</p>
  </div>
);

const GraphTab: React.FC<{ graph: GraphVisualization }> = ({ graph }) => (
  <div className="space-y-4">
    <div className="flex gap-4 text-slate-400">
      <span>Nodes: <span className="text-white">{graph.nodes.length}</span></span>
      <span>Edges: <span className="text-white">{graph.edges.length}</span></span>
      <span>Ready: <span className="text-green-400">{graph.readyCount}</span></span>
      <span>Blocked: <span className="text-amber-400">{graph.blockedCount}</span></span>
      <span>Done: <span className="text-blue-400">{graph.completedCount}</span></span>
    </div>

    <div className="space-y-1">
      {graph.nodes.map(node => (
        <div key={node.id} className="flex items-center gap-3 p-2 rounded hover:bg-white/5">
          <span className="text-slate-600 w-6">s{node.stepIndex}</span>
          <span className={`w-20 ${
            node.status === 'COMPLETED' ? 'text-green-400' :
            node.status === 'BLOCKED'   ? 'text-amber-400' :
            node.status === 'READY'     ? 'text-blue-400' :
            'text-slate-500'
          }`}>{node.status}</span>
          {node.onCriticalPath && <span className="text-purple-400 text-xs">CRITICAL</span>}
          {node.dependencies.length > 0 && (
            <span className="text-slate-600">deps: [{node.dependencies.join(', ')}]</span>
          )}
          <span className="text-slate-400 truncate">{node.title}</span>
        </div>
      ))}
    </div>
  </div>
);

const FailuresTab: React.FC<{ report: FailureDiagnosticReport }> = ({ report }) => (
  <div className="space-y-4">
    <div className="flex items-center gap-3">
      <span className="text-slate-400">Health Score:</span>
      <span className={`font-bold text-sm ${
        report.healthScore > 0.7 ? 'text-green-400' :
        report.healthScore > 0.4 ? 'text-amber-400' : 'text-red-400'
      }`}>{(report.healthScore * 100).toFixed(0)}%</span>
      <span className={`px-2 py-0.5 rounded text-xs font-bold ${
        report.overallStatus === 'HEALTHY' ? 'text-green-400 bg-green-500/10' :
        report.overallStatus === 'DEGRADED' ? 'text-amber-400 bg-amber-500/10' :
        'text-red-400 bg-red-500/10'
      }`}>{report.overallStatus}</span>
    </div>

    {report.issues.length === 0 && (
      <div className="flex items-center gap-2 text-green-400">
        <CheckCircle2 size={16} />
        <span>No issues detected</span>
      </div>
    )}

    {report.issues.map((issue, i) => (
      <div key={i} className={`p-3 rounded border ${
        issue.severity === 'HIGH' || issue.severity === 'CRITICAL'
          ? 'border-red-500/20 bg-red-500/5'
          : 'border-amber-500/20 bg-amber-500/5'
      }`}>
        <div className="flex items-center gap-2 mb-2">
          <span className={`px-1.5 py-0.5 rounded text-xs font-bold ${SEVERITY_COLORS[issue.severity]}`}>
            {issue.severity}
          </span>
          <span className="text-slate-300">{issue.type.replace(/_/g, ' ')}</span>
        </div>
        <p className="text-slate-400 mb-2">{issue.description}</p>
        <ul className="space-y-1">
          {issue.suggestions.map((s, j) => (
            <li key={j} className="text-slate-500 flex items-start gap-2">
              <span className="text-slate-700 mt-0.5">→</span>
              {s}
            </li>
          ))}
        </ul>
      </div>
    ))}
  </div>
);

const ReplayTab: React.FC<{
  frames: PlaybackFrame[];
  currentFrame: number;
  isPlaying: boolean;
  onPlay: () => void;
  onPause: () => void;
  onStep: (f: number) => void;
}> = ({ frames, currentFrame, isPlaying, onPlay, onPause, onStep }) => {
  const frame = frames[currentFrame];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <button
          onClick={isPlaying ? onPause : onPlay}
          className="flex items-center gap-2 px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white rounded transition-colors"
        >
          {isPlaying ? 'Pause' : 'Play'}
        </button>
        <span className="text-slate-400">Frame {currentFrame + 1} / {frames.length}</span>
        <input
          type="range" min={0} max={Math.max(0, frames.length - 1)}
          value={currentFrame}
          onChange={e => onStep(Number(e.target.value))}
          className="flex-1 accent-purple-500"
        />
      </div>

      {frame && (
        <div className="p-3 bg-white/5 border border-white/10 rounded space-y-2">
          <div className="flex items-center gap-3">
            <span className="text-slate-500">{relTime(frame.timestamp)}</span>
            <span className="text-purple-400">{frame.eventType}</span>
            <span className="text-slate-500">step {frame.stepIndex}</span>
            <span className={STATE_COLORS[frame.previousState] ?? 'text-slate-500'}>{frame.previousState}</span>
            <span className="text-slate-600">→</span>
            <span className={STATE_COLORS[frame.newState] ?? 'text-slate-400'}>{frame.newState}</span>
          </div>
          <div className="grid grid-cols-4 gap-1">
            {Object.entries(frame.allStatesAtFrame).map(([step, state]) => (
              <div key={step} className="flex items-center gap-1">
                <span className="text-slate-600">s{step}:</span>
                <span className={`${STATE_COLORS[state] ?? 'text-slate-500'} text-xs`}>{state}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

const InspectTab: React.FC<{ report: InspectionReport }> = ({ report }) => (
  <div className="space-y-4">
    <div className="flex gap-4 text-slate-400">
      <span>Events: <span className="text-white">{report.totalEvents}</span></span>
      <span>Timeline: <span className="text-white">{report.timelineLength}</span></span>
      {report.hasIssues && <span className="text-amber-400">⚠ Issues detected</span>}
    </div>

    {/* Event type distribution */}
    <div className="p-3 bg-white/5 border border-white/10 rounded">
      <p className="text-slate-300 font-bold mb-2">Event Distribution</p>
      {Object.entries(report.eventTypeCounts).map(([type, count]) => (
        <div key={type} className="flex justify-between text-slate-400">
          <span>{type}</span>
          <span className="text-white">{count}</span>
        </div>
      ))}
    </div>

    {report.storms.length > 0 && (
      <div className="p-3 bg-red-500/5 border border-red-500/20 rounded">
        <p className="text-red-400 font-bold mb-2">Event Storms ({report.storms.length})</p>
        {report.storms.map((s, i) => (
          <p key={i} className="text-slate-400">{s.description}</p>
        ))}
      </div>
    )}

    {report.loops.length > 0 && (
      <div className="p-3 bg-amber-500/5 border border-amber-500/20 rounded">
        <p className="text-amber-400 font-bold mb-2">Possible Loops ({report.loops.length})</p>
        {report.loops.map((l, i) => (
          <p key={i} className="text-slate-400">{l.description}</p>
        ))}
      </div>
    )}

    {report.invalidTransitions.length > 0 && (
      <div className="p-3 bg-orange-500/5 border border-orange-500/20 rounded">
        <p className="text-orange-400 font-bold mb-2">Invalid Transitions ({report.invalidTransitions.length})</p>
        {report.invalidTransitions.map((t, i) => (
          <p key={i} className="text-slate-400">
            Step {t.stepIndex}: {t.fromState} → {t.toState} — {t.reason}
          </p>
        ))}
      </div>
    )}

    {report.anomalies.length > 0 && (
      <div className="p-3 bg-purple-500/5 border border-purple-500/20 rounded">
        <p className="text-purple-400 font-bold mb-2">Anomalies ({report.anomalies.length})</p>
        {report.anomalies.map((a, i) => (
          <p key={i} className="text-slate-400">{a}</p>
        ))}
      </div>
    )}

    {!report.hasIssues && (
      <div className="flex items-center gap-2 text-green-400">
        <CheckCircle2 size={16} />
        <span>Event stream is clean — no storms, loops, or invalid transitions</span>
      </div>
    )}
  </div>
);

export default ExecutionDebugPanel;
