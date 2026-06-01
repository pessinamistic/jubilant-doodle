import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { AlertCircle, Eye, EyeOff, Info, RefreshCw, Save } from 'lucide-react'
import toast from 'react-hot-toast'

import { AppShell } from '../components/AppShell'
import { createTemplate, getCatalog, getCatalogVersions, getTemplate, updateTemplate } from '../api/client'

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
  if (lower.includes('image tag does not exist') || (lower.includes('image') && lower.includes('not found'))) {
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
    name: def.displayName.toLowerCase().replace(/\s+/g, '-') + '-template',
    description: '',
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

export function ConfigurationFormPage() {
  const navigate = useNavigate()
  const { id: editId } = useParams()           // present on /configurations/:id/edit
  const [searchParams, setSearchParams] = useSearchParams()
  const isEditMode = Boolean(editId)

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
      if (cached?.length) return cached
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
      if (refresh) toast.error('Failed to refresh versions from registry')
      return []
    } finally {
      setLoadingVersionType(current => (current === dbType ? null : current))
    }
  }

  const applySelection = (def, updateUrl = true, overrides = {}) => {
    if (!def) return
    const initialVersions = versionsByType[def.type]?.length ? versionsByType[def.type] : def.versions
    const initial = { ...buildInitialForm(def, initialVersions), ...overrides }

    selectedTypeRef.current = def.type
    setSelectedType(def.type)
    setForm(initial)
    setExtraEnv(overrides._extraEnv ?? buildExtraEnv(def))
    setFieldErrors({})
    setShowPassword(false)

    if (updateUrl) {
      const params = new URLSearchParams(searchParams)
      params.set('tool', def.type)
      setSearchParams(params, { replace: true })
    }

    void loadVersions(def.type, false).then((versions) => {
      if (!versions?.length || selectedTypeRef.current !== def.type) return
      setForm(prev => {
        if (!prev) return prev
        const nextVersion = versions.includes(prev.version) ? prev.version : versions[0]
        return nextVersion === prev.version ? prev : { ...prev, version: nextVersion }
      })
    })
  }

  // ── Bootstrap ──────────────────────────────────────────────────────────────
  useEffect(() => {
    let active = true

    const bootstrap = async () => {
      try {
        const [defs, existingTemplate] = await Promise.all([
          getCatalog(),
          isEditMode ? getTemplate(editId) : Promise.resolve(null),
        ])
        if (!active) return

        setCatalog(defs)
        const deployable = defs.filter(d => d.dockerImage && d.versions?.length)
        const byType = Object.fromEntries(deployable.map(def => [def.type, def]))

        if (isEditMode && existingTemplate) {
          const def = byType[existingTemplate.dbType]
          if (def) {
            const extraEnvFromTemplate = existingTemplate.extraEnvJson
              ? Object.entries(JSON.parse(existingTemplate.extraEnvJson)).map(([key, value]) => {
                  const envDef = def.credentialEnvVars.find(e => e.name === key)
                  return { key, value, label: envDef?.label ?? key }
                })
              : buildExtraEnv(def)
            applySelection(def, false, {
              name: existingTemplate.name,
              description: existingTemplate.description ?? '',
              version: existingTemplate.version,
              hostPort: existingTemplate.hostPort,
              username: existingTemplate.username ?? '',
              password: existingTemplate.password ?? '',
              databaseName: existingTemplate.databaseName ?? '',
              _extraEnv: extraEnvFromTemplate,
            })
          }
        } else {
          const requested = searchParams.get('tool')
          const resolved = requested && byType[requested] ? byType[requested] : deployable[0]
          if (resolved) applySelection(resolved, false)
        }
      } catch {
        toast.error(isEditMode ? 'Failed to load template' : 'Failed to load catalog')
      } finally {
        if (active) setLoadingCatalog(false)
      }
    }

    bootstrap()
    return () => { active = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onSelectTool = (def) => applySelection(def, true)

  const updateField = (key, value) => {
    setForm(prev => ({ ...prev, [key]: value }))
    if (fieldErrors[key]) setFieldErrors(prev => ({ ...prev, [key]: undefined }))
  }

  const refreshVersions = async () => {
    if (!selected || !form) return
    const versions = await loadVersions(selected.type, true)
    if (!versions.length) { toast.error('No versions returned from registry'); return }
    const nextVersion = versions.includes(form.version) ? form.version : versions[0]
    updateField('version', nextVersion)
    toast.success('Versions refreshed from registry')
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!selected || !form) return

    setFieldErrors({})
    setSubmitting(true)

    const extraEnvJson = extraEnv.length
      ? JSON.stringify(Object.fromEntries(extraEnv.map(env => [env.key, env.value])))
      : null

    const payload = {
      name: form.name,
      description: form.description || null,
      dbType: selected.type,
      version: form.version,
      hostPort: form.hostPort,
      username: form.username || null,
      password: form.password || null,
      databaseName: form.databaseName || null,
      extraEnvJson,
    }

    try {
      if (isEditMode) {
        await updateTemplate(editId, payload)
        toast.success('Configuration updated')
      } else {
        await createTemplate(payload)
        toast.success('Configuration saved')
      }
      navigate('/configurations')
    } catch (err) {
      const message = err?.response?.data?.error ?? err?.message ?? 'Save failed'
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
        <h1 className="text-xl font-semibold text-(--text-primary)">
          {isEditMode ? 'Edit Configuration' : 'New Configuration'}
        </h1>
        <p className="text-sm text-(--text-muted) mt-0.5">
          {isEditMode
            ? 'Update this reusable configuration template.'
            : 'Save a reusable configuration blueprint. Deploy it as many times as you like from the Configurations page.'}
        </p>
      </div>

      {loadingCatalog ? (
        <div className="card p-10 text-(--text-muted) flex items-center gap-2">
          <div className="w-4 h-4 border-2 border-(--status-deploying) border-t-transparent rounded-full animate-spin" />
          Loading catalog…
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-[280px_1fr] gap-5 items-start">

          {/* ── Tool picker ── */}
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

          {/* ── Form ── */}
          <section className="card p-5 animate-fade-up delay-100">
            {!selected || !form ? (
              <p className="text-sm text-(--text-muted)">Select a tool from the left to configure a template.</p>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <h2 className="text-lg font-semibold text-(--text-primary) flex items-center gap-2">
                    <span className="text-xl">{selected.icon}</span>
                    {selected.displayName}
                  </h2>
                  <p className="text-sm text-(--text-muted) flex items-center gap-1 mt-1">
                    <Info className="w-4 h-4 shrink-0" style={{ color: 'var(--status-deploying)' }} />
                    {selected.description}
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">

                  {/* Template Name */}
                  <Field label="Template Name" required error={fieldErrors.name}>
                    <input
                      type="text"
                      required
                      value={form.name}
                      onChange={e => updateField('name', e.target.value)}
                      className={`input ${fieldErrors.name ? 'input-error' : ''}`}
                    />
                  </Field>

                  {/* Version */}
                  <Field label="Version" required error={fieldErrors.version}>
                    <div className="space-y-1.5">
                      <select
                        value={form.version}
                        onChange={e => updateField('version', e.target.value)}
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

                  {/* Default Host Port */}
                  <Field label="Default Host Port" required error={fieldErrors.hostPort}>
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
                    <Field label="Username">
                      <input
                        type="text"
                        value={form.username}
                        onChange={e => updateField('username', e.target.value)}
                        className="input"
                      />
                    </Field>
                  )}

                  {selected.supportsPassword && (
                    <Field label="Password">
                      <div className="relative">
                        <input
                          type={showPassword ? 'text' : 'password'}
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
                    <Field label="Database Name">
                      <input
                        type="text"
                        value={form.databaseName}
                        onChange={e => updateField('databaseName', e.target.value)}
                        className="input"
                      />
                    </Field>
                  )}
                </div>

                {/* Description — full width */}
                <Field label="Description (optional)">
                  <input
                    type="text"
                    value={form.description}
                    onChange={e => updateField('description', e.target.value)}
                    placeholder="e.g. Production-like Postgres config for the orders service"
                    className="input"
                  />
                </Field>

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

                {fieldErrors._form && (
                  <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
                    <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" style={{ color: 'var(--status-error)' }} />
                    <p className="text-sm" style={{ color: 'var(--status-error)' }}>{fieldErrors._form}</p>
                  </div>
                )}

                <div className="flex items-center justify-end gap-2 pt-1">
                  <button type="button" onClick={() => navigate('/configurations')} className="btn-secondary">Cancel</button>
                  <button
                    type="submit"
                    className="btn-primary flex items-center gap-2 disabled:opacity-60"
                    disabled={submitting}
                  >
                    {submitting
                      ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      : <Save className="w-4 h-4" />
                    }
                    {submitting ? 'Saving…' : isEditMode ? 'Update Configuration' : 'Save Configuration'}
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
