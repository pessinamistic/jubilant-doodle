import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  getInstance, startInstance, stopInstance, removeInstance, untrackInstance, reTrackInstance, getLogs, getPipeline,
  getSystemStats, getMetricsHistory, getDeploymentActivity, getContainerMetrics,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusBadge } from '../components/StatusBadge'
import { ConnectionString } from '../components/ConnectionString'
import { ImportModal } from '../components/ImportModal'
import { ConfirmModal } from '../components/ConfirmModal'
import {
  Activity,
  ArrowLeft,
  BarChart3,
  Clipboard,
  ClipboardCheck,
  Clock3,
  CornerUpLeft,
  Cpu,
  Database,
  Eye,
  EyeOff,
  FileText,
  Folder,
  Globe,
  HardDrive,
  Hash,
  Key,
  Layers,
  Link2,
  Play,
  RefreshCw,
  Rocket,
  Server,
  Settings,
  SlidersHorizontal,
  Square,
  Timer,
  Trash2,
  TrendingUp,
  Unlink,
  Zap,
} from 'lucide-react'
import toast from 'react-hot-toast'
import { PIPELINE_STATUS_TOKENS, PIPELINE_STEP_TOKENS } from '../theme/statusTokens'
import {
  ResponsiveContainer,
  LineChart, Line,
  AreaChart, Area,
  BarChart, Bar,
  PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip,
  ReferenceLine,
} from 'recharts'

const TABS = [
  { id: 'overview',       label: 'Overview',          icon: <BarChart3 className="w-4 h-4" /> },
  { id: 'internals',      label: 'System Internals',  icon: <Cpu className="w-4 h-4" />,      systemOnly: true },
  { id: 'metrics',        label: 'Metrics',           icon: <Activity className="w-4 h-4" />, nonSystemOnly: true },
  { id: 'pipeline',       label: 'Pipeline',           icon: <Rocket className="w-4 h-4" /> },
  { id: 'configuration',  label: 'Configuration',     icon: <Settings className="w-4 h-4" /> },
  { id: 'logs',           label: 'Logs',               icon: <FileText className="w-4 h-4" /> },
]

