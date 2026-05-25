import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export const getInstances  = ()        => api.get('/instances').then(r => r.data)
export const getInstance   = (id)      => api.get(`/instances/${id}`).then(r => r.data)
export const deployInstance = (data)   => api.post('/instances', data).then(r => r.data)
export const startInstance  = (id)     => api.post(`/instances/${id}/start`).then(r => r.data)
export const stopInstance   = (id)     => api.post(`/instances/${id}/stop`).then(r => r.data)
export const removeInstance = (id)     => api.delete(`/instances/${id}`)
export const getLogs        = (id, tail=100) => api.get(`/instances/${id}/logs?tail=${tail}`).then(r => r.data)
export const getConnectionString = (id) => api.get(`/instances/${id}/connection-string`).then(r => r.data)
export const syncStatuses        = ()             => api.post('/instances/sync')
export const getCatalog          = ()             => api.get('/catalog').then(r => r.data)
export const getSystemInfo       = ()             => api.get('/system').then(r => r.data)
export const discoverContainers  = ()             => api.get('/instances/discover').then(r => r.data)
export const importContainer     = (data)         => api.post('/instances/import', data).then(r => r.data)
export const renameInstance      = (id, name)     => api.patch(`/instances/${id}`, { name }).then(r => r.data)
