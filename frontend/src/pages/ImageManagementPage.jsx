import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, ArrowRight, RefreshCw, Search } from 'lucide-react'
import toast from 'react-hot-toast'

import { AppShell } from '../components/AppShell'
import { getImageSummary, refreshImageTracking } from '../api/client'

function decisionTone(decision) {
  switch (decision) {
    case 'ALLOW':
      return {
        fg: 'var(--status-running)',
        bg: 'var(--status-running-bg)',
        border: 'var(--status-running-border)',
      }
    case 'BLOCK':
      return {
        fg: 'var(--status-error)',
        bg: 'var(--status-error-bg)',
        border: 'var(--status-error-border)',
      }
    default:
      return {
        fg: 'var(--status-warning)',
        bg: 'var(--status-warning-bg)',
        border: 'var(--status-warning-border)',
      }
  }
}

function shortTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

export function ImageManagementPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshingScope, setRefreshingScope] = useState(null)
  const [query, setQuery] = useState('')

  const load = async () => {
    try {
      const data = await getImageSummary()
      setRows(data)
    } catch {
      toast.error('Failed to load image summary')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const kick = setTimeout(() => { void load() }, 0)
    return () => clearTimeout(kick)
  }, [])

  const onRefresh = async (scope) => {
    setRefreshingScope(scope)
    try {
      const result = await refreshImageTracking(scope)
      toast.success(`Refreshed ${result.updated} tags (${result.scope})`)
      await load()
    } catch {
      toast.error('Image refresh failed')
    } finally {
      setRefreshingScope(null)
    }
  }

  const filtered = useMemo(() => {
    if (!query) return rows
    const q = query.toLowerCase()
    return rows.filter(item =>
      item.displayName?.toLowerCase().includes(q)
      || item.image?.toLowerCase().includes(q)
      || item.dbType?.toLowerCase().includes(q)
    )
  }, [rows, query])

  const totals = useMemo(() => {
    return {
      all: rows.length,
      allow: rows.reduce((acc, row) => acc + (row.allowCount ?? 0), 0),
      warning: rows.reduce((acc, row) => acc + (row.warningCount ?? 0), 0),
      blocked: rows.reduce((acc, row) => acc + (row.blockedCount ?? 0), 0),
    }
  }, [rows])

  return (
    <AppShell onRefresh={load}>
      <div className="flex items-center justify-between mb-6 animate-fade-up gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold text-(--text-primary)">Image Management</h1>
          <p className="text-sm text-(--text-muted) mt-0.5">
            Select a deployable tool to inspect image tags and source-aware validation details.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            className="btn-secondary text-xs"
            onClick={() => onRefresh('local')}
            disabled={refreshingScope !== null}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'local' ? 'animate-spin' : ''}`} />
            Refresh Local
          </button>
          <button
            className="btn-secondary text-xs"
            onClick={() => onRefresh('hub')}
            disabled={refreshingScope !== null}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'hub' ? 'animate-spin' : ''}`} />
            Refresh Hub
          </button>
          <button
            className="btn-primary text-xs"
            onClick={() => onRefresh('all')}
            disabled={refreshingScope !== null}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'all' ? 'animate-spin' : ''}`} />
            Refresh All
          </button>
        </div>
      </div>

      <section className="mb-6 animate-fade-up delay-100">
        <div className="flex flex-wrap gap-3">
          <StatChip label="Total" value={totals.all} tone="var(--status-deploying)" />
          <StatChip label="Allow" value={totals.allow} tone="var(--status-running)" />
          <StatChip label="Warn" value={totals.warning} tone="var(--status-warning)" />
          <StatChip label="Blocked" value={totals.blocked} tone="var(--status-error)" />
        </div>
      </section>

      <section className="mb-4 animate-fade-up delay-150">
        <div className="relative max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-(--text-muted)" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search tool, image, tag or decision"
            className="input pl-9"
          />
        </div>
      </section>

      <section className="card overflow-hidden animate-fade-up delay-200">
        {loading ? (
          <div className="p-10 text-sm text-(--text-muted) flex items-center gap-2">
            <RefreshCw className="w-4 h-4 animate-spin" />
            Loading image summary…
          </div>
        ) : filtered.length === 0 ? (
          <div className="p-10 text-sm text-(--text-muted)">
            No tools match the current search.
          </div>
        ) : (
          <div className="p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-4">
            {filtered.map((row) => (
              <ToolCard
                key={row.dbType}
                row={row}
                onClick={() => navigate(`/images/${row.dbType}`)}
              />
            ))}
          </div>
        )}
      </section>

      <div className="mt-4 flex items-start gap-2 text-xs text-(--text-muted) animate-fade-up delay-300">
        <AlertCircle className="w-3.5 h-3.5 mt-0.5" />
        This page fetches summary only; detailed tag rows load lazily when a tool card is opened.
      </div>
    </AppShell>
  )
}

function ToolCard({ row, onClick }) {
  const safety = row.blockedCount > 0 ? 'BLOCK' : row.warningCount > 0 ? 'WARN' : 'ALLOW'
  const tone = decisionTone(safety)
  return (
    <button
      onClick={onClick}
      className="card p-4 text-left transition-all hover:-translate-y-1 hover:shadow-(--shadow-raised)"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <p className="text-sm font-semibold text-(--text-primary)">{row.displayName}</p>
          <p className="text-[10px] text-(--text-muted) mt-0.5">{row.dbType}</p>
        </div>
        <span
          className="inline-flex px-2 py-0.5 rounded-sm border text-[10px] font-bold"
          style={{ color: tone.fg, background: tone.bg, borderColor: tone.border }}
        >
          {safety}
        </span>
      </div>

      <p className="text-xs text-(--text-muted) font-mono mb-3 truncate" title={row.image}>{row.image}</p>

      <div className="grid grid-cols-2 gap-2 text-xs">
        <MiniStat label="Tags" value={row.totalTags} />
        <MiniStat label="Local" value={row.localAvailableCount} />
        <MiniStat label="Hub" value={row.dockerHubAvailableCount} />
        <MiniStat label="Blocked" value={row.blockedCount} />
      </div>

      <div className="mt-3 pt-2 border-t border-(--border-soft) flex items-center justify-between text-[10px] text-(--text-muted)">
        <span>{shortTime(row.updatedAt)}</span>
        <span className="inline-flex items-center gap-1 text-(--text-secondary)">
          Open details
          <ArrowRight className="w-3 h-3" />
        </span>
      </div>
    </button>
  )
}

function MiniStat({ label, value }) {
  return (
    <div className="rounded-sm border px-2 py-1 border-(--border-soft) bg-(--bg-surface-2)">
      <p className="text-[10px] uppercase tracking-widest text-(--text-muted)">{label}</p>
      <p className="text-sm font-semibold text-(--text-primary)">{value ?? 0}</p>
    </div>
  )
}

function StatChip({ label, value, tone }) {
  return (
    <div className="card px-3 py-2" style={{ minWidth: 110 }}>
      <p className="text-[10px] uppercase tracking-widest text-(--text-muted)">{label}</p>
      <p className="text-lg font-bold" style={{ color: tone }}>{value}</p>
    </div>
  )
}
