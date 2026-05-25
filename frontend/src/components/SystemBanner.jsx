import { useEffect, useState } from 'react'
import { getSystemInfo } from '../api/client'

export function SystemBanner() {
  const [info, setInfo] = useState(null)

  useEffect(() => { getSystemInfo().then(setInfo).catch(() => {}) }, [])

  if (!info) return null

  const ok = info.dockerAvailable

  return (
    <div className={`flex items-center gap-3 px-4 py-3 rounded-xl text-sm mb-6 border ${
      ok
        ? 'bg-green-500/[0.08] border-green-500/20 text-green-300'
        : 'bg-red-500/[0.08] border-red-500/20 text-red-300'
    }`}>
      <span className="text-lg shrink-0">{ok ? '🐳' : '⚠️'}</span>
      <div>
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
    </div>
  )
}
