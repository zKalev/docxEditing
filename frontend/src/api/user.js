// A per-tab guest identity, so two tabs on the same document show up as two co-editors.
const KEY = 'docs-poc-user'

export function currentUser() {
  try {
    const saved = sessionStorage.getItem(KEY)
    if (saved) return JSON.parse(saved)
  } catch {
    // storage unavailable: fall through to a fresh identity
  }
  const n = Math.floor(1000 + Math.random() * 9000)
  const user = { id: `guest-${n}`, name: `Guest ${n}` }
  try {
    sessionStorage.setItem(KEY, JSON.stringify(user))
  } catch {
    // ignore
  }
  return user
}
