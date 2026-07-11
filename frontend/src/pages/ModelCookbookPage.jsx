import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { getModelSuggestions, getSystemProfile, getRuntimeDashboard, searchOllamaLibrary } from '../api/client'
import { AppShell } from '../components/AppShell'
import { Link, useNavigate } from 'react-router-dom'
import { Check, Copy, Cpu, Download, ExternalLink, Gauge, Globe, Loader2, Search, Sparkles, Wrench, Zap } from 'lucide-react'
import toast from 'react-hot-toast'

const TYPE_FILTERS = [
  { value: '', label: 'All' },
  { value: 'CHAT', label: 'Chat' },
  { value: 'CODE', label: 'Code' },
  { value: 'EMBEDDING', label: 'Embedding' },
  { value: 'VISION', label: 'Vision' },
  { value: 'REASONING', label: 'Reasoning' },
]

// Multi-select — an empty set means "no restriction" (same as the backend's Set.of()).
const COMPAT_FILTERS = [
  { value: 'FAST', label: 'Fast' },
  { value: 'OK', label: 'OK' },
  { value: 'CPU_ONLY', label: 'CPU only' },
  { value: 'TOO_LARGE', label: "Won't fit" },
]

const SORT_OPTIONS = [
  { value: 'fit', label: 'Best fit' },
  { value: 'size', label: 'Smallest first' },
  { value: 'name', label: 'Name' },
]

// Whether the model has reliable native function/tool-calling support — the axis that matters
// for agentic use cases (this app drives an agent chat elsewhere, so it isn't just trivia here).
const TOOL_FILTERS = [
  { value: '', label: 'Any' },
  { value: 'YES', label: 'Tools' },
  { value: 'NO', label: 'No tools' },
]

// Badge colour per compatibility level.
const COMPAT_STYLE = {
  FAST:      { dot: '#22c55e', text: 'Fast',     bg: 'rgba(34,197,94,0.12)',  border: 'rgba(34,197,94,0.4)' },
  OK:        { dot: '#f59e0b', text: 'OK',       bg: 'rgba(245,158,11,0.12)', border: 'rgba(245,158,11,0.4)' },
  CPU_ONLY:  { dot: '#fb923c', text: 'CPU only', bg: 'rgba(251,146,60,0.12)', border: 'rgba(251,146,60,0.4)' },
  TOO_LARGE: { dot: '#ef4444', text: "Won't fit", bg: 'rgba(239,68,68,0.12)', border: 'rgba(239,68,68,0.4)' },
}

function gbLabel(mb) {
  if (!mb) return '—'
  return `${(mb / 1024).toFixed(1)} GB`
}

