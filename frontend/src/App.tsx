import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Client } from '@stomp/stompjs'
import {
  Activity, AlertTriangle, ArrowDownRight, ArrowRight, ArrowUpRight, Bell,
  Check, ChevronDown, CircleHelp, Clock3, Command, Cpu, Eye, EyeOff,
  Gauge, LayoutDashboard, LogOut, Menu, Network, RadioTower, RefreshCw,
  Router, Search, Server, Settings2, ShieldCheck, Signal, X,
} from 'lucide-react'
import { ApiError, request, webSocketUrl } from './services/api'
import './App.css'

type Role = 'ADMIN' | 'OPERATOR' | 'VIEWER'
type ViewKey = 'overview' | 'devices' | 'agents' | 'monitoring' | 'alerts' | 'reports' | 'device-detail' | 'agent-detail'

interface AuthSession { accessToken: string; username: string; role: Role; expiresAt?: string }
interface Page<T> { content: T[]; totalElements: number }
interface MonitoringResult {
  resultId: string; deviceId: string; agentId: string; checkType: string
  responseTimeMs: number | null; available: boolean; packetLossPercent: number | null
  checkedAt: string; errorMessage: string | null
  portNumber: number | null; portOpen: boolean | null; snmpData: string | null
}
interface DashboardSummary {
  totalAgents: number; onlineAgents: number; offlineAgents: number
  totalMonitoredDevices: number; onlineDevices: number; offlineDevices: number
  availabilityPercent24h: number | null; generatedAt: string; recentActivities: MonitoringResult[]
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
  alertId: string; type: string; severity: 'CRITICAL' | 'WARNING' | 'INFO'
  status: 'UNREAD' | 'READ' | 'RESOLVED'; message: string
  deviceId: string | null; agentId: string | null; createdAt: string; resolvedAt: string | null
}
interface Snapshot {
  summary: DashboardSummary; devices: Device[]; agents: Agent[]; alerts: Alert[]; recentResults: MonitoringResult[]
  deviceTotal: number; agentTotal: number; alertTotal: number
}

const navItems: { key: ViewKey; label: string; icon: typeof LayoutDashboard }[] = [
  { key: 'overview', label: 'Overview', icon: LayoutDashboard },
  { key: 'devices', label: 'Devices', icon: Router },
  { key: 'agents', label: 'Agents', icon: RadioTower },
  { key: 'monitoring', label: 'Monitoring', icon: Activity },
  { key: 'alerts', label: 'Alerts', icon: Bell },
  { key: 'reports', label: 'Reports', icon: Gauge },
]

function readSession(): AuthSession | null {
  try {
    const saved = sessionStorage.getItem('netmoni-session')
    if (!saved) return null
    const session = JSON.parse(saved) as AuthSession
    if (session.expiresAt && Date.parse(session.expiresAt) <= Date.now()) {
      sessionStorage.removeItem('netmoni-session')
      return null
    }
    return session
  } catch { return null }
}

