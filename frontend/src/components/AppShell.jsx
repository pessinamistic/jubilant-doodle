import { NavLink } from 'react-router-dom'
import { useState, useEffect } from 'react'
import {
  HomeIcon, CircleStackIcon, PlusIcon, ArrowPathIcon,
  Bars3Icon, XMarkIcon, ChevronLeftIcon, ChevronRightIcon,
} from '@heroicons/react/24/outline'
import { syncStatuses, getSystemInfo } from '../api/client'
import toast from 'react-hot-toast'

const SIDEBAR_EXPANDED = 224
const SIDEBAR_COLLAPSED = 56
const LS_KEY = 'sidebar-collapsed'

export function AppShell({ children, onDeploy, onRefresh }) {
  const [syncing, setSyncing]       = useState(false)
  const [sysInfo, setSysInfo]       = useState(null)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [collapsed, setCollapsed]   = useState(
    () => localStorage.getItem(LS_KEY) !== 'false'   // default: collapsed
  )

  useEffect(() => {
    getSystemInfo().then(setSysInfo).catch(() => {})
  }, [])

  const toggle = () =>
    setCollapsed(v => {
      const next = !v
      localStorage.setItem(LS_KEY, String(next))
      return next
    })

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

  const sidebarW = collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED

  return (
    <div className="min-h-screen bg-[#0f1117] text-gray-100">

      {/* ── Desktop sidebar ── */}
      <aside
        style={{ width: sidebarW }}
        className="hidden lg:flex flex-col border-r border-white/[0.06] bg-[#0b0e15] fixed inset-y-0 left-0 z-30 transition-[width] duration-200 ease-in-out"
        // NOTE: no overflow-hidden — tooltips must escape the sidebar boundary
      >
        {/* Inner wrapper: clips text during animation but NOT the tooltip layer */}
        <div className="flex flex-col h-full overflow-x-hidden">

          {/* Logo */}
          <div className={`flex items-center shrink-0 px-3 py-4 animate-fade-up ${collapsed ? 'justify-center' : 'justify-between'}`}>
            <NavLink to="/" className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-sm shadow-lg shadow-blue-500/30 select-none shrink-0 transition-transform duration-200 hover:scale-105">
                DB
              </div>
              {!collapsed && (
                <span className="font-semibold text-white text-sm tracking-tight truncate">
                  DB Deployer
                </span>
              )}
            </NavLink>
          </div>

          {/* Docker + actions */}
          <div className={`shrink-0 space-y-1 pb-3 animate-fade-up delay-100 ${collapsed ? 'px-2' : 'px-3'}`}>
            {sysInfo && (collapsed
              ? <DockerDot info={sysInfo} />
              : <DockerPill info={sysInfo} />
            )}

            <SidebarBtn
              icon={<ArrowPathIcon className={`w-4 h-4 shrink-0 ${syncing ? 'animate-spin' : ''}`} />}
              label="Sync statuses"
              collapsed={collapsed}
              onClick={handleSync}
              disabled={syncing}
            />

            <SidebarBtn
              icon={<PlusIcon className="w-4 h-4 shrink-0" />}
              label="Deploy DB"
              collapsed={collapsed}
              onClick={() => onDeploy?.()}
              primary
            />
          </div>

          {/* Divider */}
          <div className="mx-3 border-t border-white/[0.05] mb-2 shrink-0 animate-fade-in delay-150" />

          {/* Nav */}
          <nav className={`flex-1 space-y-0.5 ${collapsed ? 'px-2' : 'px-2'}`}>
            {!collapsed && (
              <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-600 px-3 py-2 animate-fade-in delay-150">
                Navigation
              </p>
            )}
            <SideNavItem
              to="/" icon={<HomeIcon className="w-4 h-4" />}
              label="Home" end collapsed={collapsed}
              className="animate-fade-up delay-200"
            />
            <SideNavItem
              to="/instances" icon={<CircleStackIcon className="w-4 h-4" />}
              label="Instances" collapsed={collapsed}
              className="animate-fade-up delay-300"
            />
          </nav>

          {/* Collapse toggle */}
          <div className={`shrink-0 border-t border-white/[0.05] py-3 animate-fade-up delay-300 ${collapsed ? 'px-2' : 'px-3'}`}>
            <SidebarBtn
              icon={collapsed
                ? <ChevronRightIcon className="w-4 h-4 shrink-0" />
                : <ChevronLeftIcon className="w-4 h-4 shrink-0" />
              }
              label={collapsed ? 'Expand' : 'Collapse'}
              collapsed={collapsed}
              onClick={toggle}
              muted
            />
          </div>
        </div>
      </aside>

      {/* ── Mobile backdrop + drawer ── */}
      {mobileOpen && (
        <>
          <div
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40 lg:hidden animate-fade-in"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="fixed inset-y-0 left-0 w-56 bg-[#0b0e15] border-r border-white/[0.06] z-50 lg:hidden flex flex-col animate-slide-right">
            <div className="flex flex-col h-full overflow-x-hidden">
              <div className="flex items-center justify-between shrink-0 px-3 py-4 animate-fade-up">
                <NavLink to="/" onClick={() => setMobileOpen(false)} className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-sm shadow-lg shadow-blue-500/30 select-none shrink-0">
                    DB
                  </div>
                  <span className="font-semibold text-white text-sm tracking-tight">DB Deployer</span>
                </NavLink>
                <button onClick={() => setMobileOpen(false)} className="p-1 rounded text-gray-500 hover:text-white hover:bg-white/[0.06] transition-colors">
                  <XMarkIcon className="w-5 h-5" />
                </button>
              </div>
              <div className="shrink-0 space-y-1 pb-3 px-3 animate-fade-up delay-50">
                {sysInfo && <DockerPill info={sysInfo} />}
                <SidebarBtn icon={<ArrowPathIcon className={`w-4 h-4 shrink-0 ${syncing ? 'animate-spin' : ''}`} />} label="Sync statuses" collapsed={false} onClick={handleSync} disabled={syncing} />
                <SidebarBtn icon={<PlusIcon className="w-4 h-4 shrink-0" />} label="Deploy DB" collapsed={false} onClick={() => { onDeploy?.(); setMobileOpen(false) }} primary />
              </div>
              <div className="mx-3 border-t border-white/[0.05] mb-2 shrink-0" />
              <nav className="flex-1 space-y-0.5 px-2 animate-fade-up delay-100">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-600 px-3 py-2">Navigation</p>
                <SideNavItem to="/" icon={<HomeIcon className="w-4 h-4" />} label="Home" end collapsed={false} onNavigate={() => setMobileOpen(false)} className="animate-fade-up delay-150" />
                <SideNavItem to="/instances" icon={<CircleStackIcon className="w-4 h-4" />} label="Instances" collapsed={false} onNavigate={() => setMobileOpen(false)} className="animate-fade-up delay-200" />
              </nav>
            </div>
          </aside>
        </>
      )}

      {/* ── Mobile top bar ── */}
      <header className="lg:hidden fixed top-0 left-0 right-0 bg-[#0f1117]/90 backdrop-blur-md border-b border-white/[0.06] z-30 h-14 flex items-center px-4 gap-3 animate-slide-down">
        <button onClick={() => setMobileOpen(true)} className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-white/[0.06] transition-colors">
          <Bars3Icon className="w-5 h-5" />
        </button>
        <NavLink to="/" className="flex items-center gap-2 flex-1">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold text-xs shadow-md shadow-blue-500/30 select-none">DB</div>
          <span className="font-semibold text-white text-sm tracking-tight">DB Deployer</span>
        </NavLink>
        <button onClick={() => onDeploy?.()} className="btn-primary flex items-center gap-1.5 text-xs px-3 py-1.5">
          <PlusIcon className="w-3.5 h-3.5" />Deploy
        </button>
      </header>

      {/* ── Desktop main ── */}
      <main
        style={{ marginLeft: sidebarW }}
        className="hidden lg:block min-h-screen transition-[margin-left] duration-200 ease-in-out"
      >
        <div className="px-8 py-8">{children}</div>
      </main>

      {/* ── Mobile main ── */}
      <main className="lg:hidden min-h-screen pt-14">
        <div className="px-5 py-6">{children}</div>
      </main>
    </div>
  )
}

// ── Shared sidebar button (works expanded + collapsed) ───────────────────────
function SidebarBtn({ icon, label, collapsed, onClick, disabled, primary, muted }) {
  const base = `group relative flex items-center rounded-lg text-sm transition-all w-full disabled:opacity-50`
  const colors = primary
    ? 'bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-500/20 hover:shadow-blue-500/30 hover:-translate-y-px active:translate-y-0'
    : muted
      ? 'text-gray-500 hover:text-white hover:bg-white/[0.06]'
      : 'text-gray-400 hover:text-white hover:bg-white/[0.06]'

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`${base} ${colors} ${collapsed ? 'justify-center py-2.5 px-0' : 'gap-2 px-3 py-2'}`}
    >
      {icon}
      {collapsed ? <Tooltip>{label}</Tooltip> : <span>{label}</span>}
    </button>
  )
}

