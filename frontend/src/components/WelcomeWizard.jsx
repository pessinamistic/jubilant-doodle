import { useState } from 'react'
import {
  ArrowRight,
  Check,
  Container,
  Database,
  Layers,
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
  'QA / Tester',
  'Student / Hobbyist',
  'Other',
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

const PURPOSES = [
  { id: 'work',    label: 'Day job',          icon: <Wrench className="w-4 h-4" /> },
  { id: 'side',    label: 'Side project',     icon: <Sparkles className="w-4 h-4" /> },
  { id: 'learn',   label: 'Learning',         icon: <Layers className="w-4 h-4" /> },
  { id: 'demo',    label: 'Demo / prototype', icon: <Database className="w-4 h-4" /> },
]

/**
 * First-run welcome wizard. Captures a local-only profile that personalises
 * the home page and the deploy flow. Never sent to the backend.
 */
export function WelcomeWizard({ onComplete }) {
  const [step, setStep]       = useState(0)
  const [name, setName]       = useState('')
  const [role, setRole]       = useState(ROLES[0])
  const [purpose, setPurpose] = useState('work')
  const [favTools, setFavTools] = useState([])

  const toggleTool = (id) =>
    setFavTools(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])

  const trimmedName = name.trim()
  const nameValid   = trimmedName.length >= 1 && trimmedName.length <= 40

  const finish = () => {
    onComplete?.({
      name: trimmedName || 'Captain',
      role,
      purpose,
      favTools,
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
      key: 'purpose',
      title: 'WHAT ARE YOU SHIPPING?',
      subtitle: 'Tells us which surface to highlight first.',
      icon: <Sparkles className="w-6 h-6" />,
      body: (
        <div className="grid grid-cols-2 gap-2">
          {PURPOSES.map(p => (
            <button
              key={p.id}
              type="button"
              onClick={() => setPurpose(p.id)}
              className={`wizard-option ${purpose === p.id ? 'wizard-option-active' : ''}`}
            >
              <span className="flex items-center gap-2">{p.icon}{p.label}</span>
              {purpose === p.id && <Check className="w-4 h-4" />}
            </button>
          ))}
        </div>
      ),
      canAdvance: true,
    },
    {
      key: 'tools',
      title: 'PICK YOUR FAVORITES',
      subtitle: 'We will pin these on your home page. Skip if you cannot decide.',
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
    },
  ]

  const current = steps[step]
  const lastStep = step === steps.length - 1

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
              style={{ background: i <= step ? 'var(--accent)' : 'var(--bg-surface-2)' }}
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
            onClick={() => step > 0 ? setStep(step - 1) : null}
            disabled={step === 0}
            className="btn-ghost disabled:opacity-40"
          >
            Back
          </button>

          <div className="flex items-center gap-2">
            {!lastStep && step >= 1 && (
              <button type="button" onClick={() => setStep(step + 1)} className="btn-ghost">
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
                onClick={() => setStep(step + 1)}
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
