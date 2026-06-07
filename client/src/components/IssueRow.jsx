import SeverityBadge from './SeverityBadge'

export default function IssueRow({ issue, index }) {
  return (
    <tr className={index % 2 === 0 ? 'bg-gray-800' : 'bg-gray-750'}>
      <td className="px-4 py-3 whitespace-nowrap">
        <SeverityBadge severity={issue.severity} />
      </td>
      <td className="px-4 py-3 text-gray-200 text-sm font-medium">
        {issue.type || issue.rule_id || issue.check_id || '—'}
      </td>
      <td className="px-4 py-3 font-mono text-xs text-blue-300 max-w-xs truncate" title={issue.location || issue.file}>
        {issue.location || issue.file || '—'}
        {issue.line ? `:${issue.line}` : ''}
      </td>
      <td className="px-4 py-3 text-gray-300 text-sm max-w-md">
        <span className="line-clamp-2">{issue.description || issue.message || issue.title || '—'}</span>
      </td>
    </tr>
  )
}