// ── Nav link ─────────────────────────────────────────────────────────────────
function SideNavItem({ to, icon, label, end, collapsed, onNavigate, className = '' }) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onNavigate}
      className={({ isActive }) =>
        `group relative flex items-center rounded-lg text-sm transition-all w-full ${className}
         ${collapsed ? 'justify-center py-2.5 px-0' : 'gap-3 px-3 py-2'}
         ${isActive
           ? 'bg-blue-600/15 text-white font-medium border border-blue-500/20 shadow-sm shadow-blue-500/10'
           : 'text-gray-400 hover:text-white hover:bg-white/[0.05] border border-transparent'
         }`
      }
    >
      {icon}
      {collapsed ? <Tooltip>{label}</Tooltip> : label}
    </NavLink>
  )
}

// ── Tooltip — renders outside overflow-x-hidden via fixed positioning ────────
function Tooltip({ children }) {
  return (
    <span
      className="
        pointer-events-none fixed ml-1
        whitespace-nowrap rounded-md bg-gray-800 border border-white/[0.08]
        px-2.5 py-1 text-xs text-white shadow-lg
        opacity-0 group-hover:opacity-100
        -translate-y-1 group-hover:translate-y-0
        transition-all duration-150 z-[100]
      "
      style={{ left: SIDEBAR_COLLAPSED + 8 }}
    >
      {children}
    </span>
  )
}

