const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function webSocketUrl(): string {
  const configured = import.meta.env.VITE_WS_BASE_URL
  if (configured) return configured
  if (API_BASE.startsWith('http')) {
    const api = new URL(API_BASE)
    return `${api.protocol === 'https:' ? 'wss:' : 'ws:'}//${api.host}/ws`
  }
  return `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
}

export async function request<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 15_000)
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      signal: init?.signal ?? controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init?.headers,
      },
    })
  } catch (reason) {
    if (reason instanceof DOMException && reason.name === 'AbortError') {
      throw new ApiError('The API request timed out.', 0)
    }
    throw reason
  } finally {
    window.clearTimeout(timeoutId)
  }

  if (!response.ok) {
    const body = await response.text()
    let message = body
    try {
      const parsed = JSON.parse(body) as { message?: string; error?: string }
      message = parsed.message || parsed.error || body
    } catch { /* Keep a plain-text API error readable. */ }
    throw new ApiError(message || `Request failed (${response.status})`, response.status)
  }
  if (response.status === 204) return undefined as T
  return await response.json() as T
}
