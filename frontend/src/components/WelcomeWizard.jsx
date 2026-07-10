import { useState } from 'react'
import {
  ArrowRight,
  BookOpen,
  Bot,
  Check,
  Container,
  Database,
  Gauge,
  GitCompare,
  Layers,
  MessageSquare,
  Sparkles,
  UserCircle2,
  Wrench,
} from 'lucide-react'

const ROLES = [
  'Backend Engineer',
  'Frontend Engineer',
  'Full-stack Engineer',
  'Data Engineer',
  'DevOps / SRE',
  'ML / AI Engineer',
  'Student / Hobbyist',
  'Other',
]

const FOCUS_OPTIONS = [
  { id: 'db',   label: 'Databases',      desc: 'Spin up Postgres, Redis, Kafka & friends',      icon: <Database className="w-4 h-4" /> },
  { id: 'llm',  label: 'Local LLMs',     desc: 'Run, chat with and compare models on-device',   icon: <Sparkles className="w-4 h-4" /> },
  { id: 'both', label: 'Both',           desc: 'The full workbench — data + models',            icon: <Layers className="w-4 h-4" /> },
]

const TOOL_OPTIONS = [
  { id: 'POSTGRESQL',    label: 'PostgreSQL' },
  { id: 'MYSQL',         label: 'MySQL' },
  { id: 'MARIADB',       label: 'MariaDB' },
  { id: 'MONGODB',       label: 'MongoDB' },
  { id: 'REDIS',         label: 'Redis' },
  { id: 'ELASTICSEARCH', label: 'Elasticsearch' },
  { id: 'KAFKA',         label: 'Kafka' },
  { id: 'RABBITMQ',      label: 'RabbitMQ' },
  { id: 'CLICKHOUSE',    label: 'ClickHouse' },
  { id: 'MINIO',         label: 'MinIO' },
  { id: 'GRAFANA',       label: 'Grafana' },
  { id: 'KEYCLOAK',      label: 'Keycloak' },
]

// Keep ids in sync with LLM_FEATURES on the home page.
const LLM_OPTIONS = [
  { id: 'CHAT',     label: 'Assistant',   icon: <MessageSquare className="w-4 h-4" /> },
  { id: 'AGENT',    label: 'Agent',       icon: <Bot className="w-4 h-4" /> },
  { id: 'COMPARE',  label: 'Compare',     icon: <GitCompare className="w-4 h-4" /> },
  { id: 'COOKBOOK', label: 'Cookbook',    icon: <BookOpen className="w-4 h-4" /> },
  { id: 'RUNTIME',  label: 'Runtime',     icon: <Gauge className="w-4 h-4" /> },
]

/**
 * First-run welcome wizard. Captures a local-only profile that personalises
 * the home page and the deploy flow. Never sent to the backend.
 * Steps adapt to the chosen focus: db-only users skip the AI step,
 * llm-only users skip the database favourites step.
 */
