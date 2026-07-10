import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { getSystemInfo } from '../api/client'

export function SystemBanner() {
  const [info, setInfo] = useState(null)

  useEffect(() => { getSystemInfo().then(setInfo).catch(() => {}) }, [])

  if (!info) return null

  const ok = info.dockerAvailable

  return (
    <Link
      to="/system"
      title="Open System Health — live CPU, memory, temperature and GPU metrics"
      className={`group flex items-center gap-3 px-4 py-3 rounded-xl text-sm mb-6 border transition-colors cursor-pointer ${
        ok
          ? 'bg-green-500/[0.08] border-green-500/20 text-green-300 hover:bg-green-500/[0.15] hover:border-green-500/40'
          : 'bg-red-500/[0.08] border-red-500/20 text-red-300 hover:bg-red-500/[0.15] hover:border-red-500/40'
      }`}>
      <span className="text-lg shrink-0">{ok ? '🐳' : '⚠️'}</span>
      <div className="flex-1 min-w-0">
        {ok ? (
          <>
            Docker is running &nbsp;·&nbsp;{' '}
            <strong className="text-white">{info.osType}</strong> {info.osVersion} ({info.arch})
            &nbsp;·&nbsp; method: <strong className="text-white">{info.preferredDeployMethod}</strong>
          </>
        ) : (
          <>Docker is <strong className="text-red-200">not available</strong>. Please install and start Docker to deploy databases.</>
        )}
      </div>
      <span className="flex items-center gap-1 text-xs opacity-60 group-hover:opacity-100 transition-opacity shrink-0">
        System Health
        <ChevronRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
      </span>
    </Link>
  )
}
