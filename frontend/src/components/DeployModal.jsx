import { useState, useEffect } from 'react'
import { getCatalog } from '../api/client'
import { AlertCircle, Eye, EyeOff, Info, Rocket, X } from 'lucide-react'

const DEFAULT_PORTS = {
  POSTGRESQL: 5432, MYSQL: 3306, MONGODB: 27017, REDIS: 6379,
  MARIADB: 3307, CASSANDRA: 9042, MSSQL: 1433, CLICKHOUSE: 9000,
  ELASTICSEARCH: 9200, COUCHDB: 5984, NEO4J: 7474, DYNAMODB_LOCAL: 8000,
  RABBITMQ: 5672, KAFKA: 9092,
}

/**
 * Maps a backend error message to the field it relates to.
 * Returns { field: 'name'|'hostPort'|null, message: string }
 */
function parseFieldError(errMsg) {
  if (!errMsg) return { field: null, message: errMsg }
  const lower = errMsg.toLowerCase()
  if (lower.includes('already exists') || lower.includes('named')) {
    return { field: 'name', message: errMsg }
  }
  if (lower.includes('port') && (lower.includes('in use') || lower.includes('already'))) {
    return { field: 'hostPort', message: errMsg }
  }
  return { field: null, message: errMsg }
}

export function DeployModal({ onClose, onDeploy }) {
  const [catalog, setCatalog]           = useState([])
  const [step, setStep]                 = useState(1)
  const [selected, setSelected]         = useState(null)
  const [form, setForm]                 = useState({})
  const [extraEnv, setExtraEnv]         = useState([])
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting]     = useState(false)
  const [fieldErrors, setFieldErrors]   = useState({}) // { name?: string, hostPort?: string }

  useEffect(() => { getCatalog().then(setCatalog) }, [])

  const selectDb = (def) => {
    setSelected(def)
    const usernameDefault = def.credentialEnvVars.find(e => e.type === 'TEXT')?.placeholder     ?? 'admin'
    const passwordDefault = def.credentialEnvVars.find(e => e.type === 'PASSWORD')?.placeholder ?? 'secret'
    const databaseDefault = def.credentialEnvVars.find(e => e.type === 'DATABASE')?.placeholder ?? 'mydb'

    setForm({
      name:         def.displayName.toLowerCase().replace(/\s+/g, '-') + '-1',
      version:      def.versions[0],
      hostPort:     DEFAULT_PORTS[def.type] ?? def.defaultPort,
      username:     def.supportsUsername ? usernameDefault : '',
      password:     def.supportsPassword ? passwordDefault : '',
      databaseName: def.supportsDatabase ? databaseDefault : '',
    })
    setExtraEnv(def.credentialEnvVars
      .filter(e => !['TEXT', 'PASSWORD', 'DATABASE'].includes(e.type))
      .map(e => ({ key: e.name, value: e.placeholder, label: e.label })))
    setShowPassword(false)
    setFieldErrors({})
    setStep(2)
  }

  // Clear a field's error as soon as the user starts editing it
  const updateField = (key, value) => {
    setForm(f => ({ ...f, [key]: value }))
    if (fieldErrors[key]) setFieldErrors(fe => ({ ...fe, [key]: undefined }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setFieldErrors({})
    setSubmitting(true)

    const extraEnvJson = extraEnv.length
      ? JSON.stringify(Object.fromEntries(extraEnv.map(e => [e.key, e.value])))
      : null

    try {
      await onDeploy({ ...form, dbType: selected.type, extraEnvJson })
      // onDeploy resolves on success — close the modal
      onClose()
    } catch (err) {
      const message = err?.response?.data?.error ?? err?.message ?? 'Deploy failed'
      const { field, message: msg } = parseFieldError(message)

      if (field) {
        // Highlight the specific input that caused the conflict
        setFieldErrors({ [field]: msg })
      } else {
        // Generic error — show at the bottom of the form
        setFieldErrors({ _form: msg })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
      <div className="card w-full max-w-2xl max-h-[90vh] overflow-y-auto m-4">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b-2 border-(--border-strong)">
          <h2 className="text-lg font-semibold text-(--text-primary) flex items-center gap-2">
            <Rocket className="w-4 h-4" />
            {step === 1 ? 'Launch a Database' : `Configure ${selected?.displayName}`}
          </h2>
          <button onClick={onClose} className="text-(--text-muted) hover:text-(--text-primary) transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Step 1 — DB selector */}
        {step === 1 && (
          <div className="p-6 grid grid-cols-2 sm:grid-cols-3 gap-3">
            {catalog.map(def => (
              <button key={def.type}
                onClick={() => selectDb(def)}
                className="flex flex-col items-center gap-2 p-4 rounded-md border-2 border-(--border-strong) hover:-translate-y-1 hover:shadow-(--shadow-raised) transition-all text-center group bg-(--bg-surface-2)"
              >
                <span className="text-3xl">{def.icon}</span>
                <span className="text-sm font-medium text-(--text-primary)">{def.displayName}</span>
                <span className="text-xs text-(--text-muted)">{def.versions[0]}</span>
              </button>
            ))}
          </div>
        )}

        {/* Step 2 — Configure */}
        {step === 2 && selected && (
          <form onSubmit={handleSubmit} className="p-6 space-y-5">
            <p className="text-sm text-(--text-muted) flex items-center gap-1">
              <Info className="w-4 h-4 shrink-0" style={{ color: 'var(--status-deploying)' }} />
              {selected.description}
            </p>

            <div className="grid grid-cols-2 gap-4">
              <Field label="Instance Name" required error={fieldErrors.name}>
                <input
                  type="text"
                  required
                  value={form.name}
                  onChange={e => updateField('name', e.target.value)}
                  className={`input ${fieldErrors.name ? 'input-error' : ''}`}
                  placeholder="my-postgres"
                />
              </Field>

              <Field label="Version" required>
                <select
                  value={form.version}
                  onChange={e => updateField('version', e.target.value)}
                  className="input">
                  {selected.versions.map(v => <option key={v}>{v}</option>)}
                </select>
              </Field>

              <Field label="Host Port" required error={fieldErrors.hostPort}>
                <input
                  type="number"
                  required
                  value={form.hostPort}
                  onChange={e => updateField('hostPort', parseInt(e.target.value))}
                  className={`input ${fieldErrors.hostPort ? 'input-error' : ''}`}
                  min={1024}
                  max={65535}
                />
              </Field>

              {selected.supportsUsername && (
                <Field label="Username" required>
                  <input type="text" required value={form.username}
                    onChange={e => updateField('username', e.target.value)}
                    className="input" />
                </Field>
              )}

              {selected.supportsPassword && (
                <Field label="Password" required>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      required
                      value={form.password}
                      onChange={e => updateField('password', e.target.value)}
                      className="input pr-9" />
                    <button type="button"
                      onClick={() => setShowPassword(s => !s)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-(--text-muted) hover:text-(--text-primary)">
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </Field>
              )}

              {selected.supportsDatabase && (
                <Field label="Database Name" required>
                  <input type="text" required value={form.databaseName}
                    onChange={e => updateField('databaseName', e.target.value)}
                    className="input" />
                </Field>
              )}
            </div>

            {/* Extra env vars (e.g. root password, EULA) */}
            {extraEnv.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-(--text-secondary) mb-2">Additional Configuration</h3>
                <div className="grid grid-cols-2 gap-3">
                  {extraEnv.map((env, i) => (
                    <Field key={env.key} label={env.label}>
                      <input type="text" value={env.value}
                        onChange={e => {
                          const updated = [...extraEnv]
                          updated[i] = { ...env, value: e.target.value }
                          setExtraEnv(updated)
                        }}
                        className="input" placeholder={env.value} />
                    </Field>
                  ))}
                </div>
              </div>
            )}

            {/* Generic form-level error (not tied to a specific field) */}
            {fieldErrors._form && (
              <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" style={{ color: 'var(--status-error)' }} />
                <p className="text-sm" style={{ color: 'var(--status-error)' }}>{fieldErrors._form}</p>
              </div>
            )}

            <div className="flex items-center justify-between pt-2">
              <button type="button" onClick={() => { setStep(1); setFieldErrors({}) }}
                className="text-sm text-(--text-muted) hover:text-(--text-primary) transition-colors">
                Back
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="btn-primary flex items-center gap-2 disabled:opacity-60">
                {submitting
                  ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  : <Rocket className="w-4 h-4" />
                }
                {submitting ? 'Launching…' : `Launch ${selected.displayName}`}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

function Field({ label, required, error, children }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs font-medium text-(--text-muted)">
        {label}{required && <span className="ml-0.5" style={{ color: 'var(--status-error)' }}>*</span>}
      </label>
      {children}
      {error && (
        <p className="flex items-center gap-1 text-xs mt-0.5" style={{ color: 'var(--status-error)' }}>
          <AlertCircle className="w-3 h-3 shrink-0" />
          {error}
        </p>
      )}
    </div>
  )
}