// ── Docker pill (expanded) ────────────────────────────────────────────────────
function DockerPill({ info }) {
  const ok = info.dockerAvailable
  const os = info.osType === 'MACOS' ? 'macOS' : (info.osType ?? 'Docker')
  if (!ok) return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium bg-red-500/10 border border-red-500/20 text-red-400">
      <span className="w-1.5 h-1.5 rounded-full bg-red-400 shrink-0" />Docker offline
    </div>
  )
  return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium bg-green-500/[0.07] border border-green-500/15 text-green-400 cursor-default transition-all duration-200 hover:bg-green-500/[0.12]">
      <span className="w-1.5 h-1.5 rounded-full bg-green-400 shrink-0 animate-pulse" />
      🐳 {os} · {info.arch}
    </div>
  )
}

// ── Docker dot (collapsed) ────────────────────────────────────────────────────
function DockerDot({ info }) {
  const ok = info.dockerAvailable
  const os = info.osType === 'MACOS' ? 'macOS' : (info.osType ?? 'Docker')
  return (
    <div className={`group relative flex items-center justify-center py-2.5 w-full cursor-default rounded-lg ${ok ? 'text-green-400' : 'text-red-400'}`}>
      <span className={`w-2 h-2 rounded-full ${ok ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
      <Tooltip>{ok ? `🐳 Docker · ${os} · ${info.arch}` : 'Docker offline'}</Tooltip>
    </div>
  )
}
