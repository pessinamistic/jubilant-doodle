import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

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
