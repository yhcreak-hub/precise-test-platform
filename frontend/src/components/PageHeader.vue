<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

defineProps<{
  /** 页面标题 */
  title: string
  /** 页面描述 */
  description?: string
  /** 是否显示返回按钮 */
  back?: boolean
}>()

const router = useRouter()
</script>

<template>
  <div class="page-header">
    <div class="header-left">
      <el-button v-if="back" class="back-btn" :icon="ArrowLeft" circle size="small" @click="router.back()" />
      <div>
        <h2 class="page-title">{{ title }}</h2>
        <p v-if="description" class="page-desc">{{ description }}</p>
      </div>
    </div>
    <div class="header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 4px 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  line-height: 1.3;
}

.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
