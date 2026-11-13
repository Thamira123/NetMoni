import { afterEach, describe, expect, it, vi } from 'vitest'
import { request } from './api'

describe('API request helper', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends bearer authentication and parses a successful response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ username: 'ops' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request<{ username: string }>('/auth/me', 'test-token')).resolves.toEqual({ username: 'ops' })
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer test-token' }),
    }))
  })

  it('returns undefined for no-content responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(request<void>('/devices/device-id', 'test-token', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('surfaces the API message and HTTP status on errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Not allowed' }), {
      status: 403,
      headers: { 'Content-Type': 'application/json' },
    })))
    await expect(request('/devices', 'test-token')).rejects.toMatchObject({
      name: 'ApiError', message: 'Not allowed', status: 403,
    })
  })
})
