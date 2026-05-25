import { useState, useEffect, useCallback } from 'react'
import { discoverContainers, importContainer, reImportInstance } from '../api/client'
import {
  CheckCircle2,
  CloudDownload,
  CornerUpLeft,
  RefreshCw,
  Search,
  X,
} from 'lucide-react'
import toast from 'react-hot-toast'

/**
 * Two-step modal:
 *   Step 1 — scan Docker for untracked DB containers and pick one
 *   Step 2 — confirm / fill in credentials then import
 *
 * When `reImportInstance` prop is provided, the modal operates in re-import mode:
 *   - Discover step is still shown so the user picks the new container
 *   - Configure form is pre-filled from the existing instance config
 *   - Submit calls PUT /instances/{id}/reimport instead of POST /instances/import
 */
export function ImportModal({ onClose, onImported, reImportInstance: existingInstance }) {
  const isReImport = !!existingInstance

  // Re-import: skip discover step, seed selected from existing instance
  const [step, setStep]               = useState(isReImport ? 'configure' : 'discover')
  const [containers, setContainers]   = useState([])
  const [discovering, setDiscovering] = useState(false)
  const [selected, setSelected]       = useState(
    isReImport
      ? { containerId: existingInstance.containerId, containerName: existingInstance.containerName }
      : null
  )
  const [submitting, setSubmitting]   = useState(false)

  // Form state — pre-filled from existing instance when re-importing
  const [form, setForm] = useState({
    name:          existingInstance?.name          ?? '',
    dbType:        existingInstance?.dbType        ?? '',
    version:       existingInstance?.version       ?? '',
    hostPort:      existingInstance?.hostPort      ? String(existingInstance.hostPort) : '',
    containerPort: existingInstance?.containerPort ? String(existingInstance.containerPort) : '',
    username:      existingInstance?.username      ?? '',
    password:      '',   // never pre-fill password from server
    databaseName:  existingInstance?.databaseName  ?? '',
  })

  const discover = useCallback(async () => {
    setDiscovering(true)
    try {
      const data = await discoverContainers()
      setContainers(data)
      if (data.length === 0) {
        toast('No untracked database containers found', { icon: 'ℹ️' })
      }
    } catch (e) {
      toast.error(e.response?.data?.error ?? 'Discovery failed')
    } finally {
      setDiscovering(false)
    }
  }, [])

  // Auto-discover on mount — skip in re-import mode
  useEffect(() => {
    if (isReImport) return undefined
    const kick = setTimeout(() => discover(), 0)
    return () => clearTimeout(kick)
  }, [discover, isReImport])

  const selectContainer = (c) => {
    setSelected(c)
    if (!isReImport) {
      // Normal import: auto-fill form from container discovery
      setForm({
        name:          c.containerName.replace(/^\//, ''),
        dbType:        c.suggestedDbType ?? '',
        version:       'unknown',
        hostPort:      String(c.suggestedHostPort ?? ''),
        containerPort: String(c.containerPort ?? ''),
        username:      '',
        password:      '',
        databaseName:  '',
      })
    }
    // Re-import: keep form pre-filled from existing instance, just advance step
    setStep('configure')
  }

  const handleSubmit = async () => {
    if (!form.name.trim())    return toast.error('Name is required')
    if (!form.dbType.trim())  return toast.error('DB Type is required')
    if (!form.hostPort)       return toast.error('Host port is required')

    setSubmitting(true)
    try {
      if (isReImport) {
        await reImportInstance(existingInstance.id, {
          containerId:   selected.containerId,
          containerName: selected.containerName,
        })
        toast.success(`"${existingInstance.name}" re-imported successfully`)
      } else {
        await importContainer({
          containerId:   selected.containerId,
          containerName: selected.containerName,
          name:          form.name.trim(),
          dbType:        form.dbType.trim(),
          version:       form.version.trim() || 'unknown',
          hostPort:      Number(form.hostPort),
          containerPort: Number(form.containerPort) || undefined,
          username:      form.username.trim() || undefined,
          password:      form.password.trim() || undefined,
          databaseName:  form.databaseName.trim() || undefined,
        })
        toast.success(`"${form.name}" imported successfully`)
      }
      onImported?.()
      onClose()
    } catch (e) {
      toast.error(e.response?.data?.error ?? 'Import failed')
    } finally {
      setSubmitting(false)
    }
  }

  const field = (key) => ({
    value: form[key],
    onChange: (e) => setForm(f => ({ ...f, [key]: e.target.value })),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />

      <div className="relative z-10 w-full max-w-xl card overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b-2 border-(--border-strong)">
          <div className="flex items-center gap-3">
            {isReImport
              ? <CornerUpLeft className="w-5 h-5" style={{ color: 'var(--status-warning)' }} />
              : <CloudDownload className="w-5 h-5" style={{ color: 'var(--status-deploying)' }} />
            }
            <div>
              <h2 className="text-(--text-primary) font-semibold">
                {isReImport ? 'Re-import Instance' : 'Import Existing Container'}
              </h2>
              {isReImport && (
                <p className="text-xs mt-0.5" style={{ color: 'var(--status-warning)' }}>
                  Verifying container for <span className="font-medium">{existingInstance.name}</span>
                </p>
              )}
            </div>
          </div>
          <button onClick={onClose} className="text-(--text-muted) hover:text-(--text-primary) transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Step indicator — only shown in normal import mode */}
        {!isReImport && (
        <div className="flex items-center gap-2 px-6 pt-4 pb-1">
          {['Discover', 'Configure'].map((label, i) => {
            const active = (i === 0 && step === 'discover') || (i === 1 && step === 'configure')
            const done   = i === 0 && step === 'configure'
            return (
              <div key={label} className="flex items-center gap-1.5">
                {i > 0 && <div className="w-8 h-px bg-white/10 mx-1" />}
                <div className={`flex items-center gap-1.5 text-xs font-medium ${
                  active ? 'text-(--text-primary)' : done ? 'text-(--status-running)' : 'text-(--text-quiet)'
                }`}>
                  {done
                    ? <CheckCircle2 className="w-4 h-4" />
                    : <span className={`w-4 h-4 rounded-full border flex items-center justify-center text-[10px] ${
                        active ? 'border-(--status-deploying) text-(--status-deploying)' : 'border-(--text-quiet) text-(--text-quiet)'
                      }`}>{i + 1}</span>
                  }
                  {label}
                </div>
              </div>
            )
          })}
        </div>
        )}

        {/* ── Step 1: Discover ── */}
        {step === 'discover' && (
          <div className="px-6 pb-6 pt-3">
            <div className="flex items-center justify-between mb-4">
              <p className="text-sm text-(--text-muted)">
                {isReImport
                  ? `Select the container to bind to "${existingInstance.name}".`
                  : 'Running Docker containers detected as databases but not yet tracked.'
                }
              </p>
              <button
                onClick={discover}
                disabled={discovering}
                className="btn-ghost flex items-center gap-1.5 text-xs shrink-0">
                <RefreshCw className={`w-3.5 h-3.5 ${discovering ? 'animate-spin' : ''}`} />
                Refresh
              </button>
            </div>

            {discovering ? (
              <div className="flex items-center justify-center py-12 text-(--text-muted) gap-2">
                <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                Scanning containers…
              </div>
            ) : containers.length === 0 ? (
              <div className="text-center py-12">
                <Search className="w-10 h-10 text-(--text-quiet) mx-auto mb-3" />
                <p className="text-(--text-muted) text-sm">No untracked database containers found</p>
                <p className="text-xs text-(--text-quiet) mt-1">Start a container manually then click Refresh</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                {containers.map(c => (
                  <button
                    key={c.containerId}
                    onClick={() => selectContainer(c)}
                    className="w-full text-left px-4 py-3 rounded-md border-2 border-(--border-strong) bg-(--bg-surface-2) hover:-translate-y-1 hover:shadow-(--shadow-raised) transition-all group">
                    <div className="flex items-center gap-3">
                      <span className="text-2xl">{c.icon ?? '🗄️'}</span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-(--text-primary) font-medium text-sm truncate">{c.containerName}</span>
                          <span className="text-xs text-(--text-muted) font-mono">{c.containerId.slice(0, 12)}</span>
                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${
                            c.status === 'RUNNING' ? 'bg-(--status-running-bg) text-(--status-running)' : 'bg-(--status-stopped-bg) text-(--status-stopped)'
                          }`}>{c.status}</span>
                        </div>
                        <p className="text-xs text-(--text-muted) mt-0.5 truncate">
                          {c.suggestedDbTypeDisplay} · port {c.suggestedHostPort ?? '?'}:{c.containerPort ?? '?'}
                        </p>
                      </div>
                      <span className="text-xs text-(--status-deploying) opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
                        Select →
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── Step 2: Configure ── */}
        {step === 'configure' && selected && (
          <div className="px-6 pb-6 pt-3">
            {/* Selected container info */}
            <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-blue-500/[0.06] border border-blue-500/[0.15] mb-5">
              <span className="text-xl">{selected.icon ?? '🗄️'}</span>
              <div>
                <p className="text-sm text-(--text-primary) font-medium">{selected.containerName}</p>
                <p className="text-xs text-(--text-muted) font-mono">{selected.containerId.slice(0, 12)}</p>
              </div>
              {!isReImport && (
              <button
                onClick={() => setStep('discover')}
                className="ml-auto text-xs text-(--text-muted) hover:text-(--text-primary) transition-colors">
                Back
              </button>
              )}
            </div>

            {isReImport ? (
              /* Re-import: just confirm — all config preserved, only container changes */
              <div className="space-y-3">
                <div className="px-4 py-3 rounded-xl bg-amber-500/[0.06] border border-amber-500/[0.15]">
                  <p className="text-xs mb-2 font-medium uppercase tracking-wider" style={{ color: 'var(--status-warning)' }}>Config preserved from original</p>
                  <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-sm">
                    <Row label="Name"     value={existingInstance.name} />
                    <Row label="Type"     value={existingInstance.dbTypeDisplay} />
                    <Row label="Host port" value={existingInstance.hostPort} />
                    <Row label="Username" value={existingInstance.username || '—'} />
                    <Row label="Database" value={existingInstance.databaseName || '—'} />
                  </div>
                </div>
                <p className="text-xs text-(--text-muted) pl-1">
                  Credentials and connection settings are kept as-is. Only the container binding is updated.
                </p>
              </div>
            ) : (
              /* Normal import: editable form */
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <Label text="Display Name *">
                    <input className="input w-full" placeholder="my-postgres" {...field('name')} />
                  </Label>
                  <Label text="DB Type *">
                    <input className="input w-full" placeholder="POSTGRESQL" {...field('dbType')} />
                  </Label>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Label text="Host Port *">
                    <input className="input w-full" type="number" placeholder="5432" {...field('hostPort')} />
                  </Label>
                  <Label text="Container Port">
                    <input className="input w-full" type="number" placeholder="5432" {...field('containerPort')} />
                  </Label>
                </div>
                <div className="grid grid-cols-3 gap-3">
                  <Label text="Username">
                    <input className="input w-full" placeholder="postgres" {...field('username')} />
                  </Label>
                  <Label text="Password">
                    <input className="input w-full" type="password" placeholder="••••" {...field('password')} />
                  </Label>
                  <Label text="Database">
                    <input className="input w-full" placeholder="postgres" {...field('databaseName')} />
                  </Label>
                </div>
              </div>
            )}

            {/* Footer */}
            <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-white/[0.06]">
              <button onClick={onClose} className="btn-ghost">Cancel</button>
              <button
                onClick={handleSubmit}
                disabled={submitting}
                className={`btn-primary flex items-center gap-2 disabled:opacity-50 ${isReImport ? 'bg-amber-600 hover:bg-amber-500' : ''}`}>
                {submitting && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
                {isReImport ? 'Re-import' : 'Import Container'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function Label({ text, children }) {
  return (
    <label className="block">
      <span className="text-xs text-(--text-muted) mb-1.5 block">{text}</span>
      {children}
    </label>
  )
}

function Row({ label, value }) {
  return (
    <>
      <span className="text-(--text-muted)">{label}</span>
      <span className="text-(--text-secondary) font-mono text-xs">{value}</span>
    </>
  )
}
