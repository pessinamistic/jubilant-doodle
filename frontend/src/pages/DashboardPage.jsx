import { useCallback, useEffect, useState } from 'react'
import {
  Database,
  Layers,
  RefreshCw,
  Rocket,
  Server,
  Sparkles,
  TrendingUp,
  Zap,
} from 'lucide-react'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell,
  Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import {
  getDeploymentActivity,
  getInstances,
  getMetricsHistory,
  getStats,
  getSystemStats,
} from '../api/client'
import { AppShell } from '../components/AppShell'

const DB_TYPE_COLORS = ['#ff5f2e', '#1e58d4', '#107f43', '#a855f7', '#06b6d4', '#f59e0b', '#ec4899', '#22c55e']
const STATUS_COLORS  = {
  RUNNING:   '#22c55e',
  STOPPED:   '#f59e0b',
  DEPLOYING: '#3b82f6',
  ERROR:     '#ef4444',
  REMOVED:   '#6b7280',
  RESTARTING:'#a07000',
  UNTRACKED: '#0e7490',
  REMOVING:  '#b86600',
}

const GRID  = { stroke: 'rgba(0,0,0,0.08)', strokeDasharray: '3 3' }
const AXIS  = { tick: { fill: 'var(--text-quiet)', fontSize: 10 } }
const TOOLT = {
  contentStyle: {
    background: 'var(--bg-surface)',
    border: '2px solid var(--border-strong)',
    borderRadius: 4,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
  },
  labelStyle: { color: 'var(--text-muted)' },
  itemStyle:  { color: 'var(--text-primary)' },
}

