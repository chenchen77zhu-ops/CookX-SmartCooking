import {LOCAL_TEST_MODE} from '../config/buildMode.js'
import {localTestRuntime} from '../localtest/bootstrap.js'
import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {path:'/offline-examples',name:'OfflineExamples',component:()=>import('../localtest/DeferredExamples.vue'),meta:{requiresAuth:true}},
  {path:'/learning',name:'Learning',component:()=>import('../views/Learning.vue'),meta:{requiresAuth:true}},
  {path:'/menus',name:'MenuPlanner',component:()=>import('../views/MenuPlanner.vue'),meta:{requiresAuth:true}},
  {path:'/growth',name:'Growth',component:()=>import('../views/Growth.vue'),meta:{requiresAuth:true}},
  {path:'/leftovers',name:'Leftovers',component:()=>import('../views/Leftovers.vue'),meta:{requiresAuth:true}},
  {path:'/community',name:'Community',component:()=>import('../views/Community.vue'),meta:{requiresAuth:true}},
  {path:'/recipes',name:'Recipes',component:()=>import('../views/RecipeLibrary.vue'),meta:{requiresAuth:true}},
  {path:'/shopping',name:'Shopping',component:()=>import('../views/Shopping.vue'),meta:{requiresAuth:true}},
  {path:'/fridge',name:'FridgeItems',component:()=>import('../views/FridgeItems.vue'),meta:{requiresAuth:true}},
  {path:'/household',name:'Household',component:()=>import('../views/Household.vue'),meta:{requiresAuth:true}},
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
  routes,
  scrollBehavior: (to, from, saved) => saved || { top: 0 }
})

// 路由守卫保持不变...
router.beforeEach((to, from, next) => {
  if(LOCAL_TEST_MODE&&['/household','/shopping','/recipes','/community','/leftovers','/growth','/menus','/learning'].includes(to.path))return next({path:'/offline-examples',query:{from:to.path}})
  if(LOCAL_TEST_MODE){localStorage.setItem('user',JSON.stringify(localTestRuntime.currentUser()));if(['/login','/register','/account-security'].includes(to.path))return next('/home')}
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
