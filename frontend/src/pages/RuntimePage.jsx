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
} from 'lucide-react'
import toast from 'react-hot-toast'

const REFRESH_MS = 15000
const REFRESH_PULLING_MS = 2000 // poll fast while a download is in flight

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
  const timerRef = useRef(null)
  const autoPulledRef = useRef(false)

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
        <Stat label="Loaded models" value={`${loadedCount}/${models.length}`} />
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

      {/* ── Models ── */}
      {loading ? (
        <div className="text-sm text-[var(--text-muted)] animate-pulse">Loading models…</div>
      ) : models.length === 0 && pulls.length === 0 ? (
        <div className="card p-6 text-sm text-[var(--text-muted)] animate-fade-up delay-200">
          No models yet. Pull one above, or from the <Link to="/models" className="underline">Model Cookbook</Link>.
        </div>
      ) : (
        <div className="space-y-3 animate-fade-up delay-200">
          {/* In-flight and failed pulls render as cards ahead of the installed models. */}
          {pulls.map(([tag, pull]) => (
            <PullCard key={`pull-${tag}`} tag={tag} pull={pull} onRetry={() => startPull(tag)} />
          ))}
          {models.map(m => (
            <div key={m.name} className="card p-4">
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex-1 min-w-[200px]">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm text-[var(--text-primary)]">{m.name}</span>
                    {m.loaded ? (
                      <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold"
                            style={{ background: 'rgba(34,197,94,0.12)', color: '#22c55e', border: '1px solid rgba(34,197,94,0.4)' }}>
                        loaded{expiryLabel(m.expiresAt) ? ` · ${expiryLabel(m.expiresAt)}` : ''}
                      </span>
                    ) : (
                      <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold text-[var(--text-muted)] border border-[var(--border-strong)]">
                        on disk
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-[var(--text-muted)] mt-1 flex flex-wrap gap-x-4">
                    <span>{gb(m.sizeBytes)}</span>
                    {m.quantization && <span>{m.quantization}</span>}
                    {m.loaded && m.sizeVramBytes > 0 && (
                      <span className="flex items-center gap-1">
                        <MemoryStick className="w-3 h-3" /> {gb(m.sizeVramBytes)} VRAM
                      </span>
                    )}
                    {(m.temperature != null || m.numCtx != null || m.keepAlive) && (
                      <span>
                        {m.temperature != null && `temp ${m.temperature}`}
                        {m.numCtx != null && ` · ctx ${m.numCtx}`}
                        {m.keepAlive && ` · keep ${m.keepAlive}`}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {m.loaded ? (
                    <button
                      className="btn-secondary"
                      disabled={busyModel === m.name}
                      onClick={() => act(m.name, unloadRuntimeModel, `Unloaded ${m.name}`)}
                      title="Unload from memory (blobs stay on disk)"
                    >
                      {busyModel === m.name ? <Loader2 className="w-4 h-4 animate-spin" /> : <Pause className="w-4 h-4" />}
                      <span className="ml-1">Pause</span>
                    </button>
                  ) : (
                    <button
                      className="btn-primary"
                      disabled={busyModel === m.name || !dash?.reachable}
                      onClick={() => act(m.name, loadRuntimeModel, `Loaded ${m.name}`)}
                      title="Load into memory"
                    >
                      {busyModel === m.name ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                      <span className="ml-1">Run</span>
                    </button>
                  )}
                  <button
                    className="btn-secondary"
                    onClick={() => setSettingsFor(settingsFor === m.name ? null : m.name)}
                    title="Per-model settings"
                  >
                    <Settings2 className="w-4 h-4" />
                  </button>
                  <button
                    className="btn-secondary"
                    onClick={() => setDeleteTarget(m.name)}
                    title="Delete model blobs from disk"
                  >
                    <Trash2 className="w-4 h-4 text-[var(--status-error,#ef4444)]" />
                  </button>
                </div>
              </div>

              {settingsFor === m.name && (
                <ModelSettingsEditor
                  model={m}
                  onSaved={async () => {
                    setSettingsFor(null)
                    toast.success(`Saved settings for ${m.name}`)
                    await refresh(true)
                  }}
                  onCancel={() => setSettingsFor(null)}
                />
              )}
            </div>
          ))}
        </div>
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

/**
 * An in-flight or failed pull rendered as a model card, so a downloading model looks like it is
 * already "arriving" in the list — same layout as installed rows, with a live progress bar.
 */
function PullCard({ tag, pull, onRetry }) {
  const failed = pull?.state === 'failed'
  const total = pull?.totalBytes ?? 0
  const done  = pull?.completedBytes ?? 0
  const pct   = total > 0 ? Math.min(100, (done / total) * 100) : 0

  return (
    <div className="card p-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[200px]">
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm text-[var(--text-primary)]">{tag}</span>
            {failed ? (
              <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold"
                    style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.4)' }}>
                pull failed
              </span>
            ) : (
              <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold flex items-center gap-1"
                    style={{ background: 'rgba(59,130,246,0.12)', color: 'var(--status-deploying)', border: '1px solid rgba(59,130,246,0.4)' }}>
                <Loader2 className="w-3 h-3 animate-spin" />
                downloading{total > 0 ? ` · ${pct.toFixed(0)}%` : ''}
              </span>
            )}
          </div>
          <div className="text-xs text-[var(--text-muted)] mt-1 flex flex-wrap gap-x-4">
            {failed ? (
              <span className="text-[var(--status-error,#ef4444)] break-all">{pull.status}</span>
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
  )
}

function Stat({ label, value, tone }) {
  const color = tone === 'ok' ? '#22c55e' : tone === 'bad' ? '#ef4444' : 'var(--text-primary)'
  return (
    <div className="flex items-baseline gap-1.5 text-sm">
      <span className="text-[var(--text-muted)]">{label}:</span>
      <span className="font-semibold" style={{ color }}>{value}</span>
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
    <div className="mt-3 pt-3 border-t border-[var(--border-strong)] grid grid-cols-1 sm:grid-cols-4 gap-3 items-end">
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
