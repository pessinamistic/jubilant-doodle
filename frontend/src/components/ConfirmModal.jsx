import { useEffect, useRef } from 'react'
import { ExclamationTriangleIcon, LinkSlashIcon, XMarkIcon } from '@heroicons/react/24/outline'

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
  const iconColor = isDanger ? 'text-red-400'    : 'text-amber-400'
  const iconBg    = isDanger ? 'bg-red-500/10'   : 'bg-amber-500/10'
  const iconBorder= isDanger ? 'border-red-500/20': 'border-amber-500/20'
  const btnColor  = isDanger
    ? 'bg-red-600 hover:bg-red-500 focus:ring-red-500/40 text-white'
    : 'bg-amber-600 hover:bg-amber-500 focus:ring-amber-500/40 text-white'

  const defaultIcon = isDanger
    ? <ExclamationTriangleIcon className="w-5 h-5" />
    : <LinkSlashIcon className="w-5 h-5" />

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
        className="relative w-full max-w-sm bg-[#1a1d27] border border-white/[0.08] rounded-2xl shadow-2xl shadow-black/60 p-6 animate-in fade-in zoom-in-95 duration-150"
        onClick={e => e.stopPropagation()}
      >
        {/* Close × */}
        <button
          onClick={onCancel}
          className="absolute top-4 right-4 p-1 rounded-lg text-gray-500 hover:text-gray-300 hover:bg-white/[0.06] transition-colors"
        >
          <XMarkIcon className="w-4 h-4" />
        </button>

        {/* Icon */}
        <div className={`w-12 h-12 rounded-xl ${iconBg} border ${iconBorder} flex items-center justify-center ${iconColor} mb-4`}>
          {icon ?? defaultIcon}
        </div>

        {/* Text */}
        <h2 className="text-base font-semibold text-white mb-2">{title}</h2>
        <p className="text-sm text-gray-400 leading-relaxed">{message}</p>

        {/* Buttons */}
        <div className="flex gap-3 mt-6">
          <button
            onClick={onCancel}
            className="flex-1 px-4 py-2 rounded-xl text-sm font-medium bg-white/[0.05] border border-white/[0.08] text-gray-300 hover:text-white hover:border-white/[0.15] transition-colors"
          >
            {cancelLabel}
          </button>
          <button
            ref={confirmRef}
            onClick={onConfirm}
            className={`flex-1 px-4 py-2 rounded-xl text-sm font-medium shadow-lg transition-colors focus:outline-none focus:ring-2 ${btnColor}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
