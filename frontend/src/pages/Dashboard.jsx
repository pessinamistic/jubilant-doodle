import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getInstances, syncStatuses } from '../api/client'
import { InstanceCard } from '../components/InstanceCard'
import { SystemBanner } from '../components/SystemBanner'
import { PlusIcon, ArrowPathIcon, MagnifyingGlassIcon } from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

const STATUS_FILTERS = ['ALL', 'RUNNING', 'STOPPED', 'DEPLOYING', 'ERROR']

export function Dashboard() {
  const navigate = useNavigate()
  const [instances, setInstances]     = useState([])
  const [loading, setLoading]         = useState(true)
  const [syncing, setSyncing]         = useState(false)
  const [search, setSearch]           = useState('')
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

  useEffect(() => {
    load()
    // Auto-refresh every 10s to pick up DEPLOYING → RUNNING transitions
    const t = setInterval(load, 10_000)
    return () => clearInterval(t)
  }, [load])

  const handleSync = async () => {
    setSyncing(true)
    try {
      await syncStatuses()
      await load()
      toast.success('Statuses synced')
    } finally {
      setSyncing(false)
    }
  }

  const filtered = instances
    .filter(i => statusFilter === 'ALL' || i.status === statusFilter)
    .filter(i => !search || i.name.toLowerCase().includes(search.toLowerCase())
                          || i.dbTypeDisplay.toLowerCase().includes(search.toLowerCase()))

  const runningCount = instances.filter(i => i.status === 'RUNNING').length

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Top nav */}
      <header className="bg-white border-b border-gray-100 sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-2xl">🗄️</span>
            <div>
              <h1 className="text-lg font-bold text-gray-900 leading-tight">DB Deployer</h1>
              <p className="text-xs text-gray-400">Local database manager</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-500">
              {runningCount} running · {instances.length} total
            </span>
            <button onClick={handleSync} disabled={syncing}
              className="btn-secondary flex items-center gap-1.5">
              <ArrowPathIcon className={`w-4 h-4 ${syncing ? 'animate-spin' : ''}`} />
              Sync
            </button>
            <button onClick={() => navigate('/deploy')}
              className="btn-primary flex items-center gap-1.5">
              <PlusIcon className="w-4 h-4" />
              Deploy DB
            </button>
          </div>
        </div>
      </header>

      {/* Main content */}
      <main className="max-w-6xl mx-auto px-6 py-8">
        <SystemBanner />

        {/* Filters */}
        <div className="flex items-center gap-4 mb-6 flex-wrap">
          <div className="relative flex-1 min-w-48">
            <MagnifyingGlassIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input type="text" value={search} onChange={e => setSearch(e.target.value)}
              placeholder="Search instances…"
              className="input pl-9 w-full" />
          </div>
          <div className="flex gap-2">
            {STATUS_FILTERS.map(s => (
              <button key={s} onClick={() => setStatusFilter(s)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                  statusFilter === s
                    ? 'bg-blue-600 text-white'
                    : 'bg-white border border-gray-200 text-gray-600 hover:border-blue-300'
                }`}>
                {s}
              </button>
            ))}
          </div>
        </div>

        {/* Instance grid */}
        {loading ? (
          <div className="flex items-center justify-center py-20 text-gray-400">
            <ArrowPathIcon className="w-6 h-6 animate-spin mr-2" /> Loading…
          </div>
        ) : filtered.length === 0 ? (
          <EmptyState onDeploy={() => navigate('/deploy')} hasInstances={instances.length > 0} />
        ) : (
          <div className="flex flex-col gap-4">
            {filtered.map(instance => (
              <InstanceCard key={instance.id} instance={instance} onRefresh={load} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

function EmptyState({ onDeploy, hasInstances }) {
  return (
    <div className="text-center py-24">
      <div className="text-6xl mb-4">{hasInstances ? '🔍' : '🗄️'}</div>
      <h2 className="text-xl font-semibold text-gray-700 mb-2">
        {hasInstances ? 'No matches found' : 'No databases yet'}
      </h2>
      <p className="text-gray-400 mb-6">
        {hasInstances
          ? 'Try clearing the search or filter'
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
