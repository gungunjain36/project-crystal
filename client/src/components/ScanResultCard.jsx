import { useNavigate } from 'react-router-dom'
import SeverityBadge from './SeverityBadge'

function countBySeverity(findings) {
  if (!Array.isArray(findings)) return { critical: 0, high: 0, medium: 0, low: 0 }
  return findings.reduce((acc, f) => {
    const s = (f.severity || 'low').toLowerCase()
    acc[s] = (acc[s] || 0) + 1
    return acc
  }, { critical: 0, high: 0, medium: 0, low: 0 })
}

export default function ScanResultCard({ result }) {
  const navigate = useNavigate()
  const counts = countBySeverity(result.findings)
  const total = Object.values(counts).reduce((a, b) => a + b, 0)

  const statusColor = {
    success: 'text-green-400',
    completed: 'text-green-400',
    failure: 'text-red-400',
    failed: 'text-red-400',
    scanning: 'text-yellow-400',
    pending: 'text-gray-400',
  }[result.status] || 'text-gray-400'

  return (
    <div
      className="bg-gray-800 rounded-lg p-4 cursor-pointer hover:bg-gray-750 transition-colors border border-gray-700 hover:border-gray-600"
      onClick={() => navigate(`/jobs/${result.jobId || result.job_id}`)}
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex-1 min-w-0">
          <p className="font-mono text-xs text-gray-400 truncate">{result.jobId || result.job_id}</p>
          <p className="text-gray-200 text-sm mt-0.5 truncate">{result.target || result.repository || '—'}</p>
        </div>
        <span className={`text-xs font-semibold uppercase ml-2 ${statusColor}`}>{result.status}</span>
      </div>

      {total > 0 && (
        <div className="flex flex-wrap gap-1.5 mt-2">
          {counts.critical > 0 && <span className="flex items-center gap-1 px-2 py-0.5 bg-red-900/50 border border-red-700 rounded text-red-300 text-xs"><span className="font-bold">{counts.critical}</span> critical</span>}
          {counts.high > 0 && <span className="flex items-center gap-1 px-2 py-0.5 bg-orange-900/50 border border-orange-700 rounded text-orange-300 text-xs"><span className="font-bold">{counts.high}</span> high</span>}
          {counts.medium > 0 && <span className="flex items-center gap-1 px-2 py-0.5 bg-yellow-900/50 border border-yellow-700 rounded text-yellow-300 text-xs"><span className="font-bold">{counts.medium}</span> medium</span>}
          {counts.low > 0 && <span className="flex items-center gap-1 px-2 py-0.5 bg-green-900/50 border border-green-700 rounded text-green-300 text-xs"><span className="font-bold">{counts.low}</span> low</span>}
        </div>
      )}
      {total === 0 && result.status !== 'scanning' && (
        <p className="text-gray-500 text-xs mt-2">No issues found</p>
      )}

      {result.completedAt && (
        <p className="text-gray-500 text-xs mt-3">{new Date(result.completedAt).toLocaleString()}</p>
      )}
    </div>
  )
}
