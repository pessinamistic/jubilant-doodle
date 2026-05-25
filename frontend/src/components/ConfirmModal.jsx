import { useEffect, useRef } from 'react'
import { TriangleAlert, Unlink, X } from 'lucide-react'

/**
 * Generic confirmation dialog.
 *
 * Props:
 *   open        – boolean
 *   variant     – 'danger' (default) | 'warning'
 *   icon        – optional JSX override for the icon
 *   title       – string
 *   message     – string or JSX
 *   confirmLabel – string  (default "Confirm")
 *   cancelLabel  – string  (default "Cancel")
 *   onConfirm   – () => void
 *   onCancel    – () => void
 */
export function ConfirmModal({
  open,
  variant = 'danger',
  icon,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel  = 'Cancel',
  onConfirm,
  onCancel,
}) {
  const confirmRef = useRef(null)

  // Focus the confirm button when opened
  useEffect(() => {
    if (open) setTimeout(() => confirmRef.current?.focus(), 50)
  }, [open])

  // Close on Escape
  useEffect(() => {
    if (!open) return
    const handler = (e) => { if (e.key === 'Escape') onCancel?.() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open, onCancel])

  if (!open) return null

  const isDanger  = variant === 'danger'
  const iconTone = isDanger
    ? {
        color: 'var(--status-error)',
        background: 'var(--status-error-bg)',
        borderColor: 'var(--status-error-border)',
      }
    : {
        color: 'var(--status-warning)',
        background: 'var(--status-warning-bg)',
        borderColor: 'var(--status-warning-border)',
      }
  const btnTone = isDanger
    ? {
        color: 'var(--text-inverse)',
        background: 'var(--status-error)',
        borderColor: 'var(--status-error-border)',
      }
    : {
        color: 'var(--text-inverse)',
        background: 'var(--status-warning)',
        borderColor: 'var(--status-warning-border)',
      }

  const defaultIcon = isDanger
    ? <TriangleAlert className="w-5 h-5" />
    : <Unlink className="w-5 h-5" />

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={onCancel}
    >
      {/* Blur overlay */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      {/* Dialog */}
      <div
        className="relative w-full max-w-sm card p-6 animate-in fade-in zoom-in-95 duration-150"
        onClick={e => e.stopPropagation()}
      >
        {/* Close */}
        <button
          onClick={onCancel}
          className="absolute top-4 right-4 p-1 rounded-[4px] text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-surface-2)] transition-colors"
        >
          <X className="w-4 h-4" />
        </button>

        {/* Icon */}
        <div className="w-12 h-12 rounded-[6px] border-2 flex items-center justify-center mb-4" style={iconTone}>
          {icon ?? defaultIcon}
        </div>

        {/* Text */}
        <h2 className="text-base font-semibold text-[var(--text-primary)] mb-2">{title}</h2>
        <p className="text-sm text-[var(--text-muted)] leading-relaxed">{message}</p>

        {/* Buttons */}
        <div className="flex gap-3 mt-6">
          <button
            onClick={onCancel}
            className="btn-secondary flex-1"
          >
            {cancelLabel}
          </button>
          <button
            ref={confirmRef}
            onClick={onConfirm}
            className="flex-1 px-4 py-2 rounded-[4px] text-sm font-semibold border-2 shadow-[var(--shadow-raised)] transition-colors focus:outline-none"
            style={btnTone}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
