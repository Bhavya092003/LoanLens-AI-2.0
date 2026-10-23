import { API_BASE_URL } from './config'

export interface Product { productId: string; productName: string; productCode: string; loanType: string; description: string | null; minimumAmount: number; maximumAmount: number; minimumAnnualInterestRate: number; maximumAnnualInterestRate: number; minimumTenureMonths: number; maximumTenureMonths: number; minimumMonthlyIncome: number | null; active: boolean }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Emi { estimatedEmi: number; totalAmountPayable: number; totalInterestPayable: number; currency: string }
export interface Recommendation { productId: string; productName: string; indicativeInterestRate: number; tenureMonths: number; estimatedEmi: number; reason: string; eligibilityIndicator: string }
export interface Discovery { recommendations: Recommendation[]; disclaimer: string; clarifyingQuestion: string | null }
export interface PolicyDocument { documentId: string; documentName: string; documentType: string; version: string; effectiveDate: string | null; status: string; processingError: string | null }
export interface PolicyPage { pageNumber: number; text: string }
export interface PolicyAnswer { answer: string; confidence: string; followUpRequired: boolean; sources: Array<{document:string;section:string|null;version:string;effectiveDate:string|null;page:number|null;chunkId:string}> }
export interface AppDocument { documentId: string; documentType: string; originalFilename: string; contentType: string; fileSizeBytes: number; documentStatus: string }
export interface Check { checkId: string; agentType: string; checkStatus: string; resultDetails: Record<string, unknown>; requiresHumanReview: boolean }
export interface Review { applicationId: string; applicationReference: string; recommendation: string; missingDocuments: string[]; nextSteps: string[]; humanApprovalRequired: boolean; checks: Check[]; automatedFindings: string[]; humanDecision: string | null; humanReviewer: string | null; humanNote: string | null; humanDecisionAt: string | null; requestedDocuments: string[] }
export interface Application { applicationId: string; applicationReference: string; applicationStatus: string; requestedAmount: number; updatedAt: string }
export interface Customer { userId: string; fullName: string; email: string | null }

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('loanlens_access_token')
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })
  const body: unknown = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const message = typeof body === 'object' && body !== null && 'message' in body && typeof body.message === 'string'
      ? body.message : `Request failed (${response.status})`
    throw new Error(message)
  }
  if (body === null || typeof body !== 'object') throw new Error('The server returned an invalid response. Please retry or contact support.')
  return body as T
}

export const json = (value: unknown): RequestInit => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) })
