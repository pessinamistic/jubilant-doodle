import { useState, useEffect, useCallback, useRef } from 'react'
import {
  getRuntimeDashboard,
  loadRuntimeModel,
  unloadRuntimeModel,
  deleteRuntimeModel,
  pullRuntimeModel,
  saveRuntimeModelSettings,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { ConfirmModal } from '../components/ConfirmModal'
import { Link } from 'react-router-dom'
import {
  Gauge, Play, Pause, Trash2, Settings2, Download, Loader2, MemoryStick, Zap,
} from 'lucide-react'
import toast from 'react-hot-toast'

const REFRESH_MS = 15000

function gb(bytes) {
  if (!bytes) return '—'
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
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
  const timerRef = useRef(null)

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

  useEffect(() => {
    const kick = setTimeout(() => refresh(false), 0)
    timerRef.current = setInterval(() => refresh(true), REFRESH_MS)
    return () => {
      clearTimeout(kick)
      clearInterval(timerRef.current)
    }
  }, [refresh])

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

  const onPull = async () => {
    const tag = pullTag.trim()
    if (!tag) return
    try {
      await pullRuntimeModel(tag)
      toast.success(`Pulling ${tag} — it appears in the list when done`)
      setPullTag('')
      await refresh(true)
    } catch {
      toast.error(`Could not start pull for ${tag}`)
    }
  }

  const models = dash?.models ?? []
  const pulls = Object.entries(dash?.pulls ?? {})
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
          <Link to="/deploy" className="underline text-[var(--text-primary)]">
            Deploy one from the catalog
          </Link>{' '}
          (type <span className="font-mono">OLLAMA</span>) to let Port Wrangler manage the runtime.
        </div>
      )}

      {/* ── Pull ── */}
      <div className="card p-4 mb-6 animate-fade-up delay-150">
        <div className="flex flex-wrap items-center gap-2">
          <Download className="w-4 h-4 text-[var(--text-muted)]" />
          <input
            value={pullTag}
            onChange={e => setPullTag(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && onPull()}
            placeholder="Pull a model tag, e.g. llama3.1:8b"
            className="input flex-1 min-w-[220px]"
          />
          <button className="btn-primary" onClick={onPull} disabled={!pullTag.trim()}>
            Pull
          </button>
        </div>
        {pulls.length > 0 && (
          <ul className="mt-3 space-y-1">
            {pulls.map(([tag, status]) => (
              <li key={tag} className="text-sm flex items-center gap-2">
                {status === 'pulling'
                  ? <Loader2 className="w-3.5 h-3.5 animate-spin text-[var(--status-deploying)]" />
                  : <Zap className="w-3.5 h-3.5 text-[var(--status-error,#ef4444)]" />}
                <span className="font-mono">{tag}</span>
                <span className="text-[var(--text-muted)]">{status}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* ── Models ── */}
      {loading ? (
        <div className="text-sm text-[var(--text-muted)] animate-pulse">Loading models…</div>
      ) : models.length === 0 ? (
        <div className="card p-6 text-sm text-[var(--text-muted)] animate-fade-up delay-200">
          No models yet. Pull one above, or from the <Link to="/models" className="underline">Model Cookbook</Link>.
        </div>
      ) : (
        <div className="space-y-3 animate-fade-up delay-200">
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
