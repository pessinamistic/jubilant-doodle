const STATUS_STYLES = {
  RUNNING:   'bg-green-500/15 text-green-400 border border-green-500/25',
  STOPPED:   'bg-gray-500/15 text-gray-400 border border-gray-500/25',
  DEPLOYING: 'bg-blue-500/15 text-blue-300 border border-blue-400/25 animate-pulse',
  ERROR:     'bg-red-500/15 text-red-400 border border-red-500/25',
  REMOVING:  'bg-orange-500/15 text-orange-400 border border-orange-500/25 animate-pulse',
}

const STATUS_DOT = {
  RUNNING:   'bg-green-400',
  STOPPED:   'bg-gray-500',
  DEPLOYING: 'bg-blue-400 animate-ping',
  ERROR:     'bg-red-400',
  REMOVING:  'bg-orange-400',
}

export function StatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[status] ?? STATUS_STYLES.STOPPED}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${STATUS_DOT[status] ?? 'bg-gray-500'}`} />
      {status}
    </span>
  )
}
