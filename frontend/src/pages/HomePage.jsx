import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getInstances, getStats } from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusBadge } from '../components/StatusBadge'
import { useUserProfile } from '../hooks/useUserProfile'
import {
  CircleCheck,
  CircleOff,
  CircleX,
  Clock3,
  Database,
  Plus,
  RefreshCw,
  Settings,
  TriangleAlert,
  Play,
  Zap,
} from 'lucide-react'

export function HomePage() {
  const [instances, setInstances] = useState([])
  const [stats, setStats]         = useState(null)
  const navigate = useNavigate()
  const openDeployPage = useCallback(() => navigate('/deploy'), [navigate])
  const { profile } = useUserProfile()

  const load = useCallback(async () => {
    try {
      const [inst, statsData] = await Promise.all([getInstances(), getStats()])
      setInstances(inst)
      setStats(statsData)
    } catch { /* silent */ }
  }, [])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    const t = setInterval(load, 10_000)
    return () => {
      clearTimeout(kick)
      clearInterval(t)
    }
  }, [load])

  const recent = [...instances]
    .filter(i => i.status !== 'REMOVED')
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 5)

  const statCards = stats ? [
    { label: 'Total Instances', value: stats.total,    icon: <Database className="w-5 h-5" />, color: 'text-[var(--status-deploying)]', bg: 'bg-[var(--status-deploying-bg)]', border: 'border-[var(--status-deploying-border)]' },
    { label: 'Running',         value: stats.running,  icon: <Play className="w-5 h-5" />, color: 'text-[var(--status-running)]', bg: 'bg-[var(--status-running-bg)]', border: 'border-[var(--status-running-border)]', pulse: stats.running > 0 },
    { label: 'Stopped',         value: stats.stopped,  icon: <CircleOff className="w-5 h-5" />, color: 'text-[var(--status-stopped)]', bg: 'bg-[var(--status-stopped-bg)]', border: 'border-[var(--status-stopped-border)]' },
    { label: 'Deploying',       value: stats.deploying, icon: <RefreshCw className={`w-5 h-5 ${stats.deploying > 0 ? 'animate-spin' : ''}`} />, color: 'text-[var(--status-deploying)]', bg: 'bg-[var(--status-deploying-bg)]', border: 'border-[var(--status-deploying-border)]' },
    { label: 'Removing',        value: stats.removing, icon: <RefreshCw className={`w-5 h-5 ${stats.removing > 0 ? 'animate-spin' : ''}`} />, color: 'text-[var(--status-removing)]', bg: 'bg-[var(--status-removing-bg)]', border: 'border-[var(--status-removing-border)]' },
    { label: 'Errors',          value: stats.error,    icon: <TriangleAlert className="w-5 h-5" />, color: stats.error > 0 ? 'text-[var(--status-error)]' : 'text-[var(--status-stopped)]', bg: stats.error > 0 ? 'bg-[var(--status-error-bg)]' : 'bg-[var(--status-stopped-bg)]', border: stats.error > 0 ? 'border-[var(--status-error-border)]' : 'border-[var(--status-stopped-border)]' },
    { label: 'Removed',         value: stats.removed,  icon: <CircleX className="w-5 h-5" />, color: stats.removed > 0 ? 'text-[var(--status-removed)]' : 'text-[var(--status-stopped)]', bg: 'bg-[var(--status-removed-bg)]', border: 'border-[var(--status-removed-border)]' },
  ] : []

  return (
    <AppShell onRefresh={load}>

      {/* ── Hero ── */}
      <section className="relative mb-10 pt-4 pb-8 overflow-hidden animate-fade-up">
        <div className="absolute -top-20 left-1/3 w-[600px] h-[300px] rounded-full blur-3xl pointer-events-none" style={{ background: 'color-mix(in srgb, var(--accent) 16%, transparent)' }} />
        <div className="absolute -top-10 left-1/4 w-[300px] h-[200px] rounded-full blur-2xl pointer-events-none" style={{ background: 'color-mix(in srgb, var(--status-deploying) 12%, transparent)' }} />

        <div className="relative flex flex-col lg:flex-row lg:items-center lg:gap-12">
          {/* Left — copy */}
          <div className="flex-1 min-w-0">
            <div className="inline-flex items-center gap-2 text-xs font-medium px-3 py-1.5 rounded-full mb-5 animate-slide-down border" style={{
              background: 'var(--status-deploying-bg)',
              borderColor: 'var(--status-deploying-border)',
              color: 'var(--status-deploying)',
            }}>
              <Zap className="w-3.5 h-3.5" />
              {profile ? `${greetingFor(new Date())}, ${profile.name}` : 'Local Developer Database Manager'}
            </div>
            <h1 className="text-4xl xl:text-5xl font-bold text-[var(--text-primary)] mb-4 tracking-tight animate-fade-up delay-100">
              {profile ? <>Ready to wrangle some<br/></> : <>Deploy &amp; manage databases<br/></>}
              <span className="bg-gradient-to-r from-[var(--accent)] to-[var(--status-deploying)] bg-clip-text text-transparent">
                {profile ? 'ports today?' : 'in seconds'}
              </span>
            </h1>
            <p className="text-[var(--text-muted)] text-lg mb-8 max-w-lg animate-fade-up delay-150">
              {profile?.role
                ? `Tools tuned for a ${profile.role.toLowerCase()}. Spin up databases on your machine — no config headaches.`
                : 'Spin up PostgreSQL, MySQL, MongoDB, Redis and more on your local machine using Docker - no config headaches.'}
            </p>
            <div className="flex items-center gap-3 flex-wrap animate-fade-up delay-200">
              <button onClick={openDeployPage}
                className="btn-primary flex items-center gap-2 px-6 py-2.5 text-base">
                <Plus className="w-5 h-5" />
                Deploy a Database
              </button>
              <Link to="/instances" className="btn-secondary flex items-center gap-2 px-6 py-2.5 text-base">
                <Database className="w-5 h-5" />
                View Instances
              </Link>
            </div>
          </div>

          {/* Right — quick-step card */}
          <div className="hidden lg:flex flex-col gap-3 w-72 xl:w-80 shrink-0 stagger-children">
            {[
              { icon: <Plus className="w-4 h-4" />, step: '1', title: 'Click Deploy', desc: 'Choose a database from the catalog.' },
              { icon: <Settings className="w-4 h-4" />, step: '2', title: 'Configure', desc: 'Set port, credentials & name.' },
              { icon: <CircleCheck className="w-4 h-4" />, step: '3', title: 'Connect', desc: 'Copy the connection string and go.' },
            ].map(card => card.step === '1' ? (
              <button
                key={card.step}
                type="button"
                onClick={openDeployPage}
                className="card p-4 flex gap-3 items-start animate-slide-right hover:bg-[var(--bg-surface-2)] hover:-translate-y-0.5 transition-all duration-200 text-left cursor-pointer"
              >
                <div className="w-8 h-8 rounded-[4px] border flex items-center justify-center shrink-0" style={{
                  background: 'var(--status-deploying-bg)',
                  borderColor: 'var(--status-deploying-border)',
                  color: 'var(--status-deploying)',
                }}>
                  {card.icon}
                </div>
                <div>
                  <p className="text-[10px] text-[var(--text-muted)] mb-0.5">Step {card.step}</p>
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-0.5">{card.title}</h3>
                  <p className="text-xs text-[var(--text-muted)]">{card.desc}</p>
                </div>
              </button>
            ) : (
              <div key={card.step} className="card p-4 flex gap-3 items-start animate-slide-right hover:bg-[var(--bg-surface-2)] transition-all duration-200">
                <div className="w-8 h-8 rounded-[4px] border flex items-center justify-center shrink-0" style={{
                  background: 'var(--status-deploying-bg)',
                  borderColor: 'var(--status-deploying-border)',
                  color: 'var(--status-deploying)',
                }}>
                  {card.icon}
                </div>
                <div>
                  <p className="text-[10px] text-[var(--text-muted)] mb-0.5">Step {card.step}</p>
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-0.5">{card.title}</h3>
                  <p className="text-xs text-[var(--text-muted)]">{card.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Favorite tools quick deploy ── */}
      {profile?.favTools?.length > 0 && (
        <section className="mb-10 animate-fade-up delay-150">
          <p className="section-label">Your Pinned Tools</p>
          <div className="flex flex-wrap gap-2 stagger-children">
            {profile.favTools.map(t => (
              <button
                key={t}
                onClick={() => navigate(`/deploy?dbType=${t}`)}
                className="brutal-chip px-3 py-1.5 text-xs font-semibold uppercase tracking-wider hover:-translate-y-0.5 transition-transform animate-fade-up"
              >
                <Plus className="w-3.5 h-3.5 inline mr-1" />
                {t.replace('_', ' ')}
              </button>
            ))}
          </div>
        </section>
      )}

      {/* ── Stat cards ── */}
      {statCards.length > 0 && (
        <section className="mb-10 animate-fade-up delay-200">
          <p className="section-label">Overview</p>
          <div className="flex flex-wrap gap-4 stagger-children">
            {statCards.map(s => (
              <Link
                key={s.label}
                to="/instances"
                className={`stat-card border ${s.border} hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20 transition-all duration-200 group flex-1 min-w-[120px] animate-fade-up`}
              >
                <div className={`w-9 h-9 rounded-lg ${s.bg} flex items-center justify-center ${s.color} transition-transform duration-200 group-hover:scale-110`}>
                  {s.icon}
                </div>
                <div>
                  <div className={`text-2xl font-bold text-[var(--text-primary)] tabular-nums ${s.pulse ? 'animate-pulse' : ''}`}>
                    {s.value}
                  </div>
                  <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.label}</div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── Recent instances ── */}
      <section className="animate-fade-up delay-300">
        <div className="flex items-center justify-between mb-4">
          <p className="section-label mb-0">Recent Instances</p>
          <Link to="/instances" className="text-xs text-[var(--status-deploying)] hover:opacity-80 transition-colors">
            View all →
          </Link>
        </div>

        {recent.length === 0 ? (
          <div className="card p-12 text-center animate-scale-in">
            <div className="text-4xl mb-3">🗄️</div>
            <p className="text-[var(--text-muted)] text-sm mb-4">No databases deployed yet</p>
            <button onClick={openDeployPage} className="btn-primary inline-flex items-center gap-2">
              <Plus className="w-4 h-4" />
              Deploy your first database
            </button>
          </div>
        ) : (
          <div className="card divide-y-2 divide-[var(--border-strong)] overflow-hidden">
            {recent.map((inst, i) => (
              <RecentRow
                key={inst.id}
                instance={inst}
                onClick={() => navigate(`/instances/${inst.id}`)}
                delay={i * 50}
              />
            ))}
          </div>
        )}
      </section>

      {/* ── Quick start (mobile only, when empty) ── */}
      {stats?.total === 0 && (
        <section className="mt-10 lg:hidden animate-fade-up delay-400">
          <p className="section-label">Quick Start</p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 stagger-children">
            {[
              { icon: <Plus className="w-5 h-5" />, step: '1', title: 'Click Deploy', desc: 'Choose a database engine from the catalog - PostgreSQL, MySQL, MongoDB, Redis and more.' },
              { icon: <Settings className="w-5 h-5" />, step: '2', title: 'Configure', desc: 'Set a port, credentials and database name. Sensible defaults are pre-filled for you.' },
              { icon: <CircleCheck className="w-5 h-5" />, step: '3', title: 'Connect', desc: 'Copy the generated connection string and start building. Your data persists across restarts.' },
            ].map(card => card.step === '1' ? (
              <button
                key={card.step}
                type="button"
                onClick={openDeployPage}
                className="card p-5 flex gap-4 animate-fade-up transition-all duration-200 text-left cursor-pointer hover:bg-[var(--bg-surface-2)]"
              >
                <div className="w-9 h-9 rounded-[4px] border flex items-center justify-center shrink-0" style={{
                  background: 'var(--status-deploying-bg)',
                  borderColor: 'var(--status-deploying-border)',
                  color: 'var(--status-deploying)',
                }}>
                  {card.icon}
                </div>
                <div>
                  <p className="text-xs text-[var(--text-muted)] mb-1">Step {card.step}</p>
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-1">{card.title}</h3>
                  <p className="text-xs text-[var(--text-muted)] leading-relaxed">{card.desc}</p>
                </div>
              </button>
            ) : (
              <div key={card.step} className="card p-5 flex gap-4 animate-fade-up transition-all duration-200">
                <div className="w-9 h-9 rounded-[4px] border flex items-center justify-center shrink-0" style={{
                  background: 'var(--status-deploying-bg)',
                  borderColor: 'var(--status-deploying-border)',
                  color: 'var(--status-deploying)',
                }}>
                  {card.icon}
                </div>
                <div>
                  <p className="text-xs text-[var(--text-muted)] mb-1">Step {card.step}</p>
                  <h3 className="text-sm font-semibold text-[var(--text-primary)] mb-1">{card.title}</h3>
                  <p className="text-xs text-[var(--text-muted)] leading-relaxed">{card.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Removed DeployModal as per the new navigation */}
    </AppShell>
  )
}

function RecentRow({ instance, onClick, delay = 0 }) {
  return (
    <button
      onClick={onClick}
      style={{ animationDelay: `${delay}ms` }}
      className="w-full flex items-center gap-4 px-5 py-3.5 hover:bg-[var(--bg-surface-2)] transition-all duration-150 text-left animate-fade-in group"
    >
      <span className="text-2xl shrink-0 transition-transform duration-200 group-hover:scale-110">{instance.icon}</span>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-[var(--text-primary)] truncate">{instance.name}</span>
          <StatusBadge status={instance.status} />
        </div>
        <p className="text-xs text-[var(--text-muted)] mt-0.5">
          {instance.dbTypeDisplay} {instance.version} · port {instance.hostPort}
        </p>
      </div>
      <div className="flex items-center gap-1.5 text-xs text-[var(--text-muted)] shrink-0 transition-colors group-hover:opacity-80">
        <Clock3 className="w-3.5 h-3.5" />
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

function greetingFor(date) {
  const h = date.getHours()
  if (h < 5)  return 'Burning the midnight oil'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  if (h < 21) return 'Good evening'
  return 'Working late'
}
