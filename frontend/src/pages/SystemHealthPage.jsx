import { useEffect, useRef, useState } from 'react'
import {
  Activity,
  Cpu,
  Fan,
  Gauge,
  MemoryStick,
  MonitorCog,
  Server,
  Thermometer,
} from 'lucide-react'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell,
  Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { getHostMetrics, getSystemInfo } from '../api/client'
import { AppShell } from '../components/AppShell'

const POLL_MS = 2000
const WINDOW_SIZE = 90 // ~3 minutes of samples

const GRID  = { stroke: 'rgba(0,0,0,0.08)', strokeDasharray: '3 3' }
const AXIS  = { tick: { fill: 'var(--text-quiet)', fontSize: 10 } }
const TOOLT = {
  contentStyle: {
    background: 'var(--bg-surface)',
    border: '2px solid var(--border-strong)',
    borderRadius: 4,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
  },
  labelStyle: { color: 'var(--text-muted)' },
  itemStyle:  { color: 'var(--text-primary)' },
}

const GB = 1024 * 1024 * 1024

function fmtBytes(bytes) {
  if (bytes == null) return '—'
  if (bytes >= GB) return `${(bytes / GB).toFixed(1)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(0)} MB`
  return `${bytes} B`
}

function fmtUptime(seconds) {
  if (!seconds) return '—'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return d > 0 ? `${d}d ${h}h ${m}m` : h > 0 ? `${h}h ${m}m` : `${m}m`
}

function fmtGhz(hz) {
  return hz ? `${(hz / 1e9).toFixed(2)} GHz` : null
}

function coreColor(pct) {
  if (pct >= 85) return '#ef4444'
  if (pct >= 60) return '#f59e0b'
  return '#22c55e'
}

function StatCard({ icon, label, value, sub, tone }) {
  return (
    <div className="card p-4 flex items-start gap-3">
      <div className={`p-2 rounded-lg shrink-0 ${tone ?? 'bg-(--accent)/10 text-(--accent)'}`}>
        {icon}
      </div>
      <div className="min-w-0">
        <p className="section-label">{label}</p>
        <p className="text-2xl font-bold text-(--text-primary) leading-tight font-mono">{value}</p>
        {sub && <p className="text-[11px] text-(--text-quiet) mt-0.5 truncate">{sub}</p>}
      </div>
    </div>
  )
}

