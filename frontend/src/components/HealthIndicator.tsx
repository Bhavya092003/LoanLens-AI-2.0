import { useEffect, useState } from 'react'
import { fetchBackendHealth, type BackendHealth } from '../api/health'

type HealthState =
  | { kind: 'loading' }
  | { kind: 'ready'; health: BackendHealth }
  | { kind: 'error' }

export default function HealthIndicator() {
  const [state, setState] = useState<HealthState>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchBackendHealth(controller.signal)
      .then((health) => setState({ kind: 'ready', health }))
      .catch(() => {
        if (!controller.signal.aborted) setState({ kind: 'error' })
      })
    return () => controller.abort()
  }, [])

  if (state.kind === 'loading') {
    return <span className="health-pill" aria-live="polite">Checking backend…</span>
  }
  if (state.kind === 'error') {
    return <span className="health-pill health-pill--down" role="status">Backend unavailable</span>
  }
  return (
    <span className={`health-pill ${state.health.status === 'UP' ? 'health-pill--up' : 'health-pill--down'}`} role="status">
      API {state.health.status} · Database {state.health.database}
    </span>
  )
}
