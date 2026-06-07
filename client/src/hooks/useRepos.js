import { useState, useCallback } from 'react'

const STORAGE_KEY = 'crystal_repos'

function loadRepos() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveRepos(repos) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(repos))
}

function generateId() {
  return crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2)
}

export function useRepos() {
  const [repos, setRepos] = useState(loadRepos)

  const addRepo = useCallback((url) => {
    setRepos(prev => {
      const exists = prev.some(r => r.url === url)
      if (exists) return prev
      const next = [...prev, { id: generateId(), url, addedAt: new Date().toISOString(), lastJobId: null, lastStatus: null }]
      saveRepos(next)
      return next
    })
  }, [])

  const removeRepo = useCallback((id) => {
    setRepos(prev => {
      const next = prev.filter(r => r.id !== id)
      saveRepos(next)
      return next
    })
  }, [])

  const updateRepoJob = useCallback((id, jobId) => {
    setRepos(prev => {
      const next = prev.map(r => r.id === id ? { ...r, lastJobId: jobId, lastStatus: 'scanning' } : r)
      saveRepos(next)
      return next
    })
  }, [])

  const updateRepoStatus = useCallback((id, status, result) => {
    setRepos(prev => {
      const next = prev.map(r => r.id === id ? { ...r, lastStatus: status, lastResult: result } : r)
      saveRepos(next)
      return next
    })
  }, [])

  return { repos, addRepo, removeRepo, updateRepoJob, updateRepoStatus }
}
