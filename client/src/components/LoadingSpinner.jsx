export default function LoadingSpinner({ size = 'md', label }) {
  const sizes = { sm: 'h-4 w-4', md: 'h-8 w-8', lg: 'h-12 w-12' }
  return (
    <div className="flex items-center justify-center gap-2">
      <div className={`${sizes[size]} animate-spin rounded-full border-2 border-gray-600 border-t-blue-400`} />
      {label && <span className="text-gray-400 text-sm">{label}</span>}
    </div>
  )
}