export function ModelCookbookPage() {
  const [profile, setProfile]         = useState(null)
  const [suggestions, setSuggestions] = useState([])
  const [typeFilter, setTypeFilter]   = useState('')
  const [compatFilters, setCompatFilters] = useState(() => new Set()) // multi-select; empty = all
  const [sortBy, setSortBy]           = useState('fit')
  const [toolFilter, setToolFilter]   = useState('') // '' = any, 'YES' = tool-calling only, 'NO' = excludes it
  const [query, setQuery]             = useState('')
  const [installed, setInstalled]     = useState(new Set()) // tags already on the runtime's disk
  const [pulling, setPulling]         = useState(new Set()) // tags with a pull in flight
  const [dash, setDash]               = useState(null)      // runtime reachability for the status chip
  const [loading, setLoading]         = useState(true)
  const navigate = useNavigate()
  const searchInputRef = useRef(null)

  const compatCsv = useMemo(() => Array.from(compatFilters).join(','), [compatFilters])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [prof, sugg] = await Promise.all([
        getSystemProfile(),
        getModelSuggestions(typeFilter, compatCsv),
      ])
      setProfile(prof)
      setSuggestions(sugg)
    } catch {
      toast.error('Failed to load model suggestions')
    } finally {
      setLoading(false)
    }
  }, [typeFilter, compatCsv])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    return () => clearTimeout(kick)
  }, [load])

  // Which catalog models are already pulled / being pulled, and whether a runtime is reachable
  // at all — drives the status chip and the "deploy first" empty state.
  const loadDash = useCallback(() => {
    getRuntimeDashboard()
      .then(d => {
        setDash(d)
        setInstalled(new Set((d.models ?? []).map(m => m.name)))
        setPulling(new Set(
          Object.entries(d.pulls ?? {})
            .filter(([, p]) => p?.state === 'pulling')
            .map(([tag]) => tag),
        ))
      })
      .catch(() => {})
  }, [])

  useEffect(() => { loadDash() }, [loadDash])

  // DX: "/" jumps focus to search, matching the Runtime page's shortcut.
  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.repeat || e.key !== '/' || e.metaKey || (e.ctrlKey && !e.altKey)) return
      const active = document.activeElement
      const tag = active?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || active?.isContentEditable) return
      e.preventDefault()
      searchInputRef.current?.focus()
      searchInputRef.current?.select?.()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  const toggleCompat = (value) => {
    setCompatFilters(prev => {
      const next = new Set(prev)
      next.has(value) ? next.delete(value) : next.add(value)
      return next
    })
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return suggestions.filter(s => {
      if (toolFilter === 'YES' && !s.model.toolCalling) return false
      if (toolFilter === 'NO' && s.model.toolCalling) return false
      if (!q) return true
      return s.model.ollamaTag.toLowerCase().includes(q) ||
        s.model.family.toLowerCase().includes(q) ||
        (s.model.description ?? '').toLowerCase().includes(q)
    })
  }, [suggestions, query, toolFilter])

  const visible = useMemo(() => {
    const list = [...filtered]
    if (sortBy === 'name') list.sort((a, b) => a.model.family.localeCompare(b.model.family))
    else if (sortBy === 'size') list.sort((a, b) => a.model.paramsBillions - b.model.paramsBillions)
    // 'fit' — keep the backend's best-fit-first ordering.
    return list
  }, [filtered, sortBy])

  // Pull starts on the Runtime page (?pull=<tag>) so progress is visible immediately.
  const pullAndOpen = (tag) => navigate(`/runtime?pull=${encodeURIComponent(tag)}`)

  const hasFilters = typeFilter !== '' || compatFilters.size > 0 || toolFilter !== '' || query.trim() !== ''
  const clearFilters = () => { setTypeFilter(''); setCompatFilters(new Set()); setToolFilter(''); setQuery('') }

  return (
    <AppShell onRefresh={() => { load(); loadDash() }}>
      {/* ── Header ── */}
      <div className="mb-6 animate-fade-up flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-[var(--status-deploying)]" />
            Model Cookbook
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-0.5">
            What your machine can actually run — scored against your detected hardware.
          </p>
        </div>
        <RuntimeStatusChip dash={dash} />
      </div>

      {/* ── Hardware profile ── */}
      {profile && (
        <div className="card p-4 mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 animate-fade-up delay-100">
          <ProfileStat icon={<Cpu className="w-4 h-4" />} label="GPU" value={profile.gpuVendor} />
          <ProfileStat label="VRAM" value={gbLabel(profile.vramMb)} />
          <ProfileStat label="RAM" value={gbLabel(profile.totalRamMb)} />
          <ProfileStat label="CPU cores" value={String(profile.cpuCores)} />
          <ProfileStat label="Platform" value={profile.platform} />
          {profile.gpuVendor === 'APPLE' && (
            <span className="text-xs text-[var(--status-removing)] ml-auto">
              Containerised Ollama on macOS runs CPU-only — use native Ollama for GPU.
            </span>
          )}
        </div>
      )}

      {/* ── Search + filters ── */}
      <div className="flex flex-col gap-3 mb-6 animate-fade-up delay-150">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative flex-1 min-w-52 max-w-md">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]" />
            <input
              ref={searchInputRef}
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search models — name, family, or use case… (press /)"
              className="input w-full pl-9"
            />
          </div>
          <label className="flex items-center gap-2 text-xs text-[var(--text-muted)]">
            Sort
            <select
              value={sortBy}
              onChange={e => setSortBy(e.target.value)}
              className="input py-1.5 text-xs"
            >
              {SORT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>
          {hasFilters && (
            <button onClick={clearFilters} className="btn-secondary text-xs">Clear filters</button>
          )}
        </div>
        <FilterRow label="Type" options={TYPE_FILTERS} active={typeFilter} onChange={setTypeFilter} />
        <FilterRow
          label="Fit"
          options={COMPAT_FILTERS}
          multi
          activeSet={compatFilters}
          onToggle={toggleCompat}
        />
        <FilterRow label="Tools" options={TOOL_FILTERS} active={toolFilter} onChange={setToolFilter} />
      </div>

      {/* ── Cards ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading models…
        </div>
      ) : visible.length === 0 ? (
        <EmptyState query={query} onClear={clearFilters} onPull={pullAndOpen} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4 stagger-children animate-fade-up delay-200">
          {visible.map(s => (
            <ModelCard
              key={s.model.ollamaTag}
              suggestion={s}
              installed={installed.has(s.model.ollamaTag)}
              pulling={pulling.has(s.model.ollamaTag)}
              onPull={() => pullAndOpen(s.model.ollamaTag)}
            />
          ))}
        </div>
      )}

      {/* Ollama library search stays available even with results, in case the catalog is missing
          a specific tag the user already knows they want. */}
      {!loading && query.trim().length >= 2 && (
        <LibrarySearchPanel query={query} onPull={pullAndOpen} compact={visible.length > 0} />
      )}
    </AppShell>
  )
}

