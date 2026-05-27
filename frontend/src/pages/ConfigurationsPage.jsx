import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertCircle, Eye, EyeOff, Pencil, Plus, Rocket, SlidersHorizontal, Trash2, X } from 'lucide-react'
import toast from 'react-hot-toast'

import { AppShell } from '../components/AppShell'
import {
  checkImageStatus,
  deleteTemplate,
  deployFromTemplate,
  getTemplates,
} from '../api/client'

// ── Inline Deploy Modal ────────────────────────────────────────────────────────
function DeployModal({ template, onClose, onDeployed }) {
  const [instanceName, setInstanceName] = useState(`${template.name}-1`)
  const [hostPort, setHostPort] = useState(template.hostPort)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [imageStatus, setImageStatus] = useState(null)
  const [checkingImage, setCheckingImage] = useState(true)
  const [showPassword, setShowPassword] = useState(false)

  useEffect(() => {
    let active = true
    setCheckingImage(true)
    checkImageStatus(template.dbType, template.version, false)
      .then(status => { if (active) setImageStatus(status) })
      .catch(() => { if (active) setImageStatus({ status: 'ERROR', cached: false }) })
      .finally(() => { if (active) setCheckingImage(false) })
    return () => { active = false }
  }, [template.dbType, template.version])

  const handleDeploy = async (e) => {
    e.preventDefault()
    setFieldErrors({})
    setSubmitting(true)
    try {
      await deployFromTemplate(template.id, { instanceName, hostPort })
      toast.success(`${instanceName} deployed!`)
      onDeployed()
    } catch (err) {
      const msg = err?.response?.data?.error ?? err?.message ?? 'Deploy failed'
      const lower = msg.toLowerCase()
      if (lower.includes('already exists') || lower.includes('name')) {
        setFieldErrors({ instanceName: msg })
      } else if (lower.includes('port') || lower.includes('in use')) {
        setFieldErrors({ hostPort: msg })
      } else {
        setFieldErrors({ _form: msg })
      }
    } finally {
      setSubmitting(false)
    }
  }

  const imageOk = imageStatus?.status === 'AVAILABLE'
  const imageLocal = imageStatus?.cached

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="card w-full max-w-md p-6 animate-fade-up shadow-2xl">
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-center gap-2">
            <span className="text-2xl">{template.icon}</span>
            <div>
              <h2 className="text-lg font-bold text-(--text-primary)">Deploy Instance</h2>
              <p className="text-xs text-(--text-muted)">{template.name}</p>
            </div>
          </div>
          <button onClick={onClose} className="text-(--text-muted) hover:text-(--text-primary) p-1 rounded">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Image status */}
        <div className="mb-4 rounded-lg border p-3 flex items-center gap-3" style={{ borderColor: 'var(--border-soft)', background: 'var(--bg-surface-2)' }}>
          <div className="shrink-0">
            {checkingImage ? (
              <div className="w-4 h-4 border-2 border-(--status-deploying) border-t-transparent rounded-full animate-spin" />
            ) : imageOk ? (
              <span className="text-sm">{imageLocal ? '💾' : '🌐'}</span>
            ) : (
              <AlertCircle className="w-4 h-4" style={{ color: 'var(--status-error)' }} />
            )}
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium text-(--text-secondary)">
              {template.dbTypeDisplay} {template.version}
            </p>
            <p className="text-xs text-(--text-muted)">
              {checkingImage
                ? 'Checking image availability…'
                : imageOk
                  ? imageLocal ? 'Image cached locally — fast boot' : 'Image available on registry'
                  : 'Image not found — deploy may fail'}
            </p>
          </div>
        </div>

        <form onSubmit={handleDeploy} className="space-y-4">
          <DField label="Instance Name" required error={fieldErrors.instanceName}>
            <input
              type="text"
              required
              value={instanceName}
              onChange={e => {
                setInstanceName(e.target.value)
                if (fieldErrors.instanceName) setFieldErrors(prev => ({ ...prev, instanceName: undefined }))
              }}
              className={`input ${fieldErrors.instanceName ? 'input-error' : ''}`}
            />
          </DField>

          <DField label="Host Port" required error={fieldErrors.hostPort}>
            <input
              type="number"
              required
              min={1024}
              max={65535}
              value={hostPort}
              onChange={e => {
                setHostPort(parseInt(e.target.value, 10))
                if (fieldErrors.hostPort) setFieldErrors(prev => ({ ...prev, hostPort: undefined }))
              }}
              className={`input ${fieldErrors.hostPort ? 'input-error' : ''}`}
            />
          </DField>

          {(template.username || template.password) && (
            <div className="grid grid-cols-2 gap-3">
              {template.username && (
                <DField label="Username">
                  <input type="text" readOnly value={template.username} className="input opacity-60" />
                </DField>
              )}
              {template.password && (
                <DField label="Password">
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      readOnly
                      value={template.password}
                      className="input pr-9 opacity-60"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(p => !p)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-(--text-muted)"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </DField>
              )}
            </div>
          )}

          {fieldErrors._form && (
            <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" style={{ color: 'var(--status-error)' }} />
              <p className="text-sm" style={{ color: 'var(--status-error)' }}>{fieldErrors._form}</p>
            </div>
          )}

          <div className="flex gap-2 justify-end pt-1">
            <button type="button" onClick={onClose} className="btn-secondary">Cancel</button>
            <button
              type="submit"
              disabled={submitting}
              className="btn-primary flex items-center gap-2 disabled:opacity-60"
            >
              {submitting
                ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                : <Rocket className="w-4 h-4" />
              }
              {submitting ? 'Deploying…' : 'Launch'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function DField({ label, required, error, children }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs font-medium text-(--text-muted)">
        {label}{required && <span className="ml-0.5" style={{ color: 'var(--status-error)' }}>*</span>}
      </label>
      {children}
      {error && (
        <p className="flex items-center gap-1 text-xs mt-0.5" style={{ color: 'var(--status-error)' }}>
          <AlertCircle className="w-3 h-3 shrink-0" />{error}
        </p>
      )}
    </div>
  )
}

