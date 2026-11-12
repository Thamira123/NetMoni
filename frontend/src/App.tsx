import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Client } from '@stomp/stompjs'
import {
  Activity, AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight, Bell,
  Check, ChevronDown, CircleHelp, Clock3, Command, Cpu, Eye, EyeOff,
  Gauge, LayoutDashboard, LogOut, Menu, Network, RadioTower, RefreshCw,
  Router, Search, Server, Settings2, ShieldCheck, Signal, X,
} from 'lucide-react'
import './App.css'

type Role = 'ADMIN' | 'OPERATOR' | 'VIEWER'
type ViewKey = 'overview' | 'devices' | 'agents' | 'alerts'

interface AuthSession { accessToken: string; username: string; role: Role; demo?: boolean }
interface Page<T> { content: T[]; totalElements: number }
interface MonitoringResult {
  resultId: string; deviceId: string; agentId: string; checkType: string
  responseTimeMs: number | null; available: boolean; packetLossPercent: number | null
  checkedAt: string; errorMessage: string | null
}
interface DashboardSummary {
  totalAgents: number; onlineAgents: number; offlineAgents: number
  totalMonitoredDevices: number; onlineDevices: number; offlineDevices: number
  availabilityPercent24h: number; generatedAt: string; recentActivities: MonitoringResult[]
}
interface Device {
  deviceId: string; name: string; ipAddress: string; hostname: string | null
  deviceType: string; location: string | null; agentId: string
  status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'; monitoringEnabled: boolean; updatedAt: string
}
interface Agent {
  agentId: string; name: string; segmentCidr: string; version: string
  status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'; registeredAt: string; lastHeartbeat: string | null
}
interface Alert {
  alertId: string; type: string; severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  status: 'UNREAD' | 'READ' | 'RESOLVED'; message: string
  deviceId: string | null; agentId: string | null; createdAt: string
}
interface Snapshot { summary: DashboardSummary; devices: Device[]; agents: Agent[]; alerts: Alert[] }

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const navItems: { key: ViewKey; label: string; icon: typeof LayoutDashboard }[] = [
  { key: 'overview', label: 'Overview', icon: LayoutDashboard },
  { key: 'devices', label: 'Devices', icon: Router },
  { key: 'agents', label: 'Agents', icon: RadioTower },
  { key: 'alerts', label: 'Alerts', icon: Bell },
]

function readSession(): AuthSession | null {
  try {
    const saved = sessionStorage.getItem('netmoni-session')
    return saved ? JSON.parse(saved) as AuthSession : null
  } catch { return null }
}

