import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    activate() {}
    subscribe() {}
    deactivate() { return Promise.resolve() }
  },
}))

const agent = {
  agentId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  name: 'Lab collector', segmentCidr: '192.168.10.0/24', version: '1.0.0', status: 'ONLINE',
  registeredAt: '2026-11-01T00:00:00Z', lastHeartbeat: '2026-11-13T00:00:00Z',
}
const device = {
  deviceId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', name: 'Core router', ipAddress: '192.168.10.1',
  hostname: 'router.lab', deviceType: 'ROUTER', location: 'Lab', agentId: agent.agentId,
  status: 'ONLINE', monitoringEnabled: true, updatedAt: '2026-11-13T00:00:00Z',
}
const result = {
  resultId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', deviceId: device.deviceId, agentId: agent.agentId,
  checkType: 'ICMP', responseTimeMs: 12, available: true, packetLossPercent: 0,
  portNumber: null, portOpen: null, snmpData: null, checkedAt: '2026-11-13T00:00:00Z', errorMessage: null,
}
const summary = {
  totalAgents: 1, onlineAgents: 1, offlineAgents: 0,
  totalMonitoredDevices: 1, onlineDevices: 1, offlineDevices: 0,
  availabilityPercent24h: 100, generatedAt: '2026-11-13T00:00:00Z', recentActivities: [result],
}
const alert = {
  alertId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', type: 'AGENT_HEARTBEAT_DELAY', severity: 'WARNING',
  status: 'UNREAD', message: 'Lab collector heartbeat is late.', deviceId: null, agentId: agent.agentId,
  createdAt: '2026-11-13T00:00:00Z', resolvedAt: null,
}

beforeEach(() => {
  sessionStorage.clear()
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin)
    if (url.pathname.endsWith('/discovery/agents/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/scan') && url.search === '' && initMethod(input, init) === 'GET') return new Response(null, { status: 204 })
    if (url.pathname.endsWith('/discovery/agents/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/devices')) return new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    const response = url.pathname === '/api/auth/login'
      ? { accessToken: 'test-jwt', username: 'viewer@example.com', role: 'VIEWER', expiresAt: '2099-01-01T00:00:00Z' }
      : url.pathname.endsWith('/discovery/agents/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/scan') && initMethod(input, init) === 'POST'
        ? { scanId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', agentId: agent.agentId, status: 'QUEUED', progress: 0, checked: 0, discovered: 0, failed: 0, createdAt: '2026-11-13T00:00:00Z' }
      : url.pathname === '/api/dashboard/summary'
        ? summary
        : url.pathname === '/api/monitoring-results/recent'
          ? [result]
          : url.pathname === '/api/devices'
            ? { content: [device], totalElements: 1, totalPages: 1, page: 0, size: 100 }
            : url.pathname === '/api/agents'
              ? { content: [agent], totalElements: 1, totalPages: 1, page: 0, size: 100 }
              : url.pathname === '/api/alerts'
                ? { content: [alert], totalElements: 1, totalPages: 1, page: 0, size: 100 }
                : { content: [], totalElements: 0, totalPages: 0, page: 0, size: 100 }
    return new Response(JSON.stringify(response), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
})

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.unstubAllGlobals()
})

describe('NetMoni application', () => {
  it('authenticates through the API and renders API dashboard data', async () => {
    render(<App />)
    fireEvent.change(screen.getByLabelText('Email or username'), { target: { value: 'viewer@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'correct-horse-battery' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continue to dashboard' }))

    expect(await screen.findByRole('heading', { name: 'Good network, good day.' })).toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' })))
    expect(sessionStorage.getItem('netmoni-session')).toContain('test-jwt')
    expect(screen.getByText('100.0%')).toBeInTheDocument()
  })

  it('renders backend device, agent, monitoring, and alert data on their pages', async () => {
    sessionStorage.setItem('netmoni-session', JSON.stringify({
      accessToken: 'test-jwt', username: 'viewer@example.com', role: 'VIEWER', expiresAt: '2099-01-01T00:00:00Z',
    }))
    render(<App />)
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' })
    await screen.findByRole('heading', { name: 'Good network, good day.' })

    fireEvent.click(within(navigation).getByRole('button', { name: /Devices/ }))
    expect(await screen.findByRole('button', { name: /Core router/ })).toBeInTheDocument()
    fireEvent.click(within(navigation).getByRole('button', { name: /Agents/ }))
    expect(await screen.findByRole('heading', { name: 'Lab collector' })).toBeInTheDocument()
    fireEvent.click(within(navigation).getByRole('button', { name: /Monitoring/ }))
    expect(await screen.findByText('12 ms')).toBeInTheDocument()
    fireEvent.click(within(navigation).getByRole('button', { name: /Alerts/ }))
    expect(await screen.findByText('Lab collector heartbeat is late.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Filter alerts by severity'), { target: { value: 'CRITICAL' } })
    expect(screen.getByText('No alerts match')).toBeInTheDocument()
  })

  it('queues a subnet scan from the Network Discovery page', async () => {
    sessionStorage.setItem('netmoni-session', JSON.stringify({ accessToken: 'test-jwt', username: 'operator@example.com', role: 'OPERATOR', expiresAt: '2099-01-01T00:00:00Z' }))
    render(<App />)
    await screen.findByRole('heading', { name: 'Good network, good day.' })
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' })
    fireEvent.click(within(navigation).getByRole('button', { name: /Network discovery/ }))
    expect(await screen.findByRole('heading', { name: 'Discover devices automatically' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Start scan' }))
    expect(await screen.findByRole('button', { name: 'Scan in progress' })).toBeDisabled()
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/discovery/agents/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/scan', expect.objectContaining({ method: 'POST' })))
  })
})

function initMethod(_input: RequestInfo | URL, init?: RequestInit) {
  return init?.method ?? 'GET'
}
