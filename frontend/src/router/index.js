import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/create' },
  { path: '/create', component: () => import('../views/CreateView.vue') },
  { path: '/manage', component: () => import('../views/ManageView.vue') },
  { path: '/chat', component: () => import('../views/ChatView.vue') },
  { path: '/native', component: () => import('../views/NativeView.vue') },
]

export default createRouter({ history: createWebHistory(), routes })
