<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { DocumentEditor } from '@onlyoffice/document-editor-vue'
import { getEditorConfig, listDocuments } from '../api/documents.js'
import { currentUser } from '../api/user.js'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()

const documentServerUrl = ref('')
const config = shallowRef(null)
const loading = ref(true)
const error = ref('')
// { kind: 'info' | 'success' | 'error', text }
const status = ref(null)

let pollTimer = null
let lastModified = null

// --- ONLYOFFICE editor events (https://api.onlyoffice.com/docs/docs-api/usage-api/config/events/) ---

function onDocumentReady() {
  loading.value = false
  status.value = { kind: 'info', text: 'Document loaded. Press Ctrl+S to save to the server, or close the editor to save automatically.' }
}

// event.data === true: there are changes not yet sent to Document Server; false: Document Server has them.
function onDocumentStateChange(event) {
  if (event.data) {
    status.value = { kind: 'info', text: 'Editing…' }
  } else if (status.value?.kind !== 'success') {
    status.value = { kind: 'info', text: 'Changes synced to Document Server (not yet written to storage).' }
  }
}

function onError(event) {
  const { errorCode, errorDescription } = event.data ?? {}
  loading.value = false
  error.value = `Editor error ${errorCode}: ${errorDescription}`
}

function onWarning(event) {
  const { warningCode, warningDescription } = event.data ?? {}
  console.warn('ONLYOFFICE warning', warningCode, warningDescription)
}

// Defining this handler shows a "Close" button in the editor.
function onRequestClose() {
  router.push({ name: 'documents' })
}

// Called by the Vue wrapper when api.js cannot be loaded from Document Server.
function onLoadComponentError(errorCode, errorDescription) {
  loading.value = false
  const hint = errorCode === -2
    ? ` Is ONLYOFFICE Document Server running at ${documentServerUrl.value}?`
    : ''
  error.value = `Could not load the editor (${errorCode}): ${errorDescription}.${hint}`
}

// --- "saved to storage" notification ---
// The backend only writes the file when Document Server calls back (Ctrl+S, or ~10 s after the last
// editor closes). Watching the file's lastModified tells the user when that has actually happened.
async function pollSaved() {
  try {
    const doc = (await listDocuments()).find((d) => d.id === props.id)
    if (!doc) return
    if (lastModified && doc.lastModified !== lastModified) {
      status.value = { kind: 'success', text: `Saved to storage at ${new Date(doc.lastModified).toLocaleTimeString()}.` }
    }
    lastModified = doc.lastModified
  } catch {
    // backend briefly unavailable: try again on the next tick
  }
}

onMounted(async () => {
  try {
    const response = await getEditorConfig(props.id, currentUser())
    documentServerUrl.value = response.documentServerUrl
    config.value = {
      ...response.config,
      events: { onDocumentReady, onDocumentStateChange, onError, onWarning, onRequestClose },
    }
    await pollSaved()
    pollTimer = setInterval(pollSaved, 3000)
  } catch (e) {
    loading.value = false
    error.value = e.message
  }
})

onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<template>
  <div class="editor-page">
    <div class="toolbar">
      <RouterLink to="/" class="button secondary">← Documents</RouterLink>
      <strong class="title">{{ id }}</strong>
      <span v-if="status && !error" class="notice" :class="status.kind">{{ status.text }}</span>
    </div>

    <p v-if="error" class="notice error page">{{ error }}</p>
    <p v-else-if="loading" class="notice info page">Loading editor…</p>

    <div v-if="config && documentServerUrl" v-show="!error" class="editor-host">
      <DocumentEditor
        id="docEditor"
        :documentServerUrl="documentServerUrl"
        :config="config"
        :onLoadComponentError="onLoadComponentError"
      />
    </div>
  </div>
</template>

<style scoped>
.editor-page { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.toolbar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.toolbar .notice { margin: 0; padding: 4px 10px; font-size: 14px; }
.title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editor-host { flex: 1; min-height: 0; }
</style>
