import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  getInstance, startInstance, stopInstance, removeInstance, deployInstance, getLogs, getPipeline,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusBadge } from '../components/StatusBadge'
import { ConnectionString } from '../components/ConnectionString'
import { DeployModal } from '../components/DeployModal'
import { ImportModal } from '../components/ImportModal'
import {
  ArrowLeft,
  BarChart3,
  Clipboard,
  ClipboardCheck,
  Clock3,
  CornerUpLeft,
  Database,
  Eye,
  EyeOff,
  FileText,
  Folder,
  Globe,
  Hash,
  Key,
  Play,
  RefreshCw,
  Rocket,
  Server,
  Settings,
  Square,
  Trash2,
  Unlink,
} from 'lucide-react'
import toast from 'react-hot-toast'
import { PIPELINE_STATUS_TOKENS, PIPELINE_STEP_TOKENS } from '../theme/statusTokens'

const TABS = [
  { id: 'overview',       label: 'Overview',       icon: <BarChart3 className="w-4 h-4" /> },
  { id: 'pipeline',       label: 'Pipeline',        icon: <Rocket className="w-4 h-4" /> },
  { id: 'configuration',  label: 'Configuration',  icon: <Settings className="w-4 h-4" /> },
  { id: 'logs',           label: 'Logs',            icon: <FileText className="w-4 h-4" /> },
]

export function InstanceDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [instance, setInstance]   = useState(null)
  const [loading, setLoading]     = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [busy, setBusy]           = useState(false)
  const [showDeployModal, setShowDeployModal] = useState(false)
  const [showReImport, setShowReImport] = useState(false)

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
    if (!confirm(`${label} "${instance?.name}"?`)) return
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

  const handleDeploy = async (data) => {
    try {
      await deployInstance(data)
      toast.success(`Deploying ${data.name}… this may take a minute`)
      load()
    } catch (err) {
      // Allow DeployModal to map backend errors to inline field-level messages.
      throw err
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

  const isRunning = instance.status === 'RUNNING'
  const isStopped = instance.status === 'STOPPED'
  const isRemoved = instance.status === 'REMOVED'
  const isBusy    = ['DEPLOYING', 'REMOVING'].includes(instance.status) || busy

  return (
    <AppShell onDeploy={() => setShowDeployModal(true)} onRefresh={load}>
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
                Port <span className="font-mono text-[var(--text-secondary)]">{instance.hostPort}</span>
                {instance.containerName && (
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
            {/* Re-import: only for imported instances that have been untracked */}
            {!instance.isSystem && instance.isImported && isRemoved && (
              <button
                onClick={() => setShowReImport(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20 transition-colors">
                <CornerUpLeft className="w-4 h-4" /> Re-import
              </button>
            )}
            {/* Remove/Untrack: hidden when already removed */}
            {!instance.isSystem && !isRemoved && (
              <button
                onClick={handle(removeInstance, instance.isImported ? 'Untrack' : 'Remove', true)}
                disabled={isBusy}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors disabled:opacity-40 ${
                  instance.isImported
                    ? 'bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20'
                    : 'bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20'
                }`}>
                {instance.isImported
                  ? <><Unlink className="w-4 h-4" /> Untrack</>
                  : <><Trash2 className="w-4 h-4" /> Remove</>
                }
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Tab bar ── */}
      <div className="tab-bar mb-6 w-fit animate-fade-up delay-100">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`tab-item ${activeTab === t.id ? 'tab-active' : 'tab-inactive'}`}>
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      <div key={activeTab} className="animate-fade-up">
        {activeTab === 'overview'      && <OverviewTab      instance={instance} />}
        {activeTab === 'pipeline'      && <PipelineTab      instanceId={id} instance={instance} />}
        {activeTab === 'configuration' && <ConfigurationTab instance={instance} />}
        {activeTab === 'logs'          && <LogsTab          instanceId={id} isRunning={isRunning} />}
      </div>

      {showReImport && (
        <ImportModal
          onClose={() => setShowReImport(false)}
          onImported={() => { setShowReImport(false); load() }}
          reImportInstance={instance}
        />
      )}

      {showDeployModal && (
        <DeployModal
          onClose={() => setShowDeployModal(false)}
          onDeploy={handleDeploy}
        />
      )}
    </AppShell>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Overview Tab                                                               */
/* ─────────────────────────────────────────────────────────────────────────── */
function OverviewTab({ instance }) {
  const isRunning = instance.status === 'RUNNING'

  const uptime = (() => {
    if (!isRunning) return null
    const base = instance.startedAt ?? instance.createdAt
    if (!base) return null
    const end = instance.updatedAt ?? instance.startedAt ?? instance.createdAt
    const diff = Math.max(0, new Date(end).getTime() - new Date(base).getTime())
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
    {
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
  ]

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
                  ? `Container has been running for ${uptime ?? 'unknown'}. Accepting connections on port ${instance.hostPort}.`
                  : instance.status === 'STOPPED'
                    ? 'Container is stopped. Click Start to bring it back online.'
                    : instance.status === 'DEPLOYING'
                      ? 'Container is being deployed. This may take a few moments.'
                      : 'Container encountered an error. Check the Logs tab for details.'}
              </p>
            </div>
          </div>
          {isRunning && (
            <div className="mt-4 grid grid-cols-3 gap-3 text-center">
              {[
                { label: 'Container', value: instance.containerName ?? '—' },
                { label: 'Created', value: new Date(instance.createdAt).toLocaleDateString() },
                { label: 'Last Updated', value: new Date(instance.updatedAt ?? instance.createdAt).toLocaleTimeString() },
              ].map(item => (
                <div key={item.label} className="bg-white/[0.03] rounded-lg p-3">
                  <div className="text-xs text-[var(--text-muted)] mb-1">{item.label}</div>
                  <div className="text-sm text-[var(--text-primary)] font-mono truncate">{item.value}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Connection string quick-access */}
      {instance.connectionString && (
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