async function request<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const body = await response.text()
    let message = body
    try {
      const parsed = JSON.parse(body) as { message?: string; error?: string }
      message = parsed.message || parsed.error || body
    } catch { /* Keep the plain response body when it is not JSON. */ }
    throw new Error(message || `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return await response.json() as T
}

async function fetchSnapshot(token: string): Promise<Snapshot> {
  const [summary, devicePage, agentPage, alertPage] = await Promise.all([
    request<DashboardSummary>('/dashboard/summary', token),
    request<Page<Device>>('/devices?page=0&size=100', token),
    request<Page<Agent>>('/agents?page=0&size=100', token),
    request<Page<Alert>>('/alerts?status=UNREAD&page=0&size=50', token),
  ])
  return { summary, devices: devicePage.content, agents: agentPage.content, alerts: alertPage.content }
}

function createDemoSnapshot(): Snapshot {
  const timestamp = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60_000).toISOString()
  const devices: Device[] = [
    { deviceId: 'demo-device-01', name: 'Core switch 01', ipAddress: '10.0.0.1', hostname: 'core-sw-01', deviceType: 'SWITCH', location: 'Colombo · Rack 02', agentId: 'demo-agent-01', status: 'ONLINE', monitoringEnabled: true, updatedAt: timestamp(1) },
    { deviceId: 'demo-device-02', name: 'Gateway router', ipAddress: '10.0.0.254', hostname: 'edge-rtr-01', deviceType: 'ROUTER', location: 'Colombo · Edge', agentId: 'demo-agent-01', status: 'ONLINE', monitoringEnabled: true, updatedAt: timestamp(2) },
    { deviceId: 'demo-device-03', name: 'Backup storage', ipAddress: '10.0.1.18', hostname: 'nas-backup-01', deviceType: 'SERVER', location: 'Colombo · Rack 04', agentId: 'demo-agent-02', status: 'OFFLINE', monitoringEnabled: true, updatedAt: timestamp(5) },
    { deviceId: 'demo-device-04', name: 'Lobby access point', ipAddress: '10.0.2.14', hostname: 'ap-lobby-01', deviceType: 'ACCESS_POINT', location: 'Kandy · Floor 01', agentId: 'demo-agent-03', status: 'ONLINE', monitoringEnabled: true, updatedAt: timestamp(3) },
    { deviceId: 'demo-device-05', name: 'Database node 02', ipAddress: '10.0.1.22', hostname: 'db-node-02', deviceType: 'SERVER', location: 'Colombo · Rack 05', agentId: 'demo-agent-02', status: 'ONLINE', monitoringEnabled: true, updatedAt: timestamp(4) },
    { deviceId: 'demo-device-06', name: 'Floor 2 switch', ipAddress: '10.0.2.2', hostname: 'sw-floor-02', deviceType: 'SWITCH', location: 'Kandy · Floor 02', agentId: 'demo-agent-03', status: 'ONLINE', monitoringEnabled: true, updatedAt: timestamp(6) },
  ]
  const agents: Agent[] = [
    { agentId: 'demo-agent-01', name: 'Colombo edge', segmentCidr: '10.0.0.0/24', version: '1.4.2', status: 'ONLINE', registeredAt: timestamp(8000), lastHeartbeat: timestamp(1) },
    { agentId: 'demo-agent-02', name: 'Colombo core', segmentCidr: '10.0.1.0/24', version: '1.4.2', status: 'ONLINE', registeredAt: timestamp(6000), lastHeartbeat: timestamp(2) },
    { agentId: 'demo-agent-03', name: 'Kandy office', segmentCidr: '10.0.2.0/24', version: '1.3.8', status: 'OFFLINE', registeredAt: timestamp(4500), lastHeartbeat: timestamp(130) },
  ]
  const latencies = [31, 28, 33, 26, 29, 24, 27, 22, 25, 21]
  const recentActivities: MonitoringResult[] = latencies.map((responseTimeMs, index) => ({
    resultId: `demo-result-${index}`,
    deviceId: devices[index % devices.length].deviceId,
    agentId: devices[index % devices.length].agentId,
    checkType: index % 3 === 0 ? 'PING' : 'TCP',
    responseTimeMs,
    available: index !== 6,
    packetLossPercent: 0,
    checkedAt: timestamp((latencies.length - index) * 4),
    errorMessage: null,
  }))
  const alerts: Alert[] = [
    { alertId: 'demo-alert-01', type: 'DEVICE_UNREACHABLE', severity: 'HIGH', status: 'UNREAD', message: 'Backup storage has missed consecutive health checks.', deviceId: devices[2].deviceId, agentId: 'demo-agent-02', createdAt: timestamp(18) },
    { alertId: 'demo-alert-02', type: 'AGENT_HEARTBEAT_DELAY', severity: 'MEDIUM', status: 'UNREAD', message: 'Kandy office agent has not reported within the expected interval.', deviceId: null, agentId: 'demo-agent-03', createdAt: timestamp(42) },
  ]
  return {
    summary: { totalAgents: 12, onlineAgents: 11, offlineAgents: 1, totalMonitoredDevices: 148, onlineDevices: 143, offlineDevices: 5, availabilityPercent24h: 99.42, generatedAt: timestamp(0), recentActivities },
    devices, agents, alerts,
  }
}

function formatTime(value?: string | null) {
  if (!value) return 'No heartbeat yet'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit' }).format(date)
}
function formatRelative(value?: string | null) {
  if (!value) return 'No recent check'
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000))
  if (seconds < 60) return `${seconds}s ago`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`
  return `${Math.floor(seconds / 86400)}d ago`
}
function initials(name: string) {
  return name.split(/[.@\s_-]+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('')
}

function App() {
  const [session, setSession] = useState<AuthSession | null>(readSession)
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [view, setView] = useState<ViewKey>('overview')
  const [live, setLive] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  useEffect(() => {
    if (!session || session.demo) return
    let active = true
    const synchronize = async () => {
      if (!active) return
      setLoading(true)
      try {
        const next = await fetchSnapshot(session.accessToken)
        if (active) { setSnapshot(next); setError('') }
      } catch (reason) {
        if (active) setError(reason instanceof Error ? reason.message : 'Unable to reach the API')
      } finally { if (active) setLoading(false) }
    }
    void synchronize()
    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${session.accessToken}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setLive(true)
        for (const topic of ['/topic/dashboard', '/topic/devices', '/topic/monitoring', '/topic/alerts', '/topic/agents']) {
          client.subscribe(topic, () => void synchronize())
        }
      },
      onWebSocketClose: () => setLive(false),
      onStompError: () => setLive(false),
    })
    client.activate()
    const intervalId = window.setInterval(() => void synchronize(), 30000)
    return () => { active = false; window.clearInterval(intervalId); void client.deactivate(); setLive(false) }
  }, [session, refreshKey])

  function signOut() {
    sessionStorage.removeItem('netmoni-session')
    setSession(null); setSnapshot(null); setError('')
  }
  async function updateAlert(alertId: string, action: 'read' | 'resolve') {
    if (!session || session.demo) return
    try {
      await request(`/alerts/${alertId}/${action}`, session.accessToken, { method: 'PATCH' })
      setRefreshKey((key) => key + 1)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not update alert') }
  }

  if (!session) return <AuthScreen onAuthenticated={setSession} onDemo={() => { setSnapshot(createDemoSnapshot()); setSession({ accessToken: '', username: 'preview@netmoni.local', role: 'VIEWER', demo: true }) }} />
  const summary = snapshot?.summary
  const title = navItems.find((item) => item.key === view)?.label ?? 'Overview'
  const matchingDevices = (snapshot?.devices ?? []).filter((device) =>
    `${device.name} ${device.ipAddress} ${device.hostname ?? ''} ${device.location ?? ''}`.toLowerCase().includes(search.toLowerCase()),
  )

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNavOpen ? 'sidebar-open' : ''}`}>
      <div className="brand-lockup"><div className="brand-mark"><Network size={20} strokeWidth={2.2} /></div><div className="brand-copy"><strong>netmoni</strong><span>NETWORK INTELLIGENCE</span></div><button className="icon-button sidebar-close" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation"><X size={18} /></button></div>
      <div className="workspace-switcher"><span className="workspace-icon"><Command size={15} /></span><span><strong>Core network</strong><small>Workspace</small></span><ChevronDown size={15} className="muted-icon" /></div>
      <p className="nav-caption">MONITOR</p>
      <nav className="primary-nav" aria-label="Main navigation">{navItems.map(({ key, label, icon: Icon }) => <button key={key} className={`nav-link ${view === key ? 'nav-link-active' : ''}`} onClick={() => { setView(key); setMobileNavOpen(false) }}><Icon size={18} strokeWidth={1.8} /><span>{label}</span>{key === 'alerts' && (snapshot?.alerts.length ?? 0) > 0 && <span className="nav-count">{snapshot?.alerts.length}</span>}</button>)}</nav>
      <p className="nav-caption nav-caption-spaced">SYSTEM</p>
      <button className="nav-link" onClick={() => setError('Settings are managed by your network administrator.')}><Settings2 size={18} strokeWidth={1.8} /><span>Settings</span></button>
      <button className="nav-link" onClick={() => setError('API docs: open docs/backend-api.md in the repository.')}><CircleHelp size={18} strokeWidth={1.8} /><span>Documentation</span><ArrowUpRight size={13} className="nav-external" /></button>
      <div className="sidebar-spacer" />
      <div className="network-status-card"><div className="network-status-heading"><span className={`status-pulse ${live ? '' : 'status-pulse-off'}`} /><strong>{live ? 'Live connection' : session.demo ? 'Demo preview' : 'Reconnecting'}</strong></div><p>{live ? 'Receiving network events' : session.demo ? 'Sample data · read only' : 'Trying to reach the event stream'}</p><div className="status-track"><span style={{ width: live ? '100%' : session.demo ? '68%' : '38%' }} /></div></div>
      <button className="profile-row" onClick={signOut} title={session.demo ? 'Exit preview' : 'Sign out'}><span className="avatar">{initials(session.username)}</span><span className="profile-copy"><strong>{session.username}</strong><small>{session.demo ? 'demo preview' : session.role.toLowerCase()}</small></span><LogOut size={16} className="profile-signout" /></button>
    </aside>
    {mobileNavOpen && <button className="nav-scrim" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation" />}

    <main className="main-area">
      <header className="topbar"><button className="icon-button mobile-menu" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation"><Menu size={20} /></button><div className="breadcrumb"><span>Network</span><span className="breadcrumb-slash">/</span><strong>{title}</strong></div><div className="topbar-actions"><span className={`connection-pill ${live ? 'connection-live' : ''}`}><span />{live ? 'Live' : session.demo ? 'Demo data' : 'Offline'}</span><span className="topbar-divider" /><span className="topbar-date"><Clock3 size={15} />{new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date())}</span><button className="icon-button help-button" onClick={() => session.demo ? setSnapshot(createDemoSnapshot()) : setRefreshKey((key) => key + 1)} title={session.demo ? 'Reset sample data' : 'Refresh data'} aria-label={session.demo ? 'Reset sample data' : 'Refresh data'}><RefreshCw size={17} className={loading ? 'spinning' : ''} /></button></div></header>
      <div className="page-content">
        <section className="page-heading"><div><div className="eyebrow"><span className="eyebrow-line" />NETWORK OPERATIONS</div><h1>{view === 'overview' ? 'Good network, good day.' : title}</h1><p>{pageDescription(view)}</p></div><div className="heading-actions">{view === 'devices' && <label className="search-box"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search devices" aria-label="Search devices" /><kbd>/</kbd></label>}<button className="button button-secondary" onClick={() => setRefreshKey((key) => key + 1)}><RefreshCw size={15} className={loading ? 'spinning' : ''} /><span>Refresh</span></button></div></section>
        {session.demo && <div className="demo-banner"><span><Eye size={16} /> Demo preview · Sample network data only</span><button onClick={signOut}>Exit preview</button></div>}
        {error && <div className="error-banner" role="alert"><AlertTriangle size={17} /><span>{error}</span><button className="icon-button" onClick={() => setError('')} aria-label="Dismiss"><X size={15} /></button></div>}
        {!snapshot && loading && <LoadingState />}
        {!snapshot && !loading && error && <ConnectionState onRetry={() => setRefreshKey((key) => key + 1)} />}
        {snapshot && view === 'overview' && <Overview summary={summary} snapshot={snapshot} onNavigate={setView} onAlertAction={updateAlert} readOnly={session.demo} />}
        {snapshot && view === 'devices' && <DevicesView devices={matchingDevices} total={snapshot.summary.totalMonitoredDevices} />}
        {snapshot && view === 'agents' && <AgentsView agents={snapshot.agents} />}
        {snapshot && view === 'alerts' && <AlertsView alerts={snapshot.alerts} onAlertAction={updateAlert} canResolve={session.role !== 'VIEWER'} readOnly={session.demo} />}
        <footer className="page-footer"><span><span className={`footer-dot ${error ? 'footer-dot-warning' : ''}`} />{session.demo ? 'DEMO DATA ONLY' : error ? 'API CONNECTION DEGRADED' : 'SYSTEM OPERATIONAL'}</span><span>Last sync {session.demo ? 'Sample data' : formatTime(summary?.generatedAt)}</span></footer>
      </div>
    </main>
  </div>
}

function pageDescription(view: ViewKey) {
  const descriptions: Record<ViewKey, string> = {
    overview: 'A clear view of your infrastructure, all in one place.',
    devices: 'Every monitored endpoint across your network.',
    agents: 'Collectors reporting into your monitoring network.',
    alerts: 'Events that need a closer look from your team.',
  }
  return descriptions[view]
}

function AuthScreen({ onAuthenticated, onDemo }: { onAuthenticated: (session: AuthSession) => void; onDemo: () => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await request<{ accessToken: string; username: string; role: Role }>(`/auth/${mode}`, undefined, { method: 'POST', body: JSON.stringify({ username, password }) })
      const next = { accessToken: result.accessToken, username: result.username, role: result.role }
      sessionStorage.setItem('netmoni-session', JSON.stringify(next)); onAuthenticated(next)
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : 'Authentication failed'
      setError(/502|failed to fetch|proxy error|networkerror/i.test(message)
        ? 'Could not reach the NetMoni API. Start the backend on port 8080 and try again.'
        : message)
    }
    finally { setBusy(false) }
  }
  return <main className="auth-screen">
    <div className="auth-topline"><div className="brand-mark"><Network size={20} strokeWidth={2.2} /></div><span>NETMONI <i>/</i> NETWORK INTELLIGENCE</span><span className="auth-region"><span className="footer-dot" /> LOCAL INSTANCE</span></div>
    <div className="auth-layout">
      <section className="auth-story"><div className="auth-story-label"><span className="status-pulse" /> MONITORING CONSOLE</div><h1>Network<br /><em>monitoring.</em></h1><div className="auth-edition">DISTRIBUTED NETWORK OPERATIONS</div><div className="signal-visual" aria-hidden="true"><div className="signal-grid" /><div className="signal-node signal-node-a"><span /><i>EDGE / 01</i></div><div className="signal-node signal-node-b"><span /><i>CORE / 02</i></div><div className="signal-node signal-node-c"><span /><i>FIELD / 03</i></div><div className="signal-path signal-path-a" /><div className="signal-path signal-path-b" /><div className="signal-center"><Network size={22} /><span>NETMONI</span></div><div className="signal-coordinates">06.9271 N<br />79.8612 E</div></div><div className="auth-proof"><span><ShieldCheck size={16} /> SESSION-BASED ACCESS</span><span>LOCAL INSTANCE</span></div></section>
      <section className="auth-panel"><div className="auth-panel-kicker">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</div><h2>{mode === 'login' ? 'Sign in to NetMoni' : 'Create your account'}</h2><p>{mode === 'login' ? 'Use your network operations account to continue.' : 'New accounts are created with viewer access.'}</p>
        <div className="auth-tabs" role="tablist" aria-label="Authentication mode"><button className={mode === 'login' ? 'auth-tab-active' : ''} onClick={() => { setMode('login'); setError('') }} role="tab" aria-selected={mode === 'login'}>Sign in</button><button className={mode === 'register' ? 'auth-tab-active' : ''} onClick={() => { setMode('register'); setError('') }} role="tab" aria-selected={mode === 'register'}>Create account</button></div>
        <form onSubmit={submit} className="auth-form"><label className="field-label" htmlFor="username">Email or username</label><input id="username" className="text-input" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="you@company.com" required /><div className="password-label-row"><label className="field-label" htmlFor="password">Password</label>{mode === 'register' && <small>10 characters minimum</small>}</div><div className="password-field"><input id="password" className="text-input" type={visible ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 10 : undefined} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" required /><button type="button" className="icon-button password-toggle" onClick={() => setVisible((value) => !value)} aria-label={visible ? 'Hide password' : 'Show password'}>{visible ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>{error && <div className="auth-error" role="alert"><AlertTriangle size={15} />{error}</div>}<button className="button button-primary auth-submit" type="submit" disabled={busy}>{busy ? <RefreshCw size={16} className="spinning" /> : <span>{mode === 'login' ? 'Continue to dashboard' : 'Create account'}</span>}{!busy && <ArrowRight size={16} />}</button></form>
        <div className="auth-footnote"><ShieldCheck size={15} /><span>Secure session · Tokens stay in this browser session</span></div>
        <button type="button" className="demo-preview-button" onClick={onDemo}><Eye size={15} /> Preview dashboard with sample data <ArrowRight size={15} /></button>
      </section>
    </div>
    <div className="auth-bottomline"><span>NETMONI NETWORK MONITORING</span><span>BUILT FOR CLARITY <span className="auth-bottom-separator">/</span> DESIGNED FOR OPERATIONS</span></div>
  </main>
}

function Overview({ summary, snapshot, onNavigate, onAlertAction, readOnly }: { summary?: DashboardSummary; snapshot: Snapshot; onNavigate: (view: ViewKey) => void; onAlertAction: (id: string, action: 'read' | 'resolve') => void; readOnly?: boolean }) {
  const chartData = [...(summary?.recentActivities ?? [])].sort((a, b) => new Date(a.checkedAt).getTime() - new Date(b.checkedAt).getTime()).map((item) => ({ time: formatTime(item.checkedAt), latency: item.responseTimeMs ?? 0, status: item.available ? 'Available' : 'Unavailable', checkType: item.checkType }))
  return <>
    <section className="metric-grid" aria-label="Network summary">
      <MetricCard label="NETWORK AVAILABILITY" value={`${(summary?.availabilityPercent24h ?? 0).toFixed(1)}%`} note="Last 24 hours" icon={<Gauge size={18} />} tone="green" trend="Observed uptime" />
      <MetricCard label="MONITORED DEVICES" value={String(summary?.totalMonitoredDevices ?? 0)} note={`${summary?.onlineDevices ?? 0} online`} icon={<Server size={18} />} tone="blue" trend={`${summary?.offlineDevices ?? 0} offline`} trendDown={(summary?.offlineDevices ?? 0) > 0} />
      <MetricCard label="REGISTERED AGENTS" value={String(summary?.totalAgents ?? 0)} note={`${summary?.onlineAgents ?? 0} reporting`} icon={<RadioTower size={18} />} tone="amber" trend={`${summary?.offlineAgents ?? 0} offline`} trendDown={(summary?.offlineAgents ?? 0) > 0} />
      <MetricCard label="UNREAD ALERTS" value={String(snapshot.alerts.length)} note="Requires review" icon={<Bell size={18} />} tone="red" trend={snapshot.alerts.length ? 'Needs attention' : 'All clear'} trendDown={snapshot.alerts.length > 0} />
    </section>
    <section className="overview-main-grid">
      <article className="panel chart-panel"><div className="panel-heading"><div><span className="panel-kicker">PERFORMANCE</span><h2>Recent response time</h2><p>Latency across your latest network checks</p></div><div className="chart-legend"><span /> Response time <span className="chart-window">LATEST CHECKS</span></div></div><div className="chart-stat"><strong>{chartData.length ? <>{chartData[chartData.length - 1].latency} <small>ms</small></> : '--'}</strong><span>Most recent check</span></div><div className="chart-wrap"><LatencyChart results={summary?.recentActivities ?? []} /></div><div className="chart-footer"><span><span className="chart-footer-dot" />Checks received from monitoring agents</span><span>{chartData.length} data points</span></div></article>
      <article className="panel availability-panel"><div className="panel-heading"><div><span className="panel-kicker">FLEET HEALTH</span><h2>Device status</h2><p>Current monitored endpoints</p></div><button className="subtle-link" onClick={() => onNavigate('devices')}>All devices <ArrowRight size={14} /></button></div><div className="fleet-donut-row"><div className="fleet-donut" style={{ '--online-share': `${summary?.totalMonitoredDevices ? ((summary.onlineDevices / summary.totalMonitoredDevices) * 100).toFixed(1) : 0}%` } as React.CSSProperties}><div><strong>{summary?.totalMonitoredDevices ?? 0}</strong><span>devices</span></div></div><div className="fleet-legend"><span><i className="legend-dot online-dot" /><span>Online</span><strong>{summary?.onlineDevices ?? 0}</strong></span><span><i className="legend-dot offline-dot" /><span>Offline</span><strong>{summary?.offlineDevices ?? 0}</strong></span><span><i className="legend-dot unknown-dot" /><span>Unknown</span><strong>{Math.max(0, (summary?.totalMonitoredDevices ?? 0) - (summary?.onlineDevices ?? 0) - (summary?.offlineDevices ?? 0))}</strong></span></div></div><div className="fleet-divider" /><div className="agent-health-row"><div className="agent-health-icon"><Signal size={17} /></div><div><strong>Agent connectivity</strong><small>{summary?.onlineAgents ?? 0} of {summary?.totalAgents ?? 0} agents online</small></div><span className={`mini-status ${summary?.offlineAgents ? 'mini-status-warning' : ''}`}>{summary?.offlineAgents ? 'CHECK' : 'HEALTHY'}</span></div></article>
    </section>
    <section className="lower-grid">
      <article className="panel table-panel"><div className="panel-heading table-panel-heading"><div><span className="panel-kicker">LATEST ACTIVITY</span><h2>Recent monitoring results</h2></div><span className="live-table-label"><span className="footer-dot" />LIVE FEED</span></div><MonitoringTable results={summary?.recentActivities ?? []} devices={snapshot.devices} /><button className="panel-footer-link" onClick={() => onNavigate('devices')}>View monitored devices <ArrowRight size={14} /></button></article>
      <article className="panel alerts-panel"><div className="panel-heading table-panel-heading"><div><span className="panel-kicker">NEEDS ATTENTION</span><h2>Open alerts</h2></div><button className="icon-button panel-icon-button" onClick={() => onNavigate('alerts')} aria-label="View all alerts"><ArrowUpRight size={17} /></button></div>{snapshot.alerts.length ? <div className="alert-list compact-alert-list">{snapshot.alerts.slice(0, 4).map((alert) => <AlertRow key={alert.alertId} alert={alert} onAction={onAlertAction} canResolve={false} readOnly={readOnly} />)}</div> : <div className="all-clear"><span><Check size={18} /></span><strong>All clear</strong><p>No unread alerts right now.</p></div>}<button className="panel-footer-link" onClick={() => onNavigate('alerts')}>Open alert center <ArrowRight size={14} /></button></article>
    </section>
  </>
}

function MetricCard({ label, value, note, icon, tone, trend, trendDown }: { label: string; value: string; note: string; icon: ReactNode; tone: string; trend: string; trendDown?: boolean }) {
  return <article className="metric-card"><div className="metric-card-top"><span>{label}</span><i className={`metric-icon metric-${tone}`}>{icon}</i></div><div className="metric-value">{value}</div><div className="metric-bottom"><span>{note}</span><span className={`metric-trend ${trendDown ? 'metric-trend-warning' : ''}`}>{trendDown ? <ArrowDownRight size={13} /> : <ArrowUpRight size={13} />}{trend}</span></div></article>
}
function LatencyChart({ results }: { results: MonitoringResult[] }) {
  if (!results.length) return <EmptyState icon={<Activity size={20} />} title="No checks yet" detail="Monitoring results will appear here as agents report in." />
  const ordered = [...results].sort((a, b) => new Date(a.checkedAt).getTime() - new Date(b.checkedAt).getTime())
  const values = ordered.map((result) => result.responseTimeMs ?? 0)
  const maxValue = Math.max(10, ...values) * 1.15
  const chartWidth = 640
  const chartHeight = 160
  const left = 43
  const right = 12
  const top = 10
  const bottom = 23
  const plotWidth = chartWidth - left - right
  const plotHeight = chartHeight - top - bottom
  const points = ordered.map((result, index) => ({
    x: left + (ordered.length === 1 ? plotWidth / 2 : (index / (ordered.length - 1)) * plotWidth),
    y: top + plotHeight - ((result.responseTimeMs ?? 0) / maxValue) * plotHeight,
    result,
  }))
  const line = points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' ')
  const area = `${line} L ${points[points.length - 1].x} ${top + plotHeight} L ${points[0].x} ${top + plotHeight} Z`
  const yTicks = [0, 0.5, 1].map((fraction) => ({ y: top + plotHeight - fraction * plotHeight, label: `${Math.round(maxValue * fraction)} ms` }))
  const xIndexes = [...new Set([0, Math.floor((ordered.length - 1) / 2), ordered.length - 1])]

  return <svg className="latency-chart" viewBox={`0 0 ${chartWidth} ${chartHeight}`} role="img" aria-label="Response time for recent network checks">
    <defs><linearGradient id="latencyFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#2b7967" stopOpacity=".2" /><stop offset="100%" stopColor="#2b7967" stopOpacity=".01" /></linearGradient></defs>
    {yTicks.map((tick) => <g key={tick.label}><line x1={left} x2={chartWidth - right} y1={tick.y} y2={tick.y} stroke="#e9eeea" strokeDasharray="3 5" /><text x={left - 7} y={tick.y + 3} textAnchor="end" fill="#8b9691" fontSize="10">{tick.label}</text></g>)}
    <path d={area} fill="url(#latencyFill)" />
    <path d={line} fill="none" stroke="#287963" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
    {points.map((point) => <circle key={point.result.resultId} cx={point.x} cy={point.y} r="3" fill="#287963" stroke="#fff" strokeWidth="2"><title>{`${point.result.checkType} · ${point.result.responseTimeMs ?? 0} ms · ${point.result.available ? 'Available' : 'Unavailable'}`}</title></circle>)}
    {xIndexes.map((index) => <text key={index} x={points[index].x} y={chartHeight - 3} textAnchor={index === 0 ? 'start' : index === points.length - 1 ? 'end' : 'middle'} fill="#8b9691" fontSize="10">{formatTime(ordered[index].checkedAt)}</text>)}
  </svg>
}
function MonitoringTable({ results, devices }: { results: MonitoringResult[]; devices: Device[] }) {
  const deviceNames = new Map(devices.map((device) => [device.deviceId, device]))
  if (!results.length) return <EmptyState icon={<Activity size={20} />} title="Waiting for monitoring data" detail="Recent checks will appear when an agent submits a result." />
  return <div className="table-scroll"><table className="data-table"><thead><tr><th>DEVICE</th><th>CHECK TYPE</th><th>RESULT</th><th>LATENCY</th><th>LAST CHECK</th></tr></thead><tbody>{[...results].sort((a, b) => new Date(b.checkedAt).getTime() - new Date(a.checkedAt).getTime()).slice(0, 6).map((result) => {
    const device = deviceNames.get(result.deviceId)
    return <tr key={result.resultId}><td><span className="table-device"><span className="table-device-icon"><Cpu size={15} /></span><span><strong>{device?.name ?? 'Unknown device'}</strong><small>{device?.ipAddress ?? result.deviceId.slice(0, 8)}</small></span></span></td><td><span className="check-type">{result.checkType}</span></td><td><StatusBadge status={result.available ? 'ONLINE' : 'OFFLINE'} /></td><td className="latency-cell">{result.responseTimeMs == null ? '--' : `${result.responseTimeMs} ms`}</td><td className="time-cell">{formatRelative(result.checkedAt)}</td></tr>
  })}</tbody></table></div>
}
function DevicesView({ devices, total }: { devices: Device[]; total: number }) {
  return <section className="panel full-table-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">INVENTORY</span><h2>Monitored devices</h2><p>{total} registered endpoints</p></div><div className="table-heading-summary"><strong>{devices.filter((device) => device.status === 'ONLINE').length}</strong><span>ONLINE</span><i /><strong>{devices.filter((device) => device.status === 'OFFLINE').length}</strong><span>OFFLINE</span></div></div>{devices.length ? <div className="table-scroll"><table className="data-table device-data-table"><thead><tr><th>DEVICE</th><th>TYPE</th><th>LOCATION</th><th>STATUS</th><th>MONITORING</th><th>UPDATED</th></tr></thead><tbody>{devices.map((device) => <tr key={device.deviceId}><td><span className="table-device"><span className={`table-device-icon ${device.status === 'OFFLINE' ? 'table-device-offline' : ''}`}><Server size={15} /></span><span><strong>{device.name}</strong><small>{device.hostname || device.ipAddress}</small></span></span></td><td><span className="device-type">{device.deviceType}</span></td><td className="location-cell">{device.location || 'Unassigned'}</td><td><StatusBadge status={device.status} /></td><td><span className={`monitoring-state ${device.monitoringEnabled ? 'monitoring-on' : ''}`}><span />{device.monitoringEnabled ? 'Enabled' : 'Paused'}</span></td><td className="time-cell">{formatRelative(device.updatedAt)}</td></tr>)}</tbody></table></div> : <EmptyState icon={<Router size={20} />} title="No devices found" detail="Devices registered by your network agents will appear here." />}<div className="table-pagination"><span>Showing <strong>{devices.length}</strong> of <strong>{total}</strong> devices</span><span>Maximum page size: 100</span></div></section>
}
function AgentsView({ agents }: { agents: Agent[] }) {
  return <section className="agent-card-grid">{agents.length ? agents.map((agent) => <article className="panel agent-card" key={agent.agentId}><div className="agent-card-top"><div className="agent-emblem"><RadioTower size={19} /></div><StatusBadge status={agent.status} /></div><h2>{agent.name}</h2><p className="agent-subtitle">{agent.segmentCidr}</p><div className="agent-card-rule" /><div className="agent-meta"><span>VERSION</span><strong>{agent.version || 'Not reported'}</strong></div><div className="agent-meta"><span>LAST HEARTBEAT</span><strong>{formatRelative(agent.lastHeartbeat)}</strong></div><div className="agent-meta"><span>REGISTERED</span><strong>{formatRelative(agent.registeredAt)}</strong></div><div className="agent-id">ID <code>{agent.agentId}</code></div></article>) : <div className="panel empty-agents"><EmptyState icon={<RadioTower size={20} />} title="No agents registered" detail="Registered network collectors will be listed here." /></div>}</section>
}
function AlertsView({ alerts, onAlertAction, canResolve, readOnly }: { alerts: Alert[]; onAlertAction: (id: string, action: 'read' | 'resolve') => void; canResolve: boolean; readOnly?: boolean }) {
  return <section className="panel full-alert-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">ALERT CENTER</span><h2>Unread alerts</h2><p>Review and resolve incoming network events.</p></div><span className="alert-total"><Bell size={15} />{alerts.length} unread</span></div>{alerts.length ? <div className="alert-list full-alert-list">{alerts.map((alert) => <AlertRow key={alert.alertId} alert={alert} onAction={onAlertAction} canResolve={canResolve} readOnly={readOnly} />)}</div> : <EmptyState icon={<Check size={20} />} title="You're all caught up" detail="There are no unread alerts to review." />}</section>
}
function AlertRow({ alert, onAction, canResolve, readOnly }: { alert: Alert; onAction: (id: string, action: 'read' | 'resolve') => void; canResolve: boolean; readOnly?: boolean }) {
  return <div className="alert-row"><span className={`alert-severity alert-severity-${alert.severity.toLowerCase()}`}><AlertTriangle size={15} /></span><div className="alert-body"><div className="alert-title-row"><strong>{alert.type.replaceAll('_', ' ')}</strong><span className={`severity-label severity-${alert.severity.toLowerCase()}`}>{alert.severity}</span></div><p>{alert.message}</p><div className="alert-meta"><span>{formatRelative(alert.createdAt)}</span>{alert.deviceId && <span>Device {alert.deviceId.slice(0, 8)}</span>}</div></div>{!readOnly && <div className="alert-actions"><button className="alert-action" onClick={() => onAction(alert.alertId, 'read')} title="Mark as read"><Check size={15} /><span>Mark read</span></button>{canResolve && alert.status !== 'RESOLVED' && <button className="alert-resolve" onClick={() => onAction(alert.alertId, 'resolve')} title="Resolve alert">Resolve</button>}</div>}</div>
}
function StatusBadge({ status }: { status: string }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}><span />{status}</span>
}
function EmptyState({ icon, title, detail }: { icon: ReactNode; title: string; detail: string }) {
  return <div className="empty-state"><span className="empty-icon">{icon}</span><strong>{title}</strong><p>{detail}</p></div>
}
function LoadingState() {
  return <div className="loading-grid" aria-label="Loading network data"><div className="loading-card" /><div className="loading-card" /><div className="loading-card" /><div className="loading-card" /><div className="loading-panel" /><div className="loading-panel" /></div>
}
function ConnectionState({ onRetry }: { onRetry: () => void }) {
  return <div className="connection-state"><span><Signal size={22} /></span><h2>Dashboard is waiting for the API</h2><p>Check that the backend is running on port 8080 and your account is valid, then try again.</p><button className="button button-primary" onClick={onRetry}><RefreshCw size={15} />Retry connection</button></div>
}

export default App
