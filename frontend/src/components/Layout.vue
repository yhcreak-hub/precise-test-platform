<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Expand, Fold, List, Odometer, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 侧边栏折叠状态 */
const collapsed = ref(false)

/** 当前激活菜单（与路由路径一致） */
const activeMenu = computed(() => route.path)

/** 退出登录 */
function handleCommand(command: string | number | object): void {
  if (command === 'logout') {
    userStore.logout()
    void router.push('/login')
  }
}
</script>

<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">{{ collapsed ? '精测' : '精准测试平台' }}</div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#ffffff"
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

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            {{ userStore.username }}（{{ userStore.role }}）
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

      <!-- 主内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background-color: #001529;
  transition: width 0.2s;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  background-color: #002140;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.user-info {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
  font-size: 14px;
}

.caret {
  font-size: 12px;
  color: #909399;
}

.main {
  background: #f0f2f5;
}
</style>
