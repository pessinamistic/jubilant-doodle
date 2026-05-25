import { useState, useEffect } from 'react'
import { getCatalog } from '../api/client'
import { XMarkIcon, InformationCircleIcon, EyeIcon, EyeSlashIcon } from '@heroicons/react/24/outline'

const DEFAULT_PORTS = {
  POSTGRESQL: 5432, MYSQL: 3306, MONGODB: 27017, REDIS: 6379,
  MARIADB: 3307, CASSANDRA: 9042, MSSQL: 1433, CLICKHOUSE: 9000,
  ELASTICSEARCH: 9200, COUCHDB: 5984, NEO4J: 7474, DYNAMODB_LOCAL: 8000,
}

export function DeployModal({ onClose, onDeploy }) {
  const [catalog, setCatalog]       = useState([])
  const [step, setStep]             = useState(1)
  const [selected, setSelected]     = useState(null)
  const [form, setForm]             = useState({})
  const [loading, setLoading]       = useState(false)  // kept for future use
  const [extraEnv, setExtraEnv]     = useState([])
  const [showPassword, setShowPassword] = useState(false)

  useEffect(() => { getCatalog().then(setCatalog) }, [])

  const selectDb = (def) => {
    setSelected(def)
    // Pre-fill with catalog placeholders so nothing is ever blank on submit
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
    setStep(2)
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    const extraEnvJson = extraEnv.length
      ? JSON.stringify(Object.fromEntries(extraEnv.map(e => [e.key, e.value])))
      : null
    // Fire and forget — errors surface as toasts from the parent handler
    onDeploy({ ...form, dbType: selected.type, extraEnvJson })
    onClose() // close immediately without waiting for the HTTP round-trip
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
      <div className="bg-[#161b27] border border-white/[0.08] rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto m-4">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/[0.06]">
          <h2 className="text-lg font-semibold text-white">
            {step === 1 ? '🗄️ Deploy a Database' : `Configure ${selected?.displayName}`}
          </h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-300 transition-colors">
            <XMarkIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Step 1 — DB selector */}
        {step === 1 && (
          <div className="p-6 grid grid-cols-2 sm:grid-cols-3 gap-3">
            {catalog.map(def => (
              <button key={def.type}
                onClick={() => selectDb(def)}
                className="flex flex-col items-center gap-2 p-4 rounded-xl border-2 border-white/[0.06] hover:border-blue-500/50 hover:bg-blue-500/[0.08] transition-all text-center group"
              >
                <span className="text-3xl">{def.icon}</span>
                <span className="text-sm font-medium text-gray-200 group-hover:text-white">{def.displayName}</span>
                <span className="text-xs text-gray-500">{def.versions[0]}</span>
              </button>
            ))}
          </div>
        )}

        {/* Step 2 — Configure */}
        {step === 2 && selected && (
          <form onSubmit={handleSubmit} className="p-6 space-y-5">
            <p className="text-sm text-gray-400 flex items-center gap-1">
              <InformationCircleIcon className="w-4 h-4 shrink-0 text-blue-400" />
              {selected.description}
            </p>

            <div className="grid grid-cols-2 gap-4">
              <Field label="Instance Name" required>
                <input type="text" required value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  className="input" placeholder="my-postgres" />
              </Field>

              <Field label="Version" required>
                <select value={form.version}
                  onChange={e => setForm(f => ({ ...f, version: e.target.value }))}
                  className="input">
                  {selected.versions.map(v => <option key={v}>{v}</option>)}
                </select>
              </Field>

              <Field label="Host Port" required>
                <input type="number" required value={form.hostPort}
                  onChange={e => setForm(f => ({ ...f, hostPort: parseInt(e.target.value) }))}
                  className="input" min={1024} max={65535} />
              </Field>

              {selected.supportsUsername && (
                <Field label="Username" required>
                  <input type="text" required value={form.username}
                    onChange={e => setForm(f => ({ ...f, username: e.target.value }))}
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
                      onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                      className="input pr-9" />
                    <button type="button"
                      onClick={() => setShowPassword(s => !s)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                      {showPassword
                        ? <EyeSlashIcon className="w-4 h-4" />
                        : <EyeIcon className="w-4 h-4" />}
                    </button>
                  </div>
                </Field>
              )}

              {selected.supportsDatabase && (
                <Field label="Database Name" required>
                  <input type="text" required value={form.databaseName}
                    onChange={e => setForm(f => ({ ...f, databaseName: e.target.value }))}
                    className="input" />
                </Field>
              )}
            </div>

            {/* Extra env vars (e.g. root password, EULA) */}
            {extraEnv.length > 0 && (
              <div>
                <h3 className="text-sm font-medium text-gray-300 mb-2">Additional Configuration</h3>
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

            <div className="flex items-center justify-between pt-2">
              <button type="button" onClick={() => setStep(1)}
                className="text-sm text-gray-400 hover:text-white transition-colors">← Back</button>
              <button type="submit" className="btn-primary">
                🚀 Deploy {selected.displayName}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

function Field({ label, required, children }) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs font-medium text-gray-400">
        {label}{required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      {children}
    </div>
  )
}