export function InstanceDetailPage() {
  const { id } = useParams()

  console.log("making get instance request for id : ", id)
  const navigate = useNavigate()

  const [instance, setInstance]   = useState(null)
  const [loading, setLoading]     = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [busy, setBusy]           = useState(false)
  const [showReImport, setShowReImport] = useState(false)
  const [showConfirmUntrack, setShowConfirmUntrack] = useState(false)
  const [showConfirmRemove, setShowConfirmRemove] = useState(false)

  const load = useCallback(async () => {
    try {
      const data = await getInstance(id)
      setInstance(data)
    } catch {
      toast.error('Instance not found')
      navigate('/instances')
    } finally {
      setLoading(false)
    }
  }, [id, navigate])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    const t = setInterval(load, 8_000)
    return () => {
      clearTimeout(kick)
      clearInterval(t)
    }
  }, [load])

  const handle = (fn, label, redirectAfter = false) => async () => {
    setBusy(true)
    try {
      await fn(id)
      toast.success(`${label} successful`)
      if (redirectAfter) navigate('/instances')
      else load()
    } catch (e) {
      toast.error(e.response?.data?.error ?? `${label} failed`)
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <AppShell>
        <div className="flex items-center justify-center py-32 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading instance…
        </div>
      </AppShell>
    )
  }

  if (!instance) return null

  const isRunning   = instance.status === 'RUNNING'
  const isStopped   = instance.status === 'STOPPED'
  const isRemoved   = instance.status === 'REMOVED'
  const isUntracked = instance.status === 'UNTRACKED'
  const isBusy      = ['DEPLOYING', 'REMOVING'].includes(instance.status) || busy

  return (
    <AppShell onRefresh={load}>
      {/* ── Breadcrumb ── */}
      <div className="flex items-center gap-2 text-sm text-[var(--text-muted)] mb-6 animate-slide-down">
        <Link to="/instances" className="hover:text-[var(--text-primary)] transition-colors flex items-center gap-1">
          <ArrowLeft className="w-3.5 h-3.5" />
          Instances
        </Link>
        <span>/</span>
        <span className="text-[var(--text-secondary)]">{instance.name}</span>
      </div>

      {/* ── Instance header ── */}
      <div className="card px-6 py-5 mb-6 animate-fade-up">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4">
            <div className="text-4xl">{instance.icon}</div>
            <div>
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-xl font-bold text-[var(--text-primary)]">{instance.name}</h1>
                <StatusBadge status={instance.status} />
                {instance.isSystem && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase bg-violet-500/15 text-violet-300 border border-violet-500/25">
                    SYSTEM
                  </span>
                )}
              </div>
              <p className="text-sm text-[var(--text-muted)] mt-1">
                {instance.dbTypeDisplay} {instance.version}
                &nbsp;·&nbsp;
                {instance.isSystem
                  ? <span className="text-[var(--text-secondary)]">Embedded</span>
                  : <>Port <span className="font-mono text-[var(--text-secondary)]">{instance.hostPort}</span></>}
                {!instance.isSystem && instance.containerName && (
                  <>&nbsp;·&nbsp;<span className="font-mono text-[var(--text-muted)] text-xs">{instance.containerName}</span></>
                )}
              </p>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-2">
            <button onClick={load} disabled={isBusy}
              className="btn-ghost flex items-center gap-1.5">
              <RefreshCw className={`w-4 h-4 ${busy ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            {!instance.isSystem && isStopped && (
              <button
                onClick={async () => {
                  setBusy(true)
                  try { await startInstance(id); toast.success('Started'); load() }
                  catch (e) { toast.error(e.response?.data?.error ?? 'Start failed') }
                  finally { setBusy(false) }}
                }
                disabled={isBusy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-green-500/10 border border-green-500/20 text-green-400 hover:bg-green-500/20 transition-colors disabled:opacity-40">
                <Play className="w-4 h-4" /> Start
              </button>
            )}
            {!instance.isSystem && isRunning && (
              <button
                onClick={async () => {
                  setBusy(true)
                  try { await stopInstance(id); toast.success('Stopped'); load() }
                  catch (e) { toast.error(e.response?.data?.error ?? 'Stop failed') }
                  finally { setBusy(false) }}
                }
                disabled={isBusy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-yellow-500/10 border border-yellow-500/20 text-yellow-400 hover:bg-yellow-500/20 transition-colors disabled:opacity-40">
                <Square className="w-4 h-4" /> Stop
              </button>
            )}
            {/* Re-import: only for imported instances that have been removed (container gone) */}
            {!instance.isSystem && instance.isImported && isRemoved && (
              <button
                onClick={() => setShowReImport(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20 transition-colors">
                <CornerUpLeft className="w-4 h-4" /> Re-import
              </button>
            )}
            {/* Re-track: untracked imported instances */}
            {!instance.isSystem && isUntracked && (
              <button
                onClick={async () => {
                  setBusy(true)
                  try {
                    await reTrackInstance(id)
                    toast.success('Re-track successful')
                    load()
                  } catch (e) {
                    toast.error(e.response?.data?.error ?? 'Re-track failed')
                  } finally { setBusy(false) }
                }}
                disabled={isBusy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 hover:bg-cyan-500/20 transition-colors disabled:opacity-40">
                <Link2 className="w-4 h-4" /> Re-track
              </button>
            )}
            {/* Untrack + Remove: both shown for imported instances, hidden when removed or untracked */}
            {!instance.isSystem && !isRemoved && !isUntracked && instance.isImported && (
              <>
                <button
                  onClick={() => setShowConfirmUntrack(true)}
                  disabled={isBusy}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20 transition-colors disabled:opacity-40">
                  <Unlink className="w-4 h-4" /> Untrack
                </button>
                <button
                  onClick={() => setShowConfirmRemove(true)}
                  disabled={isBusy}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors disabled:opacity-40">
                  <Trash2 className="w-4 h-4" /> Remove
                </button>
              </>
            )}
            {/* Remove: non-imported instances */}
            {!instance.isSystem && !isRemoved && !isUntracked && !instance.isImported && (
              <button
                onClick={() => setShowConfirmRemove(true)}
                disabled={isBusy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors disabled:opacity-40">
                <Trash2 className="w-4 h-4" /> Remove
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Tab bar ── */}
      <div className="tab-bar mb-6 w-fit animate-fade-up delay-100">
        {TABS.filter(t => (!t.systemOnly || instance.isSystem) && (!t.nonSystemOnly || !instance.isSystem)).map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`tab-item ${activeTab === t.id ? 'tab-active' : 'tab-inactive'}`}>
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      <div key={activeTab} className="animate-fade-up">
        {activeTab === 'overview'      && <OverviewTab          instance={instance} />}
        {activeTab === 'internals'     && instance.isSystem  && <SystemInternalsTab />}
        {activeTab === 'metrics'       && !instance.isSystem && <InstanceMetricsTab instanceId={id} instance={instance} />}
        {activeTab === 'pipeline'      && <PipelineTab          instanceId={id} instance={instance} />}
        {activeTab === 'configuration' && <ConfigurationTab     instance={instance} />}
        {activeTab === 'logs'          && <LogsTab              instanceId={id} isRunning={isRunning} />}
      </div>

      {showReImport && (
        <ImportModal
          onClose={() => setShowReImport(false)}
          onImported={() => { setShowReImport(false); load() }}
          reImportInstance={instance}
        />
      )}

      {/* Untrack modal — imported containers only */}
      {showConfirmUntrack && (
        <ConfirmModal
          open={showConfirmUntrack}
          variant="warning"
          icon={<Unlink className="w-5 h-5" />}
          title={`Untrack "${instance.name}"?`}
          message="The Docker container will not be stopped or removed. It will only stop being managed by Port Wrangler. You can re-track it at any time."
          confirmLabel="Untrack"
          onConfirm={() => {
            setShowConfirmUntrack(false)
            handle(untrackInstance, 'Untrack', false)()
          }}
          onCancel={() => setShowConfirmUntrack(false)}
        />
      )}

      {/* Remove modal */}
      {showConfirmRemove && (
        <ConfirmModal
          open={showConfirmRemove}
          variant="danger"
          title={`Remove "${instance.name}"?`}
          message="This will stop and permanently delete the Docker container. Any data not stored in a volume will be lost."
          confirmLabel="Remove"
          requiredConfirmText={instance.name}
          onConfirm={() => {
            setShowConfirmRemove(false)
            handle(removeInstance, 'Remove', true)()
          }}
          onCancel={() => setShowConfirmRemove(false)}
        />
      )}

    </AppShell>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  DonutChart — reusable mini donut/pie chart helper                         */
/* ─────────────────────────────────────────────────────────────────────────── */
const TOOLTIP_STYLE = {
  contentStyle: {
    background: 'rgba(10,10,16,0.97)',
    border: '1px solid rgba(255,255,255,0.09)',
    borderRadius: 6,
    fontSize: 11,
    fontFamily: 'ui-monospace, monospace',
    padding: '8px 12px',
  },
  labelStyle: { color: 'rgba(255,255,255,0.45)', marginBottom: 4, fontWeight: 600 },
  itemStyle : { color: 'rgba(255,255,255,0.8)' },
  cursor    : { stroke: 'rgba(255,255,255,0.06)' },
}

const AXIS_STYLE = {
  stroke  : 'rgba(255,255,255,0.06)',
  tick    : { fill: 'rgba(255,255,255,0.3)', fontSize: 10, fontFamily: 'ui-monospace, monospace' },
  axisLine: { stroke: 'rgba(255,255,255,0.06)' },
  tickLine: false,
}

const GRID_STYLE = { stroke: 'rgba(255,255,255,0.04)', strokeDasharray: '3 3' }

function DonutChart({ data, pickColor }) {
  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-24 text-xs text-[var(--text-muted)]">
        No data yet
      </div>
    )
  }
  const total = data.reduce((s, d) => s + (d.count ?? 0), 0)
  return (
    <div>
      <div className="relative">
        <ResponsiveContainer width="100%" height={140}>
          <PieChart>
            <Pie data={data} dataKey="count" nameKey="label" cx="50%" cy="50%"
              innerRadius={38} outerRadius={60} paddingAngle={2} startAngle={90} endAngle={-270}>
              {data.map((d, i) => <Cell key={i} fill={pickColor(d, i)} />)}
            </Pie>
            <Tooltip
              contentStyle={TOOLTIP_STYLE.contentStyle}
              labelStyle={TOOLTIP_STYLE.labelStyle}
              itemStyle={TOOLTIP_STYLE.itemStyle}
              formatter={(v, n) => [`${v}`, n]}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="text-center">
            <div className="text-lg font-bold font-mono text-[var(--text-primary)]">{total}</div>
            <div className="text-[10px] text-[var(--text-muted)]">total</div>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1.5 mt-2 justify-center">
        {data.map((d, i) => (
          <div key={d.label} className="flex items-center gap-1.5 text-xs">
            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: pickColor(d, i) }} />
            <span className="text-[var(--text-muted)]">{d.label}</span>
            <span className="font-mono text-[var(--text-secondary)]">{d.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Tool-Specific Telemetry Panel                                              */
/* ─────────────────────────────────────────────────────────────────────────── */

const TOOL_METRIC_DEFS = {
  POSTGRESQL: {
    title: 'PostgreSQL Telemetry',
    rows: [
      { key: 'connections',         label: 'Connections',          unit: '' },
      { key: 'activeConnections',   label: 'Active',                unit: '' },
      { key: 'idleConnections',     label: 'Idle',                  unit: '' },
      { key: 'maxConnections',      label: 'Max Connections',       unit: '' },
      { key: 'databaseSizeBytes',   label: 'Database Size',         unit: 'bytes' },
      { key: 'serverUptimeSeconds', label: 'Postmaster Uptime',     unit: 'duration' },
    ],
  },
  MYSQL: {
    title: 'MySQL Telemetry',
    rows: [
      { key: 'threadsConnected',    label: 'Threads Connected',     unit: '' },
      { key: 'threadsRunning',      label: 'Threads Running',       unit: '' },
      { key: 'totalQueries',        label: 'Total Queries',         unit: '' },
      { key: 'slowQueries',         label: 'Slow Queries',          unit: '' },
      { key: 'abortedConnects',     label: 'Aborted Connects',      unit: '' },
      { key: 'innodbBufferPoolPagesData', label: 'InnoDB Pages',    unit: '' },
      { key: 'serverUptimeSeconds', label: 'Server Uptime',         unit: 'duration' },
    ],
  },
  MARIADB: { /* alias to MYSQL */ alias: 'MYSQL' },
  REDIS: {
    title: 'Redis Telemetry',
    rows: [
      { key: 'connectedClients',          label: 'Connected Clients',    unit: '' },
      { key: 'instantaneousOpsPerSec',    label: 'Ops / sec',            unit: '' },
      { key: 'totalCommandsProcessed',    label: 'Commands Processed',   unit: '' },
      { key: 'usedMemory',                label: 'Used Memory',          unit: 'bytes' },
      { key: 'usedMemoryPeak',            label: 'Peak Memory',          unit: 'bytes' },
      { key: 'keyspaceHits',              label: 'Keyspace Hits',        unit: '' },
      { key: 'keyspaceMisses',            label: 'Keyspace Misses',      unit: '' },
      { key: 'evictedKeys',               label: 'Evicted Keys',         unit: '' },
      { key: 'expiredKeys',               label: 'Expired Keys',         unit: '' },
      { key: 'totalKeys',                 label: 'Total Keys',           unit: '' },
      { key: 'totalConnectionsReceived',  label: 'Total Connections',    unit: '' },
      { key: 'rejectedConnections',       label: 'Rejected Connections', unit: '' },
      { key: 'uptimeInSeconds',           label: 'Server Uptime',        unit: 'duration' },
    ],
  },
  MONGODB: {
    title: 'MongoDB Telemetry',
    rows: [
      { key: 'connectionsCurrent',   label: 'Connections',     unit: '' },
      { key: 'connectionsAvailable', label: 'Available',       unit: '' },
      { key: 'opcountersInsert',     label: 'Inserts',         unit: '' },
      { key: 'opcountersQuery',      label: 'Queries',         unit: '' },
      { key: 'opcountersUpdate',     label: 'Updates',         unit: '' },
      { key: 'opcountersDelete',     label: 'Deletes',         unit: '' },
      { key: 'opcountersCommand',    label: 'Commands',        unit: '' },
      { key: 'networkBytesIn',       label: 'Network In',      unit: 'bytes' },
      { key: 'networkBytesOut',      label: 'Network Out',     unit: 'bytes' },
      { key: 'serverUptimeSeconds',  label: 'Server Uptime',   unit: 'duration' },
    ],
  },
}

function ToolTelemetryPanel({ metrics, dbType }) {
  if (!metrics || !metrics.available) return null
  const tools = metrics.toolMetrics
  if (!tools || Object.keys(tools).length === 0) return null

  let defKey = (dbType || '').toUpperCase()
  let def = TOOL_METRIC_DEFS[defKey]
  if (def && def.alias) def = TOOL_METRIC_DEFS[def.alias]
  // Fallback: render every key as plain number
  const rows = def
    ? def.rows.filter(r => tools[r.key] != null)
    : Object.keys(tools).map(k => ({ key: k, label: humanize(k), unit: '' }))
  if (rows.length === 0) return null

  return (
    <div>
      <p className="section-label">
        {def?.title ?? 'Tool Telemetry'}
        <span className="ml-2 text-[10px] font-normal normal-case text-(--text-quiet)">
          · gathered via docker exec
        </span>
      </p>
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        {rows.map(r => (
          <div key={r.key} className="card p-4">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-(--text-muted)">{r.label}</div>
            <div className="text-lg font-bold font-mono text-(--text-primary) mt-1">
              {formatToolValue(tools[r.key], r.unit)}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function humanize(key) {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, c => c.toUpperCase())
    .trim()
}

function formatToolValue(v, unit) {
  if (v == null) return '—'
  const n = typeof v === 'number' ? v : Number(v)
  if (Number.isNaN(n)) return String(v)
  if (unit === 'bytes')    return fmtBytesShort(n)
  if (unit === 'duration') return fmtDurationShort(n)
  return n.toLocaleString()
}

function fmtBytesShort(b) {
  if (b < 1024)            return `${b} B`
  if (b < 1_048_576)       return `${(b/1024).toFixed(1)} KB`
  if (b < 1_073_741_824)   return `${(b/1_048_576).toFixed(1)} MB`
  return `${(b/1_073_741_824).toFixed(2)} GB`
}

function fmtDurationShort(secs) {
  const d = Math.floor(secs / 86400)
  const h = Math.floor((secs % 86400) / 3600)
  const m = Math.floor((secs % 3600) / 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m`
  return `${secs}s`
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  System Internals Tab  (H2 embedded DB — system instance only)             */
/* ─────────────────────────────────────────────────────────────────────────── */
const DB_TYPE_COLORS = ['#4f46e5','#f97316','#ef4444','#06b6d4','#22c55e','#8b5cf6','#a855f7','#ec4899']
const STATUS_COLORS  = { RUNNING:'#22c55e', STOPPED:'#f59e0b', DEPLOYING:'#3b82f6', ERROR:'#ef4444', REMOVED:'#6b7280' }

function SystemInternalsTab() {
  const [stats,    setStats]    = useState(null)
  const [history,  setHistory]  = useState(null)
  const [activity, setActivity] = useState(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const [lastRefresh, setLastRefresh] = useState(null)

  const fmtBytes = (bytes) => {
    if (bytes == null) return '—'
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  }

  const fmtUptime = (secs) => {
    if (secs == null) return '—'
    const d = Math.floor(secs / 86400), h = Math.floor((secs % 86400) / 3600)
    const m = Math.floor((secs % 3600) / 60), s = secs % 60
    if (d > 0) return `${d}d ${h}h ${m}m`
    if (h > 0) return `${h}h ${m}m`
    if (m > 0) return `${m}m ${s}s`
    return `${s}s`
  }

  const loadAll = useCallback(async () => {
    try {
      const [s, h, a] = await Promise.all([getSystemStats(), getMetricsHistory(), getDeploymentActivity()])
      setStats(s); setHistory(h); setActivity(a)
      setLastRefresh(new Date()); setError(null)
    } catch {
      setError('Failed to load system metrics')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAll()
    const t = setInterval(loadAll, 30_000)
    return () => clearInterval(t)
  }, [loadAll])

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-16 text-[var(--text-muted)] justify-center">
        <div className="w-4 h-4 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
        Loading system internals…
      </div>
    )
  }
  if (error) return <div className="card p-6 text-center text-[var(--status-error)] text-sm">{error}</div>

  // ── Derived ────────────────────────────────────────────────────────────────
  const heapPct    = stats?.jvm ? Math.round((stats.jvm.heapUsedMb / stats.jvm.heapMaxMb) * 100) : 0
  const heapColor  = heapPct > 85 ? 'bg-red-500' : heapPct > 60 ? 'bg-yellow-500' : 'bg-green-500'
  const poolActive = stats?.pool?.activeConnections ?? 0
  const poolMax    = stats?.pool?.maxSize ?? 1
  const poolPct    = Math.round((poolActive / poolMax) * 100)
  const samples    = history?.samples ?? []
  const actDays    = activity?.deploymentsByDay ?? []
  const byType     = activity?.instancesByDbType ?? []
  const byStatus   = activity?.instancesByStatus ?? []

  const fmtTime = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const fmtDate = (d)   => new Date(d + 'T12:00:00').toLocaleDateString([], { month: 'short', day: 'numeric' })
  const heapDomain = [0, (samples[0]?.heapMaxMb ?? 512) + 32]

  return (
    <div className="space-y-6">

      {/* ── Refresh header ── */}
      <div className="flex items-center justify-between">
        <p className="text-xs text-[var(--text-muted)]">
          Auto-refreshes every 30s
          {lastRefresh && <> · Last updated {lastRefresh.toLocaleTimeString()}</>}
        </p>
        <button onClick={loadAll} className="btn-ghost flex items-center gap-1.5 text-xs">
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh now
        </button>
      </div>

      {/* ── Database ── */}
      <div>
        <p className="section-label">Database</p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 stagger-children">
          {[
            { label: 'Engine',    value: stats?.db?.type ?? 'PostgreSQL',                              icon: <Database className="w-5 h-5" />, color: 'text-violet-400', bg: 'bg-violet-500/10' },
            { label: 'Version',   value: stats?.db?.version ?? '—',                                   icon: <Hash className="w-5 h-5" />,     color: 'text-blue-400',   bg: 'bg-blue-500/10'   },
            { label: 'DB Size',   value: fmtBytes(stats?.db?.dbSizeBytes),                            icon: <HardDrive className="w-5 h-5" />, color: 'text-indigo-400', bg: 'bg-indigo-500/10' },
            { label: 'Host',      value: stats?.db?.host ? `${stats.db.host}:${stats.db.port}` : '—', icon: <Globe className="w-5 h-5" />,    color: 'text-green-400',  bg: 'bg-green-500/10'  },
          ].map(s => (
            <div key={s.label} className="stat-card animate-fade-up hover:scale-[1.03] hover:-translate-y-0.5 transition-all duration-200">
              <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>{s.icon}</div>
              <div>
                <div className="text-base font-bold text-[var(--text-primary)] font-mono">{s.value}</div>
                <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
              </div>
            </div>
          ))}
        </div>
        {stats?.db?.databaseName && (
          <div className="mt-3 px-4 py-2.5 rounded-lg bg-white/[0.03] border border-white/[0.06] flex items-center gap-2">
            <Database className="w-3.5 h-3.5 text-[var(--text-muted)] shrink-0" />
            <span className="text-xs font-mono text-[var(--text-muted)] truncate">Database: <span className="text-[var(--text-secondary)]">{stats.db.databaseName}</span></span>
          </div>
        )}
      </div>

      {/* ── Live Metrics charts ── */}
      <div>
        <p className="section-label">
          Live Metrics
          {samples.length > 0 && (
            <span className="ml-2 text-[10px] font-normal normal-case text-[var(--text-quiet)]">
              (last {Math.max(1, Math.round(samples.length * 0.5))} min)
            </span>
          )}
        </p>
        {samples.length >= 2 ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">

            {/* JVM Heap over time */}
            <div className="card p-5">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
                <Cpu className="w-3.5 h-3.5" /> JVM Heap (MB)
              </p>
              <ResponsiveContainer width="100%" height={170}>
                <LineChart data={samples} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid {...GRID_STYLE} />
                  <XAxis dataKey="timestamp" {...AXIS_STYLE} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS_STYLE} domain={heapDomain} unit=" MB" width={54} />
                  <Tooltip {...TOOLTIP_STYLE} labelFormatter={fmtTime} formatter={(v) => [`${v} MB`, 'Heap Used']} />
                  <Line type="monotone" dataKey="heapUsedMb" stroke="#3b82f6" strokeWidth={2} dot={false} activeDot={{ r: 3, fill: '#3b82f6' }} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            {/* Pool active connections over time */}
            <div className="card p-5">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
                <Layers className="w-3.5 h-3.5" /> Pool Active Connections
              </p>
              <ResponsiveContainer width="100%" height={170}>
                <LineChart data={samples} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid {...GRID_STYLE} />
                  <XAxis dataKey="timestamp" {...AXIS_STYLE} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS_STYLE} domain={[0, (stats?.pool?.maxSize ?? 5) + 1]} allowDecimals={false} width={36} />
                  <Tooltip {...TOOLTIP_STYLE} labelFormatter={fmtTime} formatter={(v) => [`${v}`, 'Active']} />
                  <Line type="monotone" dataKey="poolActive" stroke="#8b5cf6" strokeWidth={2} dot={false} activeDot={{ r: 3, fill: '#8b5cf6' }} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            {/* PostgreSQL active connections over time */}
            <div className="card p-5">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
                <Database className="w-3.5 h-3.5" /> PostgreSQL Active Connections
              </p>
              <ResponsiveContainer width="100%" height={170}>
                <LineChart data={samples} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid {...GRID_STYLE} />
                  <XAxis dataKey="timestamp" {...AXIS_STYLE} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS_STYLE} domain={[0, 'auto']} allowDecimals={false} width={36} />
                  <Tooltip {...TOOLTIP_STYLE} labelFormatter={fmtTime} formatter={(v) => [`${v}`, 'pg_stat_activity']} />
                  <Line type="monotone" dataKey="pgActiveConns" stroke="#10b981" strokeWidth={2} dot={false} activeDot={{ r: 3, fill: '#10b981' }} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            {/* PostgreSQL DB size over time */}
            <div className="card p-5">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
                <HardDrive className="w-3.5 h-3.5" /> PostgreSQL DB Size (MB)
              </p>
              <ResponsiveContainer width="100%" height={170}>
                <AreaChart data={samples} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <defs>
                    <linearGradient id="pgSizeGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.25} />
                      <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid {...GRID_STYLE} />
                  <XAxis dataKey="timestamp" {...AXIS_STYLE} tickFormatter={fmtTime} interval="preserveStartEnd" />
                  <YAxis {...AXIS_STYLE} domain={['auto', 'auto']} unit=" MB" width={54} />
                  <Tooltip {...TOOLTIP_STYLE} labelFormatter={fmtTime} formatter={(v) => [`${v} MB`, 'DB Size']} />
                  <Area type="monotone" dataKey="pgDbSizeMb" stroke="#f59e0b" strokeWidth={2} fill="url(#pgSizeGrad)" dot={false} activeDot={{ r: 3, fill: '#f59e0b' }} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        ) : (
          <div className="card p-5 flex items-center gap-3">
            <div className="w-4 h-4 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin shrink-0" />
            <p className="text-xs text-[var(--text-muted)]">Collecting live metrics — first snapshot arrives in ~30 seconds. Data builds up over time.</p>
          </div>
        )}
      </div>

      {/* ── Deployment Activity ── */}
      <div>
        <p className="section-label">Deployment Activity</p>

        {/* Bar chart — deployments per day */}
        <div className="card p-5">
          <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
            <Zap className="w-3.5 h-3.5" /> Deployments — last 30 days
          </p>
          {actDays.length > 0 ? (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={actDays} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid {...GRID_STYLE} vertical={false} />
                <XAxis dataKey="date" {...AXIS_STYLE} tickFormatter={fmtDate}
                  interval={Math.max(0, Math.ceil(actDays.length / 7) - 1)} />
                <YAxis {...AXIS_STYLE} allowDecimals={false} width={28} />
                <Tooltip {...TOOLTIP_STYLE} labelFormatter={fmtDate} formatter={(v) => [`${v}`, 'Deployments']} />
                <Bar dataKey="count" fill="#4f46e5" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-20 text-xs text-[var(--text-muted)]">
              No deployments recorded in the last 30 days
            </div>
          )}
        </div>

        {/* Donut charts — by type & by status */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mt-5">
          <div className="card p-5">
            <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3 flex items-center gap-2">
              <Database className="w-3.5 h-3.5" /> Instances by DB Type
            </p>
            <DonutChart data={byType} pickColor={(_, i) => DB_TYPE_COLORS[i % DB_TYPE_COLORS.length]} />
          </div>
          <div className="card p-5">
            <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3 flex items-center gap-2">
              <Server className="w-3.5 h-3.5" /> Instances by Status
            </p>
            <DonutChart data={byStatus} pickColor={(d) => STATUS_COLORS[d.label] ?? '#6b7280'} />
          </div>
        </div>
      </div>

      {/* ── Connection Pool ── */}
      <div>
        <p className="section-label">Connection Pool (HikariCP)</p>
        <div className="card p-5 space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {[
              { label: 'Active',   value: stats?.pool?.activeConnections ?? '—', color: 'text-green-400' },
              { label: 'Idle',     value: stats?.pool?.idleConnections   ?? '—', color: 'text-blue-400' },
              { label: 'Pending',  value: stats?.pool?.pendingThreads    ?? '—', color: 'text-yellow-400' },
              { label: 'Max Size', value: stats?.pool?.maxSize           ?? '—', color: 'text-[var(--text-secondary)]' },
            ].map(item => (
              <div key={item.label} className="bg-white/[0.03] rounded-lg p-3 text-center">
                <div className={`text-xl font-bold font-mono ${item.color}`}>{item.value}</div>
                <div className="text-xs text-[var(--text-muted)] mt-1">{item.label}</div>
              </div>
            ))}
          </div>
          <div>
            <div className="flex justify-between text-xs text-[var(--text-muted)] mb-1.5">
              <span>Pool utilisation</span>
              <span className="font-mono">{poolActive} / {poolMax} ({poolPct}%)</span>
            </div>
            <div className="h-2 bg-white/[0.06] rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${poolPct > 80 ? 'bg-red-500' : poolPct > 50 ? 'bg-yellow-500' : 'bg-blue-500'}`}
                style={{ width: `${poolPct}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* ── Schema ── */}
      <div>
        <p className="section-label">Schema</p>
        <div className="card overflow-hidden">
          <div className="px-5 py-3 border-b border-white/[0.06] bg-white/[0.02] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-[var(--text-muted)]" />
              <span className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider">Tables</span>
            </div>
            <span className="text-xs text-[var(--text-muted)]">
              {stats?.schema?.tableCount ?? 0} tables · {stats?.schema?.tables?.reduce((a, t) => a + (t.rowCount ?? 0), 0).toLocaleString()} total rows
            </span>
          </div>
          <div className="divide-y divide-white/[0.04]">
            {(stats?.schema?.tables ?? []).map(tbl => (
              <div key={tbl.tableName} className="flex items-center justify-between px-5 py-3">
                <span className="text-sm font-mono text-[var(--text-secondary)]">{tbl.tableName}</span>
                <span className="text-xs font-mono px-2 py-0.5 rounded-full bg-white/[0.05] text-[var(--text-muted)]">
                  {(tbl.rowCount ?? 0).toLocaleString()} rows
                </span>
              </div>
            ))}
            {(!stats?.schema?.tables || stats.schema.tables.length === 0) && (
              <p className="text-xs text-[var(--text-muted)] px-5 py-4">No table data available.</p>
            )}
          </div>
        </div>
      </div>

      {/* ── JVM Heap + App Info ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div>
          <p className="section-label">JVM Heap</p>
          <div className="card p-5 space-y-4">
            <div className="grid grid-cols-2 gap-3">
              {[
                { label: 'Used', value: `${stats?.jvm?.heapUsedMb ?? '—'} MB`, color: 'text-[var(--text-primary)]' },
                { label: 'Max',  value: `${stats?.jvm?.heapMaxMb  ?? '—'} MB`, color: 'text-[var(--text-muted)]'   },
              ].map(item => (
                <div key={item.label} className="bg-white/[0.03] rounded-lg p-3 text-center">
                  <div className={`text-xl font-bold font-mono ${item.color}`}>{item.value}</div>
                  <div className="text-xs text-[var(--text-muted)] mt-1">{item.label}</div>
                </div>
              ))}
            </div>
            <div>
              <div className="flex justify-between text-xs text-[var(--text-muted)] mb-1.5">
                <span>Heap usage</span>
                <span className="font-mono">{heapPct}%</span>
              </div>
              <div className="h-2 bg-white/[0.06] rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all duration-500 ${heapColor}`} style={{ width: `${heapPct}%` }} />
              </div>
            </div>
          </div>
        </div>

        <div>
          <p className="section-label">Application</p>
          <div className="card p-5 space-y-3">
            {[
              { label: 'Uptime',     value: fmtUptime(stats?.app?.uptimeSeconds), icon: <Clock3 className="w-4 h-4" /> },
              { label: 'Started At', value: stats?.app?.startedAt ? new Date(stats.app.startedAt).toLocaleString() : '—', icon: <Zap className="w-4 h-4" /> },
            ].map(row => (
              <div key={row.label} className="flex items-center gap-3 py-1">
                <div className="w-7 h-7 rounded-lg bg-white/[0.04] flex items-center justify-center text-[var(--text-muted)] shrink-0">{row.icon}</div>
                <div className="flex-1 flex justify-between items-center gap-2 min-w-0">
                  <span className="text-xs text-[var(--text-muted)]">{row.label}</span>
                  <span className="text-sm font-mono text-[var(--text-secondary)] truncate">{row.value}</span>
                </div>
              </div>
            ))}
            <div className="pt-2 border-t border-white/[0.06]">
              <div className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-[var(--text-muted)] bg-white/[0.03] border border-white/[0.06] font-mono">
                <Server className="w-3.5 h-3.5 shrink-0 text-green-400" />
                <span>postgresql://</span>
                <span className="text-[var(--text-secondary)]">{stats?.db?.host ?? 'localhost'}:{stats?.db?.port ?? 5499}/{stats?.db?.databaseName ?? 'dbdeployer'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Instance Metrics Tab  (non-system instances only)                          */
/* ─────────────────────────────────────────────────────────────────────────── */
function InstanceMetricsTab({ instanceId, instance }) {
  const [pipeline, setPipeline]         = useState(null)
  const [pipelineLoading, setPipelineLoading] = useState(true)
  const [liveMetrics, setLiveMetrics]   = useState(null)
  const [liveLoading, setLiveLoading]   = useState(true)
  // Rolling history for sparklines — last 20 samples (~1m40s at 5s poll)
  const historyRef = useRef([])
  const [history, setHistory]           = useState([])

  const isRunning = instance.status === 'RUNNING'

  // Fetch pipeline once
  useEffect(() => {
    getPipeline(instanceId)
      .then(d => setPipeline(d))
      .catch(() => setPipeline(null))
      .finally(() => setPipelineLoading(false))
  }, [instanceId])

  // Poll container metrics every 5 s when running (single shot when stopped)
  useEffect(() => {
    let cancelled = false
    const fetch = () => {
      getContainerMetrics(instanceId)
        .then(data => {
          if (cancelled) return
          setLiveMetrics(data)
          setLiveLoading(false)
          if (data.available) {
            const sample = { t: Date.now(), cpu: data.cpuPercent, mem: data.memUsageBytes / 1_048_576 }
            const next = [...historyRef.current.slice(-19), sample]
            historyRef.current = next
            setHistory([...next])
          }
        })
        .catch(() => { if (!cancelled) setLiveLoading(false) })
    }
    fetch()
    const id = isRunning ? setInterval(fetch, 5000) : null
    return () => { cancelled = true; if (id) clearInterval(id) }
  }, [instanceId, isRunning])

  const now = Date.now()

  const fmtAge = (ms) => {
    if (!ms || ms <= 0) return '—'
    const d = Math.floor(ms / 86_400_000)
    const h = Math.floor((ms % 86_400_000) / 3_600_000)
    const m = Math.floor((ms % 3_600_000) / 60_000)
    if (d > 0) return `${d}d ${h}h`
    if (h > 0) return `${h}h ${m}m`
    return `${m}m`
  }
  const fmtTs = (iso) => iso ? new Date(iso).toLocaleString() : '—'
  const durMs = (a, b) => a && b ? new Date(b) - new Date(a) : null
  const fmtMs = (ms) => {
    if (ms === null || ms === undefined) return '—'
    if (ms < 1000) return `${ms}ms`
    if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
    return `${(ms / 60_000).toFixed(1)}m`
  }
  const fmtBytes = (b) => {
    if (b == null) return '—'
    if (b < 1024)            return `${b} B`
    if (b < 1_048_576)       return `${(b/1024).toFixed(1)} KB`
    if (b < 1_073_741_824)   return `${(b/1_048_576).toFixed(1)} MB`
    return `${(b/1_073_741_824).toFixed(2)} GB`
  }

  const instanceAge = now - new Date(instance.createdAt).getTime()
  const uptimeMs = isRunning && instance.startedAt ? now - new Date(instance.startedAt).getTime() : null

  const STEP_DISPLAY = {
    IMAGE_PULL: 'Pull Image', PULL_IMAGE: 'Pull Image',
    CONTAINER_CREATE: 'Create Container', CONTAINER_START: 'Start Container',
    START_CONTAINER: 'Start Container', FINALISE: 'Finalise',
  }
  const STEP_COLORS = { SUCCESS:'#22c55e', FAILED:'#ef4444', RUNNING:'#3b82f6', PENDING:'#9ca3af', SKIPPED:'#a855f7' }

  const stepTimings = pipeline?.steps
    ?.filter(s => s.startedAt && s.completedAt)
    .map(s => ({ name: STEP_DISPLAY[s.stepType] ?? s.stepType, durationMs: durMs(s.startedAt, s.completedAt), status: s.status, fill: STEP_COLORS[s.status] ?? '#9ca3af' })) ?? []
  const totalDeployMs = pipeline?.startedAt && pipeline?.completedAt ? durMs(pipeline.startedAt, pipeline.completedAt) : null

  const timelinePoints = [
    { label: 'Registered',   ts: instance.createdAt, active: true },
    { label: 'Last Started', ts: instance.startedAt, active: !!instance.startedAt },
    { label: 'Last Updated', ts: instance.updatedAt, active: !!instance.updatedAt },
  ]

  // Gauge bar component (inline)
  const GaugeBar = ({ pct, color }) => (
    <div className="w-full h-2 rounded-full bg-[var(--bg-surface-3)] overflow-hidden">
      <div
        className="h-full rounded-full transition-all duration-700"
        style={{ width: `${Math.min(100, pct ?? 0)}%`, background: color }}
      />
    </div>
  )

  const memLimitMb = liveMetrics ? liveMetrics.memLimitBytes / 1_048_576 : 0
  const memUsageMb = liveMetrics ? liveMetrics.memUsageBytes / 1_048_576 : 0

  return (
    <div className="space-y-8">

      {/* ═══ Section 0: Live Container Stats ══════════════════════════════════ */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <p className="section-label mb-0">Live Container Stats</p>
          {isRunning && (
            <div className="flex items-center gap-1.5 text-[10px] text-[var(--text-muted)]">
              <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
              Live · refreshes every 5s
            </div>
          )}
        </div>

        {liveLoading ? (
          <div className="card p-8 flex items-center justify-center gap-2 text-[var(--text-muted)] text-sm">
            <div className="w-4 h-4 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
            Fetching container stats…
          </div>
        ) : !liveMetrics?.available ? (
          <div className="card p-6 text-center">
            <Server className="w-8 h-8 text-[var(--text-quiet)] mx-auto mb-2" />
            <p className="text-sm text-[var(--text-muted)]">Container is not running — live stats unavailable.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Port + restart badges */}
            <div className="flex flex-wrap gap-3">
              <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold border
                ${liveMetrics.portReachable
                  ? 'bg-green-500/10 border-green-500/30 text-green-400'
                  : 'bg-red-500/10 border-red-500/30 text-red-400'}`}>
                <div className={`w-2 h-2 rounded-full ${liveMetrics.portReachable ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
                Port {instance.hostPort} — {liveMetrics.portReachable ? `Reachable (${liveMetrics.portLatencyMs}ms)` : 'Not reachable'}
              </div>
              {liveMetrics.healthStatus && liveMetrics.healthStatus !== 'none' && (
                <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold border
                  ${liveMetrics.healthStatus === 'healthy'
                    ? 'bg-green-500/10 border-green-500/30 text-green-400'
                    : liveMetrics.healthStatus === 'starting'
                      ? 'bg-blue-500/10 border-blue-500/30 text-blue-400'
                      : 'bg-red-500/10 border-red-500/30 text-red-400'}`}>
                  <span className="capitalize">healthcheck: {liveMetrics.healthStatus}</span>
                </div>
              )}
              {liveMetrics.uptimeSeconds > 0 && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm border bg-(--bg-surface-2) border-(--border-soft) text-(--text-secondary)">
                  <Clock3 className="w-3.5 h-3.5" /> Up {fmtAge(liveMetrics.uptimeSeconds * 1000)}
                </div>
              )}
              {liveMetrics.oomKilled && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold border bg-red-500/10 border-red-500/30 text-red-400">
                  ⚠ OOM-killed
                </div>
              )}
              {liveMetrics.restartCount > 0 && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold border bg-orange-500/10 border-orange-500/30 text-orange-400">
                  ↺ Restarted {liveMetrics.restartCount}×
                </div>
              )}
              {liveMetrics.pids > 0 && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm border bg-[var(--bg-surface-2)] border-[var(--border-soft)] text-[var(--text-secondary)]">
                  <Hash className="w-3.5 h-3.5" /> {liveMetrics.pids}{liveMetrics.pidsLimit > 0 ? ` / ${liveMetrics.pidsLimit}` : ''} PIDs
                </div>
              )}
              {liveMetrics.cpuCores > 0 && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm border bg-[var(--bg-surface-2)] border-[var(--border-soft)] text-[var(--text-secondary)]">
                  <Cpu className="w-3.5 h-3.5" /> {liveMetrics.cpuCores} vCPU{liveMetrics.cpuCores !== 1 ? 's' : ''}
                  {liveMetrics.cpuThrottledPercent > 0 && (
                    <span className="text-amber-400">· {liveMetrics.cpuThrottledPercent.toFixed(0)}% throttled</span>
                  )}
                </div>
              )}
              {(liveMetrics.netRxErrors > 0 || liveMetrics.netTxErrors > 0) && (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold border bg-red-500/10 border-red-500/30 text-red-400">
                  Net errors: {liveMetrics.netRxErrors}rx / {liveMetrics.netTxErrors}tx
                </div>
              )}
            </div>

            {/* CPU + Memory gauges */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {/* CPU */}
              <div className="card p-5">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <Cpu className="w-3.5 h-3.5" /> CPU Usage
                  </span>
                  <span className="text-2xl font-black font-mono text-green-400">
                    {liveMetrics.cpuPercent.toFixed(1)}%
                  </span>
                </div>
                <GaugeBar pct={liveMetrics.cpuPercent} color={
                  liveMetrics.cpuPercent > 80 ? '#ef4444'
                  : liveMetrics.cpuPercent > 50 ? '#f59e0b'
                  : '#22c55e'
                } />
                <div className="flex justify-between text-[10px] text-[var(--text-quiet)] mt-1">
                  <span>0%</span>
                  <span>across {liveMetrics.cpuCores} core{liveMetrics.cpuCores !== 1 ? 's' : ''}</span>
                  <span>100%</span>
                </div>
              </div>

              {/* Memory */}
              <div className="card p-5">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <HardDrive className="w-3.5 h-3.5" /> Memory Usage
                  </span>
                  <span className="text-2xl font-black font-mono text-violet-400">
                    {liveMetrics.memPercent.toFixed(1)}%
                  </span>
                </div>
                <GaugeBar pct={liveMetrics.memPercent} color={
                  liveMetrics.memPercent > 85 ? '#ef4444'
                  : liveMetrics.memPercent > 65 ? '#f59e0b'
                  : '#a855f7'
                } />
                <div className="flex justify-between text-[10px] text-[var(--text-quiet)] mt-1">
                  <span>{fmtBytes(liveMetrics.memUsageBytes)}</span>
                  <span>of</span>
                  <span>{fmtBytes(liveMetrics.memLimitBytes)} limit</span>
                </div>
              </div>
            </div>

            {/* Network + Block I/O */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {[
                { icon: <TrendingUp className="w-4 h-4" />,   label: 'Net Received', value: fmtBytes(liveMetrics.netRxBytes),    sub: `${liveMetrics.netRxPackets.toLocaleString()} pkts`, color: 'text-blue-400',  bg: 'bg-blue-500/10' },
                { icon: <Activity   className="w-4 h-4" />,   label: 'Net Sent',     value: fmtBytes(liveMetrics.netTxBytes),    sub: `${liveMetrics.netTxPackets.toLocaleString()} pkts`, color: 'text-indigo-400', bg: 'bg-indigo-500/10' },
                { icon: <HardDrive  className="w-4 h-4" />,   label: 'Disk Read',    value: fmtBytes(liveMetrics.blockReadBytes), sub: 'cumulative',                                        color: 'text-cyan-400',  bg: 'bg-cyan-500/10' },
                { icon: <Zap        className="w-4 h-4" />,   label: 'Disk Written', value: fmtBytes(liveMetrics.blockWriteBytes), sub: 'cumulative',                                       color: 'text-amber-400', bg: 'bg-amber-500/10' },
              ].map(s => (
                <div key={s.label} className="card p-4 flex flex-col gap-1">
                  <div className={`w-8 h-8 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>{s.icon}</div>
                  <div className="text-lg font-bold font-mono text-[var(--text-primary)] mt-1">{s.value}</div>
                  <div className="text-[10px] text-[var(--text-muted)] font-semibold uppercase tracking-wide">{s.label}</div>
                  <div className="text-[10px] text-[var(--text-quiet)]">{s.sub}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* ═══ Section 0c: Tool-Specific Telemetry ═════════════════════════════ */}
      <ToolTelemetryPanel metrics={liveMetrics} dbType={instance.dbType} />

      {/* ═══ Section 0b: CPU + Memory Sparklines ══════════════════════════════ */}
      {history.length >= 2 && (
        <div>
          <p className="section-label">Resource History <span className="text-[var(--text-quiet)] font-normal normal-case tracking-normal">· last {history.length} samples</span></p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* CPU sparkline */}
            <div className="card p-4">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Cpu className="w-3.5 h-3.5 text-green-400" /> CPU %
              </p>
              <ResponsiveContainer width="100%" height={80}>
                <AreaChart data={history} margin={{ top: 2, right: 4, left: -28, bottom: 0 }}>
                  <defs>
                    <linearGradient id="cpuGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#22c55e" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="rgba(255,255,255,0.04)" strokeDasharray="3 3" />
                  <XAxis dataKey="t" hide />
                  <YAxis domain={[0, 100]} tick={{ fill: 'var(--text-quiet)', fontSize: 10 }} tickFormatter={v => `${v}%`} />
                  <Tooltip
                    contentStyle={{ background: 'var(--bg-surface-2)', border: '1px solid var(--border-soft)', borderRadius: 4, fontSize: 11 }}
                    labelFormatter={() => ''}
                    formatter={v => [`${v.toFixed(1)}%`, 'CPU']}
                  />
                  <Area type="monotone" dataKey="cpu" stroke="#22c55e" fill="url(#cpuGrad)" strokeWidth={2} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            {/* Memory sparkline */}
            <div className="card p-4">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <HardDrive className="w-3.5 h-3.5 text-violet-400" /> Memory (MB)
              </p>
              <ResponsiveContainer width="100%" height={80}>
                <AreaChart data={history} margin={{ top: 2, right: 4, left: -10, bottom: 0 }}>
                  <defs>
                    <linearGradient id="memGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#a855f7" stopOpacity={0.4} />
                      <stop offset="95%" stopColor="#a855f7" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="rgba(255,255,255,0.04)" strokeDasharray="3 3" />
                  <XAxis dataKey="t" hide />
                  <YAxis domain={[0, memLimitMb > 0 ? Math.ceil(memLimitMb) : 'auto']}
                         tick={{ fill: 'var(--text-quiet)', fontSize: 10 }}
                         tickFormatter={v => `${Math.round(v)}`} />
                  <Tooltip
                    contentStyle={{ background: 'var(--bg-surface-2)', border: '1px solid var(--border-soft)', borderRadius: 4, fontSize: 11 }}
                    labelFormatter={() => ''}
                    formatter={v => [`${v.toFixed(0)} MB`, 'Memory']}
                  />
                  {memLimitMb > 0 && <ReferenceLine y={memLimitMb} stroke="#ef4444" strokeDasharray="4 2" strokeOpacity={0.5} />}
                  <Area type="monotone" dataKey="mem" stroke="#a855f7" fill="url(#memGrad)" strokeWidth={2} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
              {memLimitMb > 0 && <p className="text-[10px] text-[var(--text-quiet)] text-right mt-1">— limit: {Math.round(memLimitMb)} MB</p>}
            </div>
          </div>
        </div>
      )}

      {/* ═══ Section 1: Lifetime Stats ════════════════════════════════════════ */}
      <div>
        <p className="section-label">Lifetime</p>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[
            { icon: <TrendingUp className="w-5 h-5" />, label: 'Instance Age',   value: fmtAge(instanceAge), color: 'text-violet-400', bg: 'bg-violet-500/10' },
            { icon: <Clock3     className="w-5 h-5" />, label: 'Current Uptime', value: uptimeMs ? fmtAge(uptimeMs) : isRunning ? '< 1m' : '—', color: isRunning ? 'text-green-400' : 'text-gray-400', bg: isRunning ? 'bg-green-500/10' : 'bg-gray-500/10' },
            { icon: <Globe      className="w-5 h-5" />, label: 'Host Port',      value: instance.hostPort,   color: 'text-indigo-400', bg: 'bg-indigo-500/10' },
            { icon: <Database   className="w-5 h-5" />, label: 'DB Type',        value: instance.dbTypeDisplay, color: 'text-cyan-400', bg: 'bg-cyan-500/10' },
          ].map(s => (
            <div key={s.label} className="stat-card">
              <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>{s.icon}</div>
              <div>
                <div className="text-base font-bold text-[var(--text-primary)] font-mono">{s.value}</div>
                <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ═══ Section 2: Lifecycle Timeline ═══════════════════════════════════ */}
      <div>
        <p className="section-label">Lifecycle Timeline</p>
        <div className="card p-6">
          <div className="flex items-center gap-0 mb-6 relative">
            {timelinePoints.map((pt, i) => (
              <div key={pt.label} className="flex items-center flex-1">
                <div className="flex flex-col items-center gap-1.5">
                  <div className={`w-3 h-3 rounded-full border-2 ${pt.active ? 'bg-[var(--accent)] border-[var(--accent)]' : 'bg-[var(--bg-surface-3)] border-[var(--border-soft)]'}`} />
                  <span className="text-[10px] text-[var(--text-muted)] font-semibold uppercase tracking-wide whitespace-nowrap">{pt.label}</span>
                  <span className="text-[10px] font-mono text-[var(--text-secondary)]">{pt.ts ? new Date(pt.ts).toLocaleDateString() : '—'}</span>
                  <span className="text-[10px] font-mono text-[var(--text-quiet)]">{pt.ts ? new Date(pt.ts).toLocaleTimeString() : ''}</span>
                </div>
                {i < timelinePoints.length - 1 && <div className="flex-1 h-px bg-[var(--border-soft)] mx-2 -mt-8" />}
              </div>
            ))}
            <div className="flex flex-col items-center gap-1.5 ml-2">
              <div className={`w-3 h-3 rounded-full border-2 ${isRunning ? 'bg-green-500 border-green-500 animate-pulse' : 'bg-gray-500/40 border-gray-500/40'}`} />
              <span className="text-[10px] text-[var(--text-muted)] font-semibold uppercase tracking-wide">Now</span>
              <span className="text-[10px] font-mono text-[var(--text-quiet)]">{isRunning ? fmtAge(uptimeMs ?? 0) + ' up' : instance.status}</span>
            </div>
          </div>
        </div>
      </div>

      {/* ═══ Section 3: Last Deploy Performance ══════════════════════════════ */}
      <div>
        <p className="section-label">Last Deploy Performance</p>
        {pipelineLoading ? (
          <div className="card p-8 flex items-center justify-center gap-2 text-[var(--text-muted)] text-sm">
            <div className="w-4 h-4 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
            Loading pipeline…
          </div>
        ) : !pipeline ? (
          <div className="card p-10 text-center">
            <Rocket className="w-8 h-8 text-[var(--text-quiet)] mx-auto mb-3" />
            <p className="text-sm text-[var(--text-muted)]">No pipeline recorded yet.</p>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {[
                { icon: <Timer    className="w-5 h-5" />, label: 'Total Deploy Time', value: fmtMs(totalDeployMs), color: 'text-orange-400', bg: 'bg-orange-500/10' },
                { icon: <Zap      className="w-5 h-5" />, label: 'Image Pull', value: fmtMs(durMs(pipeline.steps?.find(s => s.stepType === 'PULL_IMAGE' || s.stepType === 'IMAGE_PULL')?.startedAt, pipeline.steps?.find(s => s.stepType === 'PULL_IMAGE' || s.stepType === 'IMAGE_PULL')?.completedAt)), color: 'text-yellow-400', bg: 'bg-yellow-500/10' },
                { icon: <Layers   className="w-5 h-5" />, label: 'Steps Completed', value: `${pipeline.steps?.filter(s => s.status === 'SUCCESS').length ?? 0} / ${pipeline.steps?.length ?? 0}`, color: 'text-blue-400', bg: 'bg-blue-500/10' },
                { icon: <Activity className="w-5 h-5" />, label: 'Outcome', value: pipeline.status, color: pipeline.status === 'SUCCESS' ? 'text-green-400' : pipeline.status === 'FAILED' ? 'text-red-400' : 'text-yellow-400', bg: pipeline.status === 'SUCCESS' ? 'bg-green-500/10' : pipeline.status === 'FAILED' ? 'bg-red-500/10' : 'bg-yellow-500/10' },
              ].map(s => (
                <div key={s.label} className="stat-card">
                  <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>{s.icon}</div>
                  <div>
                    <div className="text-base font-bold text-[var(--text-primary)] font-mono">{s.value}</div>
                    <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
                  </div>
                </div>
              ))}
            </div>

            {stepTimings.length > 0 && (
              <div className="card p-5">
                <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-4 flex items-center gap-2">
                  <BarChart3 className="w-4 h-4" /> Step Timing Breakdown
                </p>
                <ResponsiveContainer width="100%" height={stepTimings.length * 48 + 32}>
                  <BarChart layout="vertical" data={stepTimings} margin={{ top: 0, right: 60, left: 16, bottom: 0 }} barSize={18}>
                    <CartesianGrid horizontal={false} stroke="rgba(255,255,255,0.05)" strokeDasharray="3 3" />
                    <XAxis type="number" tickFormatter={v => v < 1000 ? `${v}ms` : `${(v/1000).toFixed(1)}s`} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={{ stroke: 'var(--border-soft)' }} tickLine={false} />
                    <YAxis type="category" dataKey="name" width={110} tick={{ fill: 'var(--text-secondary)', fontSize: 12 }} axisLine={false} tickLine={false} />
                    <Tooltip contentStyle={{ background: 'var(--bg-surface-2)', border: '2px solid var(--border-strong)', borderRadius: 4, fontFamily: 'monospace', fontSize: 12 }} labelStyle={{ color: 'var(--text-primary)', fontWeight: 700 }} itemStyle={{ color: 'var(--text-secondary)' }} cursor={{ fill: 'rgba(255,255,255,0.03)' }} formatter={(v) => [v < 1000 ? `${v}ms` : `${(v/1000).toFixed(2)}s`, 'Duration']} />
                    <Bar dataKey="durationMs" radius={[0, 3, 3, 0]} label={{ position: 'right', formatter: v => v < 1000 ? `${v}ms` : `${(v/1000).toFixed(1)}s`, fill: 'var(--text-muted)', fontSize: 11 }}>
                      {stepTimings.map((entry, i) => <Cell key={i} fill={entry.fill} fillOpacity={0.85} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}

            {pipeline.status === 'FAILED' && pipeline.errorCode && (
              <div className="card p-4 border border-red-500/20 bg-red-500/5">
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-xs font-bold text-red-400 uppercase tracking-wide">Deploy Failed</span>
                  <span className="font-mono text-xs text-red-300 bg-red-500/10 px-2 py-0.5 rounded border border-red-500/20">{pipeline.errorCode}</span>
                </div>
                {pipeline.errorMessage && <p className="text-xs text-red-400/80 font-mono leading-relaxed">{pipeline.errorMessage}</p>}
              </div>
            )}

            <div className="card p-4">
              <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3">Pipeline Details</p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {[
                  { label: 'Pipeline ID', value: pipeline.id?.slice(-12) },
                  { label: 'Started',     value: fmtTs(pipeline.startedAt) },
                  { label: 'Completed',   value: fmtTs(pipeline.completedAt) },
                ].map(row => (
                  <div key={row.label} className="bg-[var(--bg-surface-2)] rounded p-3">
                    <div className="text-[10px] text-[var(--text-muted)] uppercase tracking-wide mb-1">{row.label}</div>
                    <div className="text-xs font-mono text-[var(--text-secondary)] truncate">{row.value}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ═══ Section 4: Container Identity ═══════════════════════════════════ */}
      <div>
        <p className="section-label">Container Identity</p>
        <div className="card p-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {[
              instance.containerName && { label: 'Container Name', value: instance.containerName, mono: true },
              instance.containerId   && { label: 'Container ID',   value: instance.containerId.slice(0,12), mono: true },
              liveMetrics?.image     && { label: 'Image (full)',    value: liveMetrics.image, mono: true, small: true },
              { label: 'Image Version',  value: `${instance.dbTypeDisplay} ${instance.version}` },
              { label: 'Deploy Method',  value: instance.deployMethod },
              { label: 'Container State', value: liveMetrics?.containerState ?? instance.status, mono: true },
              { label: 'Origin',         value: instance.isImported ? 'Imported (unmanaged)' : 'Deployed by Port Wrangler' },
              instance.dataDirectory && { label: 'Data Volume', value: instance.dataDirectory, mono: true, small: true },
              { label: 'Registered',     value: fmtTs(instance.createdAt) },
              instance.startedAt && { label: 'Last Started', value: fmtTs(instance.startedAt) },
            ].filter(Boolean).map(row => (
              <div key={row.label} className="flex items-start justify-between gap-3 py-2 border-b border-[var(--border-soft)]/30 last:border-0">
                <span className="text-xs text-[var(--text-muted)] shrink-0 w-32">{row.label}</span>
                <span className={`text-right flex-1 min-w-0 truncate ${row.mono ? 'font-mono text-xs' : 'text-sm'} text-[var(--text-secondary)]`}>{row.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

    </div>
  )
}


/* ─────────────────────────────────────────────────────────────────────────── */
/*  Overview Tab                                                               */
/* ─────────────────────────────────────────────────────────────────────────── */
function OverviewTab({ instance }) {
  const isRunning = instance.status === 'RUNNING'
  const isSystem  = instance.isSystem

  const uptime = (() => {
    if (!isRunning) return null
    const base = instance.startedAt ?? instance.createdAt
    if (!base) return null
    const diff = Math.max(0, Date.now() - new Date(base).getTime())
    const d = Math.floor(diff / 86_400_000)
    const h = Math.floor((diff % 86_400_000) / 3_600_000)
    const m = Math.floor((diff % 3_600_000) / 60_000)
    if (d > 0) return `${d}d ${h}h ${m}m`
    if (h > 0) return `${h}h ${m}m`
    return `${m}m`
  })()

  const statCards = [
    {
      label: 'Status',
      value: instance.status,
      icon: <Database className="w-5 h-5" />,
      color: instance.status === 'RUNNING' ? 'text-green-400' : instance.status === 'ERROR' ? 'text-red-400' : 'text-gray-400',
      bg: instance.status === 'RUNNING' ? 'bg-green-500/10' : instance.status === 'ERROR' ? 'bg-red-500/10' : 'bg-gray-500/10',
    },
    {
      label: 'Uptime',
      value: uptime ?? '—',
      icon: <Clock3 className="w-5 h-5" />,
      color: 'text-blue-400',
      bg: 'bg-blue-500/10',
    },
    !isSystem && {
      label: 'Host Port',
      value: instance.hostPort,
      icon: <Globe className="w-5 h-5" />,
      color: 'text-indigo-400',
      bg: 'bg-indigo-500/10',
    },
    {
      label: 'Version',
      value: instance.version,
      icon: <Hash className="w-5 h-5" />,
      color: 'text-purple-400',
      bg: 'bg-purple-500/10',
    },
    {
      label: 'Deploy Method',
      value: instance.deployMethod,
      icon: <Server className="w-5 h-5" />,
      color: 'text-cyan-400',
      bg: 'bg-cyan-500/10',
    },
  ].filter(Boolean)

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div>
        <p className="section-label">Stats</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4 stagger-children">
          {statCards.map(s => (
            <div key={s.label} className="stat-card animate-fade-up hover:scale-[1.03] hover:-translate-y-0.5 transition-all duration-200">
              <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>
                {s.icon}
              </div>
              <div>
                <div className="text-base font-bold text-[var(--text-primary)] font-mono">{s.value}</div>
                <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Health indicator */}
      <div>
        <p className="section-label">Health</p>
        <div className="card p-5">
          <div className="flex items-center gap-4">
            <div className={`w-12 h-12 rounded-full flex items-center justify-center text-2xl ${
              isRunning ? 'bg-green-500/10 ring-2 ring-green-500/30' : 'bg-gray-500/10 ring-2 ring-gray-500/20'
            }`}>
              {isRunning ? '✅' : instance.status === 'ERROR' ? '❌' : '⏹️'}
            </div>
            <div>
              <p className="text-[var(--text-primary)] font-medium">
                {isRunning ? 'Healthy & Running' : instance.status === 'ERROR' ? 'Error State' : `Instance ${instance.status}`}
              </p>
              <p className="text-sm text-[var(--text-muted)] mt-0.5">
                {isRunning
                  ? isSystem
                    ? `Embedded H2 database is running. Check the System Internals tab for live metrics.`
                    : `Container has been running for ${uptime ?? 'unknown'}. Accepting connections on port ${instance.hostPort}.`
                  : instance.status === 'STOPPED'
                    ? 'Container is stopped. Click Start to bring it back online.'
                    : instance.status === 'DEPLOYING'
                      ? 'Container is being deployed. This may take a few moments.'
                      : 'Container encountered an error. Check the Logs tab for details.'}
              </p>
            </div>
          </div>
          {isRunning && (
            <div className={`mt-4 grid gap-3 text-center ${isSystem ? 'grid-cols-2' : 'grid-cols-3'}`}>
              {(isSystem
                ? [
                    { label: 'Engine',       value: 'H2 Embedded' },
                    { label: 'Last Updated', value: new Date(instance.updatedAt ?? instance.createdAt).toLocaleTimeString() },
                  ]
                : [
                    { label: 'Container',    value: instance.containerName ?? '—' },
                    { label: 'Created',      value: new Date(instance.createdAt).toLocaleDateString() },
                    { label: 'Last Updated', value: new Date(instance.updatedAt ?? instance.createdAt).toLocaleTimeString() },
                  ]
              ).map(item => (
                <div key={item.label} className="bg-white/[0.03] rounded-lg p-3">
                  <div className="text-xs text-[var(--text-muted)] mb-1">{item.label}</div>
                  <div className="text-sm text-[var(--text-primary)] font-mono truncate">{item.value}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Connection string quick-access — hidden for system DB */}
      {instance.connectionString && !isSystem && (
        <div>
          <p className="section-label">Connection String</p>
          <div className="card p-5">
            <ConnectionString value={instance.connectionString} masked={instance.connectionStringMasked} />
            <p className="text-xs text-[var(--text-muted)] mt-2">
              Use this string in your application to connect. Click the eye icon to reveal the password, or copy the full string.
            </p>
          </div>
        </div>
      )}

      {/* System DB hint */}
      {isSystem && (
        <div className="card p-5 flex items-start gap-3 border-violet-500/20 bg-violet-500/5">
          <Cpu className="w-5 h-5 text-violet-400 mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-semibold text-violet-300">Embedded System Database</p>
            <p className="text-xs text-[var(--text-muted)] mt-1">
              This is Port Wrangler's internal H2 database. It stores all instance metadata, pipeline history, and configuration.
              Switch to the <strong className="text-violet-300">System Internals</strong> tab for live connection pool stats, schema row counts, JVM heap, and more.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Configuration Tab                                                          */
/* ─────────────────────────────────────────────────────────────────────────── */
function ConfigurationTab({ instance }) {
  const [showPassword, setShowPassword] = useState(false)
  const [copied, setCopied] = useState(null)

  const copyValue = async (val, key) => {
    await navigator.clipboard.writeText(val)
    setCopied(key)
    setTimeout(() => setCopied(null), 2000)
  }

  const sections = [
    {
      title: 'Credentials',
      icon: <Key className="w-4 h-4" />,
      rows: [
        instance.username    && { label: 'Username',      value: instance.username,    mono: true, copyKey: 'username' },
        instance.password    && { label: 'Password',      value: instance.password,    mono: true, secret: true, copyKey: 'password' },
        instance.databaseName && { label: 'Database Name', value: instance.databaseName, mono: true, copyKey: 'db' },
      ].filter(Boolean),
    },
    {
      title: 'Connection',
      icon: <Globe className="w-4 h-4" />,
      rows: [
        { label: 'Host',     value: 'localhost', mono: true },
        { label: 'Port',     value: String(instance.hostPort), mono: true, copyKey: 'port' },
        { label: 'DB Type',  value: instance.dbTypeDisplay },
        { label: 'Version',  value: instance.version },
      ],
    },
    {
      title: 'Container',
      icon: <Server className="w-4 h-4" />,
      rows: [
        instance.containerName && { label: 'Container Name', value: instance.containerName, mono: true, copyKey: 'container' },
        instance.containerId   && { label: 'Container ID',   value: instance.containerId.slice(0, 12), mono: true, copyKey: 'cid' },
        { label: 'Deploy Method', value: instance.deployMethod },
      ].filter(Boolean),
    },
    {
      title: 'Storage',
      icon: <Folder className="w-4 h-4" />,
      rows: [
        instance.dataDirectory && { label: 'Data Directory', value: instance.dataDirectory, mono: true, small: true, copyKey: 'dir' },
        { label: 'Created',  value: new Date(instance.createdAt).toLocaleString() },
        instance.updatedAt && { label: 'Last Updated', value: new Date(instance.updatedAt).toLocaleString() },
      ].filter(Boolean),
    },
  ]

  return (
    <div className="space-y-5">
      {/* Source Template banner */}
      {instance.templateId && (
        <div className="card p-4 flex items-center gap-3 border-(--border-strong) animate-fade-up">
          <SlidersHorizontal className="w-5 h-5 shrink-0" style={{ color: 'var(--status-deploying)' }} />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-(--text-muted) uppercase tracking-widest font-bold">Source Template</p>
            {instance.templateName
              ? <p className="text-sm font-semibold text-(--text-primary) truncate">{instance.templateName}</p>
              : <p className="text-sm text-(--text-muted) italic">Template was deleted</p>
            }
          </div>
          {instance.templateName && (
            <Link
              to="/configurations"
              className="btn-secondary text-xs flex items-center gap-1.5 shrink-0"
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              View Configurations
            </Link>
          )}
        </div>
      )}

      {/* Connection string full widget */}
      {instance.connectionString && (
        <div className="card p-5">
          <p className="section-label">Full Connection String</p>
          <ConnectionString value={instance.connectionString} masked={instance.connectionStringMasked} />
        </div>
      )}

      {/* Detail sections */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 stagger-children">
        {sections.map(sec => sec.rows.length > 0 && (
          <div key={sec.title} className="card p-5 animate-fade-up">
            <div className="flex items-center gap-2 mb-4">
              <span className="text-[var(--text-muted)]">{sec.icon}</span>
              <p className="section-label mb-0">{sec.title}</p>
            </div>
            <div className="space-y-3">
              {sec.rows.map(row => (
                <ConfigRow
                  key={row.label}
                  row={row}
                  showPassword={showPassword}
                  setShowPassword={setShowPassword}
                  copied={copied}
                  onCopy={copyValue}
                />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function ConfigRow({ row, showPassword, setShowPassword, copied, onCopy }) {
  const isSecret  = row.secret
  const display   = isSecret ? (showPassword ? row.value : '••••••••••••') : row.value

  return (
    <div className="flex items-start justify-between gap-3 py-1">
      <span className="text-xs text-[var(--text-muted)] w-32 shrink-0 pt-0.5">{row.label}</span>
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <span className={`flex-1 truncate ${row.mono ? 'font-mono text-sm text-[var(--text-secondary)]' : 'text-sm text-[var(--text-secondary)]'} ${row.small ? 'text-xs' : ''}`}>
          {display}
        </span>
        {isSecret && (
          <button onClick={() => setShowPassword(s => !s)} className="text-[var(--text-muted)] hover:text-[var(--text-primary)] shrink-0">
            {showPassword ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
          </button>
        )}
        {row.copyKey && (
          <button onClick={() => onCopy(row.value, row.copyKey)} className="text-[var(--text-muted)] hover:text-[var(--text-primary)] shrink-0">
            {copied === row.copyKey
              ? <ClipboardCheck className="w-3.5 h-3.5 text-green-400" />
              : <Clipboard className="w-3.5 h-3.5" />}
          </button>
        )}
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Pipeline Tab                                                               */
/* ─────────────────────────────────────────────────────────────────────────── */
const STEP_LABELS = {
  IMAGE_PULL:        'Pull Image',
  CONTAINER_CREATE:  'Create Container',
  CONTAINER_START:   'Start Container',
  FINALISE:          'Finalise',
}

function PipelineTab({ instanceId, instance }) {
  const [pipeline, setPipeline] = useState(null)
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState(null)

  const isActive = ['DEPLOYING', 'REMOVING'].includes(instance?.status)

  const load = useCallback(async () => {
    try {
      const data = await getPipeline(instanceId)
      setPipeline(data)
      setError(null)
    } catch (e) {
      if (e.response?.status === 404) {
        setPipeline(null)
        setError(null)
      } else {
        setError('Failed to load pipeline data')
      }
    } finally {
      setLoading(false)
    }
  }, [instanceId])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    return () => clearTimeout(kick)
  }, [load])

  // Poll while instance is actively deploying
  useEffect(() => {
    if (!isActive) return
    const t = setInterval(load, 2_000)
    return () => clearInterval(t)
  }, [isActive, load])

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-12 text-[var(--text-muted)] justify-center">
        <div className="w-4 h-4 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
        Loading pipeline…
      </div>
    )
  }

  if (error) {
    return (
      <div className="card p-6 text-center text-[var(--status-error)] text-sm">{error}</div>
    )
  }

  if (!pipeline) {
    return (
      <div className="card p-10 text-center">
        <Rocket className="w-10 h-10 text-[var(--text-quiet)] mx-auto mb-3" />
        <p className="text-[var(--text-muted)] text-sm">No pipeline found for this instance.</p>
        <p className="text-[var(--text-quiet)] text-xs mt-1">Pipeline data is recorded the first time this instance is deployed.</p>
      </div>
    )
  }

  const pipelineToken = PIPELINE_STATUS_TOKENS[pipeline.status] ?? PIPELINE_STATUS_TOKENS.PENDING
  const steps = pipeline.steps ?? []
  const completedSteps = steps.filter(s => s.status === 'SUCCESS').length
  const totalSteps = steps.length
  const progressPct = totalSteps > 0 ? Math.round((completedSteps / totalSteps) * 100) : 0

  const fmt = (iso) => iso ? new Date(iso).toLocaleTimeString() : '—'
  const dur = (start, end) => {
    if (!start) return null
    const ms = (end ? new Date(end) : new Date()) - new Date(start)
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(1)}s`
  }

  return (
    <div className="space-y-5">
      {/* Pipeline summary card */}
      <div className="card p-5">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span
                className="text-xs font-semibold px-2 py-0.5 rounded border"
                style={{
                  background: pipelineToken.background,
                  borderColor: pipelineToken.border,
                  color: pipelineToken.text,
                }}
              >
                {pipeline.status}
              </span>
              <span className="text-xs text-[var(--text-muted)] font-mono">#{pipeline.id?.slice(-8)}</span>
            </div>
            <p className="text-[var(--text-primary)] font-medium text-sm">Deploy Pipeline</p>
            <p className="text-xs text-[var(--text-muted)] mt-0.5">
              Started {fmt(pipeline.startedAt)}
              {pipeline.completedAt && <> · Finished {fmt(pipeline.completedAt)}</>}
              {pipeline.startedAt && (
                <> · <span className="text-[var(--text-secondary)]">{dur(pipeline.startedAt, pipeline.completedAt)}</span></>
              )}
            </p>
          </div>
          <button onClick={load} className="btn-ghost flex items-center gap-1.5 text-xs">
            <RefreshCw className="w-3.5 h-3.5" />
            Refresh
          </button>
        </div>

        {/* Progress bar */}
        {pipeline.status === 'RUNNING' && (
          <div className="mt-4">
            <div className="flex justify-between text-xs text-[var(--text-muted)] mb-1.5">
              <span>{completedSteps} / {totalSteps} steps</span>
              <span>{progressPct}%</span>
            </div>
            <div className="h-1.5 bg-white/[0.06] rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500 rounded-full transition-all duration-500"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          </div>
        )}
        {pipeline.status === 'SUCCESS' && (
          <div className="mt-4 h-1.5 bg-green-500/30 rounded-full overflow-hidden">
            <div className="h-full bg-green-500 rounded-full w-full" />
          </div>
        )}
        {pipeline.status === 'FAILED' && pipeline.errorCode && (
          <div className="mt-3 px-3 py-2 rounded-lg bg-red-500/10 border border-red-500/20 text-xs text-red-300 font-mono">
            Error: {pipeline.errorCode}
            {pipeline.errorMessage && <p className="mt-1 text-red-400/70">{pipeline.errorMessage}</p>}
          </div>
        )}
      </div>

      {/* Step list */}
      <div className="card overflow-hidden">
        <div className="px-5 py-3 border-b border-white/[0.06] bg-white/[0.02]">
          <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider">Steps</p>
        </div>
        <div className="divide-y divide-white/[0.04]">
          {steps.length === 0 && (
            <p className="text-xs text-[var(--text-muted)] px-5 py-4">No steps recorded.</p>
          )}
          {steps.map((step, idx) => {
            const c = PIPELINE_STEP_TOKENS[step.status] ?? PIPELINE_STEP_TOKENS.PENDING
            const isRunning = step.status === 'RUNNING'
            const label = STEP_LABELS[step.stepType] ?? step.stepType
            return (
              <div key={step.id ?? idx} className="flex items-start gap-4 px-5 py-4">
                {/* Step indicator */}
                <div className="flex flex-col items-center pt-0.5 shrink-0">
                  <div
                    className={`w-3 h-3 rounded-full border-2 ${isRunning ? 'animate-pulse' : ''}`}
                    style={{ backgroundColor: c.dot, borderColor: c.dot }}
                  />
                  {idx < steps.length - 1 && (
                    <div className="w-px h-full min-h-[28px] bg-white/[0.06] mt-1" />
                  )}
                </div>

                {/* Step details */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm text-[var(--text-primary)] font-medium">{label}</span>
                    <span
                      className="text-[10px] font-bold px-1.5 py-0.5 rounded border"
                      style={{
                        background: c.background,
                        borderColor: c.border,
                        color: c.text,
                      }}
                    >
                      {step.status}
                    </span>
                    {isRunning && (
                      <span className="w-3 h-3 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
                    )}
                  </div>
                  <div className="flex items-center gap-3 mt-1 text-xs text-[var(--text-muted)] flex-wrap">
                    {step.startedAt && <span>Started {fmt(step.startedAt)}</span>}
                    {step.completedAt && <span>· Finished {fmt(step.completedAt)}</span>}
                    {step.startedAt && (
                      <span className="text-[var(--text-secondary)]">· {dur(step.startedAt, step.completedAt)}</span>
                    )}
                  </div>
                  {step.errorMessage && (
                    <p className="mt-1.5 text-xs text-red-400 font-mono bg-red-500/5 border border-red-500/10 rounded px-2 py-1.5">
                      {step.errorMessage}
                    </p>
                  )}
                </div>

                {/* Step number */}
                <span className="text-xs text-[var(--text-quiet)] font-mono shrink-0 pt-0.5">
                  {String(idx + 1).padStart(2, '0')}
                </span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
function LogsTab({ instanceId, isRunning }) {
  const [logs, setLogs]         = useState('')
  const [loading, setLoading]   = useState(false)
  const [tail, setTail]         = useState(100)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const bottomRef               = useRef(null)

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    try {
      const data = await getLogs(instanceId, tail)
      setLogs(data.logs ?? '(no logs)')
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
    } catch {
      setLogs('Failed to fetch logs')
    } finally {
      setLoading(false)
    }
  }, [instanceId, tail])

  useEffect(() => {
    const kick = setTimeout(() => fetchLogs(), 0)
    return () => clearTimeout(kick)
  }, [fetchLogs])

  useEffect(() => {
    if (!autoRefresh) return
    const t = setInterval(fetchLogs, 5_000)
    return () => clearInterval(t)
  }, [autoRefresh, fetchLogs])

  const TAIL_OPTIONS = [50, 100, 200, 500]

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-[var(--text-muted)]">Last</span>
          {TAIL_OPTIONS.map(n => (
            <button key={n} onClick={() => setTail(n)}
              className={`px-2.5 py-1 rounded-[4px] text-xs font-medium transition-colors border-2 ${
                tail === n
                  ? 'bg-[var(--accent)] border-[var(--border-strong)] text-[var(--text-inverse)]'
                  : 'bg-[var(--bg-surface-2)] text-[var(--text-muted)] hover:text-[var(--text-primary)] border-[var(--border-strong)]'
              }`}>
              {n}
            </button>
          ))}
          <span className="text-xs text-[var(--text-muted)]">lines</span>
        </div>
        <button onClick={fetchLogs} disabled={loading}
          className="btn-ghost flex items-center gap-1.5 text-xs">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
        <label className="flex items-center gap-2 text-xs text-[var(--text-muted)] cursor-pointer select-none">
          <span className={`relative inline-block w-8 h-4 rounded-full transition-colors ${autoRefresh ? 'bg-blue-600' : 'bg-white/[0.10]'}`}>
            <span className={`absolute top-0.5 w-3 h-3 bg-white rounded-full shadow transition-transform ${autoRefresh ? 'translate-x-4' : 'translate-x-0.5'}`} />
            <input type="checkbox" className="sr-only" checked={autoRefresh} onChange={e => setAutoRefresh(e.target.checked)} />
          </span>
          Auto-refresh every 5s
        </label>
        {!isRunning && (
          <span className="text-xs text-yellow-400 bg-yellow-500/10 border border-yellow-500/20 px-2.5 py-1 rounded-lg">
            Instance is not running — logs may be stale
          </span>
        )}
      </div>

      {/* Log output */}
      <div className="card overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.06] bg-white/[0.02]">
          <div className="flex items-center gap-2">
            <div className="w-2.5 h-2.5 rounded-full bg-red-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-yellow-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-green-500/70" />
          </div>
          <span className="text-xs text-[var(--text-muted)] font-mono">container logs</span>
          <button
            onClick={async () => {
              await navigator.clipboard.writeText(logs)
              toast.success('Logs copied')
            }}
            className="text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)] flex items-center gap-1 transition-colors">
            <Clipboard className="w-3.5 h-3.5" />
            Copy
          </button>
        </div>
        <pre className="text-xs font-mono px-5 py-4 h-[500px] overflow-y-auto whitespace-pre-wrap leading-relaxed" style={{
          background: 'var(--bg-inset)',
          color: 'var(--status-running)',
        }}>
          {loading && !logs ? 'Loading…' : logs}
          <span ref={bottomRef} />
        </pre>
      </div>
    </div>
  )
}
