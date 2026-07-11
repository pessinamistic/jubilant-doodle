import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import {
  getModelSuggestions, getSystemProfile, getRuntimeDashboard, searchOllamaLibrary,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import {
  ArrowLeft, Brain, Check, Clipboard, Copy, Cpu, Download, ExternalLink, Eye, Gauge,
  Layers, Loader2, Sparkles, Terminal, Wrench, Zap,
} from 'lucide-react'
import toast from 'react-hot-toast'

// Same palette as the Cookbook grid so fit badges read identically on both pages.
const COMPAT_STYLE = {
  FAST:      { dot: '#22c55e', text: 'Fast',      bg: 'rgba(34,197,94,0.12)',  border: 'rgba(34,197,94,0.4)' },
  OK:        { dot: '#f59e0b', text: 'OK',        bg: 'rgba(245,158,11,0.12)', border: 'rgba(245,158,11,0.4)' },
  CPU_ONLY:  { dot: '#fb923c', text: 'CPU only',  bg: 'rgba(251,146,60,0.12)', border: 'rgba(251,146,60,0.4)' },
  TOO_LARGE: { dot: '#ef4444', text: "Won't fit", bg: 'rgba(239,68,68,0.12)',  border: 'rgba(239,68,68,0.4)' },
}

// ollama.com-style capability indicators; curated model types map onto the same vocabulary.
const CAPABILITY_META = {
  tools:     { label: 'Tools',     icon: Wrench, hint: 'Native function/tool calling' },
  thinking:  { label: 'Thinking',  icon: Brain,  hint: 'Emits reasoning traces' },
  vision:    { label: 'Vision',    icon: Eye,    hint: 'Accepts images as input' },
  embedding: { label: 'Embedding', icon: Layers, hint: 'Produces vector embeddings' },
}

function gbLabel(mb) {
  if (!mb) return '—'
  return `${(mb / 1024).toFixed(1)} GB`
}

/** "gemma3:4b" → "gemma3" — the library identifier is the tag's family prefix. */
const tagFamily = (tag) => tag.split(':')[0]

export function ModelDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  // Splat route: community identifiers can be namespaced ("user/model").
  const name = decodeURIComponent(params['*'] ?? '')

  const [profile, setProfile]     = useState(null)
  const [variants, setVariants]   = useState([])   // curated, hardware-scored catalog entries
  const [library, setLibrary]     = useState(null) // live ollama.com library entry (via ollamadb)
  const [libraryTried, setLibraryTried] = useState(false)
  const [dash, setDash]           = useState(null)
  const [loading, setLoading]     = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      // Curated catalog + hardware profile are the authoritative local data…
      const [prof, sugg] = await Promise.all([getSystemProfile(), getModelSuggestions('', '')])
      setProfile(prof)
      setVariants(sugg.filter(s => tagFamily(s.model.ollamaTag) === name))
    } catch {
      toast.error('Failed to load model data')
    } finally {
      setLoading(false)
    }
    // …while the live library entry (pulls, size labels, capability) is best-effort on top.
    searchOllamaLibrary(name, 20)
      .then(results => setLibrary(results.find(r => r.modelIdentifier === name) ?? null))
      .catch(() => setLibrary(null))
      .finally(() => setLibraryTried(true))
    getRuntimeDashboard().then(setDash).catch(() => {})
  }, [name])

  useEffect(() => {
    const kick = setTimeout(() => load(), 0)
    return () => clearTimeout(kick)
  }, [load])

  const installed = useMemo(() => new Set((dash?.models ?? []).map(m => m.name)), [dash])
  const pulling = useMemo(() => new Set(
    Object.entries(dash?.pulls ?? {}).filter(([, p]) => p?.state === 'pulling').map(([tag]) => tag),
  ), [dash])

  // Capabilities: union of the library's capability tag and what the curated catalog knows.
  const capabilities = useMemo(() => {
    const caps = new Set()
    if (library?.capability) caps.add(library.capability.toLowerCase())
    for (const v of variants) {
      if (v.model.toolCalling) caps.add('tools')
      if (v.model.type === 'VISION') caps.add('vision')
      if (v.model.type === 'EMBEDDING') caps.add('embedding')
      if (v.model.type === 'REASONING') caps.add('thinking')
    }
    return Array.from(caps)
  }, [library, variants])

  // Size labels published on ollama.com that the curated catalog has no scored entry for.
  const extraSizes = useMemo(() => {
    const curatedTags = new Set(variants.map(v => v.model.ollamaTag.toLowerCase()))
    return (library?.labels ?? []).filter(l => !curatedTags.has(`${name}:${l.toLowerCase()}`))
  }, [library, variants, name])

  const displayName = variants[0]?.model.family ?? name
  const description = variants[0]?.model.description ?? library?.description
  const knowsAnything = variants.length > 0 || library

  const pullAndOpen = (tag) => navigate(`/runtime?pull=${encodeURIComponent(tag)}`)
  const openRuntimeDetail = (tag) => navigate(`/runtime/models/${encodeURIComponent(tag)}`)

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

  if (!knowsAnything && libraryTried) {
    return (
      <AppShell>
        <div className="card p-16 text-center animate-scale-in">
          <div className="text-5xl mb-4">🍳</div>
          <h2 className="text-lg font-semibold text-[var(--text-primary)] mb-2">Unknown model</h2>
          <p className="text-[var(--text-muted)] text-sm mb-6">
            <span className="font-mono">{name}</span> is not in the curated catalog and was not found on ollama.com.
          </p>
          <Link to="/models" className="btn-primary inline-flex items-center gap-2">
            <ArrowLeft className="w-4 h-4" /> Back to Model Cookbook
          </Link>
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell onRefresh={load}>
      {/* ── Breadcrumb ── */}
      <div className="flex items-center gap-2 text-sm text-[var(--text-muted)] mb-6 animate-slide-down">
        <Link to="/models" className="hover:text-[var(--text-primary)] transition-colors flex items-center gap-1">
          <ArrowLeft className="w-3.5 h-3.5" />
          Model Cookbook
        </Link>
        <span>/</span>
        <span className="text-[var(--text-secondary)] font-mono">{name}</span>
      </div>

      {/* ── Header: name, description, capability + size indicators (ollama.com layout) ── */}
      <div className="card px-6 py-5 mb-6 animate-fade-up">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="min-w-0">
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl font-bold text-[var(--text-primary)]">{displayName}</h1>
              {library && (
                <span className="text-[10px] px-2 py-0.5 rounded-full font-bold tracking-wider uppercase border border-[var(--border-strong)] text-[var(--text-muted)]">
                  {library.officialSource ? 'official' : 'community'}
                </span>
              )}
            </div>
            {description && <p className="text-sm text-[var(--text-muted)] mt-2 max-w-3xl">{description}</p>}

            {/* Capability + size chips */}
            <div className="flex items-center gap-2 flex-wrap mt-4">
              {capabilities.map(c => <CapabilityChip key={c} capability={c} />)}
              {(library?.labels ?? []).map(l => (
                <span key={l} className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-semibold border border-[var(--border-strong)] bg-[var(--bg-surface-2)] text-[var(--text-muted)]">
                  {l}
                </span>
              ))}
            </div>

            {(library?.pulls > 0 || library?.tagCount > 0) && (
              <p className="text-xs text-[var(--text-muted)] mt-3 flex items-center gap-3">
                {library.pulls > 0 && <span><span className="font-semibold text-[var(--text-secondary)]">{library.pulls.toLocaleString()}</span> pulls on ollama.com</span>}
                {library.tagCount > 0 && <span>{library.tagCount} tags</span>}
                {library.url && (
                  <a href={library.url} target="_blank" rel="noreferrer"
                     className="inline-flex items-center gap-1 underline hover:text-[var(--text-primary)]">
                    ollama.com/library/{name} <ExternalLink className="w-3 h-3" />
                  </a>
                )}
              </p>
            )}
          </div>
          <RuntimeStatusChip dash={dash} />
        </div>
      </div>

      {/* ── Hardware context strip ── */}
      {profile && (
        <div className="card p-4 mb-6 flex flex-wrap items-center gap-x-6 gap-y-2 animate-fade-up delay-100">
          <span className="text-xs font-semibold uppercase tracking-widest text-[var(--text-muted)]">Your machine</span>
          <ProfileStat icon={<Cpu className="w-4 h-4" />} label="GPU" value={profile.gpuVendor} />
          <ProfileStat label="VRAM" value={gbLabel(profile.vramMb)} />
          <ProfileStat label="RAM" value={gbLabel(profile.totalRamMb)} />
          <ProfileStat label="Cores" value={String(profile.cpuCores)} />
          <ProfileStat label="Platform" value={profile.platform} />
        </div>
      )}

      {/* ── Variants table (curated = scored; library labels = unscored) ── */}
      <section className="mb-8 animate-fade-up delay-150">
        <p className="section-label">Models</p>
        {variants.length === 0 && extraSizes.length === 0 ? (
          <div className="card p-8 text-center text-sm text-[var(--text-muted)]">
            No published size variants found. You can still pull{' '}
            <button onClick={() => pullAndOpen(name)} className="underline text-[var(--text-primary)]">{name}:latest</button>.
          </div>
        ) : (
          <div className="card overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-[var(--text-muted)] uppercase tracking-wider border-b-2 border-[var(--border-strong)]">
                  <th className="px-4 py-3 font-semibold">Tag</th>
                  <th className="px-4 py-3 font-semibold">Params</th>
                  <th className="px-4 py-3 font-semibold">Needs (VRAM · RAM)</th>
                  <th className="px-4 py-3 font-semibold">Fit</th>
                  <th className="px-4 py-3 font-semibold">Speed</th>
                  <th className="px-4 py-3 font-semibold text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {variants.map(v => (
                  <VariantRow
                    key={v.model.ollamaTag}
                    suggestion={v}
                    installed={installed.has(v.model.ollamaTag)}
                    pulling={pulling.has(v.model.ollamaTag)}
                    onPull={() => pullAndOpen(v.model.ollamaTag)}
                    onOpen={() => openRuntimeDetail(v.model.ollamaTag)}
                  />
                ))}
                {extraSizes.map(label => {
                  const tag = `${name}:${label.toLowerCase()}`
                  return (
                    <UnscoredRow
                      key={tag}
                      tag={tag}
                      label={label}
                      installed={installed.has(tag)}
                      pulling={pulling.has(tag)}
                      onPull={() => pullAndOpen(tag)}
                      onOpen={() => openRuntimeDetail(tag)}
                    />
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {extraSizes.length > 0 && (
          <p className="text-[11px] text-[var(--text-muted)] mt-2">
            Sizes without a fit badge come straight from ollama.com and have not been scored against your hardware.
          </p>
        )}
      </section>

      {/* ── Use this model ── */}
      <section className="mb-8 animate-fade-up delay-200 max-w-3xl">
        <p className="section-label flex items-center gap-2"><Terminal className="w-3.5 h-3.5" /> Use this model</p>
        <div className="card p-5">
          <UsageSnippets
            tag={variants[0]?.model.ollamaTag ?? `${name}:latest`}
            baseUrl={dash?.baseUrl ?? 'http://localhost:11434'}
          />
        </div>
      </section>
    </AppShell>
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

function FitBadge({ compatibility }) {
  const style = COMPAT_STYLE[compatibility] ?? COMPAT_STYLE.TOO_LARGE
  return (
    <span
      className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full text-[11px] font-semibold border"
      style={{ background: style.bg, borderColor: style.border, color: 'var(--text-primary)' }}
    >
      <span className="w-2 h-2 rounded-full" style={{ background: style.dot }} />
      {style.text}
    </span>
  )
}

function VariantRow({ suggestion, installed, pulling, onPull, onOpen }) {
  const { model, compatibility, speedTier } = suggestion
  const tooLarge = compatibility === 'TOO_LARGE'
  return (
    <tr className="border-b border-[var(--border-strong)]/30 last:border-0 hover:bg-[var(--bg-surface-2)]/50 transition-colors">
      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5">
          <span className="font-mono text-[var(--text-primary)]">{model.ollamaTag}</span>
          <TagCopy tag={model.ollamaTag} />
        </div>
      </td>
      <td className="px-4 py-3 tabular-nums text-[var(--text-secondary)]">{model.paramsBillions}B</td>
      <td className="px-4 py-3 text-[var(--text-muted)] text-xs whitespace-nowrap">
        {gbLabel(model.minVramMb)} · {gbLabel(model.minRamMb)}
      </td>
      <td className="px-4 py-3"><FitBadge compatibility={compatibility} /></td>
      <td className="px-4 py-3 text-xs text-[var(--text-muted)]">
        <span className="flex items-center gap-1"><Zap className="w-3 h-3" />{speedTier}</span>
      </td>
      <td className="px-4 py-3 text-right">
        <VariantAction installed={installed} pulling={pulling} tooLarge={tooLarge} onPull={onPull} onOpen={onOpen} />
      </td>
    </tr>
  )
}

function UnscoredRow({ tag, label, installed, pulling, onPull, onOpen }) {
  return (
    <tr className="border-b border-[var(--border-strong)]/30 last:border-0 hover:bg-[var(--bg-surface-2)]/50 transition-colors">
      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5">
          <span className="font-mono text-[var(--text-primary)]">{tag}</span>
          <TagCopy tag={tag} />
        </div>
      </td>
      <td className="px-4 py-3 tabular-nums text-[var(--text-secondary)]">{label}</td>
      <td className="px-4 py-3 text-[var(--text-muted)] text-xs">—</td>
      <td className="px-4 py-3 text-xs text-[var(--text-muted)]">unscored</td>
      <td className="px-4 py-3 text-xs text-[var(--text-muted)]">—</td>
      <td className="px-4 py-3 text-right">
        <VariantAction installed={installed} pulling={pulling} tooLarge={false} onPull={onPull} onOpen={onOpen} />
      </td>
    </tr>
  )
}

function VariantAction({ installed, pulling, tooLarge, onPull, onOpen }) {
  if (installed) {
    return (
      <button onClick={onOpen} className="btn-secondary text-xs inline-flex items-center gap-1">
        <Check className="w-3.5 h-3.5 text-[#22c55e]" /> Installed
      </button>
    )
  }
  if (pulling) {
    return (
      <button onClick={onPull} className="btn-secondary text-xs inline-flex items-center gap-1">
        <Loader2 className="w-3.5 h-3.5 animate-spin" /> Pulling…
      </button>
    )
  }
  return (
    <button
      onClick={onPull}
      disabled={tooLarge}
      title={tooLarge ? 'Too large for the detected hardware' : 'Pull and open the Runtime page'}
      className="btn-primary text-xs inline-flex items-center gap-1 disabled:opacity-40 disabled:cursor-not-allowed"
    >
      <Download className="w-3.5 h-3.5" /> Pull
    </button>
  )
}

function TagCopy({ tag }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      onClick={() => {
        navigator.clipboard?.writeText(tag).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1200)
        }).catch(() => {})
      }}
      title={`Copy ${tag}`}
      className="p-0.5 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] opacity-40 hover:opacity-100 transition-opacity"
    >
      {copied ? <Check className="w-3 h-3" style={{ color: 'var(--status-running)' }} /> : <Copy className="w-3 h-3" />}
    </button>
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
    >
      <Gauge className="w-3.5 h-3.5" />
      {reachable ? (dash.managedInstanceName ? `Runtime: ${dash.managedInstanceName}` : 'Runtime reachable') : 'No runtime — deploy Ollama'}
    </Link>
  )
}

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
