import { useState, useEffect, useCallback } from 'react'
import { getInstances, getStats, deployInstance } from '../api/client'
import { AppShell } from '../components/AppShell'
import { InstanceCard } from '../components/InstanceCard'
import { DeployModal } from '../components/DeployModal'
import { ImportModal } from '../components/ImportModal'
import {
  PlusIcon, MagnifyingGlassIcon, CloudArrowDownIcon,
  CircleStackIcon, PlayIcon, StopCircleIcon,
  ArrowPathIcon, ExclamationTriangleIcon, ChevronDownIcon,
  TrashIcon, XCircleIcon,
} from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

const STATUS_FILTERS = ['ALL', 'RUNNING', 'STOPPED', 'DEPLOYING', 'ERROR', 'REMOVED']

export function InstancesPage() {
  const [instances, setInstances]       = useState([])
  const [stats, setStats]               = useState(null)
  const [loading, setLoading]           = useState(true)
  const [showModal, setShowModal]       = useState(false)
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
  useEffect(() => { load() }, [load])

  // Adaptive polling — 3 s when deploying/removing, 8 s otherwise
  useEffect(() => {
    const hasActive = instances.some(i => ['DEPLOYING', 'REMOVING'].includes(i.status))
    const t = setTimeout(load, hasActive ? 3_000 : 8_000)
    return () => clearTimeout(t)
  }, [instances, load])

  // handleDeploy is fire-and-forget (modal closes immediately)
  const handleDeploy = async (data) => {
    try {
      await deployInstance(data)
      toast.success(`Deploying ${data.name}… this may take a minute`)
    } catch (err) {
      toast.error(err.response?.data?.error ?? 'Deploy failed')
    }
    load()
  }

  // Split active vs removed
  const activeInstances  = instances.filter(i => i.status !== 'REMOVED')
  const removedInstances = instances.filter(i => i.status === 'REMOVED')

  const statCards = stats ? [
    {
      label: 'Total',
      value: stats.total,
      icon: <CircleStackIcon className="w-4 h-4" />,
      color: 'text-blue-400',
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/20',
    },
    {
      label: 'Running',
      value: stats.running,
      icon: <PlayIcon className="w-4 h-4" />,
      color: 'text-green-400',
      bg: 'bg-green-500/10',
      border: 'border-green-500/20',
      pulse: stats.running > 0,
    },
    {
      label: 'Stopped',
      value: stats.stopped,
      icon: <StopCircleIcon className="w-4 h-4" />,
      color: 'text-gray-400',
      bg: 'bg-gray-500/10',
      border: 'border-gray-500/20',
    },
    {
      label: 'Deploying',
      value: stats.deploying,
      icon: <ArrowPathIcon className={`w-4 h-4 ${stats.deploying > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-blue-300',
      bg: 'bg-blue-400/10',
      border: 'border-blue-400/20',
    },
    {
      label: 'Removing',
      value: stats.removing,
      icon: <ArrowPathIcon className={`w-4 h-4 ${stats.removing > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-orange-400',
      bg: 'bg-orange-500/10',
      border: 'border-orange-500/20',
    },
    {
      label: 'Errors',
      value: stats.error,
      icon: <ExclamationTriangleIcon className="w-4 h-4" />,
      color: stats.error > 0 ? 'text-red-400' : 'text-gray-600',
      bg: stats.error > 0 ? 'bg-red-500/10' : 'bg-gray-500/5',
      border: stats.error > 0 ? 'border-red-500/20' : 'border-white/[0.06]',
    },
    {
      label: 'Removed',
      value: stats.removed,
      icon: <XCircleIcon className="w-4 h-4" />,
      color: stats.removed > 0 ? 'text-gray-400' : 'text-gray-600',
      bg: 'bg-gray-500/10',
      border: 'border-gray-500/20',
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
    <AppShell onDeploy={() => setShowModal(true)} onRefresh={load}>

      {/* ── Page header ── */}
      <div className="flex items-center justify-between mb-6 animate-fade-up">
        <div>
          <h1 className="text-xl font-semibold text-white">Instances</h1>
          <p className="text-sm text-gray-500 mt-0.5">
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
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium bg-white/[0.05] border border-white/[0.08] text-gray-300 hover:text-white hover:border-white/[0.15] transition-all"
          >
            <CloudArrowDownIcon className="w-4 h-4" />
            Import
          </button>
          <button onClick={() => setShowModal(true)} className="btn-primary flex items-center gap-2">
            <PlusIcon className="w-4 h-4" />
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
              <div key={s.label} className={`stat-card border ${s.border} flex-1 min-w-[110px] animate-fade-up hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20 transition-all duration-200 group`}>
                <div className={`w-8 h-8 rounded-lg ${s.bg} flex items-center justify-center ${s.color} transition-transform duration-200 group-hover:scale-110`}>
                  {s.icon}
                </div>
                <div>
                  <div className={`text-2xl font-bold text-white tabular-nums ${s.pulse ? 'animate-pulse' : ''}`}>
                    {s.value}
                  </div>
                  <div className="text-xs text-gray-500 mt-0.5">{s.label}</div>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* ── Search + status filter ── */}
      <div className="flex items-center gap-3 mb-6 flex-wrap animate-fade-up delay-150">
        <div className="relative flex-1 min-w-52">
          <MagnifyingGlassIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" />
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
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                statusFilter === s
                  ? 'bg-blue-600 text-white shadow-lg shadow-blue-500/20'
                  : 'bg-white/[0.05] border border-white/[0.08] text-gray-400 hover:text-white hover:border-white/[0.15]'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* ── Card grid ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-gray-500 gap-2">
          <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          Loading instances…
        </div>
      ) : filtered.length === 0 && activeInstances.length === 0 && !showingRemoved ? (
        <EmptyState onDeploy={() => setShowModal(true)} hasInstances={false} />
      ) : filtered.length === 0 ? (
        <EmptyState onDeploy={() => setShowModal(true)} hasInstances={true} />
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
            className="flex items-center gap-2 text-sm text-gray-500 hover:text-gray-300 transition-colors mb-3 group"
          >
            <div className="flex items-center justify-center w-5 h-5 rounded bg-gray-500/10 border border-gray-500/20">
              <TrashIcon className="w-3 h-3" />
            </div>
            <span className="font-medium">Removed</span>
            <span className="text-xs text-gray-600 bg-white/[0.04] border border-white/[0.06] px-1.5 py-0.5 rounded-full">
              {removedInstances.length}
            </span>
            <ChevronDownIcon
              className={`w-3.5 h-3.5 transition-transform duration-200 ${showRemoved ? 'rotate-180' : ''}`}
            />
          </button>

          {showRemoved && (
            filteredRemoved.length === 0 ? (
              <p className="text-xs text-gray-600 pl-1">No removed instances match the current search.</p>
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

      {showModal && (
        <DeployModal onClose={() => setShowModal(false)} onDeploy={handleDeploy} />
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
      <h2 className="text-lg font-semibold text-white mb-2">
        {hasInstances ? 'No matches found' : 'No databases yet'}
      </h2>
      <p className="text-gray-500 text-sm mb-6">
        {hasInstances
          ? 'Try clearing the search or status filter'
          : 'Deploy your first database to get started'}
      </p>
      {!hasInstances && (
        <button onClick={onDeploy} className="btn-primary inline-flex items-center gap-2">
          <PlusIcon className="w-4 h-4" />
          Deploy a Database
        </button>
      )}
    </div>
  )
}
