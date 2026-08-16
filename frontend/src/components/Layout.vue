<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Close,
  Expand,
  Fold,
  List,
  Odometer,
  SwitchButton
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 侧边栏折叠状态 */
const collapsed = ref(false)

/** 当前激活菜单：项目相关页面统一高亮「项目管理」 */
const activeMenu = computed(() => (route.path.startsWith('/projects') ? '/projects' : route.path))

/** 退出登录 */
function handleCommand(command: string | number | object): void {
  if (command === 'logout') {
    userStore.logout()
    void router.push('/login')
  }
}

// ---------------- 面包屑 ----------------
const breadcrumbs = computed(() => {
  const crumbs: { title: string; path?: string }[] = []
  if (route.meta.title) {
    crumbs.push({ title: route.meta.title as string })
  }
  return crumbs
})

// ---------------- 多标签页 ----------------
interface Tab {
  path: string
  title: string
}

const tabs = ref<Tab[]>([])

function addTab(): void {
  const title = route.meta.title as string | undefined
  if (!title || route.meta.tag === false) return
  if (route.path === '/dashboard') {
    // 工作台作为固定首页标签
    if (!tabs.value.some((t) => t.path === '/dashboard')) {
      tabs.value.unshift({ path: '/dashboard', title })
    }
    return
  }
  if (!tabs.value.some((t) => t.path === route.path)) {
    tabs.value.push({ path: route.path, title })
  }
}

function closeTab(path: string): void {
  const idx = tabs.value.findIndex((t) => t.path === path)
  if (idx < 0) return
  tabs.value.splice(idx, 1)
  if (route.path === path) {
    const next = tabs.value[idx] ?? tabs.value[idx - 1]
    void router.push(next ? next.path : '/dashboard')
  }
}

watch(() => route.path, addTab, { immediate: true })
</script>

<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo" @click="router.push('/dashboard')">
        <span class="logo-icon">精</span>
        <span v-if="!collapsed" class="logo-text">精准测试平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        router
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/projects">
          <el-icon><List /></el-icon>
          <template #title>项目管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="right">
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <!-- 面包屑 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(b, i) in breadcrumbs" :key="i">{{ b.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="26" class="user-avatar">{{ userStore.username?.charAt(0)?.toUpperCase() ?? 'U' }}</el-avatar>
            <span class="user-name">{{ userStore.username }}</span>
            <el-icon class="caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 多标签页栏 -->
      <div class="tabs-bar">
        <el-tag
          v-for="tab in tabs"
          :key="tab.path"
          :type="tab.path === route.path ? 'primary' : 'info'"
          :effect="tab.path === route.path ? 'dark' : 'plain'"
          class="tab-item"
          @click="router.push(tab.path)"
        >
          {{ tab.title }}
          <el-icon v-if="tab.path !== '/dashboard'" class="tab-close" @click.stop="closeTab(tab.path)">
            <Close />
          </el-icon>
        </el-tag>
      </div>

      <!-- 主内容区 -->
      <el-main class="main">
        <router-view v-slot="{ Component, route: r }">
          <!-- 用完整路径做 key：既区分不同页面，也区分不同项目（切换时组件重建保证数据刷新） -->
          <keep-alive :max="10">
            <component :is="Component" :key="r.path" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}

.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
  flex-shrink: 0;
}

.logo-icon {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: linear-gradient(135deg, #409eff, #67c23a);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.side-menu {
  border-right: none;
  flex: 1;
}

.side-menu :deep(.el-menu-item) {
  height: 48px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.9), rgba(64, 158, 255, 0.4)) !important;
}

.right {
  min-width: 0;
  flex-direction: column;
}

.header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #fff;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: #606266;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #303133;
}

.user-avatar {
  background: #409eff;
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
}

.caret {
  font-size: 12px;
  color: #909399;
}

/* 多标签页栏 */
.tabs-bar {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-x: auto;
  white-space: nowrap;
  flex-shrink: 0;
}

.tab-item {
  cursor: pointer;
  flex-shrink: 0;
}

.tab-close {
  margin-left: 4px;
  font-size: 12px;
  cursor: pointer;
}

.tab-close:hover {
  color: #f56c6c;
}

.main {
  background: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}
</style>
