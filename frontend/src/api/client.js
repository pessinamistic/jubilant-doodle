import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

const rawApiLogLevel = (import.meta.env.VITE_API_LOG_LEVEL ?? (import.meta.env.DEV ? 'verbose' : 'basic'))
  .toLowerCase()

const apiLoggingEnabled = rawApiLogLevel !== 'off'
const apiLoggingVerbose = rawApiLogLevel === 'verbose'

const SENSITIVE_KEY_PATTERN = /(password|token|secret|authorization|connectionstring|masked)/i

const nowMs = () => (typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now())

function scrub(value) {
  if (value == null) return value
  if (typeof value === 'string') return value.length > 500 ? `${value.slice(0, 500)}...` : value
  if (Array.isArray(value)) return value.map(scrub)
  if (typeof value === 'object') {
	return Object.fromEntries(
	  Object.entries(value).map(([k, v]) => [k, SENSITIVE_KEY_PATTERN.test(k) ? '[REDACTED]' : scrub(v)])
	)
  }
  return value
}

function requestLabel(config) {
  const method = (config.method ?? 'get').toUpperCase()
  const path = config.url ?? ''
  return `${method} ${path}`
}

api.interceptors.request.use(
  (config) => {
	config.metadata = { ...(config.metadata ?? {}), startedAt: nowMs() }

	if (apiLoggingEnabled) {
	  const label = requestLabel(config)
	  if (apiLoggingVerbose) {
		console.info('[api:req]', label, {
		  params: scrub(config.params),
		  data: scrub(config.data)
		})
	  } else {
		console.info('[api:req]', label)
	  }
	}

	return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => {
	if (apiLoggingEnabled) {
	  const label = requestLabel(response.config)
	  const startedAt = response.config?.metadata?.startedAt
	  const durationMs = typeof startedAt === 'number' ? Math.round(nowMs() - startedAt) : -1

	  if (apiLoggingVerbose) {
		console.info('[api:res]', label, {
		  status: response.status,
		  durationMs,
		  data: scrub(response.data)
		})
	  } else {
		console.info('[api:res]', `${label} -> ${response.status} (${durationMs}ms)`)
	  }
	}

	return response
  },
  (error) => {
	if (apiLoggingEnabled) {
	  const config = error?.config ?? {}
	  const label = requestLabel(config)
	  const startedAt = config?.metadata?.startedAt
	  const durationMs = typeof startedAt === 'number' ? Math.round(nowMs() - startedAt) : -1
	  const status = error?.response?.status ?? 'NETWORK_ERROR'

	  console.error('[api:err]', `${label} -> ${status} (${durationMs}ms)`, {
		data: scrub(error?.response?.data),
		message: error?.message
	  })
	}

	return Promise.reject(error)
  }
)

export const getInstances  = ()        => api.get('/instances').then(r => r.data)
export const getStats      = ()        => api.get('/instances/stats').then(r => r.data)
export const getInstance   = (id)      => api.get(`/instances/${id}`).then(r => r.data)
export const deployInstance = (data)   => api.post('/instances', data).then(r => r.data)
export const startInstance  = (id)     => api.post(`/instances/${id}/start`).then(r => r.data)
export const stopInstance   = (id)     => api.post(`/instances/${id}/stop`).then(r => r.data)
export const removeInstance  = (id)     => api.delete(`/instances/${id}`)
export const untrackInstance = (id)     => api.post(`/instances/${id}/untrack`)
export const reTrackInstance = (id)     => api.post(`/instances/${id}/retrack`).then(r => r.data)
export const getLogs        = (id, tail=100) => api.get(`/instances/${id}/logs?tail=${tail}`).then(r => r.data)
export const getConnectionString = (id) => api.get(`/instances/${id}/connection-string`).then(r => r.data)
export const syncStatuses        = ()             => api.post('/instances/sync')
export const getCatalog          = ()             => api.get('/catalog').then(r => r.data)
export const getCatalogVersions  = (dbType, refresh=false) => api.get(`/catalog/${dbType}/versions`, { params: { refresh } }).then(r => r.data)
export const getSystemInfo       = ()             => api.get('/system').then(r => r.data)
export const discoverContainers  = ()             => api.get('/instances/discover').then(r => r.data)
export const importContainer     = (data)         => api.post('/instances/import', data).then(r => r.data)
export const reImportInstance    = (id, data)     => api.put(`/instances/${id}/reimport`, data).then(r => r.data)
export const renameInstance      = (id, name)     => api.patch(`/instances/${id}`, { name }).then(r => r.data)
export const getPipeline         = (id)           => api.get(`/instances/${id}/pipeline`).then(r => r.data)
export const getSystemStats      = ()             => api.get('/system/stats').then(r => r.data)
export const getMetricsHistory   = ()             => api.get('/system/metrics/history').then(r => r.data)
export const getDeploymentActivity = ()           => api.get('/system/metrics/activity').then(r => r.data)
export const getContainerMetrics = (id)           => api.get(`/instances/${id}/container-metrics`).then(r => r.data)

export const checkImageStatus = (dbType, tag, refresh=false) =>
	api.get('/images/check', { params: { dbType, tag, refresh } }).then(r => r.data)

export const getImageTracking = () =>
	api.get('/images/tracking').then(r => r.data)

export const refreshImageTracking = (scope='all') =>
	api.post('/images/refresh', null, { params: { scope } }).then(r => r.data)

export const getImageSummary = () =>
	api.get('/images/summary').then(r => r.data)

export const getImageToolDetails = (dbType, refresh=false) =>
	api.get(`/images/tools/${dbType}`, { params: { refresh } }).then(r => r.data)

export const refreshImageTool = (dbType, scope='all') =>
	api.post(`/images/tools/${dbType}/refresh`, null, { params: { scope } }).then(r => r.data)
