import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { Bot, MessageSquare, Play } from 'lucide-react'

export function ComparePage() {
  const [prompt, setPrompt] = useState('')
  const [modelA, setModelA] = useState('llama3.1:8b')
  const [modelB, setModelB] = useState('mistral:7b')
  const [outputs, setOutputs] = useState({ A: '', B: '' })
  const [running, setRunning] = useState(false)
  const esRef = useRef(null)

  useEffect(() => () => { if (esRef.current) esRef.current.close() }, [])

  const run = useCallback(() => {
    const text = prompt.trim()
    if (!text || running) return
    setOutputs({ A: '', B: '' })
    setRunning(true)

    const params = new URLSearchParams({ prompt: text, modelA, modelB })
    const es = new EventSource(`/api/models/compare?${params.toString()}`)
    esRef.current = es

    es.addEventListener('token', (e) => {
      try {
        const { slot, text: tok } = JSON.parse(e.data)
        setOutputs(o => ({ ...o, [slot]: (o[slot] || '') + tok }))
      } catch { /* ignore */ }
    })
    const finish = () => { es.close(); esRef.current = null; setRunning(false) }
    es.onerror = finish
  }, [prompt, modelA, modelB, running])

  return (
    <AppShell>
      <div className="flex items-center justify-between mb-4 animate-fade-up">
        <div>
          <h1 className="text-xl font-semibold text-[var(--text-primary)]">Model Comparison</h1>
          <p className="text-sm text-[var(--text-muted)] mt-0.5">Run one prompt against two models side by side.</p>
        </div>
        <Link to="/chat" className="btn-secondary text-sm"><MessageSquare className="w-4 h-4" /> Back to chat</Link>
      </div>

      <div className="flex flex-col gap-2 mb-4 animate-fade-up delay-100">
        <div className="flex gap-2">
          <input value={modelA} onChange={e => setModelA(e.target.value)} placeholder="Model A" className="input flex-1" />
          <input value={modelB} onChange={e => setModelB(e.target.value)} placeholder="Model B" className="input flex-1" />
        </div>
        <div className="flex gap-2">
          <input
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') run() }}
            placeholder="Prompt…"
            className="input flex-1"
          />
          <button onClick={run} disabled={running || !prompt.trim()} className="btn-primary disabled:opacity-50">
            <Play className="w-4 h-4" /> {running ? 'Running…' : 'Run'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 animate-fade-up delay-150">
        {['A', 'B'].map((slot) => (
          <div key={slot} className="card p-4 min-h-[40vh]">
            <div className="flex items-center gap-2 mb-3">
              <Bot className="w-4 h-4 text-[var(--status-deploying)]" />
              <span className="text-sm font-semibold text-[var(--text-primary)]">{slot === 'A' ? modelA : modelB}</span>
            </div>
            <div className="text-sm text-[var(--text-primary)] whitespace-pre-wrap">
              {outputs[slot] || (running ? '…' : <span className="text-[var(--text-muted)]">No output yet.</span>)}
            </div>
          </div>
        ))}
      </div>
    </AppShell>
  )
}
