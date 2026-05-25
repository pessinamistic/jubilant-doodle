import { useState, useEffect } from 'react'
import { discoverContainers, importContainer } from '../api/client'
import {
  XMarkIcon, ArrowPathIcon, MagnifyingGlassIcon,
  CloudArrowDownIcon, CheckCircleIcon,
} from '@heroicons/react/24/outline'
import toast from 'react-hot-toast'

/**
 * Two-step modal:
 *   Step 1 — scan Docker for untracked DB containers and pick one
 *   Step 2 — confirm / fill in credentials then import
 */
export function ImportModal({ onClose, onImported }) {
  const [step, setStep]             = useState('discover')   // 'discover' | 'configure'
  const [containers, setContainers] = useState([])
  const [discovering, setDiscovering] = useState(false)
  const [selected, setSelected]     = useState(null)
  const [submitting, setSubmitting] = useState(false)

  // Form state
  const [form, setForm] = useState({
    name: '', dbType: '', version: '',
    hostPort: '', containerPort: '',
    username: '', password: '', databaseName: '',
  })

  const discover = async () => {
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
  }

  // Auto-discover on mount
  useEffect(() => { discover() }, [])

  const selectContainer = (c) => {
    setSelected(c)
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
    setStep('configure')
  }

  const handleImport = async () => {
    if (!form.name.trim())    return toast.error('Name is required')
    if (!form.dbType.trim())  return toast.error('DB Type is required')
    if (!form.hostPort)       return toast.error('Host port is required')

    setSubmitting(true)
    try {
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

      <div className="relative z-10 w-full max-w-xl bg-[#12141c] border border-white/[0.09] rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/[0.07]">
          <div className="flex items-center gap-3">
            <CloudArrowDownIcon className="w-5 h-5 text-indigo-400" />
            <h2 className="text-white font-semibold">Import Existing Container</h2>
          </div>
          <button onClick={onClose} className="text-gray-500 hover:text-white transition-colors">
            <XMarkIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Step indicator */}
        <div className="flex items-center gap-2 px-6 pt-4 pb-1">
          {['Discover', 'Configure'].map((label, i) => {
            const active = (i === 0 && step === 'discover') || (i === 1 && step === 'configure')
            const done   = i === 0 && step === 'configure'
            return (
              <div key={label} className="flex items-center gap-1.5">
                {i > 0 && <div className="w-8 h-px bg-white/10 mx-1" />}
                <div className={`flex items-center gap-1.5 text-xs font-medium ${
                  active ? 'text-white' : done ? 'text-green-400' : 'text-gray-600'
                }`}>
                  {done
                    ? <CheckCircleIcon className="w-4 h-4" />
                    : <span className={`w-4 h-4 rounded-full border flex items-center justify-center text-[10px] ${
                        active ? 'border-blue-400 text-blue-400' : 'border-gray-600 text-gray-600'
                      }`}>{i + 1}</span>
                  }
                  {label}
                </div>
              </div>
            )
          })}
        </div>

        {/* ── Step 1: Discover ── */}
        {step === 'discover' && (
          <div className="px-6 pb-6 pt-3">
            <div className="flex items-center justify-between mb-4">
              <p className="text-sm text-gray-400">
                Running Docker containers detected as databases but not yet tracked.
              </p>
              <button
                onClick={discover}
                disabled={discovering}
                className="btn-ghost flex items-center gap-1.5 text-xs shrink-0">
                <ArrowPathIcon className={`w-3.5 h-3.5 ${discovering ? 'animate-spin' : ''}`} />
                Refresh
              </button>
            </div>

            {discovering ? (
              <div className="flex items-center justify-center py-12 text-gray-500 gap-2">
                <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                Scanning containers…
              </div>
            ) : containers.length === 0 ? (
              <div className="text-center py-12">
                <MagnifyingGlassIcon className="w-10 h-10 text-gray-700 mx-auto mb-3" />
                <p className="text-gray-500 text-sm">No untracked database containers found</p>
                <p className="text-xs text-gray-600 mt-1">Start a container manually then click Refresh</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                {containers.map(c => (
                  <button
                    key={c.containerId}
                    onClick={() => selectContainer(c)}
                    className="w-full text-left px-4 py-3 rounded-xl border border-white/[0.07] bg-white/[0.02] hover:bg-white/[0.06] hover:border-blue-500/30 transition-all group">
                    <div className="flex items-center gap-3">
                      <span className="text-2xl">{c.icon ?? '🗄️'}</span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-white font-medium text-sm truncate">{c.containerName}</span>
                          <span className="text-xs text-gray-500 font-mono">{c.containerId.slice(0, 12)}</span>
                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${
                            c.status === 'RUNNING' ? 'bg-green-500/10 text-green-400' : 'bg-gray-500/10 text-gray-400'
                          }`}>{c.status}</span>
                        </div>
                        <p className="text-xs text-gray-500 mt-0.5 truncate">
                          {c.suggestedDbTypeDisplay} · port {c.suggestedHostPort ?? '?'}:{c.containerPort ?? '?'}
                        </p>
                      </div>
                      <span className="text-xs text-blue-400 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
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
                <p className="text-sm text-white font-medium">{selected.containerName}</p>
                <p className="text-xs text-gray-500 font-mono">{selected.containerId.slice(0, 12)}</p>
              </div>
              <button
                onClick={() => setStep('discover')}
                className="ml-auto text-xs text-gray-500 hover:text-gray-300 transition-colors">
                ← Back
              </button>
            </div>

            <div className="space-y-3">
              {/* Name + DB Type */}
              <div className="grid grid-cols-2 gap-3">
                <Label text="Display Name *">
                  <input className="input w-full" placeholder="my-postgres" {...field('name')} />
                </Label>
                <Label text="DB Type *">
                  <input className="input w-full" placeholder="POSTGRESQL" {...field('dbType')} />
                </Label>
              </div>

              {/* Ports */}
              <div className="grid grid-cols-2 gap-3">
                <Label text="Host Port *">
                  <input className="input w-full" type="number" placeholder="5432" {...field('hostPort')} />
                </Label>
                <Label text="Container Port">
                  <input className="input w-full" type="number" placeholder="5432" {...field('containerPort')} />
                </Label>
              </div>

              {/* Credentials */}
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

            {/* Footer */}
            <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-white/[0.06]">
              <button onClick={onClose} className="btn-ghost">Cancel</button>
              <button
                onClick={handleImport}
                disabled={submitting}
                className="btn-primary flex items-center gap-2 disabled:opacity-50">
                {submitting && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
                Import Container
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
      <span className="text-xs text-gray-500 mb-1.5 block">{text}</span>
      {children}
    </label>
  )
}
