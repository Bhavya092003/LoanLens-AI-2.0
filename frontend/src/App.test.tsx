import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import App from './App'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('application shell', () => {
  it('shows all three module navigation placeholders', () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP', database: 'UP' }) }))
    render(<App />)

    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
    const navigation = within(screen.getByRole('navigation', { name: 'Main navigation' }))
    expect(navigation.getByText('Digital Loan Discovery')).toBeInTheDocument()
    expect(navigation.getByText('RAG Policy Assistant')).toBeInTheDocument()
    expect(navigation.getByText('AI-Assisted Loan Processing')).toBeInTheDocument()
  })

  it('uses the EMI API and renders its returned result', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/api/v1/health')) return new Response(JSON.stringify({ status: 'UP', database: 'UP' }), { status: 200 })
      if (url.includes('/api/v1/loan-products')) return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }), { status: 200 })
      if (url.includes('/api/v1/policies/documents')) return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }), { status: 200 })
      if (url.endsWith('/api/v1/loan-discovery/emi')) return new Response(JSON.stringify({ estimatedEmi: 8884.88, totalAmountPayable: 106618.56, totalInterestPayable: 6618.56 }), { status: 200 })
      return new Response(JSON.stringify({ message: 'Not found' }), { status: 404 })
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Calculate EMI' }))
    await waitFor(() => expect(screen.getByText(/Estimated EMI:/)).toBeInTheDocument())
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/loan-discovery/emi'), expect.objectContaining({ method: 'POST' }))
  })

  it('shows backend errors instead of swallowing them', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/api/v1/health')) return new Response(JSON.stringify({ status: 'UP', database: 'UP' }), { status: 200 })
      if (url.includes('/api/v1/loan-products')) return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }), { status: 200 })
      if (url.includes('/api/v1/policies/documents')) return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }), { status: 200 })
      return new Response(JSON.stringify({ message: 'Database unavailable' }), { status: 503 })
    }))
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Calculate EMI' }))
    expect(await screen.findAllByRole('alert')).toHaveLength(1)
    expect(screen.getByRole('alert')).toHaveTextContent('Database unavailable')
  })
})
