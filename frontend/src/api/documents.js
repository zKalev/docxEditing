const BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function getJson(path) {
  let response
  try {
    response = await fetch(`${BASE}${path}`)
  } catch {
    throw new Error(`Cannot reach the backend at ${BASE}. Is Spring Boot running?`)
  }
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} for ${path}`)
  }
  return response.json()
}

/** @returns {Promise<Array<{id: string, title: string, size: number, lastModified: string}>>} */
export function listDocuments() {
  return getJson('/api/documents')
}

/** @returns {Promise<{documentServerUrl: string, config: object}>} */
export function getEditorConfig(id, user) {
  const params = new URLSearchParams({ userId: user.id, userName: user.name })
  return getJson(`/api/documents/${encodeURIComponent(id)}/config?${params}`)
}