export function DashboardPage() {
  const [stats,     setStats]    = useState(null)
  const [history,   setHistory]  = useState(null)
  const [activity,  setActivity] = useState(null)
  const [instances, setInstances]= useState([])
  const [counts,    setCounts]   = useState(null)
  const [loading,   setLoading]  = useState(true)
  const [error,     setError]    = useState(null)
  const [lastRefresh, setLastRefresh] = useState(null)

  const load = useCallback(async () => {
    try {
      const [s, h, a, inst, c] = await Promise.all([
        getSystemStats(),
        getMetricsHistory(),
        getDeploymentActivity(),
        getInstances(),
        getStats(),
      ])
      setStats(s); setHistory(h); setActivity(a)
      setInstances(inst); setCounts(c)
      setLastRefresh(new Date()); setError(null)
    } catch {
      setError('Failed to load dashboard metrics')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(load, 30_000)
    return () => clearInterval(t)
  }, [load])

  return (
    <AppShell onRefresh={load}>
      {/* Header */}
      <section className="mb-8 animate-fade-up">
        <div className="flex items-end justify-between flex-wrap gap-3">
          <div>
            <p className="section-label mb-1">Service Dashboard</p>
            <h1 className="text-3xl font-bold tracking-tight text-(--text-primary)">
              Everything Port Wrangler is doing
            </h1>
            <p className="text-sm text-(--text-muted) mt-1">
              Live JVM heap, pool usage, deployment history and instance distribution — refreshes every 30s.
            </p>
          </div>
          <button onClick={load} className="btn-secondary text-xs">
            <RefreshCw className="w-3.5 h-3.5" />
            Refresh now
          </button>
        </div>
        {lastRefresh && (
          <p className="text-[10px] text-(--text-quiet) mt-2 font-mono">
            Last updated {lastRefresh.toLocaleTimeString()}
          </p>
        )}
      </section>

      {loading && !stats ? (
        <div className="card p-10 flex items-center justify-center gap-2 text-(--text-muted) text-sm">
          <div className="w-4 h-4 border-2 border-(--accent) border-t-transparent rounded-full animate-spin" />
          Loading dashboard…
        </div>
      ) : error ? (
        <div className="card p-6 text-center text-(--status-error) text-sm">{error}</div>
      ) : (
        <DashboardBody
          stats={stats} history={history} activity={activity}
          instances={instances} counts={counts}
        />
      )}
    </AppShell>
  )
}

function DashboardBody({ stats, history, activity, instances, counts }) {
  const samples  = history?.samples ?? []
  const byDay    = activity?.deploymentsByDay ?? []
  const byType   = activity?.instancesByDbType ?? []
  const byStatus = activity?.instancesByStatus ?? []

  const totalDeployments = byDay.reduce((a, d) => a + (d.count ?? 0), 0)
  const peakDay   = byDay.reduce((m, d) => (d.count > (m?.count ?? -1) ? d : m), null)
  const avgPerDay = byDay.length ? (totalDeployments / byDay.length).toFixed(1) : '0'
  const activeTypes = byType.length
  const uniqueDeployments = byType.reduce((a, t) => a + (t.count ?? 0), 0)

  const heapPct = stats?.jvm ? Math.round((stats.jvm.heapUsedMb / stats.jvm.heapMaxMb) * 100) : 0
  const poolActive = stats?.pool?.activeConnections ?? 0
  const poolMax    = stats?.pool?.maxSize ?? 1
  const poolPct    = Math.round((poolActive / poolMax) * 100)

  const fmtTime = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const fmtDate = (d)   => new Date(d + 'T12:00:00').toLocaleDateString([], { month: 'short', day: 'numeric' })

  const summary = [
    { label: 'Tracked Instances',  value: counts?.total ?? instances.length, sub: `${counts?.running ?? 0} running`,    icon: <Database className="w-5 h-5" />,  color: 'text-[var(--status-deploying)]' },
    { label: 'Deployments (30d)',  value: totalDeployments,                  sub: `${avgPerDay}/day avg`,                icon: <Rocket   className="w-5 h-5" />,  color: 'text-[var(--accent)]' },
    { label: 'Active DB Types',    value: activeTypes,                       sub: `${uniqueDeployments} configs total`,  icon: <Layers   className="w-5 h-5" />,  color: 'text-[var(--status-skipped)]' },
    { label: 'Peak Day',           value: peakDay?.count ?? 0,               sub: peakDay ? fmtDate(peakDay.date) : '—', icon: <TrendingUp className="w-5 h-5" />, color: 'text-[var(--status-running)]' },
    { label: 'Errors',             value: counts?.error ?? 0,                sub: `${counts?.stopped ?? 0} stopped`,    icon: <Zap      className="w-5 h-5" />,  color: counts?.error ? 'text-[var(--status-error)]' : 'text-[var(--text-muted)]' },
    { label: 'JVM Heap',           value: `${heapPct}%`,                     sub: `${stats?.jvm?.heapUsedMb ?? 0}/${stats?.jvm?.heapMaxMb ?? 0} MB`, icon: <Server className="w-5 h-5" />, color: heapPct > 80 ? 'text-[var(--status-error)]' : 'text-[var(--status-deploying)]' },
  ]

  return (
    <div className="space-y-8">
      {/* ── Summary cards ── */}
      <section className="animate-fade-up">
        <p className="section-label">Overview</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 stagger-children">
          {summary.map(s => (
            <div key={s.label} className="stat-card animate-fade-up">
              <div className={`w-9 h-9 rounded-(--radius-sm) bg-(--bg-surface-2) flex items-center justify-center ${s.color}`}>
                {s.icon}
              </div>
              <div>
                <div className="text-2xl font-bold tabular-nums text-(--text-primary)">{s.value}</div>
                <div className="text-xs text-(--text-muted) mt-0.5">{s.label}</div>
                <div className="text-[10px] text-(--text-quiet) mt-0.5 truncate">{s.sub}</div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── Deployment activity ── */}
      <section className="animate-fade-up">
        <p className="section-label">Deployment Activity · last 30 days</p>
        <div className="card p-5">
          {byDay.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={byDay} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid {...GRID} vertical={false} />
                <XAxis dataKey="date" {...AXIS} tickFormatter={fmtDate}
                  interval={Math.max(0, Math.ceil(byDay.length / 8) - 1)} />
                <YAxis {...AXIS} allowDecimals={false} width={28} />
                <Tooltip {...TOOLT} labelFormatter={fmtDate} formatter={(v) => [`${v}`, 'Deployments']} />
                <Bar dataKey="count" fill="var(--accent)" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="text-xs text-(--text-muted) text-center py-6">No deployments recorded in the last 30 days.</p>
          )}
        </div>
      </section>

      {/* ── Distribution ── */}
      <section className="grid grid-cols-1 lg:grid-cols-2 gap-5 animate-fade-up">
        <div>
          <p className="section-label">Instances by DB Type</p>
          <div className="card p-5">
            <Donut data={byType} pickColor={(_, i) => DB_TYPE_COLORS[i % DB_TYPE_COLORS.length]} />
          </div>
        </div>
        <div>
          <p className="section-label">Instances by Status</p>
          <div className="card p-5">
            <Donut data={byStatus} pickColor={(d) => STATUS_COLORS[d.label] ?? '#6b7280'} />
          </div>
        </div>
      </section>

      {/* ── JVM history ── */}
      <section className="animate-fade-up">
        <p className="section-label">Live JVM & Pool · last {Math.max(1, Math.round(samples.length * 0.5))} min</p>
        {samples.length >= 2 ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <div className="card p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-(--text-muted) mb-3 flex items-center gap-1.5">
                <Server className="w-3.5 h-3.5" /> JVM Heap (MB)
              </p>
              <ResponsiveContainer width="100%" height={180}>
                <AreaChart data={samples}>
                  <defs>
                    <linearGradient id="heapGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="var(--status-deploying)" stopOpacity={0.35} />
                      <stop offset="95%" stopColor="var(--status-deploying)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid {...GRID} />
                  <XAxis dataKey="timestamp" {...AXIS} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS} domain={[0, (samples[0]?.heapMaxMb ?? 512) + 32]} unit=" MB" width={54} />
                  <Tooltip {...TOOLT} labelFormatter={fmtTime} formatter={(v) => [`${v} MB`, 'Heap']} />
                  <Area type="monotone" dataKey="heapUsedMb" stroke="var(--status-deploying)" strokeWidth={2} fill="url(#heapGrad)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
              <ProgressBar pct={heapPct} label={`${stats?.jvm?.heapUsedMb ?? 0} / ${stats?.jvm?.heapMaxMb ?? 0} MB`} color={heapPct > 80 ? 'var(--status-error)' : 'var(--status-deploying)'} />
            </div>

            <div className="card p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-(--text-muted) mb-3 flex items-center gap-1.5">
                <Layers className="w-3.5 h-3.5" /> Active DB Connections
              </p>
              <ResponsiveContainer width="100%" height={180}>
                <LineChart data={samples}>
                  <CartesianGrid {...GRID} />
                  <XAxis dataKey="timestamp" {...AXIS} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS} allowDecimals={false} width={32} />
                  <Tooltip {...TOOLT} labelFormatter={fmtTime} />
                  <Line type="monotone" dataKey="poolActive" stroke="var(--accent)" strokeWidth={2} dot={false} name="Pool" />
                  <Line type="monotone" dataKey="pgActiveConns" stroke="var(--status-running)" strokeWidth={2} dot={false} name="Postgres" />
                </LineChart>
              </ResponsiveContainer>
              <ProgressBar pct={poolPct} label={`${poolActive} / ${poolMax} pool`} color={poolPct > 80 ? 'var(--status-error)' : 'var(--accent)'} />
            </div>
          </div>
        ) : (
          <div className="card p-5 flex items-center gap-3 text-xs text-(--text-muted)">
            <div className="w-4 h-4 border-2 border-(--accent) border-t-transparent rounded-full animate-spin" />
            Collecting samples — first datapoint arrives in ~30s.
          </div>
        )}
      </section>

      {/* ── Recent instances ── */}
      <section className="animate-fade-up">
        <p className="section-label flex items-center gap-2">
          <Sparkles className="w-3.5 h-3.5" /> Most Recent Instances
        </p>
        <div className="card divide-y-2 divide-(--border-strong)">
          {instances.length === 0 && (
            <p className="text-xs text-(--text-muted) text-center py-6">Deploy your first database to see activity here.</p>
          )}
          {[...instances]
            .filter(i => i.status !== 'REMOVED' && !i.isSystem)
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
            .slice(0, 6)
            .map(i => (
              <div key={i.id} className="px-4 py-3 flex items-center gap-3">
                <span className="text-xl">{i.icon}</span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-(--text-primary) truncate">{i.name}</p>
                  <p className="text-[11px] text-(--text-muted)">
                    {i.dbTypeDisplay} {i.version} · port {i.hostPort}
                  </p>
                </div>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded-full border border-(--border-soft) text-(--text-muted) uppercase">
                  {i.status}
                </span>
              </div>
            ))}
        </div>
      </section>
    </div>
  )
}

