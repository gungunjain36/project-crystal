import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getAllResults } from '../api/scans'
import LoadingSpinner from '../components/LoadingSpinner'
import SeverityBadge from '../components/SeverityBadge'
import EmptyState from '../components/EmptyState'

function countBySeverity(findings) {
  if (!Array.isArray(findings)) return { critical: 0, high: 0, medium: 0, low: 0 }
  return findings.reduce((acc, f) => {
    const s = (f.severity || 'low').toLowerCase()
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, { critical: 0, high: 0, medium: 0, low: 0 })
}

function StatusBadge({ status }) {
  const s = (status || '').toLowerCase()
  const styles = {
    success: 'text-green-400 bg-green-900/30 border-green-700',
    completed: 'text-green-400 bg-green-900/30 border-green-700',
    failure: 'text-red-400 bg-red-900/30 border-red-700',
    failed: 'text-red-400 bg-red-900/30 border-red-700',
    scanning: 'text-yellow-400 bg-yellow-900/30 border-yellow-700',
    pending: 'text-gray-400 bg-gray-700/30 border-gray-600',
  }
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-semibold uppercase border ${styles[s] || 'text-gray-400 bg-gray-700/30 border-gray-600'}`}>
      {s || '—'}
    </span>
  )
}

export default function ScanResults() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const PAGE_SIZE = 20

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['results', page],
    queryFn: () => getAllResults(page, PAGE_SIZE),
    retry: 1,
    staleTime: 10000,
  })

  const results = Array.isArray(data) ? data : (data?.content || data?.results || [])
  const hasMore = results.length === PAGE_SIZE

  const filtered = search.trim()
    ? results.filter(r =>
        (r.jobId || r.job_id || '').toLowerCase().includes(search.toLowerCase()) ||
        (r.target || r.repository || '').toLowerCase().includes(search.toLowerCase())
      )
    : results

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Scan Results</h1>
        <p className="text-gray-400 text-sm mt-1">All past and ongoing security scans</p>
      </div>

      {/* Search */}
      <div className="mb-4">
        <input
          type="text"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search by job ID or repository URL..."
          className="w-full max-w-md bg-gray-800 border border-gray-600 rounded-md px-3 py-2 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:border-blue-500"
        />
      </div>

      {isLoading && (
        <div className="flex justify-center py-20">
          <LoadingSpinner size="lg" label="Loading results..." />
        </div>
      )}

      {isError && (
        <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
          <p className="font-semibold">Failed to load results</p>
          <p className="text-sm mt-1">{error?.message || 'Unknown error'}</p>
        </div>
      )}

      {!isLoading && !isError && (
        <>
          {filtered.length === 0 ? (
            <EmptyState
              title="No scan results"
              description={search ? "No results match your search." : "No scans have been run yet. Go to the Dashboard to add repositories and trigger scans."}
            />
          ) : (
            <div className="bg-gray-800 rounded-lg border border-gray-700 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-900/50 border-b border-gray-700">
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Job ID</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Repository</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Status</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Critical</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">High</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Medium</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Low</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Completed</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-700">
                    {filtered.map((result, i) => {
                      const jobId = result.jobId || result.job_id || ''
                      const counts = countBySeverity(result.findings)
                      return (
                        <tr
                          key={jobId || i}
                          className="hover:bg-gray-700/50 cursor-pointer transition-colors"
                          onClick={() => jobId && navigate(`/jobs/${jobId}`)}
                        >
                          <td className="px-4 py-3">
                            <span className="font-mono text-xs text-blue-300">{jobId ? `${jobId.slice(0, 8)}...` : '—'}</span>
                          </td>
                          <td className="px-4 py-3 font-mono text-xs text-gray-300 max-w-xs truncate" title={result.target || result.repository}>
                            {result.target || result.repository || '—'}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={result.status} />
                          </td>
                          <td className="px-4 py-3">
                            <span className={`font-semibold ${counts.critical > 0 ? 'text-red-400' : 'text-gray-500'}`}>{counts.critical}</span>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`font-semibold ${counts.high > 0 ? 'text-orange-400' : 'text-gray-500'}`}>{counts.high}</span>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`font-semibold ${counts.medium > 0 ? 'text-yellow-400' : 'text-gray-500'}`}>{counts.medium}</span>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`font-semibold ${counts.low > 0 ? 'text-green-400' : 'text-gray-500'}`}>{counts.low}</span>
                          </td>
                          <td className="px-4 py-3 text-gray-400 text-xs">
                            {result.completedAt ? new Date(result.completedAt).toLocaleString() : '—'}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Pagination */}
          {(page > 0 || hasMore) && (
            <div className="flex items-center justify-between mt-4">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-4 py-2 bg-gray-800 border border-gray-600 rounded-md text-sm text-gray-300 hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <span className="text-gray-400 text-sm">Page {page + 1}</span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={!hasMore}
                className="px-4 py-2 bg-gray-800 border border-gray-600 rounded-md text-sm text-gray-300 hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
