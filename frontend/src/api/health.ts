import { API_BASE_URL } from './config'

export type BackendHealth = {
  status: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
}

export async function fetchBackendHealth(signal?: AbortSignal): Promise<BackendHealth> {
  const response = await fetch(`${API_BASE_URL}/api/v1/health`, { signal })
  const result = (await response.json()) as BackendHealth
  if (!response.ok) {
    throw new Error(`Backend health check returned ${response.status}`)
  }
  return result
}
