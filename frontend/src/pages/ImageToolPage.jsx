import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AlertCircle, ArrowLeft, RefreshCw, Search } from 'lucide-react'
import toast from 'react-hot-toast'

import { AppShell } from '../components/AppShell'
import { getImageToolDetails, refreshImageTool } from '../api/client'

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

function statusTone(value) {
  if (value === 'AVAILABLE') {
    return { fg: 'var(--status-running)', bg: 'var(--status-running-bg)', border: 'var(--status-running-border)' }
  }
  if (value === 'MISSING') {
    return { fg: 'var(--status-error)', bg: 'var(--status-error-bg)', border: 'var(--status-error-border)' }
  }
  if (value === 'NOT_APPLICABLE') {
    return { fg: 'var(--status-stopped)', bg: 'var(--status-stopped-bg)', border: 'var(--status-stopped-border)' }
  }
  return { fg: 'var(--status-warning)', bg: 'var(--status-warning-bg)', border: 'var(--status-warning-border)' }
}

function shortTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

export function ImageToolPage() {
  const { dbType } = useParams()
  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshingScope, setRefreshingScope] = useState(null)
  const [query, setQuery] = useState('')

  const load = async (refresh = false) => {
    setLoading(true)
    try {
      const data = await getImageToolDetails(dbType, refresh)
      setDetail(data)
    } catch {
      toast.error('Failed to load tool images')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const kick = setTimeout(() => { void load(false) }, 0)
    return () => clearTimeout(kick)
  }, [dbType])

  const onRefresh = async (scope) => {
    setRefreshingScope(scope)
    try {
      const result = await refreshImageTool(dbType, scope)
      toast.success(`Refreshed ${result.updated} tags (${result.scope})`)
      await load(false)
    } catch {
      toast.error('Refresh failed')
    } finally {
      setRefreshingScope(null)
    }
  }

  const filteredTags = useMemo(() => {
    if (!detail?.tags) return []
    if (!query) return detail.tags
    const q = query.toLowerCase()
    return detail.tags.filter(tag =>
      tag.imageRef?.toLowerCase().includes(q)
      || tag.tag?.toLowerCase().includes(q)
      || tag.decision?.toLowerCase().includes(q)
      || tag.localStatus?.toLowerCase().includes(q)
      || tag.dockerHubStatus?.toLowerCase().includes(q)
    )
  }, [detail, query])

  return (
    <AppShell onRefresh={() => load(false)}>
      <div className="flex items-center justify-between gap-3 flex-wrap mb-5 animate-fade-up">
        <div>
          <Link to="/images" className="text-sm text-(--text-muted) hover:text-(--text-primary) inline-flex items-center gap-1 mb-1">
            <ArrowLeft className="w-3.5 h-3.5" />
            Back to Image Management
          </Link>
          <h1 className="text-xl font-semibold text-(--text-primary)">
            {detail?.displayName ?? dbType} Images
          </h1>
          <p className="text-sm text-(--text-muted) mt-0.5">
            {detail?.image ? `Repository: ${detail.image}` : 'Loading image repository...'}
          </p>
        </div>
        <div className="flex gap-2">
          <button className="btn-secondary text-xs" onClick={() => onRefresh('local')} disabled={refreshingScope !== null}>
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'local' ? 'animate-spin' : ''}`} />
            Refresh Local
          </button>
          <button className="btn-secondary text-xs" onClick={() => onRefresh('hub')} disabled={refreshingScope !== null}>
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'hub' ? 'animate-spin' : ''}`} />
            Refresh Hub
          </button>
          <button className="btn-primary text-xs" onClick={() => onRefresh('all')} disabled={refreshingScope !== null}>
            <RefreshCw className={`w-3.5 h-3.5 ${refreshingScope === 'all' ? 'animate-spin' : ''}`} />
            Refresh All
          </button>
        </div>
      </div>

      {loading ? (
        <div className="card p-10 text-sm text-(--text-muted) flex items-center gap-2">
          <RefreshCw className="w-4 h-4 animate-spin" />
          Loading tool images...
        </div>
      ) : !detail ? (
        <div className="card p-10 text-sm text-(--text-muted)">
          Unable to load image detail.
        </div>
      ) : (
        <>
          <section className="mb-6 animate-fade-up delay-100">
            <div className="flex flex-wrap gap-3">
              <StatChip label="Tags" value={detail.totalTags} tone="var(--status-deploying)" />
              <StatChip label="Allow" value={detail.allowCount} tone="var(--status-running)" />
              <StatChip label="Warn" value={detail.warningCount} tone="var(--status-warning)" />
              <StatChip label="Blocked" value={detail.blockedCount} tone="var(--status-error)" />
              <StatChip label="Local Hits" value={detail.localAvailableCount} tone="var(--status-running)" />
              <StatChip label="Hub Hits" value={detail.dockerHubAvailableCount} tone="var(--status-deploying)" />
            </div>
          </section>

          <section className="mb-4 animate-fade-up delay-150">
            <div className="relative max-w-md">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-(--text-muted)" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search image tags/status"
                className="input pl-9"
              />
            </div>
          </section>

          <section className="card overflow-hidden animate-fade-up delay-200">
            {filteredTags.length === 0 ? (
              <div className="p-8 text-sm text-(--text-muted)">
                No tags match the current filter.
              </div>
            ) : (
              <div className="overflow-auto">
                <table className="w-full text-sm" style={{ minWidth: 1000 }}>
                  <thead>
                    <tr className="bg-(--bg-surface-2) border-b-2 border-(--border-strong)">
                      <Th>Tag</Th>
                      <Th>Image Ref</Th>
                      <Th>Local</Th>
                      <Th>Docker Hub</Th>
                      <Th>Decision</Th>
                      <Th>Updated</Th>
                      <Th>Message</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTags.map((tag) => {
                      const decision = decisionTone(tag.decision)
                      const local = statusTone(tag.localStatus)
                      const hub = statusTone(tag.dockerHubStatus)
                      return (
                        <tr key={tag.imageRef} className="border-b border-(--border-soft) align-top">
                          <td className="px-3 py-2.5">
                            <p className="font-mono text-xs text-(--text-secondary)">{tag.tag}</p>
                          </td>
                          <td className="px-3 py-2.5">
                            <p className="font-mono text-xs text-(--text-secondary)">{tag.imageRef}</p>
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-sm border text-xs font-semibold" style={{ color: local.fg, background: local.bg, borderColor: local.border }}>
                              {tag.localStatus}
                            </span>
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-sm border text-xs font-semibold" style={{ color: hub.fg, background: hub.bg, borderColor: hub.border }}>
                              {tag.dockerHubStatus}
                            </span>
                          </td>
                          <td className="px-3 py-2.5">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-sm border text-xs font-semibold" style={{ color: decision.fg, background: decision.bg, borderColor: decision.border }}>
                              {tag.decision}
                            </span>
                          </td>
                          <td className="px-3 py-2.5 text-xs text-(--text-muted) whitespace-nowrap">
                            {shortTime(tag.updatedAt)}
                          </td>
                          <td className="px-3 py-2.5">
                            <p className="text-xs text-(--text-muted)" style={{ maxWidth: 340 }}>{tag.message || '—'}</p>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <div className="mt-4 flex items-start gap-2 text-xs text-(--text-muted) animate-fade-up delay-300">
            <AlertCircle className="w-3.5 h-3.5 mt-0.5" />
            Decision state is evaluated per tag using local Docker cache first, then Docker Hub fallback.
          </div>
        </>
      )}
    </AppShell>
  )
}

function Th({ children }) {
  return <th className="px-3 py-2 text-left text-xs font-bold uppercase tracking-widest text-(--text-muted)">{children}</th>
}

function StatChip({ label, value, tone }) {
  return (
    <div className="card px-3 py-2" style={{ minWidth: 110 }}>
      <p className="text-[10px] uppercase tracking-widest text-(--text-muted)">{label}</p>
      <p className="text-lg font-bold" style={{ color: tone }}>{value}</p>
    </div>
  )
}
