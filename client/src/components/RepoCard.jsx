import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { triggerScan } from '../api/scans'
import { useScanJob } from '../hooks/useScanJob'
import LoadingSpinner from './LoadingSpinner'

function countBySeverity(findings) {
  if (!Array.isArray(findings)) return {}
  return findings.reduce((acc, f) => {
    const s = (f.severity || 'low').toLowerCase()
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, {})
}

export default function RepoCard({ repo, onJobStarted, onRemove }) {
  const navigate = useNavigate()
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState(null)

  const { data: jobData, isLoading: jobLoading } = useScanJob(repo.lastJobId)

  const currentStatus = jobData?.status || repo.lastStatus
  const findings = jobData?.findings || repo.lastResult?.findings || []
  const counts = countBySeverity(findings)
  const isDone = currentStatus === 'success' || currentStatus === 'completed' || currentStatus === 'failure' || currentStatus === 'failed'
  const isPolling = repo.lastJobId && !isDone && jobLoading

  const handleScan = async () => {
    setScanning(true)
    setError(null)
    try {
      const result = await triggerScan(repo.url)
      const jobId = result.jobId || result.job_id
      onJobStarted(repo.id, jobId)
    } catch (e) {
      setError(e.response?.data?.message || e.message || 'Scan failed to start')
    } finally {
      setScanning(false)
    }
  }

  const statusColor = {
    success: 'text-green-400',
    completed: 'text-green-400',
    failure: 'text-red-400',
    failed: 'text-red-400',
    scanning: 'text-yellow-400',
    pending: 'text-gray-400',
  }[currentStatus] || 'text-gray-400'

  return (
    <div className="bg-gray-800 rounded-lg p-5 border border-gray-700 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="font-mono text-sm text-blue-300 truncate" title={repo.url}>{repo.url}</p>
          <p className="text-gray-500 text-xs mt-0.5">Added {new Date(repo.addedAt).toLocaleDateString()}</p>
        </div>
        <button
          onClick={() => onRemove(repo.id)}
          className="text-gray-600 hover:text-red-400 transition-colors flex-shrink-0 p-1 rounded"
          title="Remove repository"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {currentStatus && (
        <div className="flex items-center gap-2">
          {(isPolling || currentStatus === 'scanning') && <LoadingSpinner size="sm" />}
          <span className={`text-xs font-semibold uppercase ${statusColor}`}>{currentStatus}</span>
          {repo.lastJobId && isDone && (
            <button
              className="text-blue-400 hover:text-blue-300 text-xs underline ml-auto"
              onClick={() => navigate(`/jobs/${repo.lastJobId}`)}
            >
              View details
            </button>
          )}
        </div>
      )}

      {Object.keys(counts).length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {counts.critical > 0 && <span className="px-2 py-0.5 bg-red-900/50 border border-red-700 rounded text-red-300 text-xs font-semibold">{counts.critical} critical</span>}
          {counts.high > 0 && <span className="px-2 py-0.5 bg-orange-900/50 border border-orange-700 rounded text-orange-300 text-xs font-semibold">{counts.high} high</span>}
          {counts.medium > 0 && <span className="px-2 py-0.5 bg-yellow-900/50 border border-yellow-700 rounded text-yellow-300 text-xs font-semibold">{counts.medium} medium</span>}
          {counts.low > 0 && <span className="px-2 py-0.5 bg-green-900/50 border border-green-700 rounded text-green-300 text-xs font-semibold">{counts.low} low</span>}
        </div>
      )}

      {error && <p className="text-red-400 text-xs">{error}</p>}

      <button
        onClick={handleScan}
        disabled={scanning || isPolling || currentStatus === 'scanning'}
        className="mt-auto w-full py-2 px-4 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-not-allowed text-white text-sm font-medium rounded-md transition-colors"
      >
        {scanning ? 'Starting...' : (isPolling || currentStatus === 'scanning') ? 'Scanning...' : 'Run Scan'}
      </button>
    </div>
  )
}
