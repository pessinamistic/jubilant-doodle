import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  getRuntimeModelDetail, getLogs, loadRuntimeModel, unloadRuntimeModel,
  deleteRuntimeModel, pullRuntimeModel, saveRuntimeModelSettings,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { ConfirmModal } from '../components/ConfirmModal'
import {
  ArrowLeft, Activity, BarChart3, Brain, Check, Clipboard, Clock3, Copy, Cpu, Download,
  Eye, FileText, FileCode2, HardDrive, Layers, Loader2, MemoryStick, Pause, Play,
  RefreshCw, Sparkles, Terminal, Thermometer, Trash2, Wrench, Zap,
} from 'lucide-react'
import toast from 'react-hot-toast'

const REFRESH_MS = 15000
const REFRESH_ACTIVE_MS = 4000 // loaded or pulling — poll fast so metrics/progress move

/**
 * Capability chips, ollama.com-library style: each /api/show capability becomes a small labelled
 * indicator at the top of the page (tools, thinking, vision, embedding, completion).
 */
const CAPABILITY_META = {
  completion: { label: 'Completion', icon: Sparkles, hint: 'Standard text generation' },
  tools:      { label: 'Tools',      icon: Wrench,   hint: 'Native function/tool calling — safe as an agent backbone' },
  thinking:   { label: 'Thinking',   icon: Brain,    hint: 'Emits reasoning traces before the final answer' },
  vision:     { label: 'Vision',     icon: Eye,      hint: 'Accepts images as input' },
  embedding:  { label: 'Embedding',  icon: Layers,   hint: 'Produces vector embeddings' },
  insert:     { label: 'Insert',     icon: FileCode2, hint: 'Fill-in-the-middle completion' },
}

const TABS = [
  { id: 'overview', label: 'Overview',  icon: <BarChart3 className="w-4 h-4" /> },
  { id: 'metrics',  label: 'Metrics',   icon: <Activity className="w-4 h-4" /> },
  { id: 'logs',     label: 'Logs',      icon: <FileText className="w-4 h-4" /> },
  { id: 'modelfile', label: 'Modelfile', icon: <FileCode2 className="w-4 h-4" /> },
]

