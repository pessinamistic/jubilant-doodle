import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { StatusBadge } from './StatusBadge'
import { ConfirmModal } from './ConfirmModal'
import {
  PlayIcon, StopIcon, TrashIcon, ClockIcon,
  PencilSquareIcon, CheckIcon, XMarkIcon,
  ArrowTopRightOnSquareIcon, LinkSlashIcon,
} from '@heroicons/react/24/outline'
import { startInstance, stopInstance, removeInstance, renameInstance } from '../api/client'
import toast from 'react-hot-toast'

// Inline styles to avoid Tailwind purge issues with dynamic colors
const STATUS_COLORS = {
  RUNNING:   '#22c55e',
  STOPPED:   '#6b7280',
  DEPLOYING: '#60a5fa',
  ERROR:     '#ef4444',
  REMOVING:  '#fb923c',
}

export function InstanceCard({ instance, onRefresh }) {
  const [busy, setBusy]               = useState(false)
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput]     = useState('')
  const [savingName, setSavingName]   = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const inputRef = useRef(null)
  const navigate = useNavigate()

  const accentColor = STATUS_COLORS[instance.status] ?? STATUS_COLORS.STOPPED
  const isRunning   = instance.status === 'RUNNING'
  const isStopped   = instance.status === 'STOPPED'
  const isDeploying = instance.status === 'DEPLOYING'
  const isBusy      = ['DEPLOYING', 'REMOVING'].includes(instance.status) || busy
  const isImported  = instance.isImported

  // ── Action helpers ──────────────────────────────────────────────────────────
  const action = (fn, label) => async (e) => {
    e?.stopPropagation()
    setBusy(true)
    try {
      await fn(instance.id)
      toast.success(`${label} successful`)
      onRefresh()
    } catch (err) {
      toast.error(err.response?.data?.error ?? `${label} failed`)
    } finally {
      setBusy(false)
    }
  }

  const handleRemoveClick = (e) => {
    e.stopPropagation()
    setShowConfirm(true)
  }

  const handleRemoveConfirm = async () => {
    setShowConfirm(false)
    await action(removeInstance, isImported ? 'Untrack' : 'Remove')()
  }

  // ── Inline rename ───────────────────────────────────────────────────────────
  const startEdit = (e) => {
    e.stopPropagation()
    setNameInput(instance.name)
    setEditingName(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const cancelEdit = (e) => {
    e?.stopPropagation()
    setEditingName(false)
  }

  const saveEdit = async (e) => {
    e?.stopPropagation()
    const trimmed = nameInput.trim()
    if (!trimmed || trimmed === instance.name) { cancelEdit(); return }
    setSavingName(true)
    try {
      await renameInstance(instance.id, trimmed)
      toast.success('Renamed successfully')
      onRefresh()
      setEditingName(false)
    } catch (err) {
      toast.error(err.response?.data?.error ?? 'Rename failed')
    } finally {
      setSavingName(false)
    }
  }

  // ── Uptime ──────────────────────────────────────────────────────────────────
  const uptime = (() => {
    if (!isRunning) return null
    const base = instance.startedAt ?? instance.createdAt
    if (!base) return null
    const diff = Date.now() - new Date(base).getTime()
    const h = Math.floor(diff / 3_600_000)
    const m = Math.floor((diff % 3_600_000) / 60_000)
    return h > 0 ? `${h}h ${m}m` : `${m}m`
  })()

  // ── Confirm modal config ────────────────────────────────────────────────────
  const confirmConfig = isImported
    ? {
        variant: 'warning',
        icon: <LinkSlashIcon className="w-5 h-5" />,
        title: `Untrack "${instance.name}"?`,
        message: 'The Docker container will not be stopped or removed — it will just stop being managed by DB Deployer.',
        confirmLabel: 'Untrack',
      }
    : {
        variant: 'danger',
        title: `Remove "${instance.name}"?`,
        message: 'This will stop and permanently delete the Docker container. Any data not stored in a volume will be lost.',
        confirmLabel: 'Remove',
      }

  return (
    <>
      <div
        onClick={() => navigate(`/instances/${instance.id}`)}
        className="card overflow-hidden group cursor-pointer hover:scale-[1.015] transition-transform duration-150"
      >
        {/* Status accent stripe */}
        <div className="h-[3px]" style={{ backgroundColor: accentColor }} />

        {/* Card body with very subtle status tint */}
        <div className="p-4" style={{ backgroundColor: `${accentColor}08` }}>

          {/* ── Header row: icon + name + navigate btn ── */}
          <div className="flex items-start gap-3 mb-3">
            <span className="text-3xl shrink-0 leading-none mt-0.5">{instance.icon}</span>

            <div className="flex-1 min-w-0">
              {/* Editable name */}
              <div className="flex items-center gap-1.5 min-w-0">
                {editingName ? (
                  <div
                    className="flex items-center gap-1"
                    onClick={e => e.stopPropagation()}
                  >
                    <input
                      ref={inputRef}
                      value={nameInput}
                      onChange={e => setNameInput(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter') saveEdit(e)
                        if (e.key === 'Escape') cancelEdit(e)
                      }}
                      onBlur={saveEdit}
                      className="input py-0.5 px-2 text-sm h-7 w-44"
                    />
                    <button
                      onClick={saveEdit}
                      disabled={savingName}
                      className="p-1 rounded text-green-400 hover:bg-green-500/10 disabled:opacity-40"
                    >
                      <CheckIcon className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={cancelEdit}
                      className="p-1 rounded text-gray-400 hover:bg-white/10"
                    >
                      <XMarkIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <>
                    <span className="font-semibold text-white text-sm truncate">
                      {instance.name}
                    </span>
                    <button
                      onClick={startEdit}
                      title="Rename"
                      className="opacity-0 group-hover:opacity-50 hover:!opacity-100 p-0.5 rounded text-gray-400 hover:text-gray-200 transition-opacity shrink-0"
                    >
                      <PencilSquareIcon className="w-3.5 h-3.5" />
                    </button>
                  </>
                )}
              </div>

              {/* Badges */}
              <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                <StatusBadge status={instance.status} />
                {instance.isSystem && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase bg-violet-500/15 text-violet-300 border border-violet-500/25">
                    SYSTEM
                  </span>
                )}
                {instance.isImported && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase bg-amber-500/15 text-amber-300 border border-amber-500/25">
                    IMPORTED
                  </span>
                )}
              </div>
            </div>

            {/* Open detail icon */}
            <button
              onClick={e => { e.stopPropagation(); navigate(`/instances/${instance.id}`) }}
              title="Open detail"
              className="opacity-0 group-hover:opacity-50 hover:!opacity-100 p-1 rounded text-gray-500 hover:text-gray-300 transition-opacity shrink-0"
            >
              <ArrowTopRightOnSquareIcon className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* ── Meta row ── */}
          <div className="text-xs text-gray-500 space-y-1 pl-10">
            <div className="flex items-center gap-0 flex-wrap">
              <span className="text-gray-400">{instance.dbTypeDisplay}</span>
              <span className="mx-1.5 text-gray-600">·</span>
              <span>v{instance.version}</span>
              <span className="mx-1.5 text-gray-600">·</span>
              <span className="font-mono text-gray-400">:{instance.hostPort}</span>
            </div>

            {isRunning && uptime && (
              <div className="flex items-center gap-1 text-green-500/80">
                <ClockIcon className="w-3 h-3" />
                <span>{uptime} uptime</span>
              </div>
            )}

            {isDeploying && (
              <div className="flex items-center gap-1.5 text-blue-400 animate-pulse">
                <div className="w-1.5 h-1.5 rounded-full bg-blue-400" />
                <span>Deploying — pulling image…</span>
              </div>
            )}
          </div>

          {/* ── Action footer (hidden for SYSTEM) ── */}
          {!instance.isSystem && (
            <div
              className="flex items-center gap-2 mt-4 pt-3 border-t border-white/[0.05]"
              onClick={e => e.stopPropagation()}
            >
              {isStopped && (
                <ActionBtn
                  icon={<PlayIcon className="w-3.5 h-3.5" />}
                  label="Start"
                  color="text-green-400 hover:bg-green-500/10 border-green-500/20"
                  onClick={action(startInstance, 'Start')}
                  disabled={isBusy}
                />
              )}
              {isRunning && (
                <ActionBtn
                  icon={<StopIcon className="w-3.5 h-3.5" />}
                  label="Stop"
                  color="text-yellow-400 hover:bg-yellow-500/10 border-yellow-500/20"
                  onClick={action(stopInstance, 'Stop')}
                  disabled={isBusy}
                />
              )}

              <div className="flex-1" />

              <ActionBtn
                icon={isImported
                  ? <LinkSlashIcon className="w-3.5 h-3.5" />
                  : <TrashIcon className="w-3.5 h-3.5" />
                }
                label={isImported ? 'Untrack' : 'Remove'}
                color={isImported
                  ? 'text-amber-400 hover:bg-amber-500/10 border-amber-500/20'
                  : 'text-red-400 hover:bg-red-500/10 border-red-500/20'
                }
                onClick={handleRemoveClick}
                disabled={isBusy || isDeploying}
              />
            </div>
          )}
        </div>
      </div>

      {/* Confirmation modal — rendered outside the card so it's not clipped */}
      <ConfirmModal
        open={showConfirm}
        {...confirmConfig}
        onConfirm={handleRemoveConfirm}
        onCancel={() => setShowConfirm(false)}
      />
    </>
  )
}

function ActionBtn({ icon, label, color, onClick, disabled }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={label}
      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border bg-transparent border-white/[0.08] transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${color}`}
    >
      {icon}
      {label}
    </button>
  )
}
