import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import {
  getRuntimeDashboard,
  getModelSuggestions,
  loadRuntimeModel,
  unloadRuntimeModel,
  deleteRuntimeModel,
  pullRuntimeModel,
  saveRuntimeModelSettings,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { ConfirmModal } from '../components/ConfirmModal'
import { Link, useSearchParams } from 'react-router-dom'
import {
  Gauge, Play, Pause, Trash2, Settings2, Download, Loader2, MemoryStick, RotateCw,
  Search, LayoutGrid, Rows3, Copy, Check, Thermometer, Layers, Clock3, Sparkles, Zap, HardDrive,
} from 'lucide-react'
import toast from 'react-hot-toast'

const REFRESH_MS = 15000
const REFRESH_PULLING_MS = 2000 // poll fast while a download is in flight
const VIEW_LS_KEY = 'runtime-view' // 'grid' | 'list'
const MODEL_FILTERS = ['ALL', 'LOADED', 'ON_DISK']

const MODEL_STATUS_TOKENS = {
  LOADED:   { accent: 'var(--status-running)',    background: 'var(--status-running-bg)',    border: 'var(--status-running-border)',    text: 'var(--status-running)' },
  ON_DISK:  { accent: 'var(--status-stopped)',     background: 'var(--status-stopped-bg)',     border: 'var(--status-stopped-border)',     text: 'var(--text-muted)' },
  FAILED:   { accent: 'var(--status-error)',       background: 'var(--status-error-bg)',       border: 'var(--status-error-border)',       text: 'var(--status-error)' },
  PULLING:  { accent: 'var(--status-deploying)',   background: 'var(--status-deploying-bg)',   border: 'var(--status-deploying-border)',   text: 'var(--status-deploying)' },
}

function gb(bytes) {
  if (!bytes) return '—'
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

/** Like {@link gb} but renders 0 as "0.0 GB" (progress counters start at zero). */
function gbProgress(bytes) {
  return `${((bytes ?? 0) / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function expiryLabel(expiresAt) {
  if (!expiresAt) return null
  const ts = Date.parse(expiresAt)
  if (Number.isNaN(ts)) return null
  // Ollama uses a far-future timestamp for keep_alive=-1 (resident until unloaded).
  if (ts - Date.now() > 1000 * 60 * 60 * 24 * 365) return 'resident'
  const mins = Math.max(0, Math.round((ts - Date.now()) / 60000))
  return mins === 0 ? 'expiring' : `~${mins}m left`
}

export function RuntimePage() {
  const [dash, setDash]           = useState(null)
  const [loading, setLoading]     = useState(true)
  const [busyModel, setBusyModel] = useState(null) // model name with an action in flight
  const [settingsFor, setSettingsFor] = useState(null) // model whose editor is open
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [pullTag, setPullTag]     = useState('')
  const [catalog, setCatalog]     = useState([]) // model suggestions for autocomplete
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch]       = useState('')
  const [modelFilter, setModelFilter] = useState('ALL')
  const [view, setView]           = useState(() => {
    if (typeof window === 'undefined') return 'grid'
    return localStorage.getItem(VIEW_LS_KEY) === 'list' ? 'list' : 'grid'
  })
  const timerRef = useRef(null)
  const autoPulledRef = useRef(false)
  const searchInputRef = useRef(null)

  const refresh = useCallback(async (silent = true) => {
    if (!silent) setLoading(true)
    try {
      setDash(await getRuntimeDashboard())
    } catch {
      if (!silent) toast.error('Failed to load the runtime dashboard')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [])

  const hasActivePull = Object.values(dash?.pulls ?? {}).some(p => p?.state === 'pulling')

  useEffect(() => {
    const kick = setTimeout(() => refresh(false), 0)
    return () => clearTimeout(kick)
  }, [refresh])

  // Poll fast while a pull is downloading so the progress bar moves.
  useEffect(() => {
    timerRef.current = setInterval(
      () => refresh(true),
      hasActivePull ? REFRESH_PULLING_MS : REFRESH_MS,
    )
    return () => clearInterval(timerRef.current)
  }, [refresh, hasActivePull])

  // Catalog for the pull autocomplete — one fetch, filtered client-side.
  useEffect(() => {
    getModelSuggestions('', '').then(setCatalog).catch(() => {})
  }, [])

  useEffect(() => {
    localStorage.setItem(VIEW_LS_KEY, view)
  }, [view])

  // DX: "/" jumps focus to the model search, like most dev dashboards.
  // Only bail on metaKey/ctrlKey (real OS/browser shortcuts) — altKey is left alone because
  // several non-US keyboard layouts produce "/" via AltGr, which browsers report as ctrl+alt.
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

  const startPull = useCallback(async (tag) => {
    const trimmed = (tag ?? '').trim()
    if (!trimmed) return
    try {
      await pullRuntimeModel(trimmed)
      toast.success(`Pulling ${trimmed}`)
      setPullTag('')
      await refresh(true)
    } catch {
      toast.error(`Could not start pull for ${trimmed}`)
    }
  }, [refresh])

  // Arriving from the Model Cookbook with ?pull=<tag> starts the pull immediately.
  useEffect(() => {
    const tag = searchParams.get('pull')
    if (!tag || autoPulledRef.current) return
    autoPulledRef.current = true
    setSearchParams({}, { replace: true })
    startPull(tag)
  }, [searchParams, setSearchParams, startPull])

  const act = async (model, fn, okMsg) => {
    setBusyModel(model)
    try {
      await fn(model)
      toast.success(okMsg)
      await refresh(true)
    } catch (e) {
      toast.error(e.response?.data?.message || `Action failed for ${model}`)
    } finally {
      setBusyModel(null)
    }
  }

  const models = useMemo(() => dash?.models ?? [], [dash])
  const pulls = Object.entries(dash?.pulls ?? {})
  const installed = useMemo(() => new Set(models.map(m => m.name)), [models])
  const loadedCount = models.filter(m => m.loaded).length
  const onDiskCount = models.length - loadedCount

  const filteredModels = useMemo(() => {
    const q = search.trim().toLowerCase()
    return models.filter(m => {
      if (modelFilter === 'LOADED' && !m.loaded) return false
      if (modelFilter === 'ON_DISK' && m.loaded) return false
      if (q && !m.name.toLowerCase().includes(q)) return false
      return true
    })
  }, [models, modelFilter, search])

  const isFiltered = search.trim() !== '' || modelFilter !== 'ALL'
  const clearFilters = () => { setSearch(''); setModelFilter('ALL') }

  return (
    <AppShell onRefresh={() => refresh(false)}>
      {/* ── Header ── */}
      <div className="mb-6 animate-fade-up">
        <h1 className="text-xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
          <Gauge className="w-5 h-5 text-[var(--status-deploying)]" />
          Model Runtime
        </h1>
        <p className="text-sm text-[var(--text-muted)] mt-0.5">
          One Ollama serves every model — run loads it into memory, pause frees it. Blobs stay on disk.
        </p>
      </div>

      {/* ── Runtime card ── */}
      <div className="card p-4 mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 animate-fade-up delay-100">
        <Stat label="Runtime" value={dash?.baseUrl ?? '—'} />
        <Stat
          label="Status"
          value={dash ? (dash.reachable ? 'reachable' : 'unreachable') : '—'}
          tone={dash?.reachable ? 'ok' : 'bad'}
        />
        <Stat label="GPU" value={dash?.gpuVendor ?? '—'} />
        <Stat
          label="Managed instance"
          value={dash?.managedInstanceName
            ? `${dash.managedInstanceName} (${dash.managedInstanceStatus})`
            : 'none'}
        />
      </div>

      {dash && !dash.managedInstanceName && (
        <div className="card p-3 mb-6 text-sm text-[var(--text-muted)] animate-fade-up delay-150">
          No managed Ollama instance is running.{' '}
          {dash.reachable ? (
            <>
              A runtime is reachable at <span className="font-mono">{dash.baseUrl}</span> (e.g. a
              Homebrew <span className="font-mono">ollama</span> service), but Port Wrangler is not
              managing it yet.{' '}
              <Link to="/instances" className="underline text-[var(--text-primary)]">
                Import it from the Instances page
              </Link>{' '}
              to manage it here, or{' '}
              <Link to="/deploy" className="underline text-[var(--text-primary)]">
                deploy a new one
              </Link>{' '}
              (type <span className="font-mono">OLLAMA</span>).
            </>
          ) : (
            <>
              <Link to="/deploy" className="underline text-[var(--text-primary)]">
                Deploy one from the catalog
              </Link>{' '}
              (type <span className="font-mono">OLLAMA</span>), or start a Homebrew{' '}
              <span className="font-mono">ollama</span> service and{' '}
              <Link to="/instances" className="underline text-[var(--text-primary)]">
                import it from the Instances page
              </Link>
              .
            </>
          )}
        </div>
      )}

      {/* ── Overview stat cards ── */}
      {models.length > 0 && (
        <section className="mb-8 animate-fade-up delay-150">
          <p className="section-label">Overview</p>
          <div className="flex flex-wrap gap-3 stagger-children">
            <StatCard
              label="Total models" value={models.length} tone="deploying"
              icon={<Sparkles className="w-4 h-4" />}
              active={modelFilter === 'ALL'} onClick={() => setModelFilter('ALL')}
            />
            <StatCard
              label="Loaded" value={loadedCount} tone="running"
              icon={<Zap className="w-4 h-4" />} pulse={loadedCount > 0}
              active={modelFilter === 'LOADED'} onClick={() => setModelFilter('LOADED')}
            />
            <StatCard
              label="On disk" value={onDiskCount} tone="stopped"
              icon={<HardDrive className="w-4 h-4" />}
              active={modelFilter === 'ON_DISK'} onClick={() => setModelFilter('ON_DISK')}
            />
            {pulls.length > 0 && (
              <StatCard
                label="Downloading" value={pulls.length} tone="deploying"
                icon={<Download className="w-4 h-4" />} pulse
              />
            )}
          </div>
        </section>
      )}

      {/* ── Pull ── */}
      <div className="card p-4 mb-6 animate-fade-up delay-150">
        <PullBar
          value={pullTag}
          onChange={setPullTag}
          onPull={startPull}
          catalog={catalog}
          installed={installed}
        />
      </div>

      {/* ── Search / filter / view toggle ──
           Always mounted (not gated on models.length) so the search input — and the "/" shortcut's
           focus target — exists as soon as the page renders, even before the dashboard has loaded. */}
      <div className="flex items-center gap-3 mb-6 flex-wrap animate-fade-up delay-150">
        <div className="relative flex-1 min-w-52">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)]" />
          <input
            ref={searchInputRef}
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search models by tag… (press /)"
            className="input pl-9"
          />
        </div>
        <div className="flex gap-1.5 flex-wrap">
          {MODEL_FILTERS.map(f => (
            <FilterChip key={f} label={f.replace('_', ' ')} active={modelFilter === f} onClick={() => setModelFilter(f)} />
          ))}
        </div>
        <div className="flex gap-1 p-1 rounded-[6px] border-2 border-[var(--border-strong)] bg-[var(--bg-surface-2)] shadow-[var(--shadow-raised)]">
          <ViewToggleBtn active={view === 'grid'} onClick={() => setView('grid')} icon={<LayoutGrid className="w-3.5 h-3.5" />} label="Grid" />
          <ViewToggleBtn active={view === 'list'} onClick={() => setView('list')} icon={<Rows3 className="w-3.5 h-3.5" />} label="List" />
        </div>
      </div>

      {/* ── Models ── */}
      {loading ? (
        <div className="flex items-center justify-center py-24 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading models…
        </div>
      ) : models.length === 0 && pulls.length === 0 ? (
        <EmptyModelsState />
      ) : (
        <>
          {view === 'grid' ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4 stagger-children animate-fade-up delay-200">
              {pulls.map(([tag, pull]) => (
                <div key={`pull-${tag}`} className="sm:col-span-2 xl:col-span-3 2xl:col-span-4">
                  <PullCard tag={tag} pull={pull} onRetry={() => startPull(tag)} />
                </div>
              ))}
              {filteredModels.map(m => (
                <ModelCard
                  key={m.name}
                  model={m}
                  busy={busyModel === m.name}
                  reachable={dash?.reachable}
                  settingsOpen={settingsFor === m.name}
                  onLoad={() => act(m.name, loadRuntimeModel, `Loaded ${m.name}`)}
                  onUnload={() => act(m.name, unloadRuntimeModel, `Unloaded ${m.name}`)}
                  onDelete={() => setDeleteTarget(m.name)}
                  onToggleSettings={() => setSettingsFor(settingsFor === m.name ? null : m.name)}
                  onSettingsSaved={async () => {
                    setSettingsFor(null)
                    toast.success(`Saved settings for ${m.name}`)
                    await refresh(true)
                  }}
                />
              ))}
            </div>
          ) : (
            <div className="space-y-3 stagger-children animate-fade-up delay-200">
              {pulls.map(([tag, pull]) => (
                <PullCard key={`pull-${tag}`} tag={tag} pull={pull} onRetry={() => startPull(tag)} />
              ))}
              {filteredModels.map(m => (
                <ModelRow
                  key={m.name}
                  model={m}
                  busy={busyModel === m.name}
                  reachable={dash?.reachable}
                  settingsOpen={settingsFor === m.name}
                  onLoad={() => act(m.name, loadRuntimeModel, `Loaded ${m.name}`)}
                  onUnload={() => act(m.name, unloadRuntimeModel, `Unloaded ${m.name}`)}
                  onDelete={() => setDeleteTarget(m.name)}
                  onToggleSettings={() => setSettingsFor(settingsFor === m.name ? null : m.name)}
                  onSettingsSaved={async () => {
                    setSettingsFor(null)
                    toast.success(`Saved settings for ${m.name}`)
                    await refresh(true)
                  }}
                />
              ))}
            </div>
          )}

          {filteredModels.length === 0 && pulls.length === 0 && (
            <EmptyModelsState isFiltered={isFiltered} onClear={clearFilters} />
          )}
        </>
      )}

      <ConfirmModal
        open={!!deleteTarget}
        variant="danger"
        title="Delete model"
        message={`Delete '${deleteTarget}' from the runtime's disk? Pull it again to get it back.`}
        confirmLabel="Delete"
        onConfirm={async () => {
          const target = deleteTarget
          setDeleteTarget(null)
          await act(target, deleteRuntimeModel, `Deleted ${target}`)
        }}
        onCancel={() => setDeleteTarget(null)}
      />
    </AppShell>
  )
}

/** Pull input with tag autocomplete fed by the Model Cookbook catalog. */
function PullBar({ value, onChange, onPull, catalog, installed }) {
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(-1)

  const matches = useMemo(() => {
    const q = value.trim().toLowerCase()
    const pool = q
      ? catalog.filter(s =>
          s.model.ollamaTag.toLowerCase().includes(q) ||
          s.model.family.toLowerCase().includes(q))
      : catalog
    return pool.slice(0, 8)
  }, [catalog, value])

  const pick = (tag) => {
    setOpen(false)
    setHighlight(-1)
    onChange(tag)
    onPull(tag)
  }

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setHighlight(h => Math.min(h + 1, matches.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight(h => Math.max(h - 1, -1))
    } else if (e.key === 'Enter') {
      if (open && highlight >= 0 && matches[highlight]) {
        pick(matches[highlight].model.ollamaTag)
      } else if (value.trim()) {
        setOpen(false)
        onPull(value)
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
      setHighlight(-1)
    }
  }

  return (
    <div className="relative">
      <div className="flex flex-wrap items-center gap-2">
        <Download className="w-4 h-4 text-[var(--text-muted)]" />
        <input
          value={value}
          onChange={e => { onChange(e.target.value); setOpen(true); setHighlight(-1) }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={onKeyDown}
          placeholder="Pull a model — type to search the catalog, e.g. llama3.1:8b"
          className="input flex-1 min-w-[220px]"
          role="combobox"
          aria-expanded={open}
          aria-autocomplete="list"
        />
        <button className="btn-primary" onClick={() => onPull(value)} disabled={!value.trim()}>
          Pull
        </button>
      </div>

      {open && matches.length > 0 && (
        <ul
          className="absolute left-6 right-16 z-20 mt-1 max-h-72 overflow-auto rounded-[6px] border-2 border-[var(--border-strong)] bg-[var(--bg-surface)] shadow-[var(--shadow-raised)]"
          role="listbox"
        >
          {matches.map((s, i) => {
            const tag = s.model.ollamaTag
            const isInstalled = installed.has(tag)
            return (
              <li
                key={tag}
                role="option"
                aria-selected={i === highlight}
                onMouseDown={e => { e.preventDefault(); pick(tag) }}
                onMouseEnter={() => setHighlight(i)}
                className={`px-3 py-2 cursor-pointer flex items-center gap-3 text-sm ${
                  i === highlight ? 'bg-[var(--accent-soft)]' : ''
                }`}
              >
                <span className="font-mono text-[var(--text-primary)]">{tag}</span>
                <span className="text-xs text-[var(--text-muted)] truncate">{s.model.family} · {s.model.paramsBillions}B</span>
                <span className="ml-auto flex items-center gap-2 shrink-0">
                  {isInstalled && (
                    <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded border border-[var(--border-strong)] text-[var(--text-muted)]">
                      installed
                    </span>
                  )}
                  <CompatDot compatibility={s.compatibility} />
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

const COMPAT_DOT = {
  FAST: '#22c55e', OK: '#f59e0b', CPU_ONLY: '#fb923c', TOO_LARGE: '#ef4444',
}

function CompatDot({ compatibility }) {
  return (
    <span
      title={compatibility}
      className="w-2 h-2 rounded-full inline-block"
      style={{ background: COMPAT_DOT[compatibility] ?? '#6b7280' }}
    />
  )
}

/** Copy-to-clipboard affordance for model tags — this is a dev tool, tags get pasted into terminals a lot. */
function CopyButton({ text, title = 'Copy' }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      onClick={(e) => {
        e.stopPropagation()
        navigator.clipboard?.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1200)
        }).catch(() => {})
      }}
      title={title}
      className="opacity-0 group-hover:opacity-60 hover:!opacity-100 p-0.5 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-opacity shrink-0"
    >
      {copied ? <Check className="w-3.5 h-3.5" style={{ color: 'var(--status-running)' }} /> : <Copy className="w-3.5 h-3.5" />}
    </button>
  )
}

/** Small bordered action button — mirrors InstanceCard's footer actions for a consistent visual language. */
function ActionBtn({ icon, label, color, onClick, disabled, title }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title || label}
      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-[4px] text-xs font-semibold border-2 bg-transparent transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${color}`}
    >
      {icon}
      {label}
    </button>
  )
}

/** Icon-only utility button (settings / delete). */
function IconBtn({ icon, title, onClick, active }) {
  return (
    <button
      onClick={onClick}
      title={title}
      className={`p-1.5 rounded-[4px] border-2 transition-colors ${
        active
          ? 'bg-[var(--accent-soft)] border-[var(--border-strong)]'
          : 'border-transparent hover:border-[var(--border-strong)] hover:bg-[var(--bg-surface-2)]'
      }`}
    >
      {icon}
    </button>
  )
}

function ModelMeta({ m }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span>{gb(m.sizeBytes)}</span>
        {m.quantization && <span>{m.quantization}</span>}
        {m.loaded && m.sizeVramBytes > 0 && (
          <span className="flex items-center gap-1">
            <MemoryStick className="w-3 h-3" /> {gb(m.sizeVramBytes)} VRAM
          </span>
        )}
      </div>
      {(m.temperature != null || m.numCtx != null || m.keepAlive) && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {m.temperature != null && (
            <span className="flex items-center gap-1"><Thermometer className="w-3 h-3" /> {m.temperature}</span>
          )}
          {m.numCtx != null && (
            <span className="flex items-center gap-1"><Layers className="w-3 h-3" /> ctx {m.numCtx}</span>
          )}
          {m.keepAlive && (
            <span className="flex items-center gap-1"><Clock3 className="w-3 h-3" /> keep {m.keepAlive}</span>
          )}
        </div>
      )}
    </>
  )
}

function ModelActions({ m, busy, reachable, onLoad, onUnload, onDelete, onToggleSettings, settingsOpen }) {
  return (
    <>
      {m.loaded ? (
        <ActionBtn
          icon={busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Pause className="w-3.5 h-3.5" />}
          label="Pause"
          title="Unload from memory (blobs stay on disk)"
          color="text-[var(--status-warning)] hover:bg-[var(--status-warning-bg)] border-[var(--status-warning-border)]"
          onClick={onUnload}
          disabled={busy}
        />
      ) : (
        <ActionBtn
          icon={busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
          label="Run"
          title="Load into memory"
          color="text-[var(--status-running)] hover:bg-[var(--status-running-bg)] border-[var(--status-running-border)]"
          onClick={onLoad}
          disabled={busy || !reachable}
        />
      )}
      <div className="flex-1" />
      <IconBtn
        icon={<Settings2 className="w-3.5 h-3.5" />}
        title="Per-model settings"
        onClick={onToggleSettings}
        active={settingsOpen}
      />
      <IconBtn
        icon={<Trash2 className="w-3.5 h-3.5" style={{ color: 'var(--status-error)' }} />}
        title="Delete model blobs from disk"
        onClick={onDelete}
      />
    </>
  )
}

/** Grid card for a model — same visual language as InstanceCard (accent stripe, status tint, action footer). */
function ModelCard({ model: m, busy, reachable, settingsOpen, onLoad, onUnload, onDelete, onToggleSettings, onSettingsSaved }) {
  const token = m.loaded ? MODEL_STATUS_TOKENS.LOADED : MODEL_STATUS_TOKENS.ON_DISK
  const exp = expiryLabel(m.expiresAt)

  return (
    <div className="card h-full flex flex-col overflow-hidden group transition-transform duration-150 hover:scale-[1.015]">
      <div className="h-[3px]" style={{ backgroundColor: token.accent }} />
      <div className="p-4 flex-1 flex flex-col" style={{ backgroundColor: token.background }}>
        <div className="flex items-start gap-3 mb-3">
          <span
            className="w-9 h-9 rounded-lg shrink-0 flex items-center justify-center border-2"
            style={{ borderColor: token.border, color: token.accent, background: 'var(--bg-surface)' }}
          >
            {m.loaded ? <Zap className="w-4 h-4" /> : <HardDrive className="w-4 h-4" />}
          </span>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1 min-w-0">
              <span className="font-mono text-sm font-semibold text-[var(--text-primary)] truncate" title={m.name}>
                {m.name}
              </span>
              <CopyButton text={m.name} title="Copy model tag" />
            </div>
            <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
              {m.loaded ? (
                <span className="status-pill" style={{ color: token.text, borderColor: token.border, background: 'transparent' }}>
                  loaded{exp ? ` · ${exp}` : ''}
                </span>
              ) : (
                <span className="status-pill" style={{ color: 'var(--text-muted)', borderColor: 'var(--border-strong)' }}>
                  on disk
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="text-xs text-[var(--text-muted)] space-y-1 pl-12 flex-1">
          <ModelMeta m={m} />
        </div>

        <div className="flex items-center gap-2 mt-4 pt-3 border-t-2 border-[var(--border-strong)]">
          <ModelActions
            m={m} busy={busy} reachable={reachable}
            onLoad={onLoad} onUnload={onUnload} onDelete={onDelete}
            onToggleSettings={onToggleSettings} settingsOpen={settingsOpen}
          />
        </div>

        {settingsOpen && (
          <ModelSettingsEditor model={m} onSaved={onSettingsSaved} onCancel={onToggleSettings} />
        )}
      </div>
    </div>
  )
}

/** Horizontal row variant — same data, wide-screen-friendly layout for scanning long model lists. */
function ModelRow({ model: m, busy, reachable, settingsOpen, onLoad, onUnload, onDelete, onToggleSettings, onSettingsSaved }) {
  const token = m.loaded ? MODEL_STATUS_TOKENS.LOADED : MODEL_STATUS_TOKENS.ON_DISK
  const exp = expiryLabel(m.expiresAt)

  return (
    <div className="card overflow-hidden flex group">
      <div className="w-[3px] shrink-0" style={{ backgroundColor: token.accent }} />
      <div className="p-4 flex-1" style={{ backgroundColor: token.background }}>
        <div className="flex flex-wrap items-center gap-3">
          <span
            className="w-8 h-8 rounded-lg shrink-0 flex items-center justify-center border-2"
            style={{ borderColor: token.border, color: token.accent, background: 'var(--bg-surface)' }}
          >
            {m.loaded ? <Zap className="w-4 h-4" /> : <HardDrive className="w-4 h-4" />}
          </span>

          <div className="flex-1 min-w-[220px]">
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="font-mono text-sm font-semibold text-[var(--text-primary)]">{m.name}</span>
              <CopyButton text={m.name} title="Copy model tag" />
              {m.loaded ? (
                <span className="status-pill" style={{ color: token.text, borderColor: token.border, background: 'transparent' }}>
                  loaded{exp ? ` · ${exp}` : ''}
                </span>
              ) : (
                <span className="status-pill" style={{ color: 'var(--text-muted)', borderColor: 'var(--border-strong)' }}>
                  on disk
                </span>
              )}
            </div>
            <div className="text-xs text-[var(--text-muted)] mt-1 flex flex-wrap gap-x-4 gap-y-1">
              <ModelMeta m={m} />
            </div>
          </div>

          <div className="flex items-center gap-2">
            <ModelActions
              m={m} busy={busy} reachable={reachable}
              onLoad={onLoad} onUnload={onUnload} onDelete={onDelete}
              onToggleSettings={onToggleSettings} settingsOpen={settingsOpen}
            />
          </div>
        </div>

        {settingsOpen && (
          <ModelSettingsEditor model={m} onSaved={onSettingsSaved} onCancel={onToggleSettings} />
        )}
      </div>
    </div>
  )
}

/**
 * An in-flight or failed pull rendered as a model card, so a downloading model looks like it is
 * already "arriving" in the list — same layout as installed rows, with a live progress bar.
 */
function PullCard({ tag, pull, onRetry }) {
  const failed = pull?.state === 'failed'
  const token = failed ? MODEL_STATUS_TOKENS.FAILED : MODEL_STATUS_TOKENS.PULLING
  const total = pull?.totalBytes ?? 0
  const done  = pull?.completedBytes ?? 0
  const pct   = total > 0 ? Math.min(100, (done / total) * 100) : 0

  return (
    <div className="card overflow-hidden flex">
      <div className="w-[3px] shrink-0" style={{ backgroundColor: token.accent }} />
      <div className="p-4 flex-1">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex-1 min-w-[200px]">
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm text-[var(--text-primary)]">{tag}</span>
              {failed ? (
                <span className="status-pill" style={{ color: token.text, borderColor: token.border, background: token.background }}>
                  pull failed
                </span>
              ) : (
                <span className="status-pill flex items-center gap-1" style={{ color: token.text, borderColor: token.border, background: token.background }}>
                  <Loader2 className="w-3 h-3 animate-spin" />
                  downloading{total > 0 ? ` · ${pct.toFixed(0)}%` : ''}
                </span>
              )}
            </div>
            <div className="text-xs text-[var(--text-muted)] mt-1 flex flex-wrap gap-x-4">
              {failed ? (
                <span style={{ color: 'var(--status-error)' }} className="break-all">{pull.status}</span>
              ) : (
                <>
                  <span className="tabular-nums">
                    {total > 0 ? `${gbProgress(done)} of ${gbProgress(total)}` : 'waiting for size…'}
                  </span>
                  <span className="truncate">{pull?.status || 'starting'}</span>
                </>
              )}
            </div>
          </div>
          {failed && (
            <button className="btn-secondary" onClick={onRetry} title="Retry pull">
              <RotateCw className="w-4 h-4" />
              <span className="ml-1">Retry</span>
            </button>
          )}
        </div>

        {!failed && (
          <div className="mt-3 h-2 rounded-full bg-[var(--bg-surface-2)] border border-[var(--border-strong)] overflow-hidden">
            <div
              className="h-full rounded-full transition-[width] duration-500"
              style={{
                width: total > 0 ? `${pct}%` : '100%',
                background: 'var(--status-deploying)',
                opacity: total > 0 ? 1 : 0.25, // indeterminate until Ollama reports sizes
              }}
            />
          </div>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value, tone }) {
  const color = tone === 'ok' ? 'var(--status-running)' : tone === 'bad' ? 'var(--status-error)' : 'var(--text-primary)'
  return (
    <div className="flex items-baseline gap-1.5 text-sm">
      <span className="text-[var(--text-muted)]">{label}:</span>
      <span className="font-semibold" style={{ color }}>{value}</span>
    </div>
  )
}

/** Clickable overview tile — doubles as a status filter, same pattern as the Instances page. */
function StatCard({ label, value, icon, tone, active, onClick, pulse }) {
  const bg = `var(--status-${tone}-bg)`
  const border = `var(--status-${tone}-border)`
  const color = `var(--status-${tone})`
  const clickable = !!onClick
  const Wrapper = clickable ? 'button' : 'div'

  return (
    <Wrapper
      type={clickable ? 'button' : undefined}
      onClick={onClick}
      aria-pressed={clickable ? active : undefined}
      className={`stat-card border flex-1 min-w-[130px] transition-all duration-200 group text-left ${
        clickable ? 'cursor-pointer hover:scale-[1.03] hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/20' : ''
      } ${active ? 'ring-2 ring-[var(--border-strong)] shadow-[var(--shadow-raised)]' : ''}`}
      style={{ borderColor: border }}
    >
      <div
        className="w-8 h-8 rounded-lg flex items-center justify-center transition-transform duration-200 group-hover:scale-110"
        style={{ background: bg, color }}
      >
        {icon}
      </div>
      <div>
        <div className={`text-2xl font-bold text-[var(--text-primary)] tabular-nums ${pulse ? 'animate-pulse' : ''}`}>
          {value}
        </div>
        <div className="text-xs text-[var(--text-muted)] mt-0.5">{label}</div>
      </div>
    </Wrapper>
  )
}

function FilterChip({ label, active, onClick }) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1.5 rounded-[4px] text-xs font-semibold transition-colors border-2 ${
        active
          ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)] shadow-[var(--shadow-raised)]'
          : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
      }`}
    >
      {label}
    </button>
  )
}

function ViewToggleBtn({ active, onClick, icon, label }) {
  return (
    <button
      onClick={onClick}
      title={`${label} view`}
      aria-pressed={active}
      className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-[4px] text-xs font-semibold transition-colors ${
        active
          ? 'bg-[var(--accent-soft)] text-[var(--text-primary)]'
          : 'text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-surface-3)]'
      }`}
    >
      {icon}
      <span className="hidden sm:inline">{label}</span>
    </button>
  )
}

function EmptyModelsState({ isFiltered, onClear }) {
  return (
    <div className="card p-16 text-center animate-scale-in">
      <div className="text-5xl mb-4">{isFiltered ? '🔍' : '🧠'}</div>
      <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">
        {isFiltered ? 'No matches found' : 'No models yet'}
      </h2>
      <p className="text-[var(--text-muted)] text-sm mb-6">
        {isFiltered ? 'Try clearing the search or filter' : 'Pull one above, or browse the Model Cookbook'}
      </p>
      {isFiltered ? (
        <button onClick={onClear} className="btn-secondary inline-flex items-center gap-2">Clear filters</button>
      ) : (
        <Link to="/models" className="btn-primary inline-flex items-center gap-2">
          <Sparkles className="w-4 h-4" /> Browse Model Cookbook
        </Link>
      )}
    </div>
  )
}

function ModelSettingsEditor({ model, onSaved, onCancel }) {
  const [temperature, setTemperature] = useState(model.temperature ?? '')
  const [numCtx, setNumCtx]           = useState(model.numCtx ?? '')
  const [keepAlive, setKeepAlive]     = useState(model.keepAlive ?? '')
  const [saving, setSaving]           = useState(false)

  const save = async () => {
    setSaving(true)
    try {
      await saveRuntimeModelSettings(model.name, {
        temperature: temperature === '' ? null : Number(temperature),
        numCtx: numCtx === '' ? null : Number(numCtx),
        keepAlive: keepAlive === '' ? null : keepAlive,
      })
      await onSaved()
    } catch {
      toast.error(`Failed to save settings for ${model.name}`)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div
      className="mt-3 pt-3 border-t border-[var(--border-strong)] grid grid-cols-1 sm:grid-cols-4 gap-3 items-end"
      onClick={e => e.stopPropagation()}
    >
      <label className="text-xs text-[var(--text-muted)]">
        Temperature (0–2)
        <input type="number" step="0.1" min="0" max="2" value={temperature}
               onChange={e => setTemperature(e.target.value)}
               placeholder="runtime default" className="input mt-1 w-full" />
      </label>
      <label className="text-xs text-[var(--text-muted)]">
        Context window (num_ctx)
        <input type="number" step="512" min="512" value={numCtx}
               onChange={e => setNumCtx(e.target.value)}
               placeholder="runtime default" className="input mt-1 w-full" />
      </label>
      <label className="text-xs text-[var(--text-muted)]">
        Keep-alive (&quot;5m&quot;, &quot;-1&quot; = resident)
        <input value={keepAlive}
               onChange={e => setKeepAlive(e.target.value)}
               placeholder="runtime default" className="input mt-1 w-full" />
      </label>
      <div className="flex gap-2">
        <button className="btn-primary" onClick={save} disabled={saving}>
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Save'}
        </button>
        <button className="btn-secondary" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
