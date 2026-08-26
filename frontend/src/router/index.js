import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/HomeView.vue'),
    meta: { requiresAuth: true }
  },
  // ✅ 核心修复：确保这一段代码存在，且路径拼写正确
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/capture-confirm',
    name: 'CaptureConfirm',
    component: () => import('../views/CaptureConfirm.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫保持不变...
router.beforeEach((to, from, next) => {
  const user = localStorage.getItem('user')
  if (to.meta.requiresAuth && !user) {
    next('/login')
  } else if (user && (to.path === '/login' || to.path === '/register')) {
    next('/home')
  } else {
    next()
  }
})

export default router