import { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown, Play, Pause, Loader2, Circle } from 'lucide-react'
import { getRuntimeDashboard, loadRuntimeModel, unloadRuntimeModel } from '../api/client'

const REFRESH_MS = 15000

/**
 * A model picker rendered as a dropdown list. Each row shows the model's runtime
 * status (loaded into memory vs. on disk) and a small play/pause button that
 * loads/unloads the model without leaving the page.
 *
 * The list is backed by the same runtime dashboard the Model Runtime page uses,
 * so status stays consistent across the app.
 *
 * Props:
 *   value       – selected model name ('' means none)
 *   onChange    – (name: string) => void
 *   placeholder – trigger text when nothing is selected
 *   allowEmpty  – when true, offers a "no model (runtime default)" choice
 *   className   – extra classes for the trigger button wrapper
 *   disabled    – disables the trigger
 */
export function ModelSelect({
  value,
  onChange,
  placeholder = 'Select a model',
  allowEmpty = false,
  className = '',
  disabled = false,
}) {
  const [open, setOpen] = useState(false)
  const [dash, setDash] = useState(null)
  const [busyModel, setBusyModel] = useState(null)
  const [menuPos, setMenuPos] = useState(null)
  const wrapRef = useRef(null)
  const triggerRef = useRef(null)
  const menuRef = useRef(null)

  const refresh = useCallback(async () => {
    try {
      setDash(await getRuntimeDashboard())
    } catch {
      /* leave last-known state; the trigger still works as a plain selector */
    }
  }, [])

  useEffect(() => {
    const kick = setTimeout(refresh, 0)
    return () => clearTimeout(kick)
  }, [refresh])

  // Keep status fresh, and poll faster while the menu is open.
  useEffect(() => {
    const id = setInterval(refresh, open ? 4000 : REFRESH_MS)
    return () => clearInterval(id)
  }, [refresh, open])

  const models = useMemo(() => dash?.models ?? [], [dash])
  const reachable = dash?.reachable ?? false
  const selected = models.find(m => m.name === value) || null

  // Position the portal menu under the trigger and track scroll/resize.
  const place = useCallback(() => {
    const el = triggerRef.current
    if (!el) return
    const r = el.getBoundingClientRect()
    setMenuPos({ left: r.left, top: r.bottom + 6, width: Math.max(r.width, 260) })
  }, [])

  useEffect(() => {
    if (!open) return
    place()
    const onMove = () => place()
    window.addEventListener('scroll', onMove, true)
    window.addEventListener('resize', onMove)
    return () => {
      window.removeEventListener('scroll', onMove, true)
      window.removeEventListener('resize', onMove)
    }
  }, [open, place])

  // Close on outside click / Escape.
  useEffect(() => {
    if (!open) return
    const onDown = (e) => {
      if (wrapRef.current?.contains(e.target)) return
      if (menuRef.current?.contains(e.target)) return
      setOpen(false)
    }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const pick = (name) => { onChange(name); setOpen(false) }

  const toggleLoad = async (m, e) => {
    e.stopPropagation()
    if (busyModel) return
    setBusyModel(m.name)
    try {
      await (m.loaded ? unloadRuntimeModel(m.name) : loadRuntimeModel(m.name))
      await refresh()
    } catch {
      /* swallow — a failed toggle just leaves status unchanged */
    } finally {
      setBusyModel(null)
    }
  }

  const menu = open && menuPos ? createPortal(
    <div
      ref={menuRef}
      className="fixed z-50 rounded-[6px] border-2 border-[var(--border-strong)] bg-[var(--bg-surface)] shadow-[var(--shadow-raised-lg)] overflow-hidden"
      style={{ left: menuPos.left, top: menuPos.top, width: menuPos.width }}
      role="listbox"
    >
      <div className="max-h-80 overflow-auto py-1">
        {allowEmpty && (
          <button
            type="button"
            onClick={() => pick('')}
            className={`w-full flex items-center gap-2 px-3 py-2 text-sm text-left hover:bg-[var(--accent-soft)] ${
              !value ? 'bg-[var(--accent-soft)]' : ''
            }`}
          >
            <Circle className="w-3 h-3 text-[var(--text-quiet)]" />
            <span className="text-[var(--text-muted)]">No model (runtime default)</span>
          </button>
        )}

        {models.length === 0 ? (
          <div className="px-3 py-3 text-sm text-[var(--text-muted)]">
            {dash ? 'No models installed. Pull one from the Runtime page.' : 'Loading models…'}
          </div>
        ) : (
          models.map(m => {
            const isSel = m.name === value
            const isBusy = busyModel === m.name
            return (
              <div
                key={m.name}
                role="option"
                aria-selected={isSel}
                onClick={() => pick(m.name)}
                className={`group flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-[var(--accent-soft)] ${
                  isSel ? 'bg-[var(--accent-soft)]' : ''
                }`}
              >
                <StatusDot loaded={m.loaded} />
                <div className="min-w-0 flex-1">
                  <div className="font-mono text-sm text-[var(--text-primary)] truncate">{m.name}</div>
                  <div className="text-[11px] text-[var(--text-muted)]">
                    {m.loaded ? 'loaded in memory' : 'on disk'}
                    {m.quantization ? ` · ${m.quantization}` : ''}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={(e) => toggleLoad(m, e)}
                  disabled={isBusy || (!m.loaded && !reachable)}
                  title={m.loaded ? 'Pause — unload from memory' : 'Play — load into memory'}
                  aria-label={m.loaded ? `Unload ${m.name}` : `Load ${m.name}`}
                  className="shrink-0 inline-flex items-center justify-center w-7 h-7 rounded border-2 border-[var(--border-strong)] bg-[var(--bg-surface-2)] text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-surface-3)] disabled:opacity-40"
                >
                  {isBusy
                    ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    : m.loaded
                      ? <Pause className="w-3.5 h-3.5" />
                      : <Play className="w-3.5 h-3.5" />}
                </button>
              </div>
            )
          })
        )}
      </div>
    </div>,
    document.body,
  ) : null

  return (
    <div ref={wrapRef} className={`relative ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen(o => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className="input flex items-center gap-2 text-left disabled:opacity-50"
      >
        {selected
          ? <StatusDot loaded={selected.loaded} />
          : <Circle className="w-3 h-3 text-[var(--text-quiet)]" />}
        <span className={`flex-1 truncate ${value ? 'text-[var(--text-primary)]' : 'text-[var(--text-quiet)]'}`}>
          {value || placeholder}
        </span>
        <ChevronDown className={`w-4 h-4 text-[var(--text-muted)] transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {menu}
    </div>
  )
}

/** Green when the model is resident in memory, grey when it only lives on disk. */
function StatusDot({ loaded }) {
  return (
    <span
      title={loaded ? 'Loaded in memory' : 'On disk'}
      className="shrink-0 w-2.5 h-2.5 rounded-full inline-block"
      style={{
        background: loaded ? '#22c55e' : 'var(--text-quiet)',
        boxShadow: loaded ? '0 0 0 3px rgba(34,197,94,0.18)' : 'none',
      }}
    />
  )
}