function RuntimeStatusChip({ dash }) {
  if (!dash) return null
  const reachable = dash.reachable
  return (
    <Link
      to={reachable ? '/runtime' : '/deploy?tool=OLLAMA'}
      className="shrink-0 inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border-2 transition-colors"
      style={{
        borderColor: reachable ? 'var(--status-running-border)' : 'var(--status-error-border)',
        background: reachable ? 'var(--status-running-bg)' : 'var(--status-error-bg)',
        color: reachable ? 'var(--status-running)' : 'var(--status-error)',
      }}
      title={reachable ? `Runtime reachable at ${dash.baseUrl} — open Runtime` : 'No Ollama runtime reachable — deploy one'}
    >
      <Gauge className="w-3.5 h-3.5" />
      {reachable
        ? (dash.managedInstanceName ? `Runtime: ${dash.managedInstanceName}` : 'Runtime reachable')
        : 'No runtime — deploy Ollama'}
    </Link>
  )
}

function ProfileStat({ icon, label, value }) {
  return (
    <div className="flex items-center gap-2">
      {icon && <span className="text-[var(--text-muted)]">{icon}</span>}
      <span className="text-xs text-[var(--text-muted)]">{label}</span>
      <span className="text-sm font-semibold text-[var(--text-primary)] tabular-nums">{value}</span>
    </div>
  )
}

/** Filter row — either single-select (radio-like, `active`/`onChange`) or multi-select
 *  (checkboxes, `activeSet`/`onToggle`). An empty multi-select set means "no restriction". */
function FilterRow({ label, options, active, onChange, multi, activeSet, onToggle }) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-xs font-semibold uppercase tracking-widest text-[var(--text-muted)] w-12">{label}</span>
      {multi ? (
        <>
          {options.map(o => {
            const isActive = activeSet.has(o.value)
            return (
              <button
                key={o.value}
                onClick={() => onToggle(o.value)}
                aria-pressed={isActive}
                className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-colors border-2 ${
                  isActive
                    ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)] shadow-[var(--shadow-raised)]'
                    : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
                }`}
              >
                {o.label}
              </button>
            )
          })}
          {activeSet.size > 0 && (
            <span className="text-[11px] text-[var(--text-muted)]">— any of these fit levels</span>
          )}
        </>
      ) : (
        options.map(o => (
          <button
            key={o.value || 'all'}
            onClick={() => onChange(o.value)}
            aria-pressed={active === o.value}
            className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-colors border-2 ${
              active === o.value
                ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)] shadow-[var(--shadow-raised)]'
                : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
            }`}
          >
            {o.label}
          </button>
        ))
      )}
    </div>
  )
}

