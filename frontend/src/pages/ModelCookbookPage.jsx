import { useState, useEffect, useCallback } from 'react'
import { getModelSuggestions, getSystemProfile } from '../api/client'
import { AppShell } from '../components/AppShell'
import { Cpu, Sparkles, Zap } from 'lucide-react'
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
  const [loading, setLoading]         = useState(true)

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

      {/* ── Filters ── */}
      <div className="flex flex-col gap-3 mb-6 animate-fade-up delay-150">
        <FilterRow label="Type" options={TYPE_FILTERS} active={typeFilter} onChange={setTypeFilter} />
        <FilterRow label="Fit" options={COMPAT_FILTERS} active={compatFilter} onChange={setCompatFilter} />
      </div>

      {/* ── Cards ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading models…
        </div>
      ) : suggestions.length === 0 ? (
        <div className="card p-16 text-center animate-scale-in">
          <div className="text-5xl mb-4">🍳</div>
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">No models match</h2>
          <p className="text-[var(--text-muted)] text-sm">Try widening the type or fit filters.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4 stagger-children animate-fade-up delay-200">
          {suggestions.map(s => (
            <ModelCard key={s.model.ollamaTag} suggestion={s} />
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

function ModelCard({ suggestion }) {
  const { model, compatibility, speedTier } = suggestion
  const style = COMPAT_STYLE[compatibility] ?? COMPAT_STYLE.TOO_LARGE
  const [copied, setCopied] = useState(false)

  const copyPull = async () => {
    await navigator.clipboard.writeText(`ollama pull ${model.ollamaTag}`)
    setCopied(true)
    toast.success(`Copied: ollama pull ${model.ollamaTag}`)
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

      <div className="flex items-center gap-3 text-[11px] text-[var(--text-muted)]">
        <span className="px-1.5 py-0.5 rounded bg-[var(--bg-surface-2)] border border-[var(--border-strong)]">{model.type}</span>
        <span>{model.paramsBillions}B</span>
        <span className="flex items-center gap-1"><Zap className="w-3 h-3" />{speedTier}</span>
      </div>

      <button
        onClick={copyPull}
        disabled={compatibility === 'TOO_LARGE'}
        className="btn-secondary text-xs w-full justify-center disabled:opacity-40 disabled:cursor-not-allowed"
      >
        {copied ? 'Copied!' : `Pull ${model.ollamaTag}`}
      </button>
    </div>
  )
}
