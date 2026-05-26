import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getInstances, getStats } from '../api/client'
import { AppShell } from '../components/AppShell'
import { InstanceCard } from '../components/InstanceCard'
import { ImportModal } from '../components/ImportModal'
import {
  ChevronDown,
  CircleOff,
  CircleX,
  CloudDownload,
  Database,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Trash2,
  TriangleAlert,
} from 'lucide-react'
import toast from 'react-hot-toast'

const STATUS_FILTERS = ['ALL', 'RUNNING', 'RESTARTING', 'STOPPED', 'DEPLOYING', 'REMOVING', 'ERROR', 'REMOVED']

const STATUS_SORT_ORDER = {
  RUNNING: 0,
  RESTARTING: 1,
  STOPPED: 2,
  DEPLOYING: 3,
  REMOVING: 4,
  ERROR: 5,
  REMOVED: 6,
}

function getStatusSortOrder(status) {
  return STATUS_SORT_ORDER[status] ?? 99
}

function sortInstances(a, b) {
  const statusDiff = getStatusSortOrder(a.status) - getStatusSortOrder(b.status)
  if (statusDiff !== 0) return statusDiff

  const nameDiff = a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  if (nameDiff !== 0) return nameDiff

  return String(a.id).localeCompare(String(b.id))
}

