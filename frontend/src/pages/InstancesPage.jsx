import { useState, useEffect, useCallback } from 'react'
import { getInstances, deployInstance } from '../api/client'
import { AppShell } from '../components/AppShell'
import { InstanceCard } from '../components/InstanceCard'
import { DeployModal } from '../components/DeployModal'
import { ImportModal } from '../components/ImportModal'
import {
  PlusIcon, MagnifyingGlassIcon, CloudArrowDownIcon,
  CircleStackIcon, PlayIcon, StopCircleIcon,
  ArrowPathIcon, ExclamationTriangleIcon,
} from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

const STATUS_FILTERS = ['ALL', 'RUNNING', 'STOPPED', 'DEPLOYING', 'ERROR']

export function InstancesPage() {
  const [instances, setInstances]       = useState([])
  const [loading, setLoading]           = useState(true)
  const [showModal, setShowModal]       = useState(false)
  const [showImport, setShowImport]     = useState(false)
  const [search, setSearch]             = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const load = useCallback(async () => {
    try {
      const data = await getInstances()
      setInstances(data)
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

  // Derived stats
  const total     = instances.length
  const running   = instances.filter(i => i.status === 'RUNNING').length
  const stopped   = instances.filter(i => i.status === 'STOPPED').length
  const deploying = instances.filter(i => i.status === 'DEPLOYING').length
  const errored   = instances.filter(i => i.status === 'ERROR').length

  const stats = [
    {
      label: 'Total',
      value: total,
      icon: <CircleStackIcon className="w-4 h-4" />,
      color: 'text-blue-400',
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/20',
    },
    {
      label: 'Running',
      value: running,
      icon: <PlayIcon className="w-4 h-4" />,
      color: 'text-green-400',
      bg: 'bg-green-500/10',
      border: 'border-green-500/20',
      pulse: running > 0,
    },
    {
      label: 'Stopped',
      value: stopped,
      icon: <StopCircleIcon className="w-4 h-4" />,
      color: 'text-gray-400',
      bg: 'bg-gray-500/10',
      border: 'border-gray-500/20',
    },
    {
      label: 'Deploying',
      value: deploying,
      icon: <ArrowPathIcon className={`w-4 h-4 ${deploying > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-blue-300',
      bg: 'bg-blue-400/10',
      border: 'border-blue-400/20',
    },
    {
      label: 'Errors',
      value: errored,
      icon: <ExclamationTriangleIcon className="w-4 h-4" />,
      color: errored > 0 ? 'text-red-400' : 'text-gray-600',
      bg: errored > 0 ? 'bg-red-500/10' : 'bg-gray-500/5',
      border: errored > 0 ? 'border-red-500/20' : 'border-white/[0.06]',
    },
  ]

  const filtered = instances
    .filter(i => statusFilter === 'ALL' || i.status === statusFilter)
    .filter(i => !search
      || i.name.toLowerCase().includes(search.toLowerCase())
      || i.dbTypeDisplay?.toLowerCase().includes(search.toLowerCase()))

  return (
    <AppShell onDeploy={() => setShowModal(true)} onRefresh={load}>

      {/* ── Page header ── */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-semibold text-white">Instances</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {total} total &nbsp;·&nbsp; {running} running
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
      <section className="mb-8">
        <p className="section-label">Overview</p>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          {stats.map(s => (
            <div
              key={s.label}
              className={`stat-card border ${s.border}`}
            >
              <div className={`w-8 h-8 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>
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

      {/* ── Search + status filter ── */}
      <div className="flex items-center gap-3 mb-6 flex-wrap">
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
      ) : filtered.length === 0 ? (
        <EmptyState onDeploy={() => setShowModal(true)} hasInstances={instances.length > 0} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map(instance => (
            <InstanceCard key={instance.id} instance={instance} onRefresh={load} />
          ))}
        </div>
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
    <div className="card p-16 text-center">
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
