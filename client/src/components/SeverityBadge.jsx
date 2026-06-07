const SEVERITY_STYLES = {
  critical: 'bg-red-600 text-white',
  high: 'bg-orange-500 text-white',
  medium: 'bg-yellow-500 text-black',
  low: 'bg-green-500 text-black',
  info: 'bg-blue-500 text-white',
}

export default function SeverityBadge({ severity }) {
  const s = (severity || 'info').toLowerCase()
  const style = SEVERITY_STYLES[s] || 'bg-gray-600 text-white'
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-semibold uppercase tracking-wide ${style}`}>
      {s}
    </span>
  )
}
