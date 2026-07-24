import { useState } from 'react'
import { exportDockerCompose } from '../api/client'
import { downloadBlob } from '../utils/downloadBlob'
import { FileDown, X } from 'lucide-react'
import toast from 'react-hot-toast'

/**
 * Lets the user pick a subset of active instances to include in a docker-compose.yml export.
 * All instances are pre-checked; "Export selected" is disabled at zero selections.
 */
export function ExportComposeModal({ instances, onClose }) {
  const [selected, setSelected]   = useState(() => new Set(instances.map(i => i.id)))
  const [exporting, setExporting] = useState(false)

  const allChecked = instances.length > 0 && selected.size === instances.length

  const toggle = (id) => {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleAll = () => {
    setSelected(allChecked ? new Set() : new Set(instances.map(i => i.id)))
  }

  const handleExport = async () => {
    const ids = Array.from(selected)
    setExporting(true)
    try {
      const blob = await exportDockerCompose(ids)
      downloadBlob(blob, 'docker-compose.yml')
      toast.success(`Exported ${ids.length} instance${ids.length === 1 ? '' : 's'} to docker-compose.yml`)
      onClose()
    } catch (e) {
      toast.error(e.response?.data?.error ?? 'Failed to export docker-compose.yml')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />

      <div className="relative z-10 w-full max-w-lg card overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b-2 border-(--border-strong)">
          <div className="flex items-center gap-3">
            <FileDown className="w-5 h-5" style={{ color: 'var(--status-deploying)' }} />
            <h2 className="text-(--text-primary) font-semibold">Export Compose</h2>
          </div>
          <button onClick={onClose} className="text-(--text-muted) hover:text-(--text-primary) transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="px-6 pb-6 pt-3">
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm text-(--text-muted)">
              Select the instances to include in the docker-compose.yml.
            </p>
            {instances.length > 0 && (
              <button
                onClick={toggleAll}
                className="btn-ghost text-xs shrink-0">
                {allChecked ? 'Deselect all' : 'Select all'}
              </button>
            )}
          </div>

          {instances.length === 0 ? (
            <div className="text-center py-12">
              <FileDown className="w-10 h-10 text-(--text-quiet) mx-auto mb-3" />
              <p className="text-(--text-muted) text-sm">No exportable instances</p>
            </div>
          ) : (
            <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
              {instances.map(i => (
                <label
                  key={i.id}
                  className="flex items-center gap-3 w-full text-left px-4 py-3 rounded-md border-2 border-(--border-strong) bg-(--bg-surface-2) hover:-translate-y-0.5 hover:shadow-(--shadow-raised) transition-all cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selected.has(i.id)}
                    onChange={() => toggle(i.id)}
                    className="w-4 h-4 shrink-0 accent-(--status-deploying)"
                  />
                  <span className="text-xl shrink-0">{i.icon ?? '🗄️'}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-(--text-primary) font-medium text-sm truncate">{i.name}</span>
                      <span className="text-xs text-(--text-muted)">{i.dbTypeDisplay}</span>
                    </div>
                    <p className="text-xs text-(--text-muted) mt-0.5">Port {i.hostPort}</p>
                  </div>
                </label>
              ))}
            </div>
          )}

          {/* Footer */}
          <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-white/6">
            <button onClick={onClose} className="btn-ghost">Cancel</button>
            <button
              onClick={handleExport}
              disabled={selected.size === 0 || exporting}
              className="btn-primary flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
              {exporting && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              Export selected
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