function gb(bytes) {
  if (!bytes) return '—'
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

/** 131072 → "128K", 4096 → "4K" — the shorthand ollama.com uses for context windows. */
function ctxLabel(n) {
  if (!n) return null
  return n >= 1024 ? `${Math.round(n / 1024)}K` : String(n)
}

function expiryLabel(expiresAt) {
  if (!expiresAt) return null
  const ts = Date.parse(expiresAt)
  if (Number.isNaN(ts)) return null
  if (ts - Date.now() > 1000 * 60 * 60 * 24 * 365) return 'resident'
  const mins = Math.max(0, Math.round((ts - Date.now()) / 60000))
  return mins === 0 ? 'expiring' : `~${mins}m left`
}

export function RuntimeModelDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  // Splat route — Ollama tags carry ':' and '/', so the tag is everything after /runtime/models/.
  const name = decodeURIComponent(params['*'] ?? '')

  const [detail, setDetail]   = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy]       = useState(false)
  const [activeTab, setActiveTab] = useState('overview')
  const [confirmDelete, setConfirmDelete] = useState(false)

  const load = useCallback(async (silent = true) => {
    if (!silent) setLoading(true)
    try {
      setDetail(await getRuntimeModelDetail(name))
    } catch {
      if (!silent) toast.error('Failed to load model detail')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [name])

  const isLoaded  = !!detail?.model?.loaded
  const isPulling = detail?.pull?.state === 'pulling'

  useEffect(() => {
    const kick = setTimeout(() => load(false), 0)
    return () => clearTimeout(kick)
  }, [load])

  useEffect(() => {
    const t = setInterval(() => load(true), (isLoaded || isPulling) ? REFRESH_ACTIVE_MS : REFRESH_MS)
    return () => clearInterval(t)
  }, [load, isLoaded, isPulling])

  const act = async (fn, okMsg) => {
    setBusy(true)
    try {
      await fn(name)
      toast.success(okMsg)
      await load(true)
    } catch (e) {
      toast.error(e.response?.data?.message || `Action failed for ${name}`)
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <AppShell>
        <div className="flex items-center justify-center py-32 text-[var(--text-muted)] gap-2">
          <div className="w-5 h-5 border-2 border-[var(--status-deploying)] border-t-transparent rounded-full animate-spin" />
          Loading model…
        </div>
      </AppShell>
    )
  }

  if (!detail) {
    return (
      <AppShell>
        <div className="card p-16 text-center animate-scale-in">
          <div className="text-5xl mb-4">🤖</div>
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">Model not found</h2>
          <p className="text-[var(--text-muted)] text-sm mb-6">Could not load details for <span className="font-mono">{name}</span></p>
          <Link to="/runtime" className="btn-primary inline-flex items-center gap-2">
            <ArrowLeft className="w-4 h-4" /> Back to Runtime
          </Link>
        </div>
      </AppShell>
    )
  }

  const m = detail.model
  const show = detail.show
  const onDisk = !!m
  const notInstalled = !onDisk && !isPulling
  const exp = m ? expiryLabel(m.expiresAt) : null

  return (
    <AppShell onRefresh={() => load(false)}>
      {/* ── Breadcrumb ── */}
      <div className="flex items-center gap-2 text-sm text-[var(--text-muted)] mb-6 animate-slide-down">
        <Link to="/runtime" className="hover:text-[var(--text-primary)] transition-colors flex items-center gap-1">
          <ArrowLeft className="w-3.5 h-3.5" />
          Model Runtime
        </Link>
        <span>/</span>
        <span className="text-[var(--text-secondary)] font-mono">{name}</span>
      </div>

      {/* ── Header card ── */}
      <div className="card px-6 py-5 mb-6 animate-fade-up overflow-hidden">
        <div
          className="h-[3px] -mx-6 -mt-5 mb-4"
          style={{ backgroundColor: isLoaded ? 'var(--status-running)' : isPulling ? 'var(--status-deploying)' : 'var(--status-stopped)' }}
        />
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-4 min-w-0">
            <span
              className="w-12 h-12 rounded-lg shrink-0 flex items-center justify-center border-2"
              style={{
                borderColor: isLoaded ? 'var(--status-running-border)' : 'var(--border-strong)',
                color: isLoaded ? 'var(--status-running)' : 'var(--text-muted)',
                background: 'var(--bg-surface)',
              }}
            >
              {isLoaded ? <Zap className="w-5 h-5" /> : <HardDrive className="w-5 h-5" />}
            </span>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h1 className="text-xl font-bold font-mono text-[var(--text-primary)] break-all">{name}</h1>
                <HeaderCopy text={name} />
                {isLoaded && (
                  <span className="status-pill" style={{ color: 'var(--status-running)', borderColor: 'var(--status-running-border)' }}>
                    loaded{exp ? ` · ${exp}` : ''}
                  </span>
                )}
                {onDisk && !isLoaded && (
                  <span className="status-pill" style={{ color: 'var(--text-muted)', borderColor: 'var(--border-strong)' }}>on disk</span>
                )}
                {isPulling && (
                  <span className="status-pill flex items-center gap-1" style={{ color: 'var(--status-deploying)', borderColor: 'var(--status-deploying-border)' }}>
                    <Loader2 className="w-3 h-3 animate-spin" /> downloading
                  </span>
                )}
                {notInstalled && (
                  <span className="status-pill" style={{ color: 'var(--status-error)', borderColor: 'var(--status-error-border)' }}>not installed</span>
                )}
              </div>
              <p className="text-sm text-[var(--text-muted)] mt-1 flex items-center gap-x-3 flex-wrap">
                {m && <span>{gb(m.sizeBytes)} on disk</span>}
                {show?.parameterSize && <span>{show.parameterSize} params</span>}
                {(m?.quantization || show?.quantizationLevel) && <span>{m?.quantization || show.quantizationLevel}</span>}
                {show?.modifiedAt && <span>modified {new Date(show.modifiedAt).toLocaleDateString()}</span>}
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2 flex-wrap">
            <button onClick={() => load(false)} disabled={busy} className="btn-ghost flex items-center gap-1.5">
              <RefreshCw className={`w-4 h-4 ${busy ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            {onDisk && (isLoaded ? (
              <button
                onClick={() => act(unloadRuntimeModel, `Unloaded ${name}`)}
                disabled={busy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-yellow-500/10 border border-yellow-500/20 text-yellow-400 hover:bg-yellow-500/20 transition-colors disabled:opacity-40"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Pause className="w-4 h-4" />} Pause
              </button>
            ) : (
              <button
                onClick={() => act(loadRuntimeModel, `Loaded ${name}`)}
                disabled={busy || !detail.reachable}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-green-500/10 border border-green-500/20 text-green-400 hover:bg-green-500/20 transition-colors disabled:opacity-40"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />} Run
              </button>
            ))}
            {notInstalled && (
              <button
                onClick={async () => {
                  try {
                    await pullRuntimeModel(name)
                    toast.success(`Pulling ${name}`)
                    await load(true)
                  } catch { toast.error(`Could not start pull for ${name}`) }
                }}
                disabled={!detail.reachable}
                className="btn-primary flex items-center gap-1.5 disabled:opacity-40"
              >
                <Download className="w-4 h-4" /> Pull
              </button>
            )}
            {onDisk && (
              <button
                onClick={() => setConfirmDelete(true)}
                disabled={busy}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/20 transition-colors disabled:opacity-40"
              >
                <Trash2 className="w-4 h-4" /> Delete
              </button>
            )}
          </div>
        </div>

        {/* ── Capability + spec chips (ollama.com-style status indicators) ── */}
        <div className="flex items-center gap-2 flex-wrap mt-4">
          {(show?.capabilities ?? []).map(c => <CapabilityChip key={c} capability={c} />)}
          {show?.contextLength && <SpecChip label={`${ctxLabel(show.contextLength)} context`} title={`Context window: ${show.contextLength.toLocaleString()} tokens`} />}
          {show?.architecture && <SpecChip label={show.architecture} title="Model architecture" />}
          {show?.format && <SpecChip label={show.format} title="Weights format" />}
          {show?.embeddingLength && <SpecChip label={`${show.embeddingLength} dim`} title="Embedding length" />}
          {!show && detail.reachable && (
            <span className="text-xs text-[var(--text-muted)]">
              No model card — {onDisk ? 'the runtime did not return /api/show data' : 'pull the model to see capabilities and architecture details'}.
            </span>
          )}
          {!detail.reachable && (
            <span className="text-xs" style={{ color: 'var(--status-error)' }}>
              Runtime unreachable at <span className="font-mono">{detail.baseUrl}</span> — showing last-known data.
            </span>
          )}
        </div>

        {/* In-flight pull progress */}
        {isPulling && <PullProgress pull={detail.pull} />}
        {detail.pull?.state === 'failed' && (
          <p className="text-xs mt-3 break-all" style={{ color: 'var(--status-error)' }}>
            Last pull failed: {detail.pull.status}
          </p>
        )}
      </div>

      {/* ── Tab bar ── */}
      <div className="tab-bar mb-6 w-fit animate-fade-up delay-100">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)}
            className={`tab-item ${activeTab === t.id ? 'tab-active' : 'tab-inactive'}`}>
            {t.icon}
            {t.label}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      <div key={activeTab} className="animate-fade-up">
        {activeTab === 'overview'  && <OverviewTab detail={detail} onSaved={() => load(true)} />}
        {activeTab === 'metrics'   && <MetricsTab detail={detail} />}
        {activeTab === 'logs'      && <LogsTab detail={detail} />}
        {activeTab === 'modelfile' && <ModelfileTab show={show} />}
      </div>

      <ConfirmModal
        open={confirmDelete}
        variant="danger"
        title="Delete model"
        message={`Delete '${name}' from the runtime's disk? Pull it again to get it back.`}
        confirmLabel="Delete"
        onConfirm={async () => {
          setConfirmDelete(false)
          setBusy(true)
          try {
            await deleteRuntimeModel(name)
            toast.success(`Deleted ${name}`)
            navigate('/runtime')
          } catch (e) {
            toast.error(e.response?.data?.message || `Delete failed for ${name}`)
            setBusy(false)
          }
        }}
        onCancel={() => setConfirmDelete(false)}
      />
    </AppShell>
  )
}

function HeaderCopy({ text }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      onClick={() => {
        navigator.clipboard?.writeText(text).then(() => {
          setCopied(true)
          toast.success(`Copied ${text}`)
          setTimeout(() => setCopied(false), 1500)
        }).catch(() => {})
      }}
      title="Copy model tag"
      className="p-1 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors"
    >
      {copied ? <Check className="w-4 h-4" style={{ color: 'var(--status-running)' }} /> : <Copy className="w-4 h-4" />}
    </button>
  )
}

function CapabilityChip({ capability }) {
  const meta = CAPABILITY_META[capability] ?? { label: capability, icon: Sparkles, hint: capability }
  const Icon = meta.icon
  return (
    <span
      title={meta.hint}
      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold border-2 border-[var(--border-strong)] bg-[var(--accent-soft)] text-[var(--text-primary)]"
    >
      <Icon className="w-3 h-3" />
      {meta.label}
    </span>
  )
}

function SpecChip({ label, title }) {
  return (
    <span
      title={title}
      className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold border border-[var(--border-strong)] bg-[var(--bg-surface-2)] text-[var(--text-muted)]"
    >
      {label}
    </span>
  )
}

function PullProgress({ pull }) {
  const total = pull?.totalBytes ?? 0
  const done  = pull?.completedBytes ?? 0
  const pct   = total > 0 ? Math.min(100, (done / total) * 100) : 0
  return (
    <div className="mt-4">
      <div className="flex items-center justify-between text-xs text-[var(--text-muted)] mb-1">
        <span className="truncate">{pull?.status || 'starting'}</span>
        <span className="tabular-nums shrink-0">
          {total > 0 ? `${(done / 1024 / 1024 / 1024).toFixed(1)} / ${(total / 1024 / 1024 / 1024).toFixed(1)} GB (${pct.toFixed(0)}%)` : 'waiting for size…'}
        </span>
      </div>
      <div className="h-2 rounded-full bg-[var(--bg-surface-2)] border border-[var(--border-strong)] overflow-hidden">
        <div
          className="h-full rounded-full transition-[width] duration-500"
          style={{ width: total > 0 ? `${pct}%` : '100%', background: 'var(--status-deploying)', opacity: total > 0 ? 1 : 0.25 }}
        />
      </div>
    </div>
  )
}

/* ── Overview tab ─────────────────────────────────────────────────────────── */

function OverviewTab({ detail, onSaved }) {
  const { model: m, show } = detail
  const rows = [
    ['Architecture', show?.architecture],
    ['Parameters', show?.parameterCount ? `${(show.parameterCount / 1e9).toFixed(1)}B (${show.parameterSize ?? '—'})` : show?.parameterSize],
    ['Context length', show?.contextLength?.toLocaleString()],
    ['Embedding length', show?.embeddingLength?.toLocaleString()],
    ['Quantization', m?.quantization || show?.quantizationLevel],
    ['Format', show?.format],
    ['Family', show?.families?.length ? show.families.join(', ') : show?.family],
    ['Size on disk', m ? gb(m.sizeBytes) : null],
    ['Digest', m?.digest ? m.digest.slice(0, 19) : null],
    ['Modified', show?.modifiedAt ? new Date(show.modifiedAt).toLocaleString() : null],
    ['Runtime', detail.baseUrl],
    ['Serving instance', detail.managedInstanceName ? `${detail.managedInstanceName} (${detail.managedInstanceStatus})` : 'external / unmanaged'],
  ].filter(([, v]) => v != null && v !== '')

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Details */}
      <div className="card p-5">
        <p className="section-label mb-3">Model details</p>
        <dl className="space-y-2">
          {rows.map(([k, v]) => (
            <div key={k} className="flex items-baseline justify-between gap-4 text-sm border-b border-[var(--border-strong)]/40 pb-2 last:border-0">
              <dt className="text-[var(--text-muted)] shrink-0">{k}</dt>
              <dd className="text-[var(--text-primary)] font-mono text-right break-all">{String(v)}</dd>
            </div>
          ))}
          {rows.length === 0 && (
            <p className="text-sm text-[var(--text-muted)]">No details available — the model may not be installed yet.</p>
          )}
        </dl>
      </div>

      <div className="space-y-6">
        {/* Inference settings */}
        {m && (
          <div className="card p-5">
            <p className="section-label mb-3">Inference settings</p>
            <SettingsForm model={m} onSaved={onSaved} />
          </div>
        )}

        {/* Use this model */}
        <div className="card p-5">
          <p className="section-label mb-3 flex items-center gap-2"><Terminal className="w-3.5 h-3.5" /> Use this model</p>
          <UsageSnippets tag={detail.name} baseUrl={detail.baseUrl} />
        </div>
      </div>
    </div>
  )
}