export function InstancesPage() {
  const navigate = useNavigate()
  const [instances, setInstances]       = useState([])
  const [stats, setStats]               = useState(null)
  const [loading, setLoading]           = useState(true)
  const [showImport, setShowImport]     = useState(false)
  const [search, setSearch]             = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [showRemoved, setShowRemoved]   = useState(false)

  const load = useCallback(async () => {
    try {
      const [data, statsData] = await Promise.all([getInstances(), getStats()])
      setInstances(data)
      setStats(statsData)
    } catch {
      toast.error('Failed to load instances')
    } finally {
      setLoading(false)
    }
  }, [])

  // Initial load
  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    return () => clearTimeout(kick)
  }, [load])

  // Adaptive polling — 3 s when deploying/removing, 8 s otherwise
  useEffect(() => {
    const hasActive = instances.some(i => ['DEPLOYING', 'REMOVING'].includes(i.status))
    const t = setTimeout(load, hasActive ? 3_000 : 8_000)
    return () => clearTimeout(t)
  }, [instances, load])

  // Split active vs removed after applying status and name sort.
  const sortedInstances  = [...instances].sort(sortInstances)
  const activeInstances  = sortedInstances.filter(i => i.status !== 'REMOVED')
  const removedInstances = sortedInstances.filter(i => i.status === 'REMOVED')

  const statCards = stats ? [
    {
      label: 'Total',
      filter: 'ALL',
      value: stats.total,
      icon: <Database className="w-4 h-4" />,
      color: 'text-[var(--status-deploying)]',
      bg: 'bg-[var(--status-deploying-bg)]',
      border: 'border-[var(--status-deploying-border)]',
    },
    {
      label: 'Running',
      filter: 'RUNNING',
      value: stats.running,
      icon: <Play className="w-4 h-4" />,
      color: 'text-[var(--status-running)]',
      bg: 'bg-[var(--status-running-bg)]',
      border: 'border-[var(--status-running-border)]',
      pulse: stats.running > 0,
    },
    {
      label: 'Restarting',
      filter: 'RESTARTING',
      value: stats.restarting ?? 0,
      icon: <RotateCcw className={`w-4 h-4 ${(stats.restarting ?? 0) > 0 ? 'animate-spin' : ''}`} />,
      color: (stats.restarting ?? 0) > 0 ? 'text-[var(--status-restarting)]' : 'text-[var(--status-stopped)]',
      bg: (stats.restarting ?? 0) > 0 ? 'bg-[var(--status-restarting-bg)]' : 'bg-[var(--status-stopped-bg)]',
      border: (stats.restarting ?? 0) > 0 ? 'border-[var(--status-restarting-border)]' : 'border-[var(--status-stopped-border)]',
    },
    {
      label: 'Stopped',
      filter: 'STOPPED',
      value: stats.stopped,
      icon: <CircleOff className="w-4 h-4" />,
      color: 'text-[var(--status-stopped)]',
      bg: 'bg-[var(--status-stopped-bg)]',
      border: 'border-[var(--status-stopped-border)]',
    },
    {
      label: 'Deploying',
      filter: 'DEPLOYING',
      value: stats.deploying,
      icon: <RefreshCw className={`w-4 h-4 ${stats.deploying > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-[var(--status-deploying)]',
      bg: 'bg-[var(--status-deploying-bg)]',
      border: 'border-[var(--status-deploying-border)]',
    },
    {
      label: 'Removing',
      filter: 'REMOVING',
      value: stats.removing,
      icon: <RefreshCw className={`w-4 h-4 ${stats.removing > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-[var(--status-removing)]',
      bg: 'bg-[var(--status-removing-bg)]',
      border: 'border-[var(--status-removing-border)]',
    },
    {
      label: 'Errors',
      filter: 'ERROR',
      value: stats.error,
      icon: <TriangleAlert className="w-4 h-4" />,
      color: stats.error > 0 ? 'text-[var(--status-error)]' : 'text-[var(--status-stopped)]',
      bg: stats.error > 0 ? 'bg-[var(--status-error-bg)]' : 'bg-[var(--status-stopped-bg)]',
      border: stats.error > 0 ? 'border-[var(--status-error-border)]' : 'border-[var(--status-stopped-border)]',
    },
    {
      label: 'Removed',
      filter: 'REMOVED',
      value: stats.removed,
      icon: <CircleX className="w-4 h-4" />,
      color: stats.removed > 0 ? 'text-[var(--status-removed)]' : 'text-[var(--status-stopped)]',
      bg: 'bg-[var(--status-removed-bg)]',
      border: 'border-[var(--status-removed-border)]',
    },
  ] : []

  // When REMOVED filter is active, show removed list; otherwise show active list
  const showingRemoved = statusFilter === 'REMOVED'

  const filtered = (showingRemoved ? removedInstances : activeInstances)
    .filter(i => showingRemoved || statusFilter === 'ALL' || i.status === statusFilter)
    .filter(i => !search
      || i.name.toLowerCase().includes(search.toLowerCase())
      || i.dbTypeDisplay?.toLowerCase().includes(search.toLowerCase()))

  const filteredRemoved = removedInstances.filter(i =>
    !search
    || i.name.toLowerCase().includes(search.toLowerCase())
    || i.dbTypeDisplay?.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <AppShell onRefresh={load}>

      {/* ── Page header ── */}
      <div className="flex items-center justify-between mb-6 animate-fade-up">
        <div>
          <h1 className="text-xl font-semibold text-[var(--text-primary)]">Instances</h1>
          <p className="text-sm text-[var(--text-muted)] mt-0.5">
            {stats ? (
              <>
                {stats.total} active &nbsp;·&nbsp; {stats.running} running
                {stats.removed > 0 && <> &nbsp;·&nbsp; {stats.removed} removed</>}
              </>
            ) : 'Loading…'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowImport(true)}
            className="btn-secondary text-sm"
          >
            <CloudDownload className="w-4 h-4" />
            Import
          </button>
          <button onClick={() => navigate('/deploy')} className="btn-primary flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Deploy DB
          </button>
        </div>
      </div>

      {/* ── Overview stats ── */}
      {statCards.length > 0 && (
        <section className="mb-8 animate-fade-up delay-100">
          <p className="section-label">Overview</p>
          <div className="flex flex-wrap gap-3 stagger-children">
            {statCards.map(s => (
              <button
                key={s.label}
                type="button"
                onClick={() => setStatusFilter(s.filter)}
                aria-pressed={statusFilter === s.filter}
                className={`stat-card border ${s.border} flex-1 min-w-[110px] animate-fade-up hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20 transition-all duration-200 group text-left cursor-pointer ${statusFilter === s.filter ? 'ring-2 ring-[var(--border-strong)] shadow-[var(--shadow-raised)]' : ''}`}
              >
                <div className={`w-8 h-8 rounded-lg ${s.bg} flex items-center justify-center ${s.color} transition-transform duration-200 group-hover:scale-110`}>
                  {s.icon}
                </div>
                <div>
                  <div className={`text-2xl font-bold text-[var(--text-primary)] tabular-nums ${s.pulse ? 'animate-pulse' : ''}`}>
                    {s.value}
                  </div>
                  <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
                </div>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* ── Search + status filter ── */}
      <div className="flex items-center gap-3 mb-6 flex-wrap animate-fade-up delay-150">
        <div className="relative flex-1 min-w-52">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search by name or type…"
            className="input pl-9"
          />
        </div>
        <div className="flex gap-1.5 flex-wrap">
          {STATUS_FILTERS.map(s => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-colors border-2 ${
                statusFilter === s
                  ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)] shadow-[var(--shadow-raised)]'
                  : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* ── Card grid ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading instances…
        </div>
      ) : filtered.length === 0 && activeInstances.length === 0 && !showingRemoved ? (
        <EmptyState onDeploy={() => navigate('/deploy')} hasInstances={false} />
      ) : filtered.length === 0 ? (
        <EmptyState onDeploy={() => navigate('/deploy')} hasInstances={true} />
      ) : (
        <div className={`grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4 stagger-children animate-fade-up delay-200 ${showingRemoved ? 'opacity-60 hover:opacity-80 transition-opacity' : ''}`}>
          {filtered.map(instance => (
            <InstanceCard key={instance.id} instance={instance} onRefresh={load} className="animate-fade-up" />
          ))}
        </div>
      )}

      {/* ── Removed section (only shown when not already filtering by REMOVED) ── */}
      {!loading && !showingRemoved && (filteredRemoved.length > 0 || removedInstances.length > 0) && (
        <section className="mt-10">
          <button
            onClick={() => setShowRemoved(v => !v)}
            className="flex items-center gap-2 text-sm text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors mb-3 group"
          >
            <div className="flex items-center justify-center w-5 h-5 rounded-[4px] border" style={{
              background: 'var(--status-removed-bg)',
              borderColor: 'var(--status-removed-border)',
            }}>
              <Trash2 className="w-3 h-3" style={{ color: 'var(--status-removed)' }} />
            </div>
            <span className="font-medium">Removed</span>
            <span className="text-xs px-1.5 py-0.5 rounded-full border" style={{
              color: 'var(--text-muted)',
              background: 'var(--bg-surface-2)',
              borderColor: 'var(--border-strong)',
            }}>
              {removedInstances.length}
            </span>
            <ChevronDown
              className={`w-3.5 h-3.5 transition-transform duration-200 ${showRemoved ? 'rotate-180' : ''}`}
            />
          </button>

          {showRemoved && (
            filteredRemoved.length === 0 ? (
              <p className="text-xs text-[var(--text-muted)] pl-1">No removed instances match the current search.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4 opacity-60 hover:opacity-80 transition-opacity">
                {filteredRemoved.map(instance => (
                  <InstanceCard key={instance.id} instance={instance} onRefresh={load} />
                ))}
              </div>
            )
          )}
        </section>
      )}
      {showImport && (
        <ImportModal onClose={() => setShowImport(false)} onImported={load} />
      )}
    </AppShell>
  )
}

function EmptyState({ onDeploy, hasInstances }) {
  return (
    <div className="card p-16 text-center animate-scale-in">
      <div className="text-5xl mb-4">{hasInstances ? '🔍' : '🗄️'}</div>
      <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">
        {hasInstances ? 'No matches found' : 'No databases yet'}
      </h2>
      <p className="text-[var(--text-muted)] text-sm mb-6">
        {hasInstances
          ? 'Try clearing the search or status filter'
          : 'Deploy your first database to get started'}
      </p>
      {!hasInstances && (
        <button onClick={onDeploy} className="btn-primary inline-flex items-center gap-2">
          <Plus className="w-4 h-4" />
          Deploy a Database
        </button>
      )}
    </div>
  )
}
