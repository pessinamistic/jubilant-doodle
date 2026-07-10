import { useState, useEffect, useCallback, useMemo } from 'react'
import { getModelSuggestions, getSystemProfile, getRuntimeDashboard } from '../api/client'
import { AppShell } from '../components/AppShell'
import { useNavigate } from 'react-router-dom'
import { Check, Copy, Cpu, Download, Search, Sparkles, Zap } from 'lucide-react'
import toast from 'react-hot-toast'

const TYPE_FILTERS = [
  { value: '', label: 'All' },
  { value: 'CHAT', label: 'Chat' },
  { value: 'CODE', label: 'Code' },
  { value: 'EMBEDDING', label: 'Embedding' },
  { value: 'VISION', label: 'Vision' },
  { value: 'REASONING', label: 'Reasoning' },
]

const COMPAT_FILTERS = [
  { value: '', label: 'All' },
  { value: 'FAST', label: 'Fast' },
  { value: 'OK', label: 'OK' },
  { value: 'CPU_ONLY', label: 'CPU only' },
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
  const [compatFilter, setCompatFilter] = useState('')
  const [query, setQuery]             = useState('')
  const [installed, setInstalled]     = useState(new Set()) // tags already on the runtime's disk
  const [pulling, setPulling]         = useState(new Set()) // tags with a pull in flight
  const [loading, setLoading]         = useState(true)
  const navigate = useNavigate()

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [prof, sugg] = await Promise.all([
        getSystemProfile(),
        getModelSuggestions(typeFilter, compatFilter),
      ])
      setProfile(prof)
      setSuggestions(sugg)
    } catch {
      toast.error('Failed to load model suggestions')
    } finally {
      setLoading(false)
    }
  }, [typeFilter, compatFilter])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    return () => clearTimeout(kick)
  }, [load])

  // Which catalog models are already pulled / being pulled — badges on the cards.
  useEffect(() => {
    getRuntimeDashboard()
      .then(dash => {
        setInstalled(new Set((dash.models ?? []).map(m => m.name)))
        setPulling(new Set(
          Object.entries(dash.pulls ?? {})
            .filter(([, p]) => p?.state === 'pulling')
            .map(([tag]) => tag),
        ))
      })
      .catch(() => {})
  }, [])

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return suggestions
    return suggestions.filter(s =>
      s.model.ollamaTag.toLowerCase().includes(q) ||
      s.model.family.toLowerCase().includes(q) ||
      (s.model.description ?? '').toLowerCase().includes(q))
  }, [suggestions, query])

  // Pull starts on the Runtime page (?pull=<tag>) so progress is visible immediately.
  const pullAndOpen = (tag) => navigate(`/runtime?pull=${encodeURIComponent(tag)}`)

  return (
    <AppShell onRefresh={load}>
      {/* ── Header ── */}
      <div className="mb-6 animate-fade-up">
        <h1 className="text-xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-[var(--status-deploying)]" />
          Model Cookbook
        </h1>
        <p className="text-sm text-[var(--text-muted)] mt-0.5">
          What your machine can actually run — scored against your detected hardware.
        </p>
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
        <div className="relative max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]" />
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Search models — name, family, or use case…"
            className="input w-full pl-9"
          />
        </div>
        <FilterRow label="Type" options={TYPE_FILTERS} active={typeFilter} onChange={setTypeFilter} />
        <FilterRow label="Fit" options={COMPAT_FILTERS} active={compatFilter} onChange={setCompatFilter} />
      </div>

      {/* ── Cards ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading models…
        </div>
      ) : visible.length === 0 ? (
        <div className="card p-16 text-center animate-scale-in">
          <div className="text-5xl mb-4">🍳</div>
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">No models match</h2>
          <p className="text-[var(--text-muted)] text-sm">Try a different search, or widen the type and fit filters.</p>
        </div>
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
    </AppShell>
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

function FilterRow({ label, options, active, onChange }) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-xs font-semibold uppercase tracking-widest text-[var(--text-muted)] w-12">{label}</span>
      {options.map(o => (
        <button
          key={o.value || 'all'}
          onClick={() => onChange(o.value)}
          className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-colors border-2 ${
            active === o.value
              ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)] shadow-[var(--shadow-raised)]'
              : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
          }`}
        >
          {o.label}
        </button>
      ))}
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
