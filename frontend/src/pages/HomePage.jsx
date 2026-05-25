import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getInstances, getStats, deployInstance } from '../api/client'
import { AppShell } from '../components/AppShell'
import { DeployModal } from '../components/DeployModal'
import { StatusBadge } from '../components/StatusBadge'
import {
  CircleStackIcon, PlayIcon, StopCircleIcon,
  ExclamationTriangleIcon, ArrowPathIcon, PlusIcon,
  BoltIcon, ClockIcon, CheckCircleIcon, Cog6ToothIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

export function HomePage() {
  const [instances, setInstances] = useState([])
  const [stats, setStats]         = useState(null)
  const [showModal, setShowModal] = useState(false)
  const navigate = useNavigate()

  const load = useCallback(async () => {
    try {
      const [inst, statsData] = await Promise.all([getInstances(), getStats()])
      setInstances(inst)
      setStats(statsData)
    } catch { /* silent */ }
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(load, 10_000)
    return () => clearInterval(t)
  }, [load])

  const handleDeploy = async (data) => {
    await deployInstance(data)
    toast.success(`Deploying ${data.name}…`)
    load()
  }

  // Recent = last 5 active instances, sorted by createdAt descending
  const recent = [...instances]
    .filter(i => i.status !== 'REMOVED')
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 5)

  const statCards = stats ? [
    {
      label: 'Total Instances',
      value: stats.total,
      icon: <CircleStackIcon className="w-5 h-5" />,
      color: 'text-blue-400',
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/20',
    },
    {
      label: 'Running',
      value: stats.running,
      icon: <PlayIcon className="w-5 h-5" />,
      color: 'text-green-400',
      bg: 'bg-green-500/10',
      border: 'border-green-500/20',
      pulse: stats.running > 0,
    },
    {
      label: 'Stopped',
      value: stats.stopped,
      icon: <StopCircleIcon className="w-5 h-5" />,
      color: 'text-gray-400',
      bg: 'bg-gray-500/10',
      border: 'border-gray-500/20',
    },
    {
      label: 'Deploying',
      value: stats.deploying,
      icon: <ArrowPathIcon className={`w-5 h-5 ${stats.deploying > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-blue-300',
      bg: 'bg-blue-400/10',
      border: 'border-blue-400/20',
    },
    {
      label: 'Removing',
      value: stats.removing,
      icon: <ArrowPathIcon className={`w-5 h-5 ${stats.removing > 0 ? 'animate-spin' : ''}`} />,
      color: 'text-orange-400',
      bg: 'bg-orange-500/10',
      border: 'border-orange-500/20',
    },
    {
      label: 'Errors',
      value: stats.error,
      icon: <ExclamationTriangleIcon className="w-5 h-5" />,
      color: stats.error > 0 ? 'text-red-400' : 'text-gray-600',
      bg: stats.error > 0 ? 'bg-red-500/10' : 'bg-gray-500/5',
      border: stats.error > 0 ? 'border-red-500/20' : 'border-white/[0.06]',
    },
    {
      label: 'Removed',
      value: stats.removed,
      icon: <XCircleIcon className="w-5 h-5" />,
      color: stats.removed > 0 ? 'text-gray-400' : 'text-gray-600',
      bg: 'bg-gray-500/10',
      border: 'border-gray-500/20',
    },
  ] : []

  return (
    <AppShell onDeploy={() => setShowModal(true)} onRefresh={load}>
      {/* ── Hero splash ── */}
      <section className="relative mb-12 pt-6 pb-10 overflow-hidden">
        {/* Glow background */}
        <div className="absolute -top-20 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-blue-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -top-10 left-1/4 w-[300px] h-[200px] bg-indigo-600/8 rounded-full blur-2xl pointer-events-none" />

        <div className="relative text-center">
          <div className="inline-flex items-center gap-2 bg-blue-500/10 border border-blue-500/20 text-blue-300 text-xs font-medium px-3 py-1.5 rounded-full mb-6">
            <BoltIcon className="w-3.5 h-3.5" />
            Local Developer Database Manager
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold text-white mb-4 tracking-tight">
            Deploy & manage databases
            <br />
            <span className="bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
              in seconds
            </span>
          </h1>
          <p className="text-gray-400 text-lg max-w-xl mx-auto mb-8">
            Spin up PostgreSQL, MySQL, MongoDB, Redis and more on your local machine
            using Docker — no config headaches.
          </p>
          <div className="flex items-center justify-center gap-3">
            <button onClick={() => setShowModal(true)}
              className="btn-primary flex items-center gap-2 px-6 py-2.5 text-base">
              <PlusIcon className="w-5 h-5" />
              Deploy a Database
            </button>
            <Link to="/instances" className="btn-secondary flex items-center gap-2 px-6 py-2.5 text-base">
              <CircleStackIcon className="w-5 h-5" />
              View Instances
            </Link>
          </div>
        </div>
      </section>

      {/* ── Stat cards ── */}
      {statCards.length > 0 && (
        <section className="mb-10">
          <p className="section-label">Overview</p>
          <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-4">
            {statCards.map(s => (
              <Link
                key={s.label}
                to="/instances"
                className={`stat-card border ${s.border} hover:scale-[1.02] transition-transform group`}
              >
                <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color}`}>
                  {s.icon}
                </div>
                <div>
                  <div className={`text-2xl font-bold text-white tabular-nums ${s.pulse ? 'animate-pulse' : ''}`}>
                    {s.value}
                  </div>
                  <div className="text-xs text-gray-500 mt-0.5">{s.label}</div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── Recent instances ── */}
      <section>
        <div className="flex items-center justify-between mb-4">
          <p className="section-label mb-0">Recent Instances</p>
          <Link to="/instances" className="text-xs text-blue-400 hover:text-blue-300 transition-colors">
            View all →
          </Link>
        </div>

        {recent.length === 0 ? (
          <div className="card p-12 text-center">
            <div className="text-4xl mb-3">🗄️</div>
            <p className="text-gray-400 text-sm mb-4">No databases deployed yet</p>
            <button onClick={() => setShowModal(true)} className="btn-primary inline-flex items-center gap-2">
              <PlusIcon className="w-4 h-4" />
              Deploy your first database
            </button>
          </div>
        ) : (
          <div className="card divide-y divide-white/[0.05]">
            {recent.map(inst => (
              <RecentRow key={inst.id} instance={inst} onClick={() => navigate(`/instances/${inst.id}`)} />
            ))}
          </div>
        )}
      </section>

      {/* ── Quick start guide (only when empty) ── */}
      {stats?.total === 0 && (
        <section className="mt-10">
          <p className="section-label">Quick Start</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {[
              { icon: <PlusIcon className="w-5 h-5" />, step: '1', title: 'Click Deploy', desc: 'Choose a database engine from the catalog — PostgreSQL, MySQL, MongoDB, Redis and more.' },
              { icon: <Cog6ToothIcon className="w-5 h-5" />, step: '2', title: 'Configure', desc: 'Set a port, credentials and database name. Sensible defaults are pre-filled for you.' },
              { icon: <CheckCircleIcon className="w-5 h-5" />, step: '3', title: 'Connect', desc: 'Copy the generated connection string and start building. Your data persists across restarts.' },
            ].map(card => (
              <div key={card.step} className="card p-5 flex gap-4">
                <div className="w-9 h-9 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 shrink-0">
                  {card.icon}
                </div>
                <div>
                  <p className="text-xs text-gray-500 mb-1">Step {card.step}</p>
                  <h3 className="text-sm font-semibold text-white mb-1">{card.title}</h3>
                  <p className="text-xs text-gray-400 leading-relaxed">{card.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {showModal && (
        <DeployModal onClose={() => setShowModal(false)} onDeploy={handleDeploy} />
      )}
    </AppShell>
  )
}

function RecentRow({ instance, onClick }) {
  return (
    <button
      onClick={onClick}
      className="w-full flex items-center gap-4 px-5 py-3.5 hover:bg-white/[0.03] transition-colors text-left">
      <span className="text-2xl shrink-0">{instance.icon}</span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-white truncate">{instance.name}</span>
          <StatusBadge status={instance.status} />
        </div>
        <p className="text-xs text-gray-500 mt-0.5">
          {instance.dbTypeDisplay} {instance.version} · port {instance.hostPort}
        </p>
      </div>
      <div className="flex items-center gap-1.5 text-xs text-gray-500 shrink-0">
        <ClockIcon className="w-3.5 h-3.5" />
        {timeAgo(instance.createdAt)}
      </div>
    </button>
  )
}

function timeAgo(dateStr) {
  const diff = Date.now() - new Date(dateStr).getTime()
  const m = Math.floor(diff / 60_000)
  const h = Math.floor(m / 60)
  const d = Math.floor(h / 24)
  if (d > 0) return `${d}d ago`
  if (h > 0) return `${h}h ago`
  if (m > 0) return `${m}m ago`
  return 'just now'
}
