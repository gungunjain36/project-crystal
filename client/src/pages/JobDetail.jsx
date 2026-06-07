import { useParams, useNavigate } from 'react-router-dom'
import { useScanJob } from '../hooks/useScanJob'
import SeverityBadge from '../components/SeverityBadge'
import IssueRow from '../components/IssueRow'
import LoadingSpinner from '../components/LoadingSpinner'
import EmptyState from '../components/EmptyState'

function SeveritySummaryCard({ label, count, bgClass, textClass, borderClass }) {
  return (
    <div className={`rounded-lg p-4 border ${bgClass} ${borderClass}`}>
      <p className={`text-xs uppercase tracking-wide font-medium ${textClass} opacity-70`}>{label}</p>
      <p className={`text-3xl font-bold mt-1 ${textClass}`}>{count}</p>
    </div>
  )
}

function countBySeverity(findings) {
  if (!Array.isArray(findings)) return { critical: 0, high: 0, medium: 0, low: 0 }
  return findings.reduce((acc, f) => {
    const s = (f.severity || 'low').toLowerCase()
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, { critical: 0, high: 0, medium: 0, low: 0 })
}

export default function JobDetail() {
  const { jobId } = useParams()
  const navigate = useNavigate()
  const { data, isLoading, isError, error } = useScanJob(jobId)

  const statusColor = {
    success: 'text-green-400 bg-green-900/30 border-green-700',
    completed: 'text-green-400 bg-green-900/30 border-green-700',
    failure: 'text-red-400 bg-red-900/30 border-red-700',
    failed: 'text-red-400 bg-red-900/30 border-red-700',
    scanning: 'text-yellow-400 bg-yellow-900/30 border-yellow-700',
    pending: 'text-gray-400 bg-gray-700/30 border-gray-600',
  }[data?.status] || 'text-gray-400 bg-gray-700/30 border-gray-600'

  const counts = countBySeverity(data?.findings)

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-gray-400 hover:text-gray-200 text-sm mb-6 transition-colors"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
        Back
      </button>

      {isLoading && (
        <div className="flex justify-center py-20">
          <LoadingSpinner size="lg" label="Loading scan results..." />
        </div>
      )}

      {isError && (
        <div className="bg-red-900/20 border border-red-700 rounded-lg p-4 text-red-400">
          <p className="font-semibold">Failed to load job</p>
          <p className="text-sm mt-1">{error?.message || 'Unknown error'}</p>
        </div>
      )}

      {data && (
        <>
          {/* Header */}
          <div className="bg-gray-800 rounded-lg p-5 border border-gray-700 mb-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h1 className="text-white font-bold text-xl">Scan Job</h1>
                <p className="font-mono text-blue-300 text-sm mt-1">{jobId}</p>
                {data.target && <p className="text-gray-400 text-sm mt-1">{data.target}</p>}
              </div>
              <span className={`px-3 py-1 rounded-full text-xs font-semibold uppercase border ${statusColor}`}>
                {data.status}
              </span>
            </div>
            <div className="flex flex-wrap gap-4 mt-4 text-sm text-gray-400">
              {data.createdAt && (
                <span>Started: <span className="text-gray-200">{new Date(data.createdAt).toLocaleString()}</span></span>
              )}
              {data.completedAt && (
                <span>Completed: <span className="text-gray-200">{new Date(data.completedAt).toLocaleString()}</span></span>
              )}
              {data.requestedBy && (
                <span>Requested by: <span className="text-gray-200">{data.requestedBy}</span></span>
              )}
            </div>
            {(data.status === 'scanning' || data.status === 'pending') && (
              <div className="mt-3 flex items-center gap-2">
                <LoadingSpinner size="sm" />
                <span className="text-yellow-400 text-sm">Scan in progress — auto-refreshing every 3s</span>
              </div>
            )}
          </div>

          {/* Severity summary */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
            <SeveritySummaryCard label="Critical" count={counts.critical} bgClass="bg-red-900/20" textClass="text-red-400" borderClass="border-red-800" />
            <SeveritySummaryCard label="High" count={counts.high} bgClass="bg-orange-900/20" textClass="text-orange-400" borderClass="border-orange-800" />
            <SeveritySummaryCard label="Medium" count={counts.medium} bgClass="bg-yellow-900/20" textClass="text-yellow-400" borderClass="border-yellow-800" />
            <SeveritySummaryCard label="Low" count={counts.low} bgClass="bg-green-900/20" textClass="text-green-400" borderClass="border-green-800" />
          </div>

          {/* Issues table */}
          <div className="bg-gray-800 rounded-lg border border-gray-700 overflow-hidden">
            <div className="px-5 py-3 border-b border-gray-700">
              <h2 className="text-gray-200 font-semibold">
                Findings <span className="text-gray-500 font-normal text-sm">({data.findings?.length || 0})</span>
              </h2>
            </div>
            {!data.findings?.length ? (
              <EmptyState title="No findings" description="This scan completed with no security issues detected." />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-900/50 border-b border-gray-700">
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide w-24">Severity</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide w-40">Type</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide w-64">Location</th>
                      <th className="px-4 py-3 text-left text-gray-400 font-medium text-xs uppercase tracking-wide">Description</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-700">
                    {data.findings.map((issue, i) => (
                      <IssueRow key={i} issue={issue} index={i} />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
