import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertCircle, Eye, EyeOff, Info, RefreshCw, Rocket } from 'lucide-react'
import toast from 'react-hot-toast'

import { AppShell } from '../components/AppShell'
import { checkImageStatus, deployInstance, getCatalog, getCatalogVersions } from '../api/client'

const DEFAULT_PORTS = {
  POSTGRESQL: 5432,
  MYSQL: 3306,
  MARIADB: 3307,
  MSSQL: 1433,
  MONGODB: 27017,
  COUCHDB: 5984,
  NEO4J: 7474,
  DYNAMODB_LOCAL: 8000,
  REDIS: 6379,
  CASSANDRA: 9042,
  CLICKHOUSE: 9000,
  ELASTICSEARCH: 9200,
  RABBITMQ: 5672,
  KAFKA: 9092,
  CONDUKTOR: 8080,
  GRAFANA: 3000,
  PROMETHEUS: 9090,
  LOKI: 3100,
  MINIO: 9000,
  KEYCLOAK: 8080,
  VAULT: 8200,
  NGINX: 8080,
  ADMINER: 8080,
  PGADMIN: 5050,
}

const CATALOG_GROUPS = [
  { label: 'Relational Databases', types: ['POSTGRESQL', 'MYSQL', 'MARIADB', 'MSSQL'] },
  { label: 'NoSQL Databases', types: ['MONGODB', 'COUCHDB', 'NEO4J', 'DYNAMODB_LOCAL'] },
  { label: 'Cache & Key-Value', types: ['REDIS'] },
  { label: 'Wide-Column & OLAP', types: ['CASSANDRA', 'CLICKHOUSE'] },
  { label: 'Search Engines', types: ['ELASTICSEARCH'] },
  { label: 'Messaging & Streaming', types: ['RABBITMQ', 'KAFKA', 'CONDUKTOR'] },
  { label: 'Observability', types: ['GRAFANA', 'PROMETHEUS', 'LOKI'] },
  { label: 'Object Storage', types: ['MINIO'] },
  { label: 'Identity & Secrets', types: ['KEYCLOAK', 'VAULT'] },
  { label: 'Web & Proxy', types: ['NGINX'] },
  { label: 'DB Admin UIs', types: ['ADMINER', 'PGADMIN'] },
]

function parseFieldError(errMsg) {
  if (!errMsg) return { field: null, message: errMsg }
  const lower = errMsg.toLowerCase()
  if (lower.includes('already exists') || lower.includes('named')) {
    return { field: 'name', message: errMsg }
  }
  if (lower.includes('port') && (lower.includes('in use') || lower.includes('already'))) {
    return { field: 'hostPort', message: errMsg }
  }
  if (lower.includes('image tag does not exist') || lower.includes('image') && lower.includes('not found')) {
    return { field: 'version', message: errMsg }
  }
  return { field: null, message: errMsg }
}

function buildInitialForm(def, versions = def.versions) {
  const usernameDefault = def.credentialEnvVars.find(e => e.type === 'TEXT')?.placeholder ?? 'admin'
  const passwordDefault = def.credentialEnvVars.find(e => e.type === 'PASSWORD')?.placeholder ?? 'secret'
  const databaseDefault = def.credentialEnvVars.find(e => e.type === 'DATABASE')?.placeholder ?? 'mydb'
  const initialVersion = versions?.[0] ?? def.versions?.[0] ?? 'latest'

  return {
    name: def.displayName.toLowerCase().replace(/\s+/g, '-') + '-1',
    version: initialVersion,
    hostPort: DEFAULT_PORTS[def.type] ?? def.defaultPort,
    username: def.supportsUsername ? usernameDefault : '',
    password: def.supportsPassword ? passwordDefault : '',
    databaseName: def.supportsDatabase ? databaseDefault : '',
  }
}

function buildExtraEnv(def) {
  return def.credentialEnvVars
    .filter(e => !['TEXT', 'PASSWORD', 'DATABASE'].includes(e.type))
    .map(e => ({ key: e.name, value: e.placeholder, label: e.label }))
}

