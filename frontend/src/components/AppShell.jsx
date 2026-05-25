import { NavLink } from 'react-router-dom'
import { useState, useEffect } from 'react'
import {
  ChevronLeft,
  ChevronRight,
  Database,
  HardDrive,
  House,
  Laptop,
  Menu,
  Moon,
  Plus,
  RefreshCw,
  Sun,
  X,
} from 'lucide-react'
import { syncStatuses, getSystemInfo } from '../api/client'
import { useTheme } from '../theme/useTheme'
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
  const { mode, effectiveTheme, cycleMode } = useTheme()

  const ThemeIcon = mode === 'system' ? Laptop : mode === 'light' ? Sun : Moon
  const themeLabel = mode === 'system'
    ? `Theme: Auto (${effectiveTheme === 'dark' ? 'Night' : 'Day'})`
    : `Theme: ${mode === 'dark' ? 'Night' : 'Day'}`

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
    <div className="shell-root min-h-screen text-(--text-primary)">

      {/* ── Desktop sidebar ── */}
      <aside
        style={{ width: sidebarW }}
        className="shell-sidebar hidden lg:flex flex-col fixed inset-y-0 left-0 z-30 transition-[width] duration-200 ease-in-out"
        // NOTE: no overflow-hidden — tooltips must escape the sidebar boundary
      >
        {/* Inner wrapper: clips text during animation but NOT the tooltip layer */}
        <div className="flex flex-col h-full overflow-x-hidden">

          {/* Logo */}
          <div className={`flex items-center shrink-0 px-3 py-4 animate-fade-up ${collapsed ? 'justify-center' : 'justify-between'}`}>
            <NavLink to="/" className="flex items-center gap-2.5 min-w-0">
              <div className="w-8 h-8 flex items-center justify-center font-bold text-xs brutal-chip select-none shrink-0 transition-transform duration-200 hover:scale-105">
                PW
              </div>
              {!collapsed && (
                <span className="font-semibold text-sm tracking-tight truncate text-(--text-primary)">
                  Port Wrangler
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
              icon={<RefreshCw className={`w-4 h-4 shrink-0 ${syncing ? 'animate-spin' : ''}`} />}
              label="Sync statuses"
              collapsed={collapsed}
              onClick={handleSync}
              disabled={syncing}
            />

            <SidebarBtn
              icon={<ThemeIcon className="w-4 h-4 shrink-0" />}
              label={themeLabel}
              collapsed={collapsed}
              onClick={cycleMode}
            />

            <SidebarBtn
              icon={<Plus className="w-4 h-4 shrink-0" />}
              label="Deploy DB"
              collapsed={collapsed}
              onClick={() => onDeploy?.()}
              primary
            />
          </div>

          {/* Divider */}
          <div className="mx-3 border-t-2 border-(--border-strong) mb-2 shrink-0 animate-fade-in delay-150" />

          {/* Nav */}
          <nav className={`flex-1 space-y-0.5 ${collapsed ? 'px-2' : 'px-2'}`}>
            {!collapsed && (
              <p className="text-[10px] font-semibold uppercase tracking-widest text-(--text-muted) px-3 py-2 animate-fade-in delay-150">
                Navigation
              </p>
            )}
            <SideNavItem
              to="/" icon={<House className="w-4 h-4" />}
              label="Home" end collapsed={collapsed}
              className="animate-fade-up delay-200"
            />
            <SideNavItem
              to="/instances" icon={<Database className="w-4 h-4" />}
              label="Instances" collapsed={collapsed}
              className="animate-fade-up delay-300"
            />
          </nav>

          {/* Collapse toggle */}
          <div className={`shrink-0 border-t-2 border-(--border-strong) py-3 animate-fade-up delay-300 ${collapsed ? 'px-2' : 'px-3'}`}>
            <SidebarBtn
              icon={collapsed
                ? <ChevronRight className="w-4 h-4 shrink-0" />
                : <ChevronLeft className="w-4 h-4 shrink-0" />
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
          <aside className="shell-sidebar fixed inset-y-0 left-0 w-56 z-50 lg:hidden flex flex-col animate-slide-right">
            <div className="flex flex-col h-full overflow-x-hidden">
              <div className="flex items-center justify-between shrink-0 px-3 py-4 animate-fade-up">
                <NavLink to="/" onClick={() => setMobileOpen(false)} className="flex items-center gap-2.5">
                  <div className="w-8 h-8 brutal-chip flex items-center justify-center text-xs font-bold shrink-0">
                    PW
                  </div>
                  <span className="font-semibold text-sm tracking-tight">Port Wrangler</span>
                </NavLink>
                <button onClick={() => setMobileOpen(false)} className="p-1 rounded text-(--text-muted) hover:text-(--text-primary) hover:bg-(--bg-surface-2) transition-colors">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="shrink-0 space-y-1 pb-3 px-3 animate-fade-up delay-50">
                {sysInfo && <DockerPill info={sysInfo} />}
                <SidebarBtn icon={<RefreshCw className={`w-4 h-4 shrink-0 ${syncing ? 'animate-spin' : ''}`} />} label="Sync statuses" collapsed={false} onClick={handleSync} disabled={syncing} />
                <SidebarBtn icon={<ThemeIcon className="w-4 h-4 shrink-0" />} label={themeLabel} collapsed={false} onClick={cycleMode} />
                <SidebarBtn icon={<Plus className="w-4 h-4 shrink-0" />} label="Deploy DB" collapsed={false} onClick={() => { onDeploy?.(); setMobileOpen(false) }} primary />
              </div>
              <div className="mx-3 border-t-2 border-(--border-strong) mb-2 shrink-0" />
              <nav className="flex-1 space-y-0.5 px-2 animate-fade-up delay-100">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-(--text-muted) px-3 py-2">Navigation</p>
                <SideNavItem to="/" icon={<House className="w-4 h-4" />} label="Home" end collapsed={false} onNavigate={() => setMobileOpen(false)} className="animate-fade-up delay-150" />
                <SideNavItem to="/instances" icon={<Database className="w-4 h-4" />} label="Instances" collapsed={false} onNavigate={() => setMobileOpen(false)} className="animate-fade-up delay-200" />
              </nav>
            </div>
          </aside>
        </>
      )}

      {/* ── Mobile top bar ── */}
      <header className="shell-topbar lg:hidden fixed top-0 left-0 right-0 z-30 h-14 flex items-center px-4 gap-2 animate-slide-down">
        <button onClick={() => setMobileOpen(true)} className="p-1.5 rounded text-(--text-muted) hover:text-(--text-primary) hover:bg-(--bg-surface-2) transition-colors">
          <Menu className="w-5 h-5" />
        </button>
        <NavLink to="/" className="flex items-center gap-2 flex-1">
          <div className="w-7 h-7 brutal-chip flex items-center justify-center text-[10px] font-bold select-none">PW</div>
          <span className="font-semibold text-sm tracking-tight">Port Wrangler</span>
        </NavLink>
        <button onClick={cycleMode} className="btn-secondary text-[11px] px-2.5 py-1.5">
          <ThemeIcon className="w-3.5 h-3.5" />
          {mode === 'system' ? 'Auto' : mode === 'dark' ? 'Night' : 'Day'}
        </button>
        <button onClick={() => onDeploy?.()} className="btn-primary flex items-center gap-1.5 text-xs px-3 py-1.5">
          <Plus className="w-3.5 h-3.5" />Deploy
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
  const base = `group relative flex items-center rounded-sm text-sm transition-all w-full disabled:opacity-50`
  const colors = primary
    ? 'btn-primary'
    : muted
      ? 'btn-ghost text-[var(--text-muted)]'
      : 'btn-secondary'

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
          `group relative flex items-center rounded-sm text-sm transition-all w-full border-2 ${className}
         ${collapsed ? 'justify-center py-2.5 px-0' : 'gap-3 px-3 py-2'}
         ${isActive
            ? 'bg-(--accent-soft) text-(--text-primary) font-semibold border-(--border-strong) shadow-(--shadow-raised)'
            : 'text-(--text-muted) hover:text-(--text-primary) hover:bg-(--bg-surface-2) border-transparent hover:border-(--border-soft)'
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
        whitespace-nowrap rounded-sm
        px-2.5 py-1 text-xs shadow-lg
        opacity-0 group-hover:opacity-100
        -translate-y-1 group-hover:translate-y-0
        transition-all duration-150 z-100
      "
      style={{
        background: 'var(--bg-surface)',
        border: '2px solid var(--border-strong)',
        color: 'var(--text-primary)',
        boxShadow: 'var(--shadow-raised)',
        left: SIDEBAR_COLLAPSED + 8,
      }}
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
    <div className="flex items-center gap-2 px-3 py-2 rounded-sm text-xs font-medium border-2" style={{
      background: 'var(--status-error-bg)',
      borderColor: 'var(--status-error-border)',
      color: 'var(--status-error)',
    }}>
      <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: 'var(--status-error)' }} />Docker offline
    </div>
  )
  return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-sm text-xs font-medium border-2 cursor-default transition-all duration-200" style={{
      background: 'var(--status-running-bg)',
      borderColor: 'var(--status-running-border)',
      color: 'var(--status-running)',
    }}>
      <span className="w-1.5 h-1.5 rounded-full shrink-0 animate-pulse" style={{ backgroundColor: 'var(--status-running)' }} />
      <HardDrive className="w-3.5 h-3.5" />
      Docker {os} · {info.arch}
    </div>
  )
}

// ── Docker dot (collapsed) ────────────────────────────────────────────────────
function DockerDot({ info }) {
  const ok = info.dockerAvailable
  const os = info.osType === 'MACOS' ? 'macOS' : (info.osType ?? 'Docker')
  return (
    <div className="group relative flex items-center justify-center py-2.5 w-full cursor-default rounded-sm">
      <span className={`w-2 h-2 rounded-full ${ok ? 'animate-pulse' : ''}`} style={{ backgroundColor: ok ? 'var(--status-running)' : 'var(--status-error)' }} />
      <Tooltip>{ok ? `Docker · ${os} · ${info.arch}` : 'Docker offline'}</Tooltip>
    </div>
  )
}