export function WelcomeWizard({ onComplete }) {
  const [step, setStep]         = useState(0)
  const [name, setName]         = useState('')
  const [role, setRole]         = useState(ROLES[0])
  const [focus, setFocus]       = useState('both')
  const [favTools, setFavTools] = useState([])
  const [favLlm, setFavLlm]     = useState([])

  const toggleIn = (setter) => (id) =>
    setter(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])
  const toggleTool = toggleIn(setFavTools)
  const toggleLlm  = toggleIn(setFavLlm)

  const trimmedName = name.trim()
  const nameValid   = trimmedName.length >= 1 && trimmedName.length <= 40

  const finish = () => {
    onComplete?.({
      name: trimmedName || 'Captain',
      role,
      focus,
      favTools: focus === 'llm' ? [] : favTools,
      favLlm:   focus === 'db'  ? [] : favLlm,
    })
  }

  const steps = [
    {
      key: 'name',
      title: 'WHO ARE YOU?',
      subtitle: 'A first name or handle is enough. Lives only in this browser.',
      icon: <UserCircle2 className="w-6 h-6" />,
      body: (
        <div className="space-y-3">
          <input
            autoFocus
            className="input text-lg"
            placeholder="e.g. Alex"
            maxLength={40}
            value={name}
            onChange={e => setName(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter' && nameValid) setStep(1) }}
          />
          <p className="text-[11px] text-(--text-quiet)">{trimmedName.length}/40</p>
        </div>
      ),
      canAdvance: nameValid,
    },
    {
      key: 'role',
      title: 'WHAT DO YOU DO?',
      subtitle: 'Pick the closest match — used for friendlier copy.',
      icon: <Wrench className="w-6 h-6" />,
      body: (
        <div className="grid grid-cols-2 sm:grid-cols-2 gap-2">
          {ROLES.map(r => (
            <button
              key={r}
              type="button"
              onClick={() => setRole(r)}
              className={`wizard-option ${role === r ? 'wizard-option-active' : ''}`}
            >
              {r}
              {role === r && <Check className="w-4 h-4" />}
            </button>
          ))}
        </div>
      ),
      canAdvance: true,
    },
    {
      key: 'focus',
      title: 'WHAT WILL YOU WRANGLE?',
      subtitle: 'Shapes your home page — you can use everything either way.',
      icon: <Layers className="w-6 h-6" />,
      body: (
        <div className="grid grid-cols-1 gap-2">
          {FOCUS_OPTIONS.map(f => (
            <button
              key={f.id}
              type="button"
              onClick={() => setFocus(f.id)}
              className={`wizard-option ${focus === f.id ? 'wizard-option-active' : ''}`}
            >
              <span className="flex items-center gap-2 min-w-0">
                {f.icon}
                <span className="text-left">
                  <span className="block">{f.label}</span>
                  <span className="block text-[11px] font-normal text-(--text-muted)">{f.desc}</span>
                </span>
              </span>
              {focus === f.id && <Check className="w-4 h-4 shrink-0" />}
            </button>
          ))}
        </div>
      ),
      canAdvance: true,
    },
    ...(focus !== 'llm' ? [{
      key: 'tools',
      title: 'PICK YOUR DATABASES',
      subtitle: 'Pinned on your home page for one-click deploys. Skip if unsure.',
      icon: <Database className="w-6 h-6" />,
      body: (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-64 overflow-y-auto pr-1">
          {TOOL_OPTIONS.map(t => {
            const on = favTools.includes(t.id)
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => toggleTool(t.id)}
                className={`wizard-option ${on ? 'wizard-option-active' : ''}`}
              >
                {t.label}
                {on && <Check className="w-4 h-4" />}
              </button>
            )
          })}
        </div>
      ),
      canAdvance: true,
    }] : []),
    ...(focus !== 'db' ? [{
      key: 'llm',
      title: 'PICK YOUR AI STARTERS',
      subtitle: 'Shortcuts pinned next to your databases. Skip if unsure.',
      icon: <Sparkles className="w-6 h-6" />,
      body: (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {LLM_OPTIONS.map(o => {
            const on = favLlm.includes(o.id)
            return (
              <button
                key={o.id}
                type="button"
                onClick={() => toggleLlm(o.id)}
                className={`wizard-option ${on ? 'wizard-option-active' : ''}`}
              >
                <span className="flex items-center gap-2">{o.icon}{o.label}</span>
                {on && <Check className="w-4 h-4" />}
              </button>
            )
          })}
        </div>
      ),
      canAdvance: true,
    }] : []),
  ]

  const safeStep = Math.min(step, steps.length - 1)
  const current  = steps[safeStep]
  const lastStep = safeStep === steps.length - 1

  return (
    <div className="wizard-backdrop" role="dialog" aria-modal="true" aria-label="Welcome to Port Wrangler">
      <div className="wizard-card animate-scale-in">

        {/* Header */}
        <div className="flex items-center gap-3 mb-2">
          <div className="w-10 h-10 rounded-(--radius-sm) border-2 border-(--border-strong) bg-(--accent) text-(--text-inverse) flex items-center justify-center shrink-0">
            <Container className="w-5 h-5" strokeWidth={2.5} />
          </div>
          <div className="min-w-0">
            <p className="text-[10px] tracking-[0.18em] text-(--text-quiet) font-semibold uppercase">Port Wrangler · setup</p>
            <h2 className="text-base font-bold text-(--text-primary) truncate">Welcome aboard</h2>
          </div>
        </div>

        {/* Step indicator */}
        <div className="flex gap-1.5 mb-5">
          {steps.map((_, i) => (
            <div
              key={i}
              className="h-1.5 flex-1 rounded-full border border-(--border-strong)"
              style={{ background: i <= safeStep ? 'var(--accent)' : 'var(--bg-surface-2)' }}
            />
          ))}
        </div>

        {/* Step body */}
        <div className="space-y-4 min-h-[230px]">
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-(--radius-sm) border-2 border-(--border-strong) bg-(--bg-surface-2) text-(--text-primary) flex items-center justify-center shrink-0">
              {current.icon}
            </div>
            <div>
              <h3 className="text-lg font-black tracking-tight text-(--text-primary)">{current.title}</h3>
              <p className="text-xs text-(--text-muted) mt-0.5">{current.subtitle}</p>
            </div>
          </div>

          <div>{current.body}</div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between mt-6 gap-2">
          <button
            type="button"
            onClick={() => safeStep > 0 ? setStep(safeStep - 1) : null}
            disabled={safeStep === 0}
            className="btn-ghost disabled:opacity-40"
          >
            Back
          </button>

          <div className="flex items-center gap-2">
            {!lastStep && safeStep >= 1 && (
              <button type="button" onClick={() => setStep(safeStep + 1)} className="btn-ghost">
                Skip
              </button>
            )}
            {lastStep ? (
              <button type="button" onClick={finish} className="btn-primary">
                Let&rsquo;s wrangle <ArrowRight className="w-4 h-4" />
              </button>
            ) : (
              <button
                type="button"
                onClick={() => setStep(safeStep + 1)}
                disabled={!current.canAdvance}
                className="btn-primary disabled:opacity-50"
              >
                Continue <ArrowRight className="w-4 h-4" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
