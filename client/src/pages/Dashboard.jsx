import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getSeverityStats } from '../api/scans'
import { useRepos } from '../hooks/useRepos'
import RepoCard from '../components/RepoCard'
import EmptyState from '../components/EmptyState'
import LoadingSpinner from '../components/LoadingSpinner'

function StatCard({ label, value, color }) {
  return (
    <div className="bg-gray-800 rounded-lg p-4 border border-gray-700">
      <p className="text-gray-400 text-xs uppercase tracking-wide">{label}</p>
      <p className={`text-3xl font-bold mt-1 ${color}`}>{value ?? '—'}</p>
    </div>
  )
}

export default function Dashboard() {
  const [urlInput, setUrlInput] = useState('')
  const [inputError, setInputError] = useState('')
  const { repos, addRepo, removeRepo, updateRepoJob } = useRepos()

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['stats'],
    queryFn: getSeverityStats,
    retry: false,
    staleTime: 30000,
  })

  const handleAddRepo = (e) => {
    e.preventDefault()
    const url = urlInput.trim()
    if (!url) return setInputError('Please enter a GitHub URL')
    if (!url.startsWith('https://github.com/') && !url.startsWith('http://github.com/')) {
      return setInputError('URL must start with https://github.com/')
    }
    addRepo(url)
    setUrlInput('')
    setInputError('')
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <p className="text-gray-400 text-sm mt-1">Manage repositories and run security scans</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {statsLoading ? (
          <div className="col-span-4 flex justify-center py-4"><LoadingSpinner size="sm" label="Loading stats..." /></div>
        ) : (
          <>
            <StatCard label="Total Scans" value={stats?.total} color="text-white" />
            <StatCard label="Critical" value={stats?.critical} color="text-red-400" />
            <StatCard label="High" value={stats?.high} color="text-orange-400" />
            <StatCard label="Medium" value={stats?.medium} color="text-yellow-400" />
          </>
        )}
      </div>

      {/* Add Repo */}
      <div className="bg-gray-800 rounded-lg p-5 border border-gray-700 mb-8">
        <h2 className="text-gray-200 font-semibold mb-3">Add Repository</h2>
        <form onSubmit={handleAddRepo} className="flex gap-3">
          <div className="flex-1">
            <input
              type="text"
              value={urlInput}
              onChange={e => { setUrlInput(e.target.value); setInputError('') }}
              placeholder="https://github.com/owner/repo"
              className="w-full bg-gray-900 border border-gray-600 rounded-md px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500 font-mono"
            />
            {inputError && <p className="text-red-400 text-xs mt-1">{inputError}</p>}
          </div>
          <button
            type="submit"
            className="px-5 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-md transition-colors whitespace-nowrap"
          >
            Add Repository
          </button>
        </form>
      </div>

      {/* Repo Grid */}
      <div>
        <h2 className="text-gray-200 font-semibold mb-4">
          Repositories <span className="text-gray-500 font-normal text-sm">({repos.length})</span>
        </h2>
        {repos.length === 0 ? (
          <EmptyState
            title="No repositories configured"
            description="Add a GitHub repository URL above to start scanning for security vulnerabilities."
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {repos.map(repo => (
              <RepoCard
                key={repo.id}
                repo={repo}
                onJobStarted={updateRepoJob}
                onRemove={removeRepo}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