async function fetchSnapshot(token: string, devicePageNumber: number, agentPageNumber: number, alertPageNumber: number): Promise<Snapshot> {
  const [summary, devicePage, agentPage, alertPage, recentResults] = await Promise.all([
    request<DashboardSummary>('/dashboard/summary', token),
    request<Page<Device>>(`/devices?page=${devicePageNumber}&size=100`, token),
    request<Page<Agent>>(`/agents?page=${agentPageNumber}&size=100`, token),
    request<Page<Alert>>(`/alerts?page=${alertPageNumber}&size=100`, token),
    request<MonitoringResult[]>('/monitoring-results/recent?limit=500', token),
  ])
  return {
    summary, devices: devicePage.content, agents: agentPage.content, alerts: alertPage.content, recentResults,
    deviceTotal: devicePage.totalElements, agentTotal: agentPage.totalElements, alertTotal: alertPage.totalElements,
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
  const [devicePageNumber, setDevicePageNumber] = useState(0)
  const [agentPageNumber, setAgentPageNumber] = useState(0)
  const [alertPageNumber, setAlertPageNumber] = useState(0)
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null)
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null)
  const [detailDevice, setDetailDevice] = useState<Device | null>(null)
  const [detailAgent, setDetailAgent] = useState<Agent | null>(null)
  const [agentDetailDevices, setAgentDetailDevices] = useState<Device[]>([])
  const [agentDetailResults, setAgentDetailResults] = useState<MonitoringResult[]>([])
  const [detailResults, setDetailResults] = useState<MonitoringResult[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    if (!session) return
    let active = true
    const synchronize = async () => {
      if (!active) return
      setLoading(true)
      try {
        const next = await fetchSnapshot(session.accessToken, devicePageNumber, agentPageNumber, alertPageNumber)
        if (active) { setSnapshot(next); setError('') }
      } catch (reason) {
        if (active && reason instanceof ApiError && reason.status === 401) {
          sessionStorage.removeItem('netmoni-session')
          setSession(null); setSnapshot(null); setError('Your session expired. Sign in again.')
        } else if (active) setError(reason instanceof Error ? reason.message : 'Unable to reach the API')
      } finally { if (active) setLoading(false) }
    }
    void synchronize()
    const client = new Client({
      brokerURL: webSocketUrl(),
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
  }, [session, refreshKey, devicePageNumber, agentPageNumber, alertPageNumber])

  function signOut() {
    sessionStorage.removeItem('netmoni-session')
    setSession(null); setSnapshot(null); setError(''); setSelectedAgentId(null); setSelectedDeviceId(null)
    setDetailDevice(null); setDetailAgent(null); setDetailResults([]); setAgentDetailDevices([]); setAgentDetailResults([])
  }
  async function updateAlert(alertId: string, action: 'read' | 'resolve') {
    if (!session) return
    try {
      await request(`/alerts/${alertId}/${action}`, session.accessToken, { method: 'PATCH' })
      setRefreshKey((key) => key + 1)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not update alert') }
  }

  async function openDevice(deviceId: string) {
    if (!session) return
    setSelectedDeviceId(deviceId); setSelectedAgentId(null); setView('device-detail'); setDetailResults([])
    setDetailDevice(snapshot?.devices.find((device) => device.deviceId === deviceId) ?? null)
    setDetailLoading(true)
    try {
      const to = new Date(); const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000)
      const params = new URLSearchParams({ from: from.toISOString(), to: to.toISOString() })
      const [device, results] = await Promise.all([
        request<Device>(`/devices/${deviceId}`, session.accessToken),
        request<MonitoringResult[]>(`/monitoring-results/device/${deviceId}?${params}`, session.accessToken),
      ])
      setDetailDevice(device); setDetailResults(results)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not load device history') }
    finally { setDetailLoading(false) }
  }

  async function openAgent(agentId: string) {
    if (!session) return
    setSelectedAgentId(agentId); setSelectedDeviceId(null); setView('agent-detail')
    setDetailAgent(snapshot?.agents.find((agent) => agent.agentId === agentId) ?? null)
    setAgentDetailDevices(snapshot?.devices.filter((device) => device.agentId === agentId) ?? [])
    setAgentDetailResults(snapshot?.recentResults.filter((result) => result.agentId === agentId) ?? [])
    setDetailLoading(true)
    try {
      const to = new Date(); const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000)
      const params = new URLSearchParams({ from: from.toISOString(), to: to.toISOString() })
      const [agent, devices, results] = await Promise.all([
        request<Agent>(`/agents/${agentId}`, session.accessToken),
        request<Device[]>(`/devices/by-agent/${agentId}`, session.accessToken),
        request<MonitoringResult[]>(`/monitoring-results/agent/${agentId}?${params}`, session.accessToken),
      ])
      setDetailAgent(agent); setAgentDetailDevices(devices); setAgentDetailResults(results)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not load agent details') }
    finally { setDetailLoading(false) }
  }

  if (!session) return <AuthScreen onAuthenticated={setSession} />
  const summary = snapshot?.summary
  const title = navItems.find((item) => item.key === view)?.label
    ?? (view === 'device-detail' ? 'Device details' : view === 'agent-detail' ? 'Agent details' : 'Overview')
  const unreadAlerts = (snapshot?.alerts ?? []).filter((alert) => alert.status === 'UNREAD')
  const overviewSnapshot = snapshot ? { ...snapshot, alerts: unreadAlerts } : null
  const matchingDevices = (snapshot?.devices ?? []).filter((device) =>
    `${device.name} ${device.ipAddress} ${device.hostname ?? ''} ${device.location ?? ''}`.toLowerCase().includes(search.toLowerCase()),
  )
  const matchingAgents = (snapshot?.agents ?? []).filter((agent) =>
    `${agent.name} ${agent.agentId} ${agent.segmentCidr} ${agent.version}`.toLowerCase().includes(search.toLowerCase()),
  )

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNavOpen ? 'sidebar-open' : ''}`}>
      <div className="brand-lockup"><div className="brand-mark"><Network size={20} strokeWidth={2.2} /></div><div className="brand-copy"><strong>netmoni</strong><span>NETWORK INTELLIGENCE</span></div><button className="icon-button sidebar-close" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation"><X size={18} /></button></div>
      <div className="workspace-switcher"><span className="workspace-icon"><Command size={15} /></span><span><strong>Core network</strong><small>Workspace</small></span><ChevronDown size={15} className="muted-icon" /></div>
      <p className="nav-caption">MONITOR</p>
      <nav className="primary-nav" aria-label="Main navigation">{navItems.map(({ key, label, icon: Icon }) => <button key={key} className={`nav-link ${view === key ? 'nav-link-active' : ''}`} onClick={() => { setView(key); setMobileNavOpen(false); setSearch('') }}><Icon size={18} strokeWidth={1.8} /><span>{label}</span>{key === 'alerts' && unreadAlerts.length > 0 && <span className="nav-count">{unreadAlerts.length}</span>}</button>)}</nav>
      <p className="nav-caption nav-caption-spaced">SYSTEM</p>
      <button className="nav-link" onClick={() => setError('Settings are managed by your network administrator.')}><Settings2 size={18} strokeWidth={1.8} /><span>Settings</span></button>
      <button className="nav-link" onClick={() => setError('API docs: open docs/backend-api.md in the repository.')}><CircleHelp size={18} strokeWidth={1.8} /><span>Documentation</span><ArrowUpRight size={13} className="nav-external" /></button>
      <div className="sidebar-spacer" />
      <div className="network-status-card"><div className="network-status-heading"><span className={`status-pulse ${live ? '' : 'status-pulse-off'}`} /><strong>{live ? 'Live connection' : 'Reconnecting'}</strong></div><p>{live ? 'Receiving network events' : 'Trying to reach the event stream'}</p><div className="status-track"><span style={{ width: live ? '100%' : '38%' }} /></div></div>
      <button className="profile-row" onClick={signOut} title="Sign out"><span className="avatar">{initials(session.username)}</span><span className="profile-copy"><strong>{session.username}</strong><small>{session.role.toLowerCase()}</small></span><LogOut size={16} className="profile-signout" /></button>
    </aside>
    {mobileNavOpen && <button className="nav-scrim" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation" />}

    <main className="main-area">
      <header className="topbar"><button className="icon-button mobile-menu" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation"><Menu size={20} /></button><div className="breadcrumb"><span>Network</span><span className="breadcrumb-slash">/</span><strong>{title}</strong></div><div className="topbar-actions"><span className={`connection-pill ${live ? 'connection-live' : ''}`}><span />{live ? 'Live' : 'Offline'}</span><span className="topbar-divider" /><span className="topbar-date"><Clock3 size={15} />{new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date())}</span><button className="icon-button help-button" onClick={() => setRefreshKey((key) => key + 1)} title="Refresh data" aria-label="Refresh data"><RefreshCw size={17} className={loading ? 'spinning' : ''} /></button></div></header>
      <div className="page-content">
        <section className="page-heading"><div><div className="eyebrow"><span className="eyebrow-line" />NETWORK OPERATIONS</div><h1>{view === 'overview' ? 'Good network, good day.' : title}</h1><p>{pageDescription(view)}</p></div><div className="heading-actions"><button className="button button-secondary" onClick={() => setRefreshKey((key) => key + 1)}><RefreshCw size={15} className={loading ? 'spinning' : ''} /><span>Refresh</span></button></div></section>
        {error && <div className="error-banner" role="alert"><AlertTriangle size={17} /><span>{error}</span><button className="icon-button" onClick={() => setError('')} aria-label="Dismiss"><X size={15} /></button></div>}
        {!snapshot && loading && <LoadingState />}
        {!snapshot && !loading && error && <ConnectionState onRetry={() => setRefreshKey((key) => key + 1)} />}
        {view === 'devices' && <label className="search-box page-search"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search devices" aria-label="Search devices" /></label>}
        {view === 'agents' && <label className="search-box page-search"><Search size={16} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search agents" aria-label="Search agents" /></label>}
        {snapshot && view === 'overview' && overviewSnapshot && <Overview summary={summary} snapshot={overviewSnapshot} onNavigate={setView} onAlertAction={updateAlert} />}
        {snapshot && view === 'devices' && <DevicesView devices={matchingDevices} total={snapshot.deviceTotal} page={devicePageNumber} onPageChange={setDevicePageNumber} canManage={session.role !== 'VIEWER'} canDelete={session.role === 'ADMIN'} agents={snapshot.agents} onOpenDevice={openDevice} onOpenAgent={openAgent} token={session.accessToken} onChanged={() => setRefreshKey((key) => key + 1)} onError={setError} />}
        {snapshot && view === 'agents' && <AgentsView agents={matchingAgents} total={snapshot.agentTotal} page={agentPageNumber} onPageChange={setAgentPageNumber} onOpenAgent={openAgent} />}
        {snapshot && view === 'monitoring' && <MonitoringView results={snapshot.recentResults} devices={snapshot.devices} agents={snapshot.agents} onOpenDevice={openDevice} />}
        {snapshot && view === 'alerts' && <AlertsView alerts={snapshot.alerts} total={snapshot.alertTotal} page={alertPageNumber} onPageChange={setAlertPageNumber} onAlertAction={updateAlert} canResolve={session.role !== 'VIEWER'} />}
        {snapshot && view === 'reports' && <ReportsView results={snapshot.recentResults} devices={snapshot.devices} agents={snapshot.agents} />}
        {snapshot && view === 'device-detail' && selectedDeviceId && <DeviceDetailView device={detailDevice ?? undefined} results={detailResults} loading={detailLoading} onBack={() => setView('devices')} onOpenAgent={openAgent} />}
        {snapshot && view === 'agent-detail' && selectedAgentId && <AgentDetailView agent={detailAgent ?? undefined} devices={agentDetailDevices} results={agentDetailResults} loading={detailLoading} onBack={() => setView('agents')} onOpenDevice={openDevice} />}
        <footer className="page-footer"><span><span className={`footer-dot ${error ? 'footer-dot-warning' : ''}`} />{error ? 'API CONNECTION DEGRADED' : 'SYSTEM OPERATIONAL'}</span><span>Last sync {formatTime(summary?.generatedAt)}</span></footer>
      </div>
    </main>
  </div>
}

function pageDescription(view: ViewKey) {
  const descriptions: Record<ViewKey, string> = {
    overview: 'A clear view of your infrastructure, all in one place.',
    devices: 'Every monitored endpoint across your network.',
    agents: 'Collectors reporting into your monitoring network.',
    monitoring: 'Recent checks and results received from monitoring agents.',
    alerts: 'Events that need a closer look from your team.',
    reports: 'Availability and response trends from real monitoring results.',
    'device-detail': 'Device status and recent monitoring history.',
    'agent-detail': 'Collector health and the devices it reports for.',
  }
  return descriptions[view]
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const result = await request<{ accessToken: string; username: string; role: Role; expiresAt: string }>(`/auth/${mode}`, undefined, { method: 'POST', body: JSON.stringify({ username, password }) })
      const next = { accessToken: result.accessToken, username: result.username, role: result.role, expiresAt: result.expiresAt }
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
        <form onSubmit={submit} className="auth-form"><label className="field-label" htmlFor="username">Email or username</label><input id="username" className="text-input" type={mode === 'register' ? 'email' : 'text'} maxLength={254} autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="you@company.com" required /><div className="password-label-row"><label className="field-label" htmlFor="password">Password</label>{mode === 'register' && <small>10–72 characters</small>}</div><div className="password-field"><input id="password" className="text-input" type={visible ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 10 : undefined} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" required /><button type="button" className="icon-button password-toggle" onClick={() => setVisible((value) => !value)} aria-label={visible ? 'Hide password' : 'Show password'}>{visible ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>{error && <div className="auth-error" role="alert"><AlertTriangle size={15} />{error}</div>}<button className="button button-primary auth-submit" type="submit" disabled={busy}>{busy ? <RefreshCw size={16} className="spinning" /> : <span>{mode === 'login' ? 'Continue to dashboard' : 'Create account'}</span>}{!busy && <ArrowRight size={16} />}</button></form>
        <div className="auth-footnote"><ShieldCheck size={15} /><span>Secure session · Tokens stay in this browser session</span></div>
      </section>
    </div>
    <div className="auth-bottomline"><span>NETMONI NETWORK MONITORING</span><span>BUILT FOR CLARITY <span className="auth-bottom-separator">/</span> DESIGNED FOR OPERATIONS</span></div>
  </main>
}

function Overview({ summary, snapshot, onNavigate, onAlertAction }: { summary?: DashboardSummary; snapshot: Snapshot; onNavigate: (view: ViewKey) => void; onAlertAction: (id: string, action: 'read' | 'resolve') => void }) {
  const chartData = [...(summary?.recentActivities ?? [])].sort((a, b) => new Date(a.checkedAt).getTime() - new Date(b.checkedAt).getTime()).map((item) => ({ time: formatTime(item.checkedAt), latency: item.responseTimeMs ?? 0, status: item.available ? 'Available' : 'Unavailable', checkType: item.checkType }))
  return <>
    <section className="metric-grid" aria-label="Network summary">
      <MetricCard label="NETWORK AVAILABILITY" value={summary?.availabilityPercent24h == null ? '—' : `${summary.availabilityPercent24h.toFixed(1)}%`} note={summary?.availabilityPercent24h == null ? 'No checks in last 24 hours' : 'Last 24 hours'} icon={<Gauge size={18} />} tone="green" trend={summary?.availabilityPercent24h == null ? 'No sample' : 'Observed uptime'} />
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
      <article className="panel alerts-panel"><div className="panel-heading table-panel-heading"><div><span className="panel-kicker">NEEDS ATTENTION</span><h2>Open alerts</h2></div><button className="icon-button panel-icon-button" onClick={() => onNavigate('alerts')} aria-label="View all alerts"><ArrowUpRight size={17} /></button></div>{snapshot.alerts.length ? <div className="alert-list compact-alert-list">{snapshot.alerts.slice(0, 4).map((alert) => <AlertRow key={alert.alertId} alert={alert} onAction={onAlertAction} canResolve={false} />)}</div> : <div className="all-clear"><span><Check size={18} /></span><strong>All clear</strong><p>No unread alerts right now.</p></div>}<button className="panel-footer-link" onClick={() => onNavigate('alerts')}>Open alert center <ArrowRight size={14} /></button></article>
    </section>
  </>
}

function MetricCard({ label, value, note, icon, tone, trend, trendDown }: { label: string; value: string; note: string; icon: ReactNode; tone: string; trend: string; trendDown?: boolean }) {
  return <article className="metric-card"><div className="metric-card-top"><span>{label}</span><i className={`metric-icon metric-${tone}`}>{icon}</i></div><div className="metric-value">{value}</div><div className="metric-bottom"><span>{note}</span><span className={`metric-trend ${trendDown ? 'metric-trend-warning' : ''}`}>{trendDown ? <ArrowDownRight size={13} /> : <ArrowUpRight size={13} />}{trend}</span></div></article>
}
function LatencyChart({ results }: { results: MonitoringResult[] }) {
  const measured = results.filter((result) => result.responseTimeMs != null)
  if (!measured.length) return <EmptyState icon={<Activity size={20} />} title="No latency readings yet" detail="Response-time measurements will appear here when checks include a latency value." />
  const ordered = [...measured].sort((a, b) => new Date(a.checkedAt).getTime() - new Date(b.checkedAt).getTime())
  const values = ordered.map((result) => result.responseTimeMs as number)
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
    y: top + plotHeight - ((result.responseTimeMs as number) / maxValue) * plotHeight,
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
    {points.map((point) => <circle key={point.result.resultId} cx={point.x} cy={point.y} r="3" fill="#287963" stroke="#fff" strokeWidth="2"><title>{`${point.result.checkType} · ${point.result.responseTimeMs} ms · ${point.result.available ? 'Available' : 'Unavailable'}`}</title></circle>)}
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
function DevicesView({ devices, total, page, onPageChange, canManage, canDelete, agents, onOpenDevice, onOpenAgent, token, onChanged, onError }: {
  devices: Device[]; total: number; page: number; onPageChange: (page: number) => void; canManage: boolean; canDelete: boolean
  agents: Agent[]; onOpenDevice: (id: string) => void; onOpenAgent: (id: string) => void; token: string; onChanged: () => void; onError: (message: string) => void
}) {
  const [editing, setEditing] = useState<Device | null | false>(false)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [agentFilter, setAgentFilter] = useState('ALL')
  const visible = devices.filter((device) => (statusFilter === 'ALL' || device.status === statusFilter)
    && (agentFilter === 'ALL' || device.agentId === agentFilter))
  const pages = Math.max(1, Math.ceil(total / 100))
  async function remove(device: Device) {
    if (!window.confirm(`Delete ${device.name} (${device.ipAddress})? This cannot be undone.`)) return
    try { await request<void>(`/devices/${device.deviceId}`, token, { method: 'DELETE' }); onChanged() }
    catch (reason) { onError(reason instanceof Error ? reason.message : 'Could not delete device') }
  }
  return <>
    <section className="panel full-table-panel">
      <div className="panel-heading full-table-heading"><div><span className="panel-kicker">INVENTORY</span><h2>Monitored devices</h2><p>{total} registered endpoints</p></div>
        <div className="heading-actions"><select className="filter-select" aria-label="Filter device status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option><option>ONLINE</option><option>OFFLINE</option><option>UNKNOWN</option></select><select className="filter-select" aria-label="Filter devices by agent" value={agentFilter} onChange={(event) => setAgentFilter(event.target.value)}><option value="ALL">All agents</option>{agents.map((agent) => <option key={agent.agentId} value={agent.agentId}>{agent.name}</option>)}</select>{canManage && <button className="button button-primary" onClick={() => setEditing(null)}>Add device</button>}</div>
      </div>
      {visible.length ? <div className="table-scroll"><table className="data-table device-data-table"><thead><tr><th>DEVICE</th><th>TYPE</th><th>LOCATION</th><th>AGENT</th><th>STATUS</th><th>MONITORING</th><th>UPDATED</th><th>ACTIONS</th></tr></thead><tbody>{visible.map((device) => <tr key={device.deviceId}>
        <td><button className="table-link table-device" onClick={() => onOpenDevice(device.deviceId)}><span className={`table-device-icon ${device.status === 'OFFLINE' ? 'table-device-offline' : ''}`}><Server size={15} /></span><span><strong>{device.name}</strong><small>{device.hostname || device.ipAddress}</small></span></button></td><td><span className="device-type">{device.deviceType}</span></td><td className="location-cell">{device.location || 'Unassigned'}</td>
        <td><button className="text-link" onClick={() => onOpenAgent(device.agentId)}>{agents.find((agent) => agent.agentId === device.agentId)?.name ?? device.agentId.slice(0, 8)}</button></td><td><StatusBadge status={device.status} /></td><td><span className={`monitoring-state ${device.monitoringEnabled ? 'monitoring-on' : ''}`}><span />{device.monitoringEnabled ? 'Enabled' : 'Paused'}</span></td><td className="time-cell">{formatRelative(device.updatedAt)}</td>
        <td><div className="row-actions">{canManage && <button className="text-link" onClick={() => setEditing(device)}>Edit</button>}{canDelete && <button className="text-link danger-link" onClick={() => void remove(device)}>Delete</button>}</div></td>
      </tr>)}</tbody></table></div> : <EmptyState icon={<Router size={20} />} title="No devices found" detail="Try a different search or register a device assigned to an agent." />}
      <div className="table-pagination"><span>Showing {total ? page * 100 + 1 : 0}–{Math.min((page + 1) * 100, total)} of {total} devices</span><div><button className="button button-secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button className="button button-secondary" disabled={page + 1 >= pages} onClick={() => onPageChange(page + 1)}>Next</button></div></div>
    </section>
    {editing !== false && <DeviceForm initial={editing} agents={agents} token={token} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); onChanged() }} />}
  </>
}

function AgentsView({ agents, total, page, onPageChange, onOpenAgent }: {
  agents: Agent[]; total: number; page: number; onPageChange: (page: number) => void; onOpenAgent: (id: string) => void
}) {
  const [statusFilter, setStatusFilter] = useState('ALL')
  const visible = agents.filter((agent) => statusFilter === 'ALL' || agent.status === statusFilter)
  const pages = Math.max(1, Math.ceil(total / 100))
  return <>
    <div className="view-toolbar"><span>{total} registered agents</span><select className="filter-select" aria-label="Filter agent status" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="ALL">All statuses</option><option>ONLINE</option><option>OFFLINE</option><option>UNKNOWN</option></select></div>
    <section className="agent-card-grid">{visible.length ? visible.map((agent) => <article className="panel agent-card" key={agent.agentId}><div className="agent-card-top"><div className="agent-emblem"><RadioTower size={19} /></div><StatusBadge status={agent.status} /></div><h2>{agent.name}</h2><p className="agent-subtitle">{agent.segmentCidr}</p><div className="agent-card-rule" /><div className="agent-meta"><span>VERSION</span><strong>{agent.version || 'Not reported'}</strong></div><div className="agent-meta"><span>LAST HEARTBEAT</span><strong>{formatRelative(agent.lastHeartbeat)}</strong></div><div className="agent-meta"><span>REGISTERED</span><strong>{formatRelative(agent.registeredAt)}</strong></div><div className="agent-id">ID <code>{agent.agentId}</code></div><button className="panel-footer-link" onClick={() => onOpenAgent(agent.agentId)}>View agent details <ArrowRight size={14} /></button></article>) : <div className="panel empty-agents"><EmptyState icon={<RadioTower size={20} />} title="No agents found" detail="Try a different search or status filter." /></div>}</section>
    <div className="table-pagination"><span>Showing {total ? page * 100 + 1 : 0}–{Math.min((page + 1) * 100, total)} of {total} agents</span><div><button className="button button-secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button className="button button-secondary" disabled={page + 1 >= pages} onClick={() => onPageChange(page + 1)}>Next</button></div></div>
  </>
}

function DeviceForm({ initial, agents, token, onClose, onSaved }: {
  initial: Device | null; agents: Agent[]; token: string; onClose: () => void; onSaved: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [name, setName] = useState(initial?.name ?? '')
  const [ipAddress, setIpAddress] = useState(initial?.ipAddress ?? '')
  const [hostname, setHostname] = useState(initial?.hostname ?? '')
  const [deviceType, setDeviceType] = useState(initial?.deviceType ?? 'SERVER')
  const [location, setLocation] = useState(initial?.location ?? '')
  const [agentId, setAgentId] = useState(initial?.agentId ?? agents[0]?.agentId ?? '')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setFormError('')
    try {
      const body = JSON.stringify({ name, ipAddress, hostname: hostname || null, deviceType, location: location || null, agentId })
      await request(initial ? `/devices/${initial.deviceId}` : '/devices', token, { method: initial ? 'PUT' : 'POST', body })
      onSaved()
    } catch (reason) { setFormError(reason instanceof Error ? reason.message : 'Could not save device') }
    finally { setBusy(false) }
  }
  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="modal-panel" role="dialog" aria-modal="true" aria-labelledby="device-form-title">
      <div className="panel-heading"><div><span className="panel-kicker">DEVICE INVENTORY</span><h2 id="device-form-title">{initial ? 'Edit device' : 'Register device'}</h2><p>Assign the device to an existing monitoring agent.</p></div><button className="icon-button" onClick={onClose} aria-label="Close device form"><X size={17} /></button></div>
      <form className="device-form" onSubmit={submit}>
        <label>Name<input className="text-input" maxLength={100} required value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label>IP address<input className="text-input" required value={ipAddress} onChange={(event) => setIpAddress(event.target.value)} placeholder="192.168.1.10" /></label>
        <label>Hostname (optional)<input className="text-input" maxLength={255} value={hostname} onChange={(event) => setHostname(event.target.value)} /></label>
        <label>Device type<input className="text-input" maxLength={40} required value={deviceType} onChange={(event) => setDeviceType(event.target.value)} placeholder="ROUTER, SERVER, SWITCH" /></label>
        <label>Location (optional)<input className="text-input" maxLength={120} value={location} onChange={(event) => setLocation(event.target.value)} /></label>
        <label>Monitoring agent<select className="filter-select form-select" required value={agentId} onChange={(event) => setAgentId(event.target.value)}><option value="" disabled>Select an agent</option>{agents.map((agent) => <option key={agent.agentId} value={agent.agentId}>{agent.name} · {agent.segmentCidr}</option>)}</select></label>
        {!agents.length && <p className="form-note">Register an agent before adding devices.</p>}
        {formError && <div className="auth-error" role="alert">{formError}</div>}
        <div className="modal-actions"><button type="button" className="button button-secondary" onClick={onClose}>Cancel</button><button type="submit" className="button button-primary" disabled={busy || !agents.length}>{busy ? 'Saving…' : initial ? 'Save changes' : 'Register device'}</button></div>
      </form>
    </section>
  </div>
}

function MonitoringView({ results, devices, agents, onOpenDevice }: {
  results: MonitoringResult[]; devices: Device[]; agents: Agent[]; onOpenDevice: (id: string) => void
}) {
  const [deviceId, setDeviceId] = useState('ALL')
  const [agentId, setAgentId] = useState('ALL')
  const [checkType, setCheckType] = useState('ALL')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const filtered = results.filter((result) => (deviceId === 'ALL' || result.deviceId === deviceId)
    && (agentId === 'ALL' || result.agentId === agentId) && (checkType === 'ALL' || result.checkType === checkType)
    && (!from || result.checkedAt.slice(0, 10) >= from) && (!to || result.checkedAt.slice(0, 10) <= to))
  return <section className="panel full-table-panel">
    <div className="panel-heading full-table-heading"><div><span className="panel-kicker">MONITORING HISTORY</span><h2>Recent checks</h2><p>Latest {results.length} results returned by the backend (maximum 500).</p></div>
      <div className="heading-actions"><input className="filter-select" aria-label="Monitoring results from date" type="date" value={from} max={to || undefined} onChange={(event) => setFrom(event.target.value)} /><input className="filter-select" aria-label="Monitoring results to date" type="date" value={to} min={from || undefined} onChange={(event) => setTo(event.target.value)} /><select className="filter-select" aria-label="Filter by check type" value={checkType} onChange={(event) => setCheckType(event.target.value)}><option value="ALL">All check types</option><option>ICMP</option><option>PING</option><option>TCP</option><option>SNMP</option></select><select className="filter-select" aria-label="Filter by agent" value={agentId} onChange={(event) => setAgentId(event.target.value)}><option value="ALL">All agents</option>{agents.map((agent) => <option key={agent.agentId} value={agent.agentId}>{agent.name}</option>)}</select><select className="filter-select" aria-label="Filter by device" value={deviceId} onChange={(event) => setDeviceId(event.target.value)}><option value="ALL">All devices</option>{devices.map((device) => <option key={device.deviceId} value={device.deviceId}>{device.name}</option>)}</select></div>
    </div>
    <MonitoringResultsTable results={filtered} devices={devices} agents={agents} onOpenDevice={onOpenDevice} />
    <div className="table-pagination">Showing {filtered.length} of {results.length} recent results</div>
  </section>
}

function MonitoringResultsTable({ results, devices, agents, onOpenDevice }: {
  results: MonitoringResult[]; devices: Device[]; agents: Agent[]; onOpenDevice?: (id: string) => void
}) {
  const deviceMap = new Map(devices.map((device) => [device.deviceId, device]))
  const agentMap = new Map(agents.map((agent) => [agent.agentId, agent]))
  if (!results.length) return <EmptyState icon={<Activity size={20} />} title="No monitoring results" detail="Results will appear here after an agent submits its first check." />
  return <div className="table-scroll"><table className="data-table result-table"><thead><tr><th>CHECKED AT</th><th>DEVICE</th><th>AGENT</th><th>CHECK</th><th>STATUS</th><th>LATENCY</th><th>PACKET LOSS</th><th>PORT</th><th>SNMP DATA / ERROR</th></tr></thead><tbody>{[...results].sort((a, b) => Date.parse(b.checkedAt) - Date.parse(a.checkedAt)).map((result) => {
    const device = deviceMap.get(result.deviceId)
    const agent = agentMap.get(result.agentId)
    const snmp = readSnmpData(result.snmpData)
    return <tr key={result.resultId}><td className="time-cell">{formatRelative(result.checkedAt)}</td><td>{device && onOpenDevice ? <button className="text-link" onClick={() => onOpenDevice(device.deviceId)}>{device.name}</button> : device?.name ?? result.deviceId.slice(0, 8)}<small className="subcell">{device?.ipAddress ?? ''}</small></td><td>{agent?.name ?? result.agentId.slice(0, 8)}</td><td><span className="check-type">{result.checkType}</span></td><td><StatusBadge status={result.available ? 'ONLINE' : 'OFFLINE'} /></td><td>{result.responseTimeMs == null ? '—' : `${result.responseTimeMs} ms`}</td><td>{result.packetLossPercent == null ? '—' : `${result.packetLossPercent}%`}</td><td>{result.portNumber == null ? '—' : <span>{result.portNumber} · {result.portOpen ? 'open' : 'closed'}</span>}</td><td className="snmp-cell">{snmp.length ? snmp.map(([key, value]) => <span key={key}>{key}: {value}</span>) : result.errorMessage || '—'}</td></tr>
  })}</tbody></table></div>
}

function readSnmpData(value: string | null): [string, string][] {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return Object.entries(parsed).map(([key, item]) => [key, String(item)])
  } catch { return [['SNMP', value]] }
  return [['SNMP', String(value)]]
}

function ReportsView({ results, devices, agents }: { results: MonitoringResult[]; devices: Device[]; agents: Agent[] }) {
  const now = new Date()
  const today = now.toISOString().slice(0, 10)
  const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  const [from, setFrom] = useState(weekAgo)
  const [to, setTo] = useState(today)
  const filtered = results.filter((result) => result.checkedAt.slice(0, 10) >= from && result.checkedAt.slice(0, 10) <= to)
  const successful = filtered.filter((result) => result.available)
  const responseTimes = successful.map((result) => result.responseTimeMs).filter((time): time is number => time != null)
  const packetLoss = filtered.map((result) => result.packetLossPercent).filter((loss): loss is number => loss != null)
  const average = responseTimes.length ? Math.round(responseTimes.reduce((sum, value) => sum + value, 0) / responseTimes.length) : null
  const meanLoss = packetLoss.length ? packetLoss.reduce((sum, value) => sum + value, 0) / packetLoss.length : null
  function exportCsv() {
    const deviceMap = new Map(devices.map((item) => [item.deviceId, item.name]))
    const agentMap = new Map(agents.map((item) => [item.agentId, item.name]))
    const headers = ['checkedAt', 'device', 'agent', 'checkType', 'available', 'responseTimeMs', 'packetLossPercent', 'portNumber', 'portOpen', 'snmpData', 'errorMessage']
    const lines = [headers, ...filtered.map((result) => [result.checkedAt, deviceMap.get(result.deviceId) ?? result.deviceId, agentMap.get(result.agentId) ?? result.agentId, result.checkType, result.available, result.responseTimeMs ?? '', result.packetLossPercent ?? '', result.portNumber ?? '', result.portOpen ?? '', result.snmpData ?? '', result.errorMessage ?? ''])]
    const csv = lines.map((line) => line.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\r\n')
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a'); link.href = url; link.download = `netmoni-report-${from}-to-${to}.csv`; link.click(); URL.revokeObjectURL(url)
  }
  return <>
    <section className="report-toolbar panel"><label>From<input className="filter-select" type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} /></label><label>To<input className="filter-select" type="date" value={to} min={from} max={today} onChange={(event) => setTo(event.target.value)} /></label><button className="button button-secondary" onClick={exportCsv} disabled={!filtered.length}>Export CSV</button><span className="report-source">Based on up to 500 most recent API results</span></section>
    <section className="metric-grid report-metrics"><MetricCard label="RESULT AVAILABILITY" value={filtered.length ? `${(100 * successful.length / filtered.length).toFixed(1)}%` : '—'} note={`${successful.length} successful checks`} icon={<Gauge size={18} />} tone="green" trend={`${filtered.length} results`} /><MetricCard label="AVERAGE RESPONSE" value={average == null ? '—' : `${average} ms`} note="Successful checks with latency" icon={<Activity size={18} />} tone="blue" trend={`${responseTimes.length} measurements`} /><MetricCard label="AVERAGE PACKET LOSS" value={meanLoss == null ? '—' : `${meanLoss.toFixed(2)}%`} note="ICMP results with packet data" icon={<Signal size={18} />} tone="amber" trend={`${packetLoss.length} measurements`} /><MetricCard label="TCP PORT CHECKS" value={String(filtered.filter((result) => result.checkType === 'TCP').length)} note="In selected period" icon={<Network size={18} />} tone="red" trend="From API results" /></section>
    <section className="panel report-chart"><div className="panel-heading"><div><span className="panel-kicker">RESPONSE TREND</span><h2>Latency history</h2><p>Successful measurements in the selected period</p></div></div><div className="chart-wrap"><LatencyChart results={successful} /></div></section>
    <section className="panel full-table-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">EXPORT PREVIEW</span><h2>Filtered monitoring results</h2></div></div><MonitoringResultsTable results={filtered} devices={devices} agents={agents} /></section>
  </>
}

function DeviceDetailView({ device, results, loading, onBack, onOpenAgent }: {
  device?: Device; results: MonitoringResult[]; loading: boolean; onBack: () => void; onOpenAgent: (id: string) => void
}) {
  if (!device) return loading ? <LoadingState /> : <ConnectionState onRetry={onBack} />
  return <>
    <section className="detail-grid"><article className="panel detail-panel"><div className="panel-heading"><div><span className="panel-kicker">DEVICE</span><h2>{device.name}</h2><p>{device.ipAddress} · {device.hostname || 'Hostname unavailable'}</p></div><StatusBadge status={device.status} /></div><dl className="detail-list"><dt>Type</dt><dd>{device.deviceType}</dd><dt>Location</dt><dd>{device.location || 'Not provided'}</dd><dt>Monitoring</dt><dd>{device.monitoringEnabled ? 'Enabled' : 'Paused'}</dd><dt>Last updated</dt><dd>{formatRelative(device.updatedAt)}</dd><dt>Device ID</dt><dd><code>{device.deviceId}</code></dd><dt>Agent</dt><dd><button className="text-link" onClick={() => onOpenAgent(device.agentId)}>{device.agentId}</button></dd></dl><button className="button button-secondary" onClick={onBack}>Back to devices</button></article><article className="panel detail-panel"><div className="panel-heading"><div><span className="panel-kicker">HISTORY</span><h2>Recent response time</h2><p>Last 30 days returned by the backend</p></div></div><LatencyChart results={results} /></article></section>
    <section className="panel full-table-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">CHECKS</span><h2>Monitoring results</h2></div></div>{loading ? <LoadingState /> : <MonitoringResultsTable results={results} devices={[device]} agents={[]} />}</section>
  </>
}

function AgentDetailView({ agent, devices, results, loading, onBack, onOpenDevice }: {
  agent?: Agent; devices: Device[]; results: MonitoringResult[]; loading: boolean; onBack: () => void; onOpenDevice: (id: string) => void
}) {
  if (!agent) return loading ? <LoadingState /> : <ConnectionState onRetry={onBack} />
  return <>
    <section className="detail-grid"><article className="panel detail-panel"><div className="panel-heading"><div><span className="panel-kicker">AGENT</span><h2>{agent.name}</h2><p>{agent.segmentCidr} · version {agent.version || 'unknown'}</p></div><StatusBadge status={agent.status} /></div><dl className="detail-list"><dt>Agent ID</dt><dd><code>{agent.agentId}</code></dd><dt>Registered</dt><dd>{formatTime(agent.registeredAt)}</dd><dt>Last heartbeat</dt><dd>{formatRelative(agent.lastHeartbeat)}</dd><dt>Associated devices</dt><dd>{devices.length}</dd></dl><button className="button button-secondary" onClick={onBack}>Back to agents</button></article><article className="panel detail-panel"><div className="panel-heading"><div><span className="panel-kicker">RECENT ACTIVITY</span><h2>{results.length} checks</h2><p>Recent results recorded by this agent</p></div></div><MonitoringResultsTable results={results} devices={devices} agents={[agent]} onOpenDevice={onOpenDevice} /></article></section>
    <section className="panel full-table-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">ASSOCIATED DEVICES</span><h2>Managed devices</h2></div></div>{devices.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>NAME</th><th>ADDRESS</th><th>STATUS</th><th>LAST UPDATED</th></tr></thead><tbody>{devices.map((device) => <tr key={device.deviceId}><td><button className="text-link" onClick={() => onOpenDevice(device.deviceId)}>{device.name}</button></td><td>{device.ipAddress}</td><td><StatusBadge status={device.status} /></td><td>{formatRelative(device.updatedAt)}</td></tr>)}</tbody></table></div> : <EmptyState icon={<Server size={20} />} title="No devices assigned" detail="Devices assigned to this agent will appear here." />}</section>
  </>
}
function AlertsView({ alerts, total, page, onPageChange, onAlertAction, canResolve }: { alerts: Alert[]; total: number; page: number; onPageChange: (page: number) => void; onAlertAction: (id: string, action: 'read' | 'resolve') => void; canResolve: boolean }) {
  const [status, setStatus] = useState('ALL')
  const [severity, setSeverity] = useState('ALL')
  const [search, setSearch] = useState('')
  const filtered = alerts.filter((alert) => (status === 'ALL' || alert.status === status)
    && (severity === 'ALL' || alert.severity === severity)
    && `${alert.type} ${alert.message} ${alert.deviceId ?? ''} ${alert.agentId ?? ''}`.toLowerCase().includes(search.toLowerCase()))
  return <section className="panel full-alert-panel"><div className="panel-heading full-table-heading"><div><span className="panel-kicker">ALERT CENTER</span><h2>Network alerts</h2><p>Review unread items or inspect resolved events.</p></div><span className="alert-total"><Bell size={15} />{alerts.filter((alert) => alert.status === 'UNREAD').length} unread</span></div>
    <div className="view-toolbar"><span>{total} alerts</span><div><label className="search-box"><Search size={15} /><input aria-label="Search alerts" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search alerts" /></label><select className="filter-select" aria-label="Filter alerts by status" value={status} onChange={(event) => setStatus(event.target.value)}><option value="ALL">All statuses</option><option>UNREAD</option><option>READ</option><option>RESOLVED</option></select><select className="filter-select" aria-label="Filter alerts by severity" value={severity} onChange={(event) => setSeverity(event.target.value)}><option value="ALL">All severities</option><option>CRITICAL</option><option>WARNING</option><option>INFO</option></select></div></div>
    {filtered.length ? <div className="alert-list full-alert-list">{filtered.map((alert) => <AlertRow key={alert.alertId} alert={alert} onAction={onAlertAction} canResolve={canResolve} />)}</div> : <EmptyState icon={<Check size={20} />} title="No alerts match" detail="Change the status or severity filters to see other alerts." />}
    <div className="table-pagination"><span>Showing {total ? page * 100 + 1 : 0}–{Math.min((page + 1) * 100, total)} of {total} alerts</span><div><button className="button button-secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>Previous</button><span>Page {page + 1} of {Math.max(1, Math.ceil(total / 100))}</span><button className="button button-secondary" disabled={(page + 1) * 100 >= total} onClick={() => onPageChange(page + 1)}>Next</button></div></div>
  </section>
}
function AlertRow({ alert, onAction, canResolve }: { alert: Alert; onAction: (id: string, action: 'read' | 'resolve') => void; canResolve: boolean }) {
  return <div className="alert-row"><span className={`alert-severity alert-severity-${alert.severity.toLowerCase()}`}><AlertTriangle size={15} /></span><div className="alert-body"><div className="alert-title-row"><strong>{alert.type.replaceAll('_', ' ')}</strong><span className={`severity-label severity-${alert.severity.toLowerCase()}`}>{alert.severity}</span><span className="alert-status-label">{alert.status}</span></div><p>{alert.message}</p><div className="alert-meta"><span>{formatRelative(alert.createdAt)}</span>{alert.deviceId && <span>Device {alert.deviceId.slice(0, 8)}</span>}{alert.agentId && <span>Agent {alert.agentId.slice(0, 8)}</span>}{alert.resolvedAt && <span>Resolved {formatRelative(alert.resolvedAt)}</span>}</div></div><div className="alert-actions">{alert.status === 'UNREAD' && <button className="alert-action" onClick={() => onAction(alert.alertId, 'read')} title="Mark as read"><Check size={15} /><span>Mark read</span></button>}{canResolve && alert.status !== 'RESOLVED' && <button className="alert-resolve" onClick={() => { if (window.confirm(`Resolve alert “${alert.type.replaceAll('_', ' ')}”?`)) onAction(alert.alertId, 'resolve') }} title="Resolve alert">Resolve</button>}</div></div>
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