// ── Confirm Delete Dialog ─────────────────────────────────────────────────────
function DeleteConfirm({ name, onConfirm, onCancel, deleting }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="card w-full max-w-sm p-6 animate-fade-up">
        <h2 className="text-base font-bold text-(--text-primary) mb-2">Delete configuration?</h2>
        <p className="text-sm text-(--text-muted) mb-5">
          <strong className="text-(--text-primary)">{name}</strong> will be removed. Any instances deployed from it will remain, but will no longer link to a template.
        </p>
        <div className="flex gap-2 justify-end">
          <button onClick={onCancel} className="btn-secondary">Cancel</button>
          <button
            onClick={onConfirm}
            disabled={deleting}
            className="btn-primary flex items-center gap-2 disabled:opacity-60"
            style={{ background: 'var(--status-error)' }}
          >
            {deleting && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
            {deleting ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export function ConfigurationsPage() {
  const navigate = useNavigate()
  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(true)
  const [deployTarget, setDeployTarget] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let active = true
    getTemplates()
      .then(data => { if (active) setTemplates(data) })
      .catch(() => { if (active) toast.error('Failed to load configurations') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await deleteTemplate(deleteTarget.id)
      toast.success(`${deleteTarget.name} deleted`)
      setTemplates(prev => prev.filter(t => t.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch {
      toast.error('Failed to delete configuration')
    } finally {
      setDeleting(false)
    }
  }

  const handleDeployed = () => {
    setDeployTarget(null)
    navigate('/instances')
  }

  const hasTemplates = templates.length > 0

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-5 animate-fade-up">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="w-5 h-5 text-(--text-muted)" />
          <h1 className="text-xl font-semibold text-(--text-primary)">Configurations</h1>
        </div>
        <button onClick={() => navigate('/configurations/new')} className="btn-primary flex items-center gap-2">
          <Plus className="w-4 h-4" />
          New Configuration
        </button>
      </div>

      {loading ? (
        <div className="card p-10 text-(--text-muted) flex items-center gap-2">
          <div className="w-4 h-4 border-2 border-(--status-deploying) border-t-transparent rounded-full animate-spin" />
          Loading configurations…
        </div>
      ) : !hasTemplates ? (
        <div className="card p-12 text-center animate-fade-up">
          <SlidersHorizontal className="w-12 h-12 mx-auto mb-4" style={{ color: 'var(--border-strong)' }} />
          <h2 className="text-lg font-bold text-(--text-primary) mb-1">No configurations yet</h2>
          <p className="text-sm text-(--text-muted) mb-5">
            Create a reusable configuration blueprint to quickly deploy database instances.
          </p>
          <button onClick={() => navigate('/configurations/new')} className="btn-primary inline-flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Create your first configuration
          </button>
        </div>
      ) : (
        <div className="card overflow-hidden animate-fade-up delay-100">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b" style={{ borderColor: 'var(--border-soft)' }}>
                  {['Configuration', 'Tool', 'Default Port', 'Deploys', 'Created', 'Actions'].map(col => (
                    <th key={col} className="text-left px-4 py-3 text-xs font-bold uppercase tracking-widest text-(--text-muted)">
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {templates.map((t, i) => (
                  <tr
                    key={t.id}
                    className="border-b last:border-0 hover:bg-(--bg-surface-2) transition-colors"
                    style={{ borderColor: 'var(--border-soft)' }}
                  >
                    {/* Configuration name + description */}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2.5">
                        <span className="text-xl">{t.icon}</span>
                        <div>
                          <p className="font-semibold text-(--text-primary)">{t.name}</p>
                          {t.description && (
                            <p className="text-xs text-(--text-muted) truncate max-w-60">{t.description}</p>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* Tool */}
                    <td className="px-4 py-3">
                      <p className="text-(--text-secondary)">{t.dbTypeDisplay}</p>
                      <p className="text-xs text-(--text-muted)">{t.version}</p>
                    </td>

                    {/* Default Port */}
                    <td className="px-4 py-3 font-mono text-(--text-secondary)">{t.hostPort}</td>

                    {/* Deploy count */}
                    <td className="px-4 py-3">
                      <span className="inline-block px-2 py-0.5 rounded text-xs font-bold"
                        style={{ background: 'var(--accent-soft)', color: 'var(--status-deploying)' }}>
                        {t.deployCount}
                      </span>
                    </td>

                    {/* Created */}
                    <td className="px-4 py-3 text-xs text-(--text-muted) whitespace-nowrap">
                      {new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(t.createdAt))}
                    </td>

                    {/* Actions */}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => setDeployTarget(t)}
                          title="Deploy instance from this template"
                          className="btn-secondary p-1.5 flex items-center gap-1"
                        >
                          <Rocket className="w-3.5 h-3.5" />
                          <span className="text-xs">Deploy</span>
                        </button>
                        <button
                          onClick={() => navigate(`/configurations/${t.id}/edit`)}
                          title="Edit configuration"
                          className="btn-secondary p-1.5"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setDeleteTarget(t)}
                          title="Delete configuration"
                          className="btn-secondary p-1.5 hover:border-red-400/50"
                          style={{ color: 'var(--status-error)' }}
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {deployTarget && (
        <DeployModal template={deployTarget} onClose={() => setDeployTarget(null)} onDeployed={handleDeployed} />
      )}

      {deleteTarget && (
        <DeleteConfirm
          name={deleteTarget.name}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
          deleting={deleting}
        />
      )}
    </AppShell>
  )
}
