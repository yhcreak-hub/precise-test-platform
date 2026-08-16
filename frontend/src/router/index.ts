import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 路由 meta 类型扩展
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/ProjectList.vue'),
        meta: { title: '项目管理' }
      },
      {
        path: 'projects/:id/apis',
        name: 'ProjectApis',
        component: () => import('@/views/ApiList.vue'),
        meta: { title: '接口管理' }
      },
      {
        path: 'projects/:id/cases',
        name: 'ProjectCases',
        component: () => import('@/views/CaseList.vue'),
        meta: { title: '用例管理' }
      },
      {
        path: 'projects/:id/change-analysis',
        name: 'ProjectChangeAnalysis',
        component: () => import('@/views/ChangeAnalysis.vue'),
        meta: { title: '变更分析' }
      }
    ]
  },
  // 兜底路由
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫：
 * - 未登录访问受保护页面 -> /login
 * - 已登录访问 /login -> /
 */
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    return { path: '/login' }
  }
  if (to.path === '/login' && userStore.token) {
    return { path: '/' }
  }
  document.title = to.meta.title ? `${to.meta.title} - 精准测试平台` : '精准测试平台'
  return true
})

export default router