function SettingsForm({ model, onSaved }) {
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
      toast.success(`Saved settings for ${model.name}`)
      onSaved()
    } catch {
      toast.error(`Failed to save settings for ${model.name}`)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
      <label className="text-xs text-[var(--text-muted)]">
        <span className="flex items-center gap-1"><Thermometer className="w-3 h-3" /> Temperature (0–2)</span>
        <input type="number" step="0.1" min="0" max="2" value={temperature}
               onChange={e => setTemperature(e.target.value)}
               placeholder="runtime default" className="input mt-1 w-full" />
      </label>
      <label className="text-xs text-[var(--text-muted)]">
        <span className="flex items-center gap-1"><Layers className="w-3 h-3" /> Context (num_ctx)</span>
        <input type="number" step="512" min="512" value={numCtx}
               onChange={e => setNumCtx(e.target.value)}
               placeholder="runtime default" className="input mt-1 w-full" />
      </label>
      <label className="text-xs text-[var(--text-muted)]">
        <span className="flex items-center gap-1"><Clock3 className="w-3 h-3" /> Keep-alive</span>
        <input value={keepAlive}
               onChange={e => setKeepAlive(e.target.value)}
               placeholder='"5m", "-1" = resident' className="input mt-1 w-full" />
      </label>
      <div className="sm:col-span-3">
        <button className="btn-primary" onClick={save} disabled={saving}>
          {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Save settings'}
        </button>
      </div>
    </div>
  )
}