// ── Helpers ─────────────────────────────────────────────────────────────────
function ProgressBar({ pct, label, color }) {
  const p = Math.min(100, Math.max(0, pct ?? 0))
  return (
    <div className="mt-4">
      <div className="flex justify-between text-[10px] text-(--text-muted) mb-1 font-mono">
        <span>{label}</span>
        <span>{p}%</span>
      </div>
      <div className="h-2 rounded-full bg-(--bg-surface-3) overflow-hidden border border-(--border-soft)">
        <div className="h-full transition-all duration-500" style={{ width: `${p}%`, background: color }} />
      </div>
    </div>
  )
}

function Donut({ data, pickColor }) {
  if (!data || data.length === 0) {
    return <p className="text-xs text-(--text-muted) text-center py-6">No data yet</p>
  }
  const total = data.reduce((s, d) => s + (d.count ?? 0), 0)
  return (
    <div>
      <div className="relative">
        <ResponsiveContainer width="100%" height={160}>
          <PieChart>
            <Pie data={data} dataKey="count" nameKey="label" cx="50%" cy="50%"
              innerRadius={44} outerRadius={68} paddingAngle={2} startAngle={90} endAngle={-270}>
              {data.map((d, i) => <Cell key={i} fill={pickColor(d, i)} stroke="var(--border-strong)" strokeWidth={1.5} />)}
            </Pie>
            <Tooltip {...TOOLT} formatter={(v, n) => [`${v}`, n]} />
          </PieChart>
        </ResponsiveContainer>
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="text-center">
            <div className="text-xl font-bold font-mono text-(--text-primary)">{total}</div>
            <div className="text-[10px] text-(--text-muted) uppercase tracking-wide">total</div>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1.5 mt-3 justify-center">
        {data.map((d, i) => (
          <div key={d.label} className="flex items-center gap-1.5 text-xs">
            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: pickColor(d, i) }} />
            <span className="text-(--text-muted)">{d.label}</span>
            <span className="font-mono text-(--text-secondary)">{d.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

