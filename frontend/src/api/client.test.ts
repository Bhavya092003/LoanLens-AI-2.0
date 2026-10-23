import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

afterEach(() => vi.unstubAllGlobals())

describe('API client', () => {
  it('surfaces structured API errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Provider unavailable' }), { status: 503 })))
    await expect(api('/api/v1/policy-questions')).rejects.toThrow('Provider unavailable')
  })

  it('rejects a malformed successful response body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not-json', { status: 200 })))
    await expect(api('/api/v1/loan-products')).rejects.toThrow('invalid response')
  })
})
