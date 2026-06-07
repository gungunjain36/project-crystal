import api from './client'

export const triggerScan = (target, requestedBy = 'crystal-client') =>
  api.post('/api/v1/scans', { targetType: 'github_url', target, requestedBy })
    .then(r => r.data)

export const getResult = (jobId) =>
  api.get(`/api/v1/results/${jobId}`).then(r => r.data)

export const getAllResults = (page = 0, size = 20) =>
  api.get('/api/v1/results', { params: { page, size } }).then(r => r.data)

export const getSeverityStats = () =>
  api.get('/api/v1/results/stats/severity').then(r => r.data)
