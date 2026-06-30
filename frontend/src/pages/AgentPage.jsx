import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { ConfirmModal } from '../components/ConfirmModal'
import {
  Bot,
  ChevronDown,
  ChevronRight,
  Lock,
  MessageSquare,
  Send,
  ShieldAlert,
  TriangleAlert,
  User,
  Wrench,
} from 'lucide-react'

// Destructive tools get the red treatment; other gated (write) tools get amber.
const DESTRUCTIVE = new Set(['stopInstance', 'removeInstance'])

function prettyArgs(raw) {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

/**
 * The agentic assistant: the model may call infrastructure tools. Read-only tools run
 * automatically; write/destructive tools surface a confirmation modal and only execute when the
 * user approves (the request is re-issued with approve=true). A client-side read-only toggle blocks
 * approvals entirely.
 */
export function AgentPage() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [model, setModel] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [readOnly, setReadOnly] = useState(false)
  const [pending, setPending] = useState(null) // { turnIndex, tool, arguments, message }
  const esRef = useRef(null)
  const bottomRef = useRef(null)

  useEffect(() => () => { if (esRef.current) esRef.current.close() }, [])
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, pending])

  const updateTurn = useCallback((turnIndex, fn) => {
    setMessages(m => {
      const copy = [...m]
      copy[turnIndex] = fn(copy[turnIndex])
      return copy
    })
  }, [])

  const closeStream = useCallback(() => {
    if (esRef.current) { esRef.current.close(); esRef.current = null }
    setStreaming(false)
  }, [])

  // Open an agent stream for `message`; route events into the assistant turn at `turnIndex`.
  const openStream = useCallback((message, approve, turnIndex) => {
    const params = new URLSearchParams({ message })
    if (model.trim()) params.set('model', model.trim())
    if (approve) params.set('approve', 'true')

    const es = new EventSource(`/api/chat/agent/stream?${params.toString()}`)
    esRef.current = es
    setStreaming(true)

    es.addEventListener('tool_call', (e) => {
      try {
        const d = JSON.parse(e.data)
        updateTurn(turnIndex, t => ({
          ...t,
          tools: [...t.tools, { tool: d.tool, arguments: d.arguments, status: 'ran' }],
        }))
      } catch { /* ignore */ }
    })

    es.addEventListener('confirmation_request', (e) => {
      try {
        const d = JSON.parse(e.data)
        updateTurn(turnIndex, t => ({
          ...t,
          tools: t.tools.map(x =>
            x.tool === d.tool && x.status === 'ran' ? { ...x, status: 'awaiting' } : x),
          blocked: readOnly,
        }))
        if (!readOnly) {
          setPending({ turnIndex, tool: d.tool, arguments: d.arguments, message })
        }
      } catch { /* ignore */ }
    })

    es.addEventListener('token', (e) => {
      try {
        const d = JSON.parse(e.data)
        updateTurn(turnIndex, t => ({ ...t, content: t.content + (d.text || '') }))
      } catch { /* ignore */ }
    })

    es.addEventListener('agent_error', (e) => {
      try {
        const d = JSON.parse(e.data)
        updateTurn(turnIndex, t => ({ ...t, error: d.text }))
      } catch { /* ignore */ }
    })

    es.addEventListener('done', closeStream)
    // EventSource auto-reconnects on stream end — always close to stop the prompt re-firing.
    es.onerror = closeStream
  }, [model, readOnly, updateTurn, closeStream])

  const send = useCallback(() => {
    const text = input.trim()
    if (!text || streaming || pending) return
    setInput('')
    let turnIndex = -1
    setMessages(m => {
      turnIndex = m.length + 1
      return [
        ...m,
        { role: 'user', content: text },
        { role: 'assistant', content: '', tools: [], error: null, blocked: false },
      ]
    })
    // turnIndex points at the assistant turn just appended.
    openStream(text, false, turnIndex)
  }, [input, streaming, pending, openStream])

  const approve = useCallback(() => {
    if (!pending) return
    const { turnIndex, message } = pending
    setPending(null)
    updateTurn(turnIndex, t => ({
      ...t,
      tools: t.tools.map(x => x.status === 'awaiting' ? { ...x, status: 'approved' } : x),
      approvedRun: true,
    }))
    // Stateless resume: re-issue the same message with approval; the server re-runs read-only
    // tools (idempotent) then executes the approved write.
    openStream(message, true, turnIndex)
  }, [pending, updateTurn, openStream])

  const cancel = useCallback(() => {
    if (!pending) return
    const { turnIndex } = pending
    setPending(null)
    updateTurn(turnIndex, t => ({
      ...t,
      tools: t.tools.map(x => x.status === 'awaiting' ? { ...x, status: 'cancelled' } : x),
    }))
  }, [pending, updateTurn])

  const isDestructive = pending && DESTRUCTIVE.has(pending.tool)

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-4 animate-fade-up">
        <div>
          <h1 className="text-xl font-semibold text-[var(--text-primary)] flex items-center gap-2">
            <Wrench className="w-5 h-5 text-[var(--status-deploying)]" /> Agent
          </h1>
          <p className="text-sm text-[var(--text-muted)] mt-0.5">
            Ask the model to act on your stack — it calls tools, and you approve anything destructive.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setReadOnly(v => !v)}
            className={`btn-secondary text-sm ${readOnly ? 'text-[var(--status-running)]' : 'text-[var(--text-muted)]'}`}
            title="When on, write/destructive tools are never approved from this session"
          >
            <Lock className="w-4 h-4" /> Read-only {readOnly ? 'on' : 'off'}
          </button>
          <Link to="/chat" className="btn-secondary text-sm"><MessageSquare className="w-4 h-4" /> Plain chat</Link>
        </div>
      </div>

      <div className="card p-4 mb-4 min-h-[50vh] max-h-[62vh] overflow-y-auto space-y-4 animate-fade-up delay-100">
        {messages.length === 0 ? (
          <div className="text-center text-[var(--text-muted)] py-16 text-sm">
            Try “list my running instances”, or “stop the redis instance”.
          </div>
        ) : messages.map((m, i) => (
          <div key={i} className={`flex gap-3 ${m.role === 'user' ? 'justify-end' : ''}`}>
            {m.role === 'assistant' && <Bot className="w-5 h-5 text-[var(--status-deploying)] shrink-0 mt-1" />}
            <div className={`max-w-[80%] space-y-2 ${m.role === 'user' ? 'order-1' : ''}`}>
              {m.role === 'assistant' && m.tools.length > 0 && (
                <div className="space-y-1.5">
                  {m.tools.map((t, j) => <ToolCallRow key={j} call={t} />)}
                </div>
              )}
              {(m.content || (m.role === 'assistant' && streaming && i === messages.length - 1 && m.tools.length === 0)) && (
                <div className={`rounded-lg px-3 py-2 text-sm whitespace-pre-wrap ${
                  m.role === 'user'
                    ? 'bg-[var(--accent-soft)] text-[var(--text-primary)]'
                    : 'bg-[var(--bg-surface-2)] text-[var(--text-primary)]'
                }`}>
                  {m.content || '…'}
                </div>
              )}
              {m.blocked && (
                <div className="flex items-center gap-2 text-xs px-3 py-2 rounded-md border-2"
                  style={{ background: 'var(--status-error-bg)', borderColor: 'var(--status-error-border)', color: 'var(--status-error)' }}>
                  <Lock className="w-3.5 h-3.5" /> Blocked by read-only mode — turn it off to approve writes.
                </div>
              )}
              {m.error && (
                <div className="flex items-center gap-2 text-xs px-3 py-2 rounded-md border-2"
                  style={{ background: 'var(--status-error-bg)', borderColor: 'var(--status-error-border)', color: 'var(--status-error)' }}>
                  <TriangleAlert className="w-3.5 h-3.5" /> {m.error}
                </div>
              )}
            </div>
            {m.role === 'user' && <User className="w-5 h-5 text-[var(--text-muted)] shrink-0 mt-1" />}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="flex items-center gap-2 animate-fade-up delay-150">
        <input
          value={model}
          onChange={e => setModel(e.target.value)}
          placeholder="model (optional)"
          className="input max-w-44"
        />
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') send() }}
          placeholder={pending ? 'Awaiting your confirmation…' : 'Ask the agent to do something…'}
          disabled={!!pending}
          className="input flex-1 disabled:opacity-50"
        />
        <button onClick={send} disabled={streaming || !!pending || !input.trim()} className="btn-primary disabled:opacity-50">
          <Send className="w-4 h-4" /> Send
        </button>
      </div>

      <ConfirmModal
        open={!!pending}
        variant={isDestructive ? 'danger' : 'warning'}
        icon={isDestructive ? <TriangleAlert className="w-5 h-5" /> : <ShieldAlert className="w-5 h-5" />}
        title={`Run “${pending?.tool}”?`}
        message={
          <span>
            The agent wants to run a {isDestructive ? 'destructive' : 'state-changing'} tool.
            {pending?.arguments && pending.arguments !== '{}' && (
              <code className="block mt-2 p-2 rounded bg-[var(--bg-surface-2)] text-xs font-mono whitespace-pre-wrap">
                {prettyArgs(pending.arguments)}
              </code>
            )}
          </span>
        }
        confirmLabel="Run tool"
        cancelLabel="Cancel"
        onConfirm={approve}
        onCancel={cancel}
      />
    </AppShell>
  )
}

// ── A single tool-call row in the trace ────────────────────────────────────────
function ToolCallRow({ call }) {
  const [open, setOpen] = useState(false)
  const hasArgs = call.arguments && call.arguments !== '{}'
  const tone = STATUS_TONE[call.status] ?? STATUS_TONE.ran

  return (
    <div className="rounded-md border-2 text-xs" style={{ borderColor: 'var(--border-soft)', background: 'var(--bg-surface)' }}>
      <button
        onClick={() => hasArgs && setOpen(o => !o)}
        className="w-full flex items-center gap-2 px-2.5 py-1.5 text-left"
        style={{ cursor: hasArgs ? 'pointer' : 'default' }}
      >
        <Wrench className="w-3.5 h-3.5 shrink-0 text-[var(--text-muted)]" />
        <span className="font-mono font-semibold text-[var(--text-primary)]">{call.tool}</span>
        <span className="ml-auto px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide"
          style={{ background: tone.bg, color: tone.fg }}>
          {call.status}
        </span>
        {hasArgs && (open
          ? <ChevronDown className="w-3.5 h-3.5 text-[var(--text-muted)]" />
          : <ChevronRight className="w-3.5 h-3.5 text-[var(--text-muted)]" />)}
      </button>
      {open && hasArgs && (
        <pre className="px-2.5 pb-2 pt-0 font-mono text-[11px] text-[var(--text-muted)] whitespace-pre-wrap">
          {prettyArgs(call.arguments)}
        </pre>
      )}
    </div>
  )
}

const STATUS_TONE = {
  ran:       { bg: 'var(--status-running-bg)',  fg: 'var(--status-running)' },
  awaiting:  { bg: 'var(--status-warning-bg)',  fg: 'var(--status-warning)' },
  approved:  { bg: 'var(--status-deploying-bg)', fg: 'var(--status-deploying)' },
  cancelled: { bg: 'var(--bg-surface-2)',       fg: 'var(--text-muted)' },
}
