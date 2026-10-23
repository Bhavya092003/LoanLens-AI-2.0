import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import HealthIndicator from './components/HealthIndicator'
import { api, json, type Application, type AppDocument, type Discovery, type Emi, type Page, type PolicyAnswer, type PolicyDocument, type PolicyPage, type Product, type Review } from './api/client'
import { API_BASE_URL } from './api/config'

type Identity = { userId: string; username: string; role: string }

function AuthPage({ onIdentityChange }: { onIdentityChange: (identity: Identity) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [registering, setRegistering] = useState(false)
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState('')
  async function login(event: FormEvent) {
    event.preventDefault(); setError('')
    const endpoint = registering ? '/api/v1/auth/register' : '/api/v1/auth/login'
    const body = registering ? { username, password, fullName } : { username, password }
    const response = await fetch(`${API_BASE_URL}${endpoint}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    if (!response.ok) { setError('Invalid username or password.'); return }
    const result = await response.json() as { userId: string; username: string; role: string; token: string }
    localStorage.setItem('loanlens_access_token', result.token)
    onIdentityChange(result); setPassword('')
  }
  return <div className="auth-page"><div className="auth-card"><div className="auth-brand"><span className="brand-mark">L</span><span>Lendwise<span className="brand-period">.</span></span></div><p className="eyebrow">DIGITAL LENDING PLATFORM</p><h1>{registering ? 'Create your account.' : 'Welcome back.'}</h1><p className="auth-description">{registering ? 'Register as a customer to start and track your loan application.' : 'Sign in to continue to your lending workspace.'}</p><form className="auth-form" onSubmit={login}>{registering && <label>Full name<input value={fullName} onChange={(e) => setFullName(e.target.value)} required /></label>}<label>Username<input value={username} onChange={(e) => setUsername(e.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required /></label><button className="auth-submit">{registering ? 'Create customer account' : 'Sign in'}</button>{error && <span className="error-message" role="alert">{error}</span>}</form><button className="auth-switch" type="button" onClick={() => { setRegistering(!registering); setError('') }}>{registering ? 'Already registered? Sign in' : 'New customer? Create an account'}</button><p className="auth-role-note">Reviewer accounts are provisioned by the lending administrator.</p></div></div>
}

const modules = [
  { number: '01', title: 'Digital Loan Discovery', description: 'Browse persisted catalogue products, calculate indicative EMI, and request catalogue-grounded recommendations.', target: 'discovery' },
  { number: '02', title: 'RAG Policy Assistant', description: 'Upload versioned policies, inspect extracted pages, and ask questions grounded in indexed policy evidence.', target: 'policy' },
  { number: '03', title: 'AI-Assisted Loan Processing', description: 'Create an application, upload required documents, and run deterministic checks for human review.', target: 'applications' },
]

function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function run(action: () => Promise<void>) {
    setBusy(true); setError('')
    try { await action() } catch (failure) { setError(failure instanceof Error ? failure.message : 'The request failed. Please try again.') }
    finally { setBusy(false) }
  }
  return { busy, error, setError, run }
}

function LoanDiscovery() {
  const [products, setProducts] = useState<Product[]>([])
  const [emi, setEmi] = useState<Emi | null>(null)
  const [discovery, setDiscovery] = useState<Discovery | null>(null)
  const [amount, setAmount] = useState('500000')
  const [income, setIncome] = useState('60000')
  const [tenure, setTenure] = useState('60')
  const [rate, setRate] = useState('12')
  const action = useAction()
  useEffect(() => { api<Page<Product>>('/api/v1/loan-products?size=100').then((page) => setProducts(page.content)).catch((e: unknown) => action.setError(e instanceof Error ? e.message : 'Could not load products')) }, [])
  function calculate(event: FormEvent) { event.preventDefault(); void action.run(async () => { setEmi(await api<Emi>('/api/v1/loan-discovery/emi', json({ loanAmount: Number(amount), annualInterestRate: Number(rate), tenureMonths: Number(tenure) }))) }) }
  function recommend(event: FormEvent) { event.preventDefault(); void action.run(async () => { setDiscovery(await api<Discovery>('/api/v1/loan-discovery/recommendations', json({ requestedAmount: Number(amount), monthlyIncome: Number(income), preferredTenureMonths: Number(tenure) }))) }) }
  return <section className="feature-panel">
    <h3>Loan catalogue and EMI calculator</h3><p>Catalogue entries come from the backend database. EMI and matches are indicative, not an offer or approval.</p>
    <form className="feature-form" onSubmit={calculate}><label>Loan amount (INR)<input required type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></label><label>Annual rate (%)<input required type="number" min="0" max="100" step="0.01" value={rate} onChange={(e) => setRate(e.target.value)} /></label><label>Tenure (months)<input required type="number" min="1" max="600" value={tenure} onChange={(e) => setTenure(e.target.value)} /></label><button disabled={action.busy}>Calculate EMI</button></form>
    {emi && <p className="result-box">Estimated EMI: ₹{emi.estimatedEmi.toLocaleString('en-IN')} · Total payable ₹{emi.totalAmountPayable.toLocaleString('en-IN')} · Interest ₹{emi.totalInterestPayable.toLocaleString('en-IN')}</p>}
    <form className="feature-form" onSubmit={recommend}><label>Monthly income (INR)<input required type="number" min="0" step="0.01" value={income} onChange={(e) => setIncome(e.target.value)} /></label><button disabled={action.busy}>Find matching products</button></form>
    {discovery && <div className="result-box"><strong>Recommendations</strong>{discovery.recommendations.length === 0 ? <p>No persisted catalogue products match these inputs.</p> : discovery.recommendations.map((item) => <p key={item.productId}><b>{item.productName}</b> · estimated EMI ₹{item.estimatedEmi.toLocaleString('en-IN')} · {item.eligibilityIndicator}<br />{item.reason}</p>)}{discovery.clarifyingQuestion && <p>{discovery.clarifyingQuestion}</p>}<small>{discovery.disclaimer}</small></div>}
    <h4>Available catalogue products ({products.length})</h4>{products.length === 0 ? <p>No products are currently available from the API.</p> : <ul>{products.map((product) => <li key={product.productId}><b>{product.productName}</b> · {product.loanType} · ₹{product.minimumAmount.toLocaleString('en-IN')}–₹{product.maximumAmount.toLocaleString('en-IN')} · {product.minimumAnnualInterestRate}%–{product.maximumAnnualInterestRate}%</li>)}</ul>}
    {action.error && <p className="error-message" role="alert">{action.error}</p>}
  </section>
}

function PolicyAssistant() {
  const [documents, setDocuments] = useState<PolicyDocument[]>([])
  const [selected, setSelected] = useState('')
  const [pages, setPages] = useState<PolicyPage[]>([])
  const [answer, setAnswer] = useState<PolicyAnswer | null>(null)
  const [question, setQuestion] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const action = useAction()
  const refresh = () => api<Page<PolicyDocument>>('/api/v1/policies/documents?size=100').then((p) => setDocuments(p.content))
  useEffect(() => { refresh().catch((e: unknown) => action.setError(e instanceof Error ? e.message : 'Could not load policies')) }, [])
  function upload(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); if (!file) { action.setError('Choose a policy PDF.'); return } data.set('file', file); void action.run(async () => { await api<PolicyDocument>('/api/v1/policies/documents', { method: 'POST', body: data }); form.reset(); setFile(null); await refresh() }) }
  function loadPages(documentId: string) { void action.run(async () => { const page = await api<Page<PolicyPage>>(`/api/v1/policies/documents/${documentId}/pages?size=100`); setSelected(documentId); setPages(page.content) }) }
  function retry(documentId: string) { void action.run(async () => { await api<PolicyDocument>(`/api/v1/policies/documents/${documentId}/retry`, { method: 'POST' }); await refresh() }) }
  function ask(event: FormEvent) { event.preventDefault(); void action.run(async () => setAnswer(await api<PolicyAnswer>('/api/v1/policy-questions', json({ question })))) }
  return <section className="feature-panel">
    <h3>Policy document assistant</h3><p>PDF extraction, embeddings, and answers require the reviewed pgvector migration and an AI provider key. Retrieved text is treated as untrusted evidence.</p>
    <form className="feature-form" onSubmit={upload}><label>Policy name<input name="documentName" required maxLength={240} /></label><label>Type<input name="documentType" required maxLength={64} defaultValue="LENDING_POLICY" /></label><label>Version<input name="version" required maxLength={64} /></label><label>Effective date<input name="effectiveDate" type="date" /></label><label>PDF file<input required type="file" accept="application/pdf,.pdf" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label><button disabled={action.busy}>Upload and process</button></form>
    <h4>Policy versions</h4>{documents.length === 0 ? <p>No policy documents have been uploaded.</p> : <ul>{documents.map((doc) => <li key={doc.documentId}><b>{doc.documentName}</b> · v{doc.version} · {doc.status} {doc.processingError && <span>· {doc.processingError}</span>} <button type="button" onClick={() => loadPages(doc.documentId)}>View extracted pages</button>{doc.status === 'FAILED' && <button type="button" onClick={() => retry(doc.documentId)}>Retry</button>}</li>)}</ul>}
    {selected && <div className="result-box"><h4>Extracted text</h4>{pages.map((page) => <p key={page.pageNumber}><b>Page {page.pageNumber}</b><br />{page.text || '[No text extracted on this page]'}</p>)}</div>}
    <form className="feature-form" onSubmit={ask}><label>Ask a policy question<textarea required maxLength={4000} value={question} onChange={(e) => setQuestion(e.target.value)} /></label><button disabled={action.busy}>Ask from indexed policy evidence</button></form>
    {answer && <div className="result-box"><b>{answer.confidence}</b><p>{answer.answer}</p>{answer.followUpRequired && <p>Follow-up is required.</p>}<ul>{answer.sources.map((source) => <li key={source.chunkId}>{source.document} · v{source.version} · page {source.page ?? 'not recorded'} · chunk {source.chunkId}</li>)}</ul></div>}
    {action.error && <p className="error-message" role="alert">{action.error}</p>}
  </section>
}

function ApplicationWorkflow({ identity }: { identity: Identity | null }) {
  const [products, setProducts] = useState<Product[]>([])
  const [application, setApplication] = useState<Application | null>(null)
  const [documents, setDocuments] = useState<AppDocument[]>([])
  const [review, setReview] = useState<Review | null>(null)
  const [reviewQueue, setReviewQueue] = useState<Application[]>([])
  const [file, setFile] = useState<File | null>(null)
  const action = useAction()
  const isCustomer = identity?.role === 'CUSTOMER'
  const isReviewer = identity?.role === 'REVIEWER'
  useEffect(() => { api<Page<Product>>('/api/v1/loan-products?size=100').then((p) => setProducts(p.content)).catch((e: unknown) => action.setError(e instanceof Error ? e.message : 'Could not load products')) }, [])
  useEffect(() => {
    if (!isReviewer) { setReviewQueue([]); return }
    api<Application[]>('/api/v1/applications/review-queue').then(setReviewQueue).catch((e: unknown) => action.setError(e instanceof Error ? e.message : 'Could not load reviewer tasks'))
  }, [isReviewer])
  function create(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!identity) return; const form = new FormData(event.currentTarget); void action.run(async () => { const productId = String(form.get('productId')); const created = await api<Application>('/api/v1/applications', json({ userId: identity.userId, loanProductId: productId || null, requestedAmount: Number(form.get('amount')), requestedTenureMonths: Number(form.get('tenure')), monthlyIncome: Number(form.get('income')) })); setApplication(created); setReview(null); setDocuments([]) }) }
  function upload(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!application || !file) return; const type = String(new FormData(event.currentTarget).get('documentType')); const data = new FormData(); data.set('documentType', type); data.set('file', file); void action.run(async () => { await api<AppDocument>(`/api/v1/applications/${application.applicationId}/documents`, { method: 'POST', body: data }); setFile(null); const page = await api<Page<AppDocument>>(`/api/v1/applications/${application.applicationId}/documents?size=100`); setDocuments(page.content) }) }
  function submitApplication() { if (!application) return; void action.run(async () => setApplication(await api<Application>(`/api/v1/applications/${application.applicationId}/submit`, { method: 'POST' }))) }
  function runReview() { if (!application) return; void action.run(async () => setReview(await api<Review>(`/api/v1/applications/${application.applicationId}/review`, json({ requestedChecks: ['DOCUMENT_VERIFICATION', 'CREDIT_ASSESSMENT', 'COMPLIANCE'] })))) }
  function decide(decision: string) {
    if (!application) return
    const note = window.prompt('Add a reviewer note (optional):') ?? ''
    const requestedDocumentTypes = decision === 'REQUEST_DOCUMENTS'
      ? (window.prompt('Which documents should the customer upload? Use comma-separated types, e.g. ADDRESS, INCOME:') ?? '').split(',').map((value) => value.trim()).filter(Boolean)
      : []
    if (decision === 'REQUEST_DOCUMENTS' && requestedDocumentTypes.length === 0) return
    void action.run(async () => {
      const updated = await api<Application>(`/api/v1/applications/${application.applicationId}/decision`, json({ decision, note, requestedDocumentTypes }))
      setApplication(updated)
      setReviewQueue((current) => current.filter((item) => item.applicationId !== updated.applicationId))
      setReview(await api<Review>(`/api/v1/applications/${updated.applicationId}/review`))
    })
  }
  function loadReview() { if (!application) return; void action.run(async () => setReview(await api<Review>(`/api/v1/applications/${application.applicationId}/review`))) }
  return <section className="feature-panel"><h3>Loan application and review</h3><p>Customer users create applications and upload documents. Reviewer users process the assigned review queue; automated checks are informational and cannot approve or reject lending.</p>
    {!identity && <p className="result-box">Sign in as a customer to create an application or as a reviewer to process review tasks.</p>}
    {isReviewer && <div className="result-box"><h4>Pending review stack ({reviewQueue.length})</h4><p>These applications are submitted or awaiting a human review step. Automated checks support your decision; they do not approve or reject a loan.</p>{reviewQueue.length === 0 ? <p>No applications are currently awaiting review.</p> : <div className="review-stack">{reviewQueue.map((task) => <button className="review-card" type="button" key={task.applicationId} onClick={() => setApplication(task)}><strong>{task.applicationReference}</strong><span>{task.applicationStatus}</span><small>₹{task.requestedAmount.toLocaleString('en-IN')} · updated {new Date(task.updatedAt).toLocaleString()}</small></button>)}</div>}</div>}
    {isCustomer && <form className="feature-form" onSubmit={create}><label>Loan product<select name="productId"><option value="">No catalogue product</option>{products.map((p) => <option key={p.productId} value={p.productId}>{p.productName}</option>)}</select></label><label>Requested amount<input required name="amount" type="number" min="0.01" step="0.01" defaultValue="250000" /></label><label>Tenure (months)<input required name="tenure" type="number" min="1" max="600" defaultValue="36" /></label><label>Declared monthly income<input name="income" type="number" min="0" step="0.01" defaultValue="50000" /></label><button disabled={action.busy}>Create application</button></form>}
    {application && <div className="result-box"><b>{application.applicationReference}</b> · <strong>{application.applicationStatus}</strong>{isCustomer && <form className="feature-form" onSubmit={upload}><label>Document type<select name="documentType"><option>IDENTITY</option><option>ADDRESS</option><option>INCOME</option><option>OTHER</option></select></label><label>File<input required type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label><button disabled={action.busy}>Upload document</button></form>}{isCustomer && application.applicationStatus === 'DRAFT' && <button type="button" disabled={action.busy} onClick={submitApplication}>Submit for reviewer</button>}{isReviewer && <button type="button" disabled={action.busy} onClick={runReview}>Run reviewer checks</button>}
      <ul>{documents.map((doc) => <li key={doc.documentId}>{doc.documentType} · {doc.originalFilename} · {doc.documentStatus}</li>)}</ul>{isCustomer && application.applicationStatus !== 'DRAFT' && <button type="button" disabled={action.busy} onClick={loadReview}>Check review status and requests</button>}{isReviewer && <div className="decision-panel"><h4>Human decision</h4><p>Review the uploaded documents and automated findings, then record what you checked and what must happen next.</p><div className="decision-actions"><button type="button" disabled={action.busy} onClick={() => decide('APPROVE')}>Approve</button><button type="button" disabled={action.busy} onClick={() => decide('DECLINE')}>Decline</button><button type="button" disabled={action.busy} onClick={() => decide('REQUEST_DOCUMENTS')}>Request documents</button></div></div>}
    </div>}
    {review && <div className="result-box review-detail"><h4>Review record: {review.recommendation}</h4><p><b>Human decision:</b> {review.humanDecision ?? 'Not recorded yet'}</p>{review.humanReviewer && <p><b>Reviewed by:</b> {review.humanReviewer} · {review.humanDecisionAt ? new Date(review.humanDecisionAt).toLocaleString() : ''}</p>}{review.humanNote && <p><b>Reviewer note:</b> {review.humanNote}</p>}<h4>What automated checks found</h4><ul>{review.automatedFindings.map((finding) => <li key={finding}>{finding}</li>)}</ul>{review.requestedDocuments.length > 0 && <p className="follow-up-alert"><b>Customer must upload:</b> {review.requestedDocuments.join(', ')}</p>}{review.nextSteps.map((step) => <p key={step}>{step}</p>)}<h4>Check details</h4><ul>{review.checks.map((check) => <li key={check.checkId}>{check.agentType}: {check.checkStatus} {check.requiresHumanReview ? '(human review required)' : ''}<pre>{JSON.stringify(check.resultDetails, null, 2)}</pre></li>)}</ul></div>}
    {action.error && <p className="error-message" role="alert">{action.error}</p>}
  </section>
}

function ReviewerWorkspace({ identity, onSignOut }: { identity: Identity; onSignOut: () => void }) {
  return <div className="reviewer-shell"><header className="reviewer-header"><div><div className="auth-brand"><span className="brand-mark">L</span><span>Lendwise<span className="brand-period">.</span></span></div><p className="eyebrow">REVIEWER WORKSPACE</p><h1>Human review queue</h1><p>Review submitted applications, inspect automated checks, and record the human assessment workflow.</p></div><div className="reviewer-user"><span>{identity.username} · REVIEWER</span><button type="button" onClick={onSignOut}>Log out</button></div></header><main className="reviewer-main"><ApplicationWorkflow identity={identity} /></main></div>
}

export default function App() {
  const [identity, setIdentity] = useState<Identity | null>(null)
  useEffect(() => {
    const token = localStorage.getItem('loanlens_access_token')
    if (!token) return
    fetch(`${API_BASE_URL}/api/v1/auth/me`, { headers: { Authorization: `Bearer ${token}` } })
      .then((response) => response.ok ? response.json() as Promise<{ userId: string; username: string; roles: string[] }> : null)
      .then((current) => current && setIdentity({ userId: current.userId, username: current.username, role: current.roles[0] }))
      .catch(() => { localStorage.removeItem('loanlens_access_token') })
  }, [])
  if (!identity) return <AuthPage onIdentityChange={setIdentity} />
  function signOut() { localStorage.removeItem('loanlens_access_token'); setIdentity(null) }
  if (identity.role === 'REVIEWER') return <ReviewerWorkspace identity={identity} onSignOut={signOut} />
  return <div className="app-shell"><aside className="sidebar"><a className="brand" href="#home" aria-label="Lendwise home"><span className="brand-mark">L</span><span>Lendwise<span className="brand-period">.</span></span></a><p className="nav-label">WORKSPACE</p><nav aria-label="Main navigation"><a className="nav-link nav-link--active" href="#home"><span>⌂</span> Overview</a>{modules.map((module) => <a className="nav-link" href={`#module-${module.number}`} key={module.number}><span>{module.number}</span>{module.title}</a>)}</nav><div className="sidebar-footer">SIGNED IN AS {identity.username.toUpperCase()}<br /><span>{identity.role} · <button className="sidebar-signout" type="button" onClick={signOut}>Log out</button></span></div></aside>
    <main className="main-content" id="home"><header className="topbar"><div className="breadcrumb">Workspace <span>/</span> Lending demo</div><HealthIndicator /></header><section className="welcome"><div className="welcome-copy"><p className="eyebrow">LENDING, MADE CLEARER</p><h1>A smarter path<br />to <em>what’s next.</em></h1><p className="welcome-description">Explore the real catalogue, review policy evidence, and move demo applications through deterministic checks.</p><a className="text-link" href="#modules">Explore the platform <span aria-hidden="true">→</span></a></div><div className="welcome-art" aria-hidden="true"><div className="art-orbit art-orbit--one" /><div className="art-orbit art-orbit--two" /><div className="art-card"><span className="art-card-label">YOUR NEXT STEP</span><span className="art-card-line" /><span className="art-card-line art-card-line--short" /><span className="art-card-dot">↗</span></div><span className="art-star art-star--one">✳</span><span className="art-star art-star--two">✦</span></div></section>
      <section className="modules-section" id="modules"><div className="section-heading"><div><p className="eyebrow">THE PLATFORM</p><h2>Three ways forward</h2></div><span className="section-note">Connected to the Spring Boot API</span></div><div className="module-grid">{modules.map((module) => <article className="module-card" id={`module-${module.number}`} key={module.number}><div className="module-card-top"><span className="module-number">{module.number}</span><a className="module-arrow" href={`#${module.target}`} aria-label={`Open ${module.title}`}>↗</a></div><div><span className="module-status">Live API workflow</span><h3>{module.title}</h3><p>{module.description}</p></div><a className="text-link" href={`#${module.target}`}>Open workflow →</a></article>)}</div></section>
      <section id="discovery"><LoanDiscovery /></section><section id="policy"><PolicyAssistant /></section><section id="applications"><ApplicationWorkflow identity={identity} /></section><footer className="page-footer"><span>Indicative tools only. Consequential lending decisions require human review.</span><span>© Lendwise · Demo MVP</span></footer>
    </main></div>
}