/** Copy-ready integration snippets — the "get it into your app" part of an ollama.com page. */
function UsageSnippets({ tag, baseUrl }) {
  const snippets = [
    { id: 'cli', label: 'CLI', code: `ollama run ${tag}` },
    {
      id: 'curl', label: 'curl',
      code: `curl ${baseUrl}/api/chat -d '{\n  "model": "${tag}",\n  "messages": [{ "role": "user", "content": "Hello!" }]\n}'`,
    },
    {
      id: 'openai', label: 'OpenAI SDK',
      code: `from openai import OpenAI\n\nclient = OpenAI(base_url="${baseUrl}/v1", api_key="ollama")\nresp = client.chat.completions.create(\n    model="${tag}",\n    messages=[{"role": "user", "content": "Hello!"}],\n)`,
    },
  ]
  const [active, setActive] = useState('cli')
  const current = snippets.find(s => s.id === active)

  return (
    <div>
      <div className="flex gap-1.5 mb-2">
        {snippets.map(s => (
          <button key={s.id} onClick={() => setActive(s.id)}
            className={`px-2.5 py-1 rounded-[4px] text-xs font-semibold border-2 transition-colors ${
              active === s.id
                ? 'bg-[var(--accent-soft)] border-[var(--border-strong)] text-[var(--text-primary)]'
                : 'bg-[var(--bg-surface-2)] border-[var(--border-strong)] text-[var(--text-muted)] hover:text-[var(--text-primary)]'
            }`}>
            {s.label}
          </button>
        ))}
      </div>
      <div className="relative group">
        <pre className="text-xs font-mono p-3 rounded-[6px] border-2 border-[var(--border-strong)] overflow-x-auto whitespace-pre-wrap"
             style={{ background: 'var(--bg-inset)', color: 'var(--text-secondary)' }}>
          {current.code}
        </pre>
        <button
          onClick={async () => { await navigator.clipboard.writeText(current.code); toast.success('Copied') }}
          className="absolute top-2 right-2 p-1 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] opacity-0 group-hover:opacity-100 transition-opacity"
          title="Copy snippet"
        >
          <Clipboard className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  )
}

