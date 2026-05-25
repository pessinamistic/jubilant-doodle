import { NavLink } from 'react-router-dom'
import { useState, useEffect } from 'react'
import {
  HomeIcon, CircleStackIcon, PlusIcon, ArrowPathIcon,
} from '@heroicons/react/24/outline'
import { syncStatuses, getSystemInfo } from '../api/client'
import toast from 'react-hot-toast'

export function AppShell({ children, onDeploy, onRefresh }) {
  const [syncing, setSyncing]   = useState(false)
  const [sysInfo, setSysInfo]   = useState(null)

  // Fetch system info once on mount (Docker availability + OS details)
  useEffect(() => {
    getSystemInfo().then(setSysInfo).catch(() => {})
  }, [])

  const handleSync = async () => {
    setSyncing(true)
    try {
      await syncStatuses()
      onRefresh?.()
      toast.success('Statuses synced')
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#0f1117] flex flex-col text-gray-100">
      {/* ── Top nav ── */}
      <header className="bg-[#0f1117]/90 backdrop-blur-md border-b border-white/[0.06] sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-6 h-14 flex items-center justify-between gap-4">

          {/* Logo */}
          <NavLink to="/" className="flex items-center gap-2.5 shrink-0">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-sm shadow-lg shadow-blue-500/30 select-none">
              DB
            </div>
            <span className="font-semibold text-white text-sm tracking-tight">DB Deployer</span>
          </NavLink>

          {/* Nav links */}
          <nav className="flex items-center gap-1 flex-1">
            <NavItem to="/" icon={<HomeIcon className="w-4 h-4" />} label="Home" end />
            <NavItem to="/instances" icon={<CircleStackIcon className="w-4 h-4" />} label="Instances" />
          </nav>

          {/* Docker status pill */}
          {sysInfo && (
            <DockerPill info={sysInfo} />
          )}

          {/* Actions */}
          <div className="flex items-center gap-2 shrink-0">
            <button onClick={handleSync} disabled={syncing} className="btn-ghost flex items-center gap-1.5">
              <ArrowPathIcon className={`w-4 h-4 ${syncing ? 'animate-spin' : ''}`} />
              Sync
            </button>
            <button onClick={onDeploy} className="btn-primary flex items-center gap-1.5">
              <PlusIcon className="w-4 h-4" />
              Deploy
            </button>
          </div>
        </div>
      </header>

      {/* ── Page content ── */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8">
        {children}
      </main>
    </div>
  )
}

function DockerPill({ info }) {
  const ok = info.dockerAvailable

  if (!ok) {
    return (
      <div className="hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium bg-red-500/10 border border-red-500/20 text-red-400 shrink-0">
        <span className="w-1.5 h-1.5 rounded-full bg-red-400 shrink-0" />
        Docker offline
      </div>
    )
  }

  // e.g. "MACOS" → "macOS", "LINUX" → "Linux"
  const os = info.osType === 'MACOS' ? 'macOS' : (info.osType ?? 'Docker')

  return (
    <div
      title={`Docker is running · ${info.osType} ${info.osVersion} (${info.arch}) · method: ${info.preferredDeployMethod}`}
      className="hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium bg-green-500/[0.08] border border-green-500/15 text-green-400 shrink-0 cursor-default"
    >
      <span className="w-1.5 h-1.5 rounded-full bg-green-400 shrink-0 animate-pulse" />
      <span>🐳</span>
      <span>{os} · {info.arch}</span>
    </div>
  )
}

function NavItem({ to, icon, label, end }) {
  return (
    <NavLink to={to} end={end}
      className={({ isActive }) =>
        `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-colors ${
          isActive
            ? 'bg-white/[0.08] text-white font-medium'
            : 'text-gray-400 hover:text-white hover:bg-white/[0.04]'
        }`
      }>
      {icon}
      {label}
    </NavLink>
  )
}