export function DeployPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const [catalog, setCatalog] = useState([])
  const [loadingCatalog, setLoadingCatalog] = useState(true)
  const [selectedType, setSelectedType] = useState(null)
  const [versionsByType, setVersionsByType] = useState({})
  const [loadingVersionType, setLoadingVersionType] = useState(null)
  const selectedTypeRef = useRef(null)

  const [form, setForm] = useState(null)
  const [extraEnv, setExtraEnv] = useState([])
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const [imageStatus, setImageStatus] = useState(null)
  const [checkingImage, setCheckingImage] = useState(false)

  const deployableCatalog = useMemo(() => catalog.filter(d => d.dockerImage && d.versions?.length), [catalog])
  const selected = useMemo(() => deployableCatalog.find(d => d.type === selectedType) ?? null, [deployableCatalog, selectedType])
  const selectedVersions = useMemo(() => {
    if (!selected) return []
    const live = versionsByType[selected.type]
    return live?.length ? live : selected.versions
  }, [selected, versionsByType])
  const isLoadingSelectedVersions = selected && loadingVersionType === selected.type

  const loadVersions = async (dbType, refresh = false) => {
    if (!dbType) return []

    if (!refresh) {
      const cached = versionsByType[dbType]
      if (cached?.length) {
        return cached
      }
    }

    setLoadingVersionType(dbType)
    try {
      const versions = await getCatalogVersions(dbType, refresh)
      if (Array.isArray(versions) && versions.length > 0) {
        setVersionsByType(prev => ({ ...prev, [dbType]: versions }))
        return versions
      }
      return []
    } catch {
      if (refresh) {
        toast.error('Failed to refresh versions from registry')
      }
      return []
    } finally {
      setLoadingVersionType(current => (current === dbType ? null : current))
    }
  }

  const runImageCheck = async (dbType, tag, refresh = false) => {
    setCheckingImage(true)
    try {
      const status = await checkImageStatus(dbType, tag, refresh)
      setImageStatus(status)
      return status
    } catch {
      const fallback = {
        decision: 'ALLOW_WITH_WARNING',
        message: 'Could not check image status right now',
        localStatus: 'UNKNOWN',
        dockerHubStatus: 'UNKNOWN',
      }
      setImageStatus(fallback)
      return fallback
    } finally {
      setCheckingImage(false)
    }
  }

  const applySelection = (def, updateUrl = true) => {
    if (!def) return
    const initialVersions = versionsByType[def.type]?.length ? versionsByType[def.type] : def.versions
    const initial = buildInitialForm(def, initialVersions)

    selectedTypeRef.current = def.type
    setSelectedType(def.type)
    setForm(initial)
    setExtraEnv(buildExtraEnv(def))
    setFieldErrors({})
    setImageStatus(null)
    setShowPassword(false)

    if (updateUrl) {
      const params = new URLSearchParams(searchParams)
      params.set('tool', def.type)
      setSearchParams(params, { replace: true })
    }

    void runImageCheck(def.type, initial.version, false)

    void loadVersions(def.type, false).then((versions) => {
      if (!versions?.length) {
        return
      }
      if (selectedTypeRef.current !== def.type) {
        return
      }

      setForm(prev => {
        if (!prev) return prev
        const nextVersion = versions.includes(prev.version) ? prev.version : versions[0]
        return nextVersion === prev.version ? prev : { ...prev, version: nextVersion }
      })

      const nextVersion = versions.includes(initial.version) ? initial.version : versions[0]
      void runImageCheck(def.type, nextVersion, false)
    })
  }

  useEffect(() => {
    let active = true
    getCatalog()
      .then((defs) => {
        if (!active) return
        setCatalog(defs)

        const requested = searchParams.get('tool')
        const deployable = defs.filter(d => d.dockerImage && d.versions?.length)
        const byType = Object.fromEntries(deployable.map(def => [def.type, def]))
        const resolved = requested && byType[requested] ? byType[requested] : deployable[0]
        if (resolved) applySelection(resolved, false)
      })
      .catch(() => toast.error('Failed to load catalog'))
      .finally(() => {
        if (active) setLoadingCatalog(false)
      })

    return () => { active = false }
    // Intentionally run once for initial catalog bootstrap.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSelectTool = (def) => {
    applySelection(def, true)
  }

  const updateField = (key, value) => {
    setForm(prev => ({ ...prev, [key]: value }))
    if (fieldErrors[key]) setFieldErrors(prev => ({ ...prev, [key]: undefined }))
  }

  const refreshImageCheck = async () => {
    if (!selected || !form?.version) return
    try {
      await runImageCheck(selected.type, form.version, true)
      toast.success('Image status refreshed')
    } catch {
      toast.error('Failed to refresh image status')
    }
  }

  const refreshVersions = async () => {
    if (!selected || !form) return
    const versions = await loadVersions(selected.type, true)
    if (!versions.length) {
      toast.error('No versions returned from registry')
      return
    }

    const nextVersion = versions.includes(form.version) ? form.version : versions[0]
    updateField('version', nextVersion)
    await runImageCheck(selected.type, nextVersion, true)
    toast.success('Versions refreshed from registry')
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!selected || !form) return
    if (imageStatus?.decision === 'BLOCK') {
      setFieldErrors({ version: imageStatus.message || 'Selected image tag is not deployable' })
      return
    }

    setFieldErrors({})
    setSubmitting(true)

    const extraEnvJson = extraEnv.length
      ? JSON.stringify(Object.fromEntries(extraEnv.map(env => [env.key, env.value])))
      : null

    try {
      await deployInstance({ ...form, dbType: selected.type, extraEnvJson })
      toast.success(`Deploying ${form.name}… this may take a minute`)
      navigate('/instances')
    } catch (err) {
      const message = err?.response?.data?.error ?? err?.message ?? 'Deploy failed'
      const parsed = parseFieldError(message)
      if (parsed.field) setFieldErrors({ [parsed.field]: parsed.message })
      else setFieldErrors({ _form: parsed.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AppShell>
      <div className="mb-5 animate-fade-up">
        <h1 className="text-xl font-semibold text-(--text-primary)">Deploy Database</h1>
        <p className="text-sm text-(--text-muted) mt-0.5">Select a tool from the left and launch a configured instance from this workspace.</p>
      </div>

      {loadingCatalog ? (
        <div className="card p-10 text-(--text-muted) flex items-center gap-2">
          <div className="w-4 h-4 border-2 border-(--status-deploying) border-t-transparent rounded-full animate-spin" />
          Loading deployment catalog…
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-[280px_1fr] gap-5 items-start">
          <aside className="card p-3 max-h-[72vh] overflow-auto animate-fade-up">
            <p className="text-xs font-bold uppercase tracking-widest text-(--text-muted) mb-2 px-2">Supported Tools</p>
            <div className="space-y-4">
              {CATALOG_GROUPS
                .map(group => ({
                  ...group,
                  defs: group.types.map(type => deployableCatalog.find(def => def.type === type)).filter(Boolean),
                }))
                .filter(group => group.defs.length > 0)
                .map(group => (
                  <div key={group.label}>
                    <p className="text-[10px] uppercase tracking-widest text-(--text-muted) mb-1 px-2">{group.label}</p>
                    <div className="space-y-1">
                      {group.defs.map(def => {
                        const active = def.type === selected?.type
                        const liveDefault = versionsByType[def.type]?.[0] ?? def.versions[0]
                        return (
                          <button
                            key={def.type}
                            onClick={() => onSelectTool(def)}
                            className={`w-full text-left px-2.5 py-2 rounded-sm border-2 transition-all ${active
                              ? 'bg-(--accent-soft) border-(--border-strong) shadow-(--shadow-raised)'
                              : 'bg-(--bg-surface-2) border-transparent hover:border-(--border-soft)'
                            }`}
                          >
                            <div className="flex items-center gap-2">
                              <span className="text-lg leading-none">{def.icon}</span>
                              <div className="min-w-0">
                                <p className="text-sm font-semibold text-(--text-primary) truncate">{def.displayName}</p>
                                <p className="text-xs text-(--text-muted) truncate">Default: {liveDefault}</p>
                              </div>
                            </div>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                ))}
            </div>
          </aside>

          <section className="card p-5 animate-fade-up delay-100">
            {!selected || !form ? (
              <p className="text-sm text-(--text-muted)">Select a tool from the left navigation to configure deployment.</p>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <h2 className="text-lg font-semibold text-(--text-primary) flex items-center gap-2">
                    <Rocket className="w-4 h-4" />
                    {selected.displayName}
                  </h2>
                  <p className="text-sm text-(--text-muted) flex items-center gap-1 mt-1">
                    <Info className="w-4 h-4 shrink-0" style={{ color: 'var(--status-deploying)' }} />
                    {selected.description}
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <Field label="Instance Name" required error={fieldErrors.name}>
                    <input
                      type="text"
                      required
                      value={form.name}
                      onChange={e => updateField('name', e.target.value)}
                      className={`input ${fieldErrors.name ? 'input-error' : ''}`}
                    />
                  </Field>

                  <Field label="Version" required error={fieldErrors.version}>
                    <div className="space-y-1.5">
                      <select
                        value={form.version}
                        onChange={e => {
                          const version = e.target.value
                          updateField('version', version)
                          void runImageCheck(selected.type, version, false)
                        }}
                        className={`input ${fieldErrors.version ? 'input-error' : ''}`}
                      >
                        {selectedVersions.map(version => <option key={version}>{version}</option>)}
                      </select>
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-[11px] text-(--text-muted)">
                          {isLoadingSelectedVersions
                            ? 'Loading live tags from registry...'
                            : `${selectedVersions.length} versions available`}
                        </p>
                        <button
                          type="button"
                          onClick={refreshVersions}
                          className="btn-secondary text-[11px] px-2.5 py-1"
                          disabled={isLoadingSelectedVersions}
                        >
                          <RefreshCw className={`w-3 h-3 ${isLoadingSelectedVersions ? 'animate-spin' : ''}`} />
                          Refresh tags
                        </button>
                      </div>
                    </div>
                  </Field>

                  <Field label="Host Port" required error={fieldErrors.hostPort}>
                    <input
                      type="number"
                      required
                      min={1024}
                      max={65535}
                      value={form.hostPort}
                      onChange={e => updateField('hostPort', parseInt(e.target.value, 10))}
                      className={`input ${fieldErrors.hostPort ? 'input-error' : ''}`}
                    />
                  </Field>

                  {selected.supportsUsername && (
                    <Field label="Username" required>
                      <input
                        type="text"
                        required
                        value={form.username}
                        onChange={e => updateField('username', e.target.value)}
                        className="input"
                      />
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
                          className="input pr-9"
                        />
                        <button
                          type="button"
                          onClick={() => setShowPassword(prev => !prev)}
                          className="absolute right-2 top-1/2 -translate-y-1/2 text-(--text-muted) hover:text-(--text-primary)"
                        >
                          {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                      </div>
                    </Field>
                  )}

                  {selected.supportsDatabase && (
                    <Field label="Database Name" required>
                      <input
                        type="text"
                        required
                        value={form.databaseName}
                        onChange={e => updateField('databaseName', e.target.value)}
                        className="input"
                      />
                    </Field>
                  )}
                </div>

                {extraEnv.length > 0 && (
                  <div>
                    <h3 className="text-sm font-medium text-(--text-secondary) mb-2">Additional Configuration</h3>
                    <div className="grid grid-cols-2 gap-3">
                      {extraEnv.map((env, idx) => (
                        <Field key={env.key} label={env.label}>
                          <input
                            type="text"
                            value={env.value}
                            onChange={e => {
                              const next = [...extraEnv]
                              next[idx] = { ...env, value: e.target.value }
                              setExtraEnv(next)
                            }}
                            className="input"
                          />
                        </Field>
                      ))}
                    </div>
                  </div>
                )}

                <div className="rounded-lg border p-3" style={{
                  background: 'var(--bg-surface-2)',
                  borderColor: 'var(--border-soft)',
                }}>
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="text-xs uppercase tracking-widest text-(--text-muted) mb-1">Image Status</p>
                      {checkingImage ? (
                        <p className="text-sm text-(--text-muted) flex items-center gap-2">
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          Checking {selected.dockerImage}:{form.version}…
                        </p>
                      ) : imageStatus ? (
                        <>
                          <p className="text-sm font-medium text-(--text-primary)">{imageStatus.message}</p>
                          <p className="text-xs text-(--text-muted) mt-1">
                            Local: {imageStatus.localStatus} · Docker Hub: {imageStatus.dockerHubStatus} · Decision: {imageStatus.decision}
                          </p>
                        </>
                      ) : (
                        <p className="text-sm text-(--text-muted)">No status yet</p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={refreshImageCheck}
                      className="btn-secondary text-xs"
                      disabled={checkingImage}
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${checkingImage ? 'animate-spin' : ''}`} />
                      Refresh
                    </button>
                  </div>
                  {imageStatus?.decision === 'BLOCK' && (
                    <p className="text-xs mt-2" style={{ color: 'var(--status-error)' }}>
                      Deployment is blocked until you pick a valid image tag.
                    </p>
                  )}
                  {imageStatus?.decision === 'ALLOW_WITH_WARNING' && (
                    <p className="text-xs mt-2" style={{ color: 'var(--status-warning)' }}>
                      Proceeding may still fail if the image tag is not available remotely.
                    </p>
                  )}
                </div>

                {fieldErrors._form && (
                  <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" style={{ color: 'var(--status-error)' }} />
                    <p className="text-sm" style={{ color: 'var(--status-error)' }}>{fieldErrors._form}</p>
                  </div>
                )}

                <div className="flex items-center justify-end gap-2 pt-1">
                  <button type="button" onClick={() => navigate('/instances')} className="btn-secondary">Cancel</button>
                  <button
                    type="submit"
                    className="btn-primary flex items-center gap-2 disabled:opacity-60"
                    disabled={submitting || imageStatus?.decision === 'BLOCK'}
                  >
                    {submitting
                      ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      : <Rocket className="w-4 h-4" />
                    }
                    {submitting ? 'Launching…' : `Launch ${selected.displayName}`}
                  </button>
                </div>
              </form>
            )}
          </section>
        </div>
      )}
    </AppShell>
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