function ModelCard({ suggestion, installed, pulling, onPull }) {
  const { model, compatibility, speedTier } = suggestion
  const style = COMPAT_STYLE[compatibility] ?? COMPAT_STYLE.TOO_LARGE
  const [copied, setCopied] = useState(false)
  const tooLarge = compatibility === 'TOO_LARGE'

  const copyTag = async () => {
    await navigator.clipboard.writeText(model.ollamaTag)
    setCopied(true)
    toast.success(`Copied ${model.ollamaTag}`)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="card p-4 flex flex-col gap-3 animate-fade-up">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-[var(--text-primary)] truncate">{model.family}</p>
          <p className="text-xs text-[var(--text-muted)] font-mono truncate">{model.ollamaTag}</p>
        </div>
        <span
          className="shrink-0 inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-[11px] font-semibold border"
          style={{ background: style.bg, borderColor: style.border, color: 'var(--text-primary)' }}
        >
          <span className="w-2 h-2 rounded-full" style={{ background: style.dot }} />
          {style.text}
        </span>
      </div>

      <p className="text-xs text-[var(--text-muted)] line-clamp-2 min-h-[2rem]">{model.description}</p>

      <div className="flex items-center gap-3 text-[11px] text-[var(--text-muted)] flex-wrap">
        <span className="px-1.5 py-0.5 rounded bg-[var(--bg-surface-2)] border border-[var(--border-strong)]">{model.type}</span>
        <span>{model.paramsBillions}B</span>
        <span className="flex items-center gap-1"><Zap className="w-3 h-3" />{speedTier}</span>
        <span title="Minimum VRAM (GPU) / RAM (CPU fallback) at the default quantization">
          needs {gbLabel(model.minVramMb)} VRAM · {gbLabel(model.minRamMb)} RAM
        </span>
        {model.toolCalling && (
          <span
            className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-[var(--bg-surface-2)] border border-[var(--border-strong)]"
            title="Supports native function/tool calling — safe to use as an agent backbone"
          >
            <Wrench className="w-3 h-3" /> Tools
          </span>
        )}
      </div>

      <div className="flex items-center gap-2 mt-auto">
        {installed ? (
          <button onClick={onPull} className="btn-secondary text-xs flex-1 justify-center">
            <Check className="w-3.5 h-3.5 text-[#22c55e]" />
            <span className="ml-1">Installed — open Runtime</span>
          </button>
        ) : pulling ? (
          <button onClick={onPull} className="btn-secondary text-xs flex-1 justify-center">
            <Download className="w-3.5 h-3.5 animate-pulse" />
            <span className="ml-1">Pulling… view progress</span>
          </button>
        ) : (
          <button
            onClick={onPull}
            disabled={tooLarge}
            title={tooLarge ? 'Too large for the detected hardware' : `Pull ${model.ollamaTag} and open the Runtime page`}
            className="btn-primary text-xs flex-1 justify-center disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Download className="w-3.5 h-3.5" />
            <span className="ml-1">Pull & run</span>
          </button>
        )}
        <button
          onClick={copyTag}
          title={`Copy tag: ${model.ollamaTag}`}
          className="btn-secondary text-xs shrink-0"
        >
          {copied ? <Check className="w-3.5 h-3.5 text-[#22c55e]" /> : <Copy className="w-3.5 h-3.5" />}
        </button>
      </div>
    </div>
  )
}

function EmptyState({ query, onClear, onPull }) {
  return (
    <div className="card p-16 text-center animate-scale-in">
      <div className="text-5xl mb-4">🍳</div>
      <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">No models match</h2>
      <p className="text-[var(--text-muted)] text-sm mb-6">Try a different search, or widen the type and fit filters.</p>
      <button onClick={onClear} className="btn-secondary inline-flex items-center gap-2">Clear filters</button>
      {query.trim().length >= 2 && (
        <div className="mt-8 max-w-lg mx-auto text-left">
          <LibrarySearchPanel query={query} onPull={onPull} />
        </div>
      )}
    </div>
  )
}