/* ── Metrics tab ──────────────────────────────────────────────────────────── */

function MetricsTab({ detail }) {
  const m = detail.model
  if (!m) {
    return <div className="card p-10 text-center text-sm text-[var(--text-muted)]">Metrics appear once the model is installed.</div>
  }

  const loaded = m.loaded
  const vram = m.sizeVramBytes ?? 0
  // When loaded, /api/ps 'size' is the total resident footprint; the non-VRAM remainder sits in system RAM.
  const residentTotal = loaded ? Math.max(m.sizeBytes, vram) : 0
  const cpuRam = loaded ? Math.max(0, residentTotal - vram) : 0
  const vramPct = residentTotal > 0 ? (vram / residentTotal) * 100 : 0
  const exp = expiryLabel(m.expiresAt)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-3 stagger-children">
        <MetricCard icon={<HardDrive className="w-4 h-4" />} label="Disk footprint" value={gb(m.sizeBytes)} tone="stopped" />
        <MetricCard icon={<Zap className="w-4 h-4" />} label="Status" value={loaded ? 'Loaded' : 'On disk'} tone={loaded ? 'running' : 'stopped'} pulse={loaded} />
        <MetricCard icon={<MemoryStick className="w-4 h-4" />} label="VRAM resident" value={loaded ? gb(vram) : '—'} tone="deploying" />
        <MetricCard icon={<Cpu className="w-4 h-4" />} label="System RAM" value={loaded ? gb(cpuRam) : '—'} tone="deploying" />
        <MetricCard icon={<Clock3 className="w-4 h-4" />} label="Keep-alive" value={loaded ? (exp ?? '—') : (m.keepAlive ?? 'default')} tone="stopped" />
      </div>

      {loaded ? (
        <div className="card p-5">
          <p className="section-label mb-3">Memory placement</p>
          <div className="h-4 rounded-full bg-[var(--bg-surface-2)] border border-[var(--border-strong)] overflow-hidden flex">
            <div className="h-full transition-[width] duration-500" style={{ width: `${vramPct}%`, background: 'var(--status-running)' }} title={`GPU VRAM: ${gb(vram)}`} />
            <div className="h-full transition-[width] duration-500" style={{ width: `${100 - vramPct}%`, background: 'var(--status-deploying)', opacity: 0.6 }} title={`System RAM: ${gb(cpuRam)}`} />
          </div>
          <div className="flex items-center gap-4 mt-2 text-xs text-[var(--text-muted)]">
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: 'var(--status-running)' }} /> GPU VRAM {gb(vram)} ({vramPct.toFixed(0)}%)</span>
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full inline-block" style={{ background: 'var(--status-deploying)', opacity: 0.6 }} /> System RAM {gb(cpuRam)}</span>
            <span className="ml-auto">GPU: {detail.gpuVendor ?? 'unknown'}</span>
          </div>
          {vramPct < 100 && vramPct > 0 && (
            <p className="text-xs text-[var(--text-muted)] mt-3">
              Part of the model is offloaded to system RAM — expect slower tokens/s than a full-VRAM fit.
            </p>
          )}
        </div>
      ) : (
        <div className="card p-6 text-sm text-[var(--text-muted)]">
          The model is not resident in memory. Press <span className="font-semibold text-[var(--text-primary)]">Run</span> to load it —
          live VRAM/RAM placement shows up here while it is loaded. Values refresh every few seconds.
        </div>
      )}
    </div>
  )
}

