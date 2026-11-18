import { createRouter, createWebHistory } from 'vue-router'
import DocumentListView from './views/DocumentListView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'documents', component: DocumentListView },
    {
      path: '/edit/:id',
      name: 'editor',
      component: () => import('./views/EditorView.vue'),
      props: true,
    },
  ],
})
