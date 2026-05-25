import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  getInstance, startInstance, stopInstance, removeInstance, getLogs,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusBadge } from '../components/StatusBadge'
import { ConnectionString } from '../components/ConnectionString'
import {
  ArrowLeftIcon, PlayIcon, StopIcon, TrashIcon,
  ArrowPathIcon, ChartBarIcon, Cog6ToothIcon, DocumentTextIcon,
  ServerIcon, ClockIcon, HashtagIcon, CircleStackIcon,
  FolderIcon, KeyIcon, UserIcon, GlobeAltIcon,
  EyeIcon, EyeSlashIcon, ClipboardDocumentIcon, ClipboardDocumentCheckIcon,
} from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

const TABS = [
  { id: 'overview',       label: 'Overview',       icon: <ChartBarIcon className="w-4 h-4" /> },
  { id: 'configuration',  label: 'Configuration',  icon: <Cog6ToothIcon className="w-4 h-4" /> },
  { id: 'logs',           label: 'Logs',            icon: <DocumentTextIcon className="w-4 h-4" /> },
]

export function InstanceDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [instance, setInstance] = useState(null)
  const [loading, setLoading]   = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [busy, setBusy]         = useState(false)

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
    load()
    const t = setInterval(load, 8_000)
    return () => clearInterval(t)
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

  if (loading) {
    return (
      <AppShell>
        <div className="flex items-center justify-center py-32 text-gray-500 gap-2">
          <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          Loading instance…
        </div>
      </AppShell>
    )
  }

  if (!instance) return null

  const isRunning = instance.status === 'RUNNING'
  const isStopped = instance.status === 'STOPPED'
  const isBusy    = ['DEPLOYING', 'REMOVING'].includes(instance.status) || busy

  return (
    <AppShell onRefresh={load}>
      {/* ── Breadcrumb ── */}
      <div className="flex items-center gap-2 text-sm text-gray-500 mb-6">
        <Link to="/instances" className="hover:text-white transition-colors flex items-center gap-1">
          <ArrowLeftIcon className="w-3.5 h-3.5" />
          Instances
        </Link>
        <span>/</span>
        <span className="text-gray-300">{instance.name}</span>
      </div>

      {/* ── Instance header ── */}
      <div className="card px-6 py-5 mb-6">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4">
            <div className="text-4xl">{instance.icon}</div>
            <div>
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-xl font-bold text-white">{instance.name}</h1>
                <StatusBadge status={instance.status} />
                {instance.isSystem && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase bg-violet-500/15 text-violet-300 border border-violet-500/25">
                    SYSTEM
                  </span>
                )}
              </div>
              <p className="text-sm text-gray-400 mt-1">
                {instance.dbTypeDisplay} {instance.version}
                &nbsp;·&nbsp;
                Port <span className="font-mono text-gray-200">{instance.hostPort}</span>
                {instance.containerName && (
                  <>&nbsp;·&nbsp;<span className="font-mono text-gray-500 text-xs">{instance.containerName}</span></>
                )}
              </p>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex items-center gap-2">
            <button onClick={load} disabled={isBusy}
              className="btn-ghost flex items-center gap-1.5">
              <ArrowPathIcon className={`w-4 h-4 ${busy ? 'animate-spin' : ''}`} />
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
                <PlayIcon className="w-4 h-4" /> Start
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
                <StopIcon className="w-4 h-4" /> Stop
              </button>
            )}
            {!instance.isSystem && (
              <button
                onClick={handle(removeInstance, 'Remove', true)}
                disabled={isBusy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors disabled:opacity-40">
                <TrashIcon className="w-4 h-4" /> Remove
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Tab bar ── */}
      <div className="tab-bar mb-6 w-fit">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`tab-item ${activeTab === t.id ? 'tab-active' : 'tab-inactive'}`}>
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      {activeTab === 'overview'      && <OverviewTab      instance={instance} />}
      {activeTab === 'configuration' && <ConfigurationTab instance={instance} />}
      {activeTab === 'logs'          && <LogsTab          instanceId={id} isRunning={isRunning} />}
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
    const diff = Date.now() - new Date(base).getTime()
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
      icon: <CircleStackIcon className="w-5 h-5" />,
      color: instance.status === 'RUNNING' ? 'text-green-400' : instance.status === 'ERROR' ? 'text-red-400' : 'text-gray-400',
      bg: instance.status === 'RUNNING' ? 'bg-green-500/10' : instance.status === 'ERROR' ? 'bg-red-500/10' : 'bg-gray-500/10',
    },
    {
      label: 'Uptime',
      value: uptime ?? '—',
      icon: <ClockIcon className="w-5 h-5" />,
      color: 'text-blue-400',
      bg: 'bg-blue-500/10',
    },
    {
      label: 'Host Port',
      value: instance.hostPort,
      icon: <GlobeAltIcon className="w-5 h-5" />,
      color: 'text-indigo-400',
      bg: 'bg-indigo-500/10',
    },
    {
      label: 'Version',
      value: instance.version,
      icon: <HashtagIcon className="w-5 h-5" />,
      color: 'text-purple-400',
      bg: 'bg-purple-500/10',
    },
    {
      label: 'Deploy Method',
      value: instance.deployMethod,
      icon: <ServerIcon className="w-5 h-5" />,
      color: 'text-cyan-400',
      bg: 'bg-cyan-500/10',
    },
  ]

  return (
    <div className="space-y-6">
      {/* Stat cards */}
      <div>
        <p className="section-label">Stats</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
          {statCards.map(s => (
            <div key={s.label} className="stat-card">
              <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>
                {s.icon}
              </div>
              <div>
                <div className="text-base font-bold text-white font-mono">{s.value}</div>
                <div className="text-xs text-gray-500 mt-0.5">{s.label}</div>
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
              <p className="text-white font-medium">
                {isRunning ? 'Healthy & Running' : instance.status === 'ERROR' ? 'Error State' : `Instance ${instance.status}`}
              </p>
              <p className="text-sm text-gray-400 mt-0.5">
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
                  <div className="text-xs text-gray-500 mb-1">{item.label}</div>
                  <div className="text-sm text-white font-mono truncate">{item.value}</div>
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
            <p className="text-xs text-gray-500 mt-2">
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
      icon: <KeyIcon className="w-4 h-4" />,
      rows: [
        instance.username    && { label: 'Username',      value: instance.username,    mono: true, copyKey: 'username' },
        instance.password    && { label: 'Password',      value: instance.password,    mono: true, secret: true, copyKey: 'password' },
        instance.databaseName && { label: 'Database Name', value: instance.databaseName, mono: true, copyKey: 'db' },
      ].filter(Boolean),
    },
    {
      title: 'Connection',
      icon: <GlobeAltIcon className="w-4 h-4" />,
      rows: [
        { label: 'Host',     value: 'localhost', mono: true },
        { label: 'Port',     value: String(instance.hostPort), mono: true, copyKey: 'port' },
        { label: 'DB Type',  value: instance.dbTypeDisplay },
        { label: 'Version',  value: instance.version },
      ],
    },
    {
      title: 'Container',
      icon: <ServerIcon className="w-4 h-4" />,
      rows: [
        instance.containerName && { label: 'Container Name', value: instance.containerName, mono: true, copyKey: 'container' },
        instance.containerId   && { label: 'Container ID',   value: instance.containerId.slice(0, 12), mono: true, copyKey: 'cid' },
        { label: 'Deploy Method', value: instance.deployMethod },
      ].filter(Boolean),
    },
    {
      title: 'Storage',
      icon: <FolderIcon className="w-4 h-4" />,
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
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {sections.map(sec => sec.rows.length > 0 && (
          <div key={sec.title} className="card p-5">
            <div className="flex items-center gap-2 mb-4">
              <span className="text-gray-400">{sec.icon}</span>
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
      <span className="text-xs text-gray-500 w-32 shrink-0 pt-0.5">{row.label}</span>
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <span className={`flex-1 truncate ${row.mono ? 'font-mono text-sm text-gray-200' : 'text-sm text-gray-300'} ${row.small ? 'text-xs' : ''}`}>
          {display}
        </span>
        {isSecret && (
          <button onClick={() => setShowPassword(s => !s)} className="text-gray-500 hover:text-gray-300 shrink-0">
            {showPassword ? <EyeSlashIcon className="w-3.5 h-3.5" /> : <EyeIcon className="w-3.5 h-3.5" />}
          </button>
        )}
        {row.copyKey && (
          <button onClick={() => onCopy(row.value, row.copyKey)} className="text-gray-500 hover:text-gray-300 shrink-0">
            {copied === row.copyKey
              ? <ClipboardDocumentCheckIcon className="w-3.5 h-3.5 text-green-400" />
              : <ClipboardDocumentIcon className="w-3.5 h-3.5" />}
          </button>
        )}
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Logs Tab                                                                   */
/* ─────────────────────────────────────────────────────────────────────────── */
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

  useEffect(() => { fetchLogs() }, [fetchLogs])

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
          <span className="text-xs text-gray-500">Last</span>
          {TAIL_OPTIONS.map(n => (
            <button key={n} onClick={() => setTail(n)}
              className={`px-2.5 py-1 rounded-md text-xs font-medium transition-colors ${
                tail === n
                  ? 'bg-blue-600 text-white'
                  : 'bg-white/[0.05] text-gray-400 hover:text-white border border-white/[0.08]'
              }`}>
              {n}
            </button>
          ))}
          <span className="text-xs text-gray-500">lines</span>
        </div>
        <button onClick={fetchLogs} disabled={loading}
          className="btn-ghost flex items-center gap-1.5 text-xs">
          <ArrowPathIcon className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
        <label className="flex items-center gap-2 text-xs text-gray-400 cursor-pointer select-none">
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
          <span className="text-xs text-gray-500 font-mono">container logs</span>
          <button
            onClick={async () => {
              await navigator.clipboard.writeText(logs)
              toast.success('Logs copied')
            }}
            className="text-xs text-gray-500 hover:text-gray-300 flex items-center gap-1 transition-colors">
            <ClipboardDocumentIcon className="w-3.5 h-3.5" />
            Copy
          </button>
        </div>
        <pre className="bg-[#0a0d14] text-green-300 text-xs font-mono px-5 py-4 h-[500px] overflow-y-auto whitespace-pre-wrap leading-relaxed">
          {loading && !logs ? 'Loading…' : logs}
          <span ref={bottomRef} />
        </pre>
      </div>
    </div>
  )
}