function MetricCard({ icon, label, value, tone, pulse }) {
  return (
    <div className="stat-card border flex-1 min-w-[150px]" style={{ borderColor: `var(--status-${tone}-border)` }}>
      <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: `var(--status-${tone}-bg)`, color: `var(--status-${tone})` }}>
        {icon}
      </div>
      <div>
        <div className={`text-xl font-bold text-[var(--text-primary)] tabular-nums ${pulse ? 'animate-pulse' : ''}`}>{value}</div>
        <div className="text-xs text-[var(--text-muted)] mt-0.5">{label}</div>
      </div>
    </div>
  )
}

/* ── Logs tab ─────────────────────────────────────────────────────────────── */

function LogsTab({ detail }) {
  const instanceId = detail.managedInstanceId
  const [logs, setLogs]       = useState('')
  const [loading, setLoading] = useState(false)
  const [tail, setTail]       = useState(100)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const bottomRef = useRef(null)

  const fetchLogs = useCallback(async () => {
    if (!instanceId) return
    setLoading(true)
    try {
      const data = await getLogs(instanceId, tail)
      setLogs(data.logs ?? '(no logs)')
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
    } catch {
      setLogs('Failed to fetch logs')
    } finally {
      setLoading(false)
    }
  }, [instanceId, tail])

  useEffect(() => {
    const kick = setTimeout(() => fetchLogs(), 0)
    return () => clearTimeout(kick)
  }, [fetchLogs])

  useEffect(() => {
    if (!autoRefresh) return
    const t = setInterval(fetchLogs, 5_000)
    return () => clearInterval(t)
  }, [autoRefresh, fetchLogs])

  if (!instanceId) {
    return (
      <div className="card p-10 text-center text-sm text-[var(--text-muted)]">
        Logs are read from the managed Ollama container, and this runtime
        (<span className="font-mono">{detail.baseUrl}</span>) is not managed by Port Wrangler.{' '}
        <Link to="/instances" className="underline text-[var(--text-primary)]">Import it from the Instances page</Link> to see logs here.
      </div>
    )
  }

  const TAIL_OPTIONS = [50, 100, 200, 500]

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-[var(--text-muted)]">Last</span>
          {TAIL_OPTIONS.map(n => (
            <button key={n} onClick={() => setTail(n)}
              className={`px-2.5 py-1 rounded-[4px] text-xs font-medium transition-colors border-2 ${
                tail === n
                  ? 'bg-[var(--accent)] border-[var(--border-strong)] text-[var(--text-inverse)]'
                  : 'bg-[var(--bg-surface-2)] text-[var(--text-muted)] hover:text-[var(--text-primary)] border-[var(--border-strong)]'
              }`}>
              {n}
            </button>
          ))}
          <span className="text-xs text-[var(--text-muted)]">lines</span>
        </div>
        <button onClick={fetchLogs} disabled={loading} className="btn-ghost flex items-center gap-1.5 text-xs">
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
        <label className="flex items-center gap-2 text-xs text-[var(--text-muted)] cursor-pointer select-none">
          <span className={`relative inline-block w-8 h-4 rounded-full transition-colors ${autoRefresh ? 'bg-blue-600' : 'bg-white/[0.10]'}`}>
            <span className={`absolute top-0.5 w-3 h-3 bg-white rounded-full shadow transition-transform ${autoRefresh ? 'translate-x-4' : 'translate-x-0.5'}`} />
            <input type="checkbox" className="sr-only" checked={autoRefresh} onChange={e => setAutoRefresh(e.target.checked)} />
          </span>
          Auto-refresh every 5s
        </label>
        <span className="text-xs text-[var(--text-muted)]">
          from <span className="font-mono">{detail.managedInstanceName}</span> — the shared runtime serving every model
        </span>
      </div>

      <div className="card overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.06] bg-white/[0.02]">
          <div className="flex items-center gap-2">
            <div className="w-2.5 h-2.5 rounded-full bg-red-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-yellow-500/70" />
            <div className="w-2.5 h-2.5 rounded-full bg-green-500/70" />
          </div>
          <span className="text-xs text-[var(--text-muted)] font-mono">ollama runtime logs</span>
          <button
            onClick={async () => { await navigator.clipboard.writeText(logs); toast.success('Logs copied') }}
            className="text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)] flex items-center gap-1 transition-colors">
            <Clipboard className="w-3.5 h-3.5" />
            Copy
          </button>
        </div>
        <pre className="text-xs font-mono px-5 py-4 h-[500px] overflow-y-auto whitespace-pre-wrap leading-relaxed"
             style={{ background: 'var(--bg-inset)', color: 'var(--status-running)' }}>
          {loading && !logs ? 'Loading…' : logs}
          <span ref={bottomRef} />
        </pre>
      </div>
    </div>
  )
}