/**
 * Fallback search against the live Ollama library for models that aren't in the curated,
 * hardware-scored catalog — e.g. a brand-new release or a community fine-tune. Results are
 * un-scored (no hardware fit badge) since they never went through {@code ModelSuggestionService}.
 */
function LibrarySearchPanel({ query, onPull, compact }) {
  const [results, setResults] = useState(null) // null = not searched yet, [] = searched, no hits
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const q = query.trim()
    let cancelled = false
    // Every setState call lives inside this async callback (not the effect body itself) so a
    // fast-typing user doesn't trigger cascading synchronous renders on every keystroke.
    const debounce = setTimeout(() => {
      if (cancelled) return
      if (q.length < 2) { setResults(null); setLoading(false); setFailed(false); return }
      setLoading(true)
      setFailed(false)
      searchOllamaLibrary(q)
        .then(r => { if (!cancelled) setResults(r) })
        .catch(() => { if (!cancelled) { setResults([]); setFailed(true) } })
        .finally(() => { if (!cancelled) setLoading(false) })
    }, q.length < 2 ? 0 : 400)
    return () => { cancelled = true; clearTimeout(debounce) }
  }, [query])

  if (results === null && !loading) return null

  return (
    <div className={compact ? 'mt-8 animate-fade-up' : ''}>
      <div className="flex items-center gap-2 mb-3">
        <Globe className="w-4 h-4 text-[var(--text-muted)]" />
        <p className="text-xs font-semibold uppercase tracking-widest text-[var(--text-muted)]">
          Not in the catalog? Search ollama.com's library
        </p>
      </div>
      {loading ? (
        <div className="flex items-center gap-2 text-sm text-[var(--text-muted)] py-4">
          <Loader2 className="w-4 h-4 animate-spin" /> Searching ollama.com…
        </div>
      ) : failed ? (
        <p className="text-sm text-[var(--text-muted)]">Couldn't reach the Ollama library right now — try again later.</p>
      ) : results.length === 0 ? (
        <p className="text-sm text-[var(--text-muted)]">No matches on ollama.com for &ldquo;{query}&rdquo; either.</p>
      ) : (
        <div className="space-y-2">
          {results.map(r => (
            <LibraryResultRow key={r.modelIdentifier} result={r} onPull={onPull} />
          ))}
        </div>
      )}
    </div>
  )
}

function LibraryResultRow({ result, onPull }) {
  const sizes = (result.labels ?? []).slice(0, 5)
  return (
    <div className="card p-3 flex items-start justify-between gap-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-semibold text-[var(--text-primary)]">{result.modelIdentifier}</span>
          {!result.officialSource && (
            <span className="text-[10px] px-1.5 py-0.5 rounded border border-[var(--border-strong)] text-[var(--text-muted)]">community</span>
          )}
          {result.url && (
            <a href={result.url} target="_blank" rel="noreferrer" className="text-[var(--text-muted)] hover:text-[var(--text-primary)]" title="View on ollama.com">
              <ExternalLink className="w-3 h-3" />
            </a>
          )}
        </div>
        <p className="text-xs text-[var(--text-muted)] line-clamp-2 mt-0.5">{result.description}</p>
        {result.pulls > 0 && (
          <p className="text-[11px] text-[var(--text-muted)] mt-1">{result.pulls.toLocaleString()} pulls · {result.tagCount} tags</p>
        )}
      </div>
      <div className="flex flex-wrap gap-1.5 justify-end shrink-0 max-w-[45%]">
        {(sizes.length > 0 ? sizes : ['latest']).map(size => {
          const tag = size === 'latest' ? result.modelIdentifier : `${result.modelIdentifier}:${size.toLowerCase()}`
          return (
            <button
              key={tag}
              onClick={() => onPull(tag)}
              title={`Pull ${tag}`}
              className="btn-secondary text-[11px] px-2 py-1"
            >
              <Download className="w-3 h-3" />
              <span className="ml-1">{size}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
