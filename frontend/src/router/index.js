import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {path:'/recipes',component:()=>import('../views/RecipeLibrary.vue'),meta:{requiresAuth:true}},
  {path:'/shopping',component:()=>import('../views/Shopping.vue'),meta:{requiresAuth:true}},
  {path:'/household',component:()=>import('../views/Household.vue'),meta:{requiresAuth:true}},
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
    path: '/favorites',
    name: 'Favorites',
    component: () => import('../views/Favorites.vue'),
    meta: { requiresAuth: true, navTab: 'Profile' }
  },
  {
    path: '/cooking-history',
    name: 'CookingHistory',
    component: () => import('../views/CookingHistory.vue'),
    meta: { requiresAuth: true, navTab: 'Profile' }
  },
  {
    path: '/preferences',
    name: 'Preferences',
    component: () => import('../views/Preferences.vue'),
    meta: { requiresAuth: true, navTab: 'Profile' }
  },
  {
    path: '/account-security',
    name: 'AccountSecurity',
    component: () => import('../views/AccountSecurity.vue'),
    meta: { requiresAuth: true, navTab: 'Profile' }
  },
  {
    path: '/about',
    name: 'AboutCookX',
    component: () => import('../views/AboutCookX.vue'),
    meta: { requiresAuth: true, navTab: 'Profile' }
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
