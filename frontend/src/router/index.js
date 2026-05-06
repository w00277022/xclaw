import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/', redirect: '/create' },
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/register', component: () => import('../views/RegisterView.vue') },
  { path: '/create', component: () => import('../views/CreateView.vue'), meta: { requiresAuth: true } },
  { path: '/manage', component: () => import('../views/ManageView.vue'), meta: { requiresAuth: true } },
  { path: '/chat', component: () => import('../views/ChatView.vue'), meta: { requiresAuth: true } },
  { path: '/native', component: () => import('../views/NativeView.vue'), meta: { requiresAuth: true } },
  { path: '/admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/nodes', component: () => import('../views/NodeView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('xclaw_token')
  if (to.meta.requiresAuth && !token) {
    ElMessage.warning('请先登录')
    next('/login')
    return
  }
  if (to.meta.requiresAdmin) {
    try {
      const user = JSON.parse(localStorage.getItem('xclaw_user') || '{}')
      if (user.role !== 'ADMIN') {
        ElMessage.warning('仅管理员可访问此页面')
        next('/create')
        return
      }
    } catch { next('/login'); return }
  }
  if (to.path === '/login' && token) {
    next('/create')
    return
  }
  next()
})

export default router
