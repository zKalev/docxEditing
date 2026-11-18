<script setup>
import { onMounted, ref } from 'vue'
import { listDocuments } from '../api/documents.js'

const documents = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await listDocuments()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function formatSize(bytes) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="heading">
      <h1>Documents</h1>
      <button class="secondary" :disabled="loading" @click="load">Refresh</button>
    </div>

    <p v-if="error" class="notice error">{{ error }}</p>
    <p v-else-if="loading">Loading…</p>
    <p v-else-if="documents.length === 0" class="notice info">
      No .docx files found. Put some in the backend's <code>storage/</code> folder and refresh.
    </p>

    <table v-else class="docs">
      <thead>
        <tr><th>Name</th><th>Size</th><th>Last modified</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="doc in documents" :key="doc.id">
          <td>{{ doc.title }}</td>
          <td>{{ formatSize(doc.size) }}</td>
          <td>{{ formatDate(doc.lastModified) }}</td>
          <td class="actions">
            <RouterLink class="button" :to="{ name: 'editor', params: { id: doc.id } }">Edit</RouterLink>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.heading { display: flex; align-items: center; justify-content: space-between; }
.docs { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
.docs th, .docs td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #e5e7eb; }
.docs th { font-size: 13px; color: #6b7280; font-weight: 600; }
.actions { text-align: right; }
</style>