/* ── Modelfile tab ────────────────────────────────────────────────────────── */

function ModelfileTab({ show }) {
  if (!show) {
    return <div className="card p-10 text-center text-sm text-[var(--text-muted)]">Modelfile data appears once the model is installed and the runtime is reachable.</div>
  }
  const blocks = [
    ['Parameters', show.parameters, 'Modelfile PARAMETER lines baked into this model'],
    ['Chat template', show.template, 'Prompt template the runtime renders messages into'],
    ['License', show.license, null],
  ].filter(([, v]) => v)

  if (blocks.length === 0) {
    return <div className="card p-10 text-center text-sm text-[var(--text-muted)]">This model ships no parameter, template, or license text.</div>
  }

  return (
    <div className="space-y-6">
      {blocks.map(([title, content, hint]) => (
        <div key={title} className="card overflow-hidden">
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/[0.06] bg-white/[0.02]">
            <span className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider">{title}</span>
            {hint && <span className="text-[11px] text-[var(--text-muted)] hidden sm:inline">{hint}</span>}
            <button
              onClick={async () => { await navigator.clipboard.writeText(content); toast.success(`${title} copied`) }}
              className="text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)] flex items-center gap-1 transition-colors">
              <Clipboard className="w-3.5 h-3.5" /> Copy
            </button>
          </div>
          <pre className="text-xs font-mono px-5 py-4 max-h-[400px] overflow-y-auto whitespace-pre-wrap leading-relaxed"
               style={{ background: 'var(--bg-inset)', color: 'var(--text-secondary)' }}>
            {content}
          </pre>
        </div>
      ))}
    </div>
  )
}