export function SystemHealthPage() {
  const [info, setInfo]         = useState(null)   // static system info (banner data)
  const [latest, setLatest]     = useState(null)   // most recent host-metrics sample
  const [history, setHistory]   = useState([])     // rolling client-side window
  const [error, setError]       = useState(null)
  const timerRef = useRef(null)

  useEffect(() => {
    getSystemInfo().then(setInfo).catch(() => {})

    let cancelled = false
    const poll = async () => {
      try {
        const m = await getHostMetrics()
        if (cancelled) return
        setLatest(m)
        setError(null)
        setHistory(prev => {
          const point = {
            time: new Date(m.timestamp).toLocaleTimeString([], { minute: '2-digit', second: '2-digit' }),
            cpu: m.cpu.totalLoadPct,
            memUsedGb: +(m.memory.usedBytes / GB).toFixed(2),
            memPct: m.memory.usedPct,
            cpuTemp: m.sensors.cpuTempC,
            gpu: m.gpus?.[0]?.utilizationPct ?? null,
            gpuTemp: m.gpus?.[0]?.tempC ?? null,
          }
          return [...prev, point].slice(-WINDOW_SIZE)
        })
      } catch {
        if (!cancelled) setError('Failed to fetch host metrics — is the backend running?')
      }
    }

    poll()
    timerRef.current = setInterval(poll, POLL_MS)
    return () => { cancelled = true; clearInterval(timerRef.current) }
  }, [])

  const cpu = latest?.cpu
  const mem = latest?.memory
  const sensors = latest?.sensors
  const gpus = latest?.gpus ?? []
  const host = latest?.host

  const perCoreData = (cpu?.perCoreLoadPct ?? []).map((v, i) => ({ core: `C${i}`, load: v }))
  const hasTemp = history.some(p => p.cpuTemp != null || p.gpuTemp != null)
  const hasGpuUsage = gpus.some(g => g.utilizationPct != null)
  const memTotalGb = mem ? +(mem.totalBytes / GB).toFixed(1) : 0

  return (
    <AppShell>
      {/* Header */}
      <section className="mb-6 animate-fade-up">
        <p className="section-label mb-1">System Health</p>
        <h1 className="text-3xl font-bold tracking-tight text-(--text-primary)">
          {host?.hostname ?? 'This machine'}
        </h1>
        <p className="text-sm text-(--text-muted) mt-1 font-mono">
          {host ? (
            <>
              {host.os} · {host.arch} · up {fmtUptime(host.uptimeSeconds)} ·{' '}
              {host.processCount} procs / {host.threadCount} threads
              {info && <> · deploy: {info.preferredDeployMethod}</>}
            </>
          ) : 'Detecting hardware…'}
        </p>
      </section>

      {error && (
        <div className="card p-4 mb-6 text-center text-(--status-error) text-sm">{error}</div>
      )}

      {!latest && !error ? (
        <div className="card p-10 flex items-center justify-center gap-2 text-(--text-muted) text-sm">
          <div className="w-4 h-4 border-2 border-(--accent) border-t-transparent rounded-full animate-spin" />
          Reading hardware sensors…
        </div>
      ) : latest && (
        <>
          {/* Stat cards */}
          <section className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6 animate-fade-up">
            <StatCard
              icon={<Cpu className="w-4 h-4" />}
              label="CPU load"
              value={`${cpu.totalLoadPct.toFixed(1)}%`}
              sub={`${cpu.physicalCores}c / ${cpu.logicalCores}t${fmtGhz(cpu.currentFreqHz) ? ` · ${fmtGhz(cpu.currentFreqHz)}` : ''}`}
            />
            <StatCard
              icon={<MemoryStick className="w-4 h-4" />}
              label="Memory"
              value={`${mem.usedPct.toFixed(1)}%`}
              sub={`${fmtBytes(mem.usedBytes)} of ${fmtBytes(mem.totalBytes)}`}
            />
            <StatCard
              icon={<Thermometer className="w-4 h-4" />}
              label="CPU temp"
              value={sensors.cpuTempC != null ? `${sensors.cpuTempC.toFixed(0)}°C` : 'N/A'}
              sub={sensors.cpuTempC == null
                ? 'Not exposed on this platform'
                : sensors.fanSpeedsRpm?.length ? `Fans: ${sensors.fanSpeedsRpm.join(' / ')} rpm` : null}
              tone={sensors.cpuTempC >= 85 ? 'bg-red-500/10 text-red-400' : undefined}
            />
            <StatCard
              icon={<MonitorCog className="w-4 h-4" />}
              label="GPU"
              value={hasGpuUsage ? `${gpus.find(g => g.utilizationPct != null).utilizationPct.toFixed(0)}%` : gpus.length ? '—' : 'None'}
              sub={gpus[0]?.name}
            />
          </section>

          {/* CPU + memory over time */}
          <section className="grid lg:grid-cols-2 gap-4 mb-6 animate-fade-up">
            <div className="card p-4">
              <p className="section-label mb-3 flex items-center gap-1.5">
                <Activity className="w-3.5 h-3.5" /> CPU utilization (%)
              </p>
              <ResponsiveContainer width="100%" height={200}>
                <AreaChart data={history}>
                  <defs>
                    <linearGradient id="cpuGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#ff5f2e" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#ff5f2e" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid {...GRID} />
                  <XAxis dataKey="time" {...AXIS} minTickGap={40} />
                  <YAxis domain={[0, 100]} {...AXIS} width={32} />
                  <Tooltip {...TOOLT} />
                  <Area type="monotone" dataKey="cpu" name="CPU %" stroke="#ff5f2e"
                        strokeWidth={2} fill="url(#cpuGrad)" isAnimationActive={false} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            <div className="card p-4">
              <p className="section-label mb-3 flex items-center gap-1.5">
                <MemoryStick className="w-3.5 h-3.5" /> Memory used (GB of {memTotalGb})
              </p>
              <ResponsiveContainer width="100%" height={200}>
                <AreaChart data={history}>
                  <defs>
                    <linearGradient id="memGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#1e58d4" stopOpacity={0.35} />
                      <stop offset="100%" stopColor="#1e58d4" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid {...GRID} />
                  <XAxis dataKey="time" {...AXIS} minTickGap={40} />
                  <YAxis domain={[0, memTotalGb]} {...AXIS} width={32} />
                  <Tooltip {...TOOLT} />
                  <Area type="monotone" dataKey="memUsedGb" name="Used GB" stroke="#1e58d4"
                        strokeWidth={2} fill="url(#memGrad)" isAnimationActive={false} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
              {mem.swapTotalBytes > 0 && (
                <p className="text-[11px] text-(--text-quiet) mt-2 font-mono">
                  Swap: {fmtBytes(mem.swapUsedBytes)} / {fmtBytes(mem.swapTotalBytes)}
                </p>
              )}
            </div>
          </section>

          {/* Per-core + temperature */}
          <section className="grid lg:grid-cols-2 gap-4 mb-6 animate-fade-up">
            <div className="card p-4">
              <p className="section-label mb-3 flex items-center gap-1.5">
                <Gauge className="w-3.5 h-3.5" /> Per-core load ({perCoreData.length} logical cores)
              </p>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={perCoreData}>
                  <CartesianGrid {...GRID} />
                  <XAxis dataKey="core" {...AXIS} interval={perCoreData.length > 16 ? 1 : 0} />
                  <YAxis domain={[0, 100]} {...AXIS} width={32} />
                  <Tooltip {...TOOLT} />
                  <Bar dataKey="load" name="Load %" isAnimationActive={false} radius={[3, 3, 0, 0]}>
                    {perCoreData.map((d, i) => <Cell key={i} fill={coreColor(d.load)} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
              {cpu.loadAverage?.[0] >= 0 && (
                <p className="text-[11px] text-(--text-quiet) mt-2 font-mono">
                  Load average: {cpu.loadAverage.map(l => l < 0 ? '—' : l.toFixed(2)).join(' / ')} (1 / 5 / 15 min)
                </p>
              )}
            </div>

            <div className="card p-4">
              <p className="section-label mb-3 flex items-center gap-1.5">
                <Thermometer className="w-3.5 h-3.5" /> Temperature (°C)
              </p>
              {hasTemp ? (
                <ResponsiveContainer width="100%" height={200}>
                  <LineChart data={history}>
                    <CartesianGrid {...GRID} />
                    <XAxis dataKey="time" {...AXIS} minTickGap={40} />
                    <YAxis {...AXIS} width={32} domain={['auto', 'auto']} />
                    <Tooltip {...TOOLT} />
                    <Line type="monotone" dataKey="cpuTemp" name="CPU °C" stroke="#ef4444"
                          strokeWidth={2} isAnimationActive={false} dot={false} connectNulls />
                    <Line type="monotone" dataKey="gpuTemp" name="GPU °C" stroke="#a855f7"
                          strokeWidth={2} isAnimationActive={false} dot={false} connectNulls />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-[200px] flex flex-col items-center justify-center text-center text-(--text-quiet) text-xs gap-2">
                  <Fan className="w-6 h-6" />
                  <p>Temperature sensors aren't readable without elevated privileges on this platform.</p>
                  <p className="font-mono">(Common on macOS and stock Windows.)</p>
                </div>
              )}
            </div>
          </section>

          {/* GPUs */}
          <section className="mb-6 animate-fade-up">
            <p className="section-label mb-3 flex items-center gap-1.5">
              <Server className="w-3.5 h-3.5" /> Graphics ({gpus.length || 'none detected'})
            </p>
            <div className="grid lg:grid-cols-2 gap-4">
              {gpus.map((g, i) => (
                <div key={i} className="card p-4">
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-(--text-primary) truncate">{g.name}</p>
                      <p className="text-[11px] text-(--text-quiet) font-mono">
                        {g.vendor}{g.vramBytes > 0 ? ` · ${fmtBytes(g.vramBytes)} VRAM` : ''}
                        {g.memoryUsedBytes != null ? ` · ${fmtBytes(g.memoryUsedBytes)} used` : ''}
                        {g.tempC != null ? ` · ${g.tempC.toFixed(0)}°C` : ''}
                      </p>
                    </div>
                    <span className="text-xl font-bold font-mono text-(--text-primary) shrink-0">
                      {g.utilizationPct != null ? `${g.utilizationPct.toFixed(0)}%` : 'N/A'}
                    </span>
                  </div>
                  {i === 0 && g.utilizationPct != null ? (
                    <ResponsiveContainer width="100%" height={120}>
                      <AreaChart data={history}>
                        <defs>
                          <linearGradient id="gpuGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="#a855f7" stopOpacity={0.35} />
                            <stop offset="100%" stopColor="#a855f7" stopOpacity={0.02} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid {...GRID} />
                        <XAxis dataKey="time" {...AXIS} minTickGap={40} hide />
                        <YAxis domain={[0, 100]} {...AXIS} width={32} />
                        <Tooltip {...TOOLT} />
                        <Area type="monotone" dataKey="gpu" name="GPU %" stroke="#a855f7"
                              strokeWidth={2} fill="url(#gpuGrad)" isAnimationActive={false} dot={false} connectNulls />
                      </AreaChart>
                    </ResponsiveContainer>
                  ) : g.utilizationPct == null && (
                    <p className="text-[11px] text-(--text-quiet)">
                      Live utilization unavailable for this GPU (needs nvidia-smi, or macOS IOAccelerator).
                    </p>
                  )}
                </div>
              ))}
              {gpus.length === 0 && (
                <div className="card p-6 text-center text-(--text-quiet) text-sm">No GPUs detected.</div>
              )}
            </div>
          </section>

          <p className="text-[10px] text-(--text-quiet) font-mono mb-4">
            CPU: {cpu.model} · sampling every {POLL_MS / 1000}s · window {WINDOW_SIZE} samples
          </p>
        </>
      )}
    </AppShell>
  )
}
