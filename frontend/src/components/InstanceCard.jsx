import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { StatusBadge } from './StatusBadge'
import { ConfirmModal } from './ConfirmModal'
import {
  Check,
  Clock3,
  ExternalLink,
  Pencil,
  Play,
  Square,
  Trash2,
  Unlink,
  X,
} from 'lucide-react'
import { startInstance, stopInstance, removeInstance, renameInstance } from '../api/client'
import { INSTANCE_STATUS_TOKENS } from '../theme/statusTokens'
import toast from 'react-hot-toast'

export function InstanceCard({ instance, onRefresh }) {
  const [busy, setBusy]               = useState(false)
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput]     = useState('')
  const [savingName, setSavingName]   = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const inputRef = useRef(null)
  const navigate = useNavigate()

  const statusToken = INSTANCE_STATUS_TOKENS[instance.status] ?? INSTANCE_STATUS_TOKENS.UNKNOWN
  const accentColor = statusToken.accent
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
    const end = instance.updatedAt ?? instance.startedAt ?? instance.createdAt
    const diff = Math.max(0, new Date(end).getTime() - new Date(base).getTime())
    const h = Math.floor(diff / 3_600_000)
    const m = Math.floor((diff % 3_600_000) / 60_000)
    return h > 0 ? `${h}h ${m}m` : `${m}m`
  })()

  // ── Confirm modal config ────────────────────────────────────────────────────
  const confirmConfig = isImported
    ? {
        variant: 'warning',
        icon: <Unlink className="w-5 h-5" />,
        title: `Untrack "${instance.name}"?`,
        message: 'The Docker container will not be stopped or removed. It will only stop being managed by Port Wrangler.',
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
        className="card h-full flex flex-col overflow-hidden group cursor-pointer hover:scale-[1.015] transition-transform duration-150"
      >
        {/* Status accent stripe */}
        <div className="h-[3px]" style={{ backgroundColor: accentColor }} />

        {/* Card body with very subtle status tint */}
        <div className="p-4 flex-1" style={{ backgroundColor: statusToken.background }}>

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
                      className="p-1 rounded text-[var(--status-running)] hover:bg-[var(--status-running-bg)] disabled:opacity-40"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={cancelEdit}
                      className="p-1 rounded text-[var(--text-muted)] hover:bg-[var(--bg-surface-2)]"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <>
                    <span className="font-semibold text-sm truncate text-[var(--text-primary)]">
                      {instance.name}
                    </span>
                    <button
                      onClick={startEdit}
                      title="Rename"
                      className="opacity-0 group-hover:opacity-50 hover:!opacity-100 p-0.5 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-opacity shrink-0"
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                  </>
                )}
              </div>

              {/* Badges */}
              <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                <StatusBadge status={instance.status} />
                {instance.isSystem && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase border" style={{
                    background: 'var(--status-skipped-bg)',
                    color: 'var(--status-skipped)',
                    borderColor: 'var(--status-skipped-border)',
                  }}>
                    SYSTEM
                  </span>
                )}
                {instance.isImported && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase border" style={{
                    background: 'var(--status-warning-bg)',
                    color: 'var(--status-warning)',
                    borderColor: 'var(--status-warning-border)',
                  }}>
                    IMPORTED
                  </span>
                )}
              </div>
            </div>

            {/* Open detail icon */}
            <button
              onClick={e => { e.stopPropagation(); navigate(`/instances/${instance.id}`) }}
              title="Open detail"
              className="opacity-0 group-hover:opacity-50 hover:!opacity-100 p-1 rounded text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-opacity shrink-0"
            >
              <ExternalLink className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* ── Meta row ── */}
          <div className="text-xs text-[var(--text-muted)] space-y-1 pl-10">
            <div className="flex items-center gap-0 flex-wrap">
              <span className="text-[var(--text-secondary)]">{instance.dbTypeDisplay}</span>
              <span className="mx-1.5 text-[var(--text-quiet)]">·</span>
              <span>v{instance.version}</span>
              <span className="mx-1.5 text-[var(--text-quiet)]">·</span>
              <span className="mono-info">:{instance.hostPort}</span>
            </div>

            {isRunning && uptime && (
              <div className="flex items-center gap-1" style={{ color: 'var(--status-running)' }}>
                <Clock3 className="w-3 h-3" />
                <span>{uptime} uptime</span>
              </div>
            )}

            {isDeploying && (
              <div className="flex items-center gap-1.5 animate-pulse" style={{ color: 'var(--status-deploying)' }}>
                <div className="w-1.5 h-1.5 rounded-full" style={{ background: 'var(--status-deploying)' }} />
                <span>Deploying - pulling image...</span>
              </div>
            )}
          </div>

          {/* ── Action footer (hidden for SYSTEM) ── */}
          {!instance.isSystem && (
            <div
              className="flex items-center gap-2 mt-4 pt-3 border-t-2 border-[var(--border-strong)]"
              onClick={e => e.stopPropagation()}
            >
              {isStopped && (
                <ActionBtn
                  icon={<Play className="w-3.5 h-3.5" />}
                  label="Start"
                  color="text-[var(--status-running)] hover:bg-[var(--status-running-bg)] border-[var(--status-running-border)]"
                  onClick={action(startInstance, 'Start')}
                  disabled={isBusy}
                />
              )}
              {isRunning && (
                <ActionBtn
                  icon={<Square className="w-3.5 h-3.5" />}
                  label="Stop"
                  color="text-[var(--status-warning)] hover:bg-[var(--status-warning-bg)] border-[var(--status-warning-border)]"
                  onClick={action(stopInstance, 'Stop')}
                  disabled={isBusy}
                />
              )}

              <div className="flex-1" />

              <ActionBtn
                icon={isImported
                  ? <Unlink className="w-3.5 h-3.5" />
                  : <Trash2 className="w-3.5 h-3.5" />
                }
                label={isImported ? 'Untrack' : 'Remove'}
                color={isImported
                  ? 'text-[var(--status-warning)] hover:bg-[var(--status-warning-bg)] border-[var(--status-warning-border)]'
                  : 'text-[var(--status-error)] hover:bg-[var(--status-error-bg)] border-[var(--status-error-border)]'
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
      className={`flex items-center gap-1.5 px-2.5 py-1 rounded-[4px] text-xs font-semibold border-2 bg-transparent transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${color}`}
    >
      {icon}
      {label}
    </button>
  )
}
