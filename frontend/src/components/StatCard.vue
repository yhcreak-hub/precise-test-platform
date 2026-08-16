<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 统计数值 */
  value: string | number
  /** 标签文字 */
  label: string
  /** 图标（Element Plus 图标组件） */
  icon: string
  /** 主题色 */
  color?: string
}>()

const theme = computed(() => props.color ?? '#409eff')
</script>

<template>
  <div class="stat-card" :style="{ '--stat-color': theme }">
    <div class="stat-icon">
      <el-icon :size="28"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-info">
      <div class="stat-value">{{ value }}</div>
      <div class="stat-label">{{ label }}</div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 21, 41, 0.12);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--stat-color), color-mix(in srgb, var(--stat-color) 60%, white));
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}
</style>
