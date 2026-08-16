<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/** 统计卡片（M2 TODO: 接入真实统计数据：项目数 / 接口数 / 用例数 / 关联数） */
const stats = [
  { label: '项目数', value: '--' },
  { label: '接口数', value: '--' },
  { label: '用例数', value: '--' },
  { label: '用例-代码关联', value: '--' }
]

// ---------------- ECharts 占位图 ----------------
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function renderChart(): void {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '用例生成趋势（占位数据）', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['自动生成', '人工维护'], bottom: 0 },
    grid: { left: 40, right: 20, top: 50, bottom: 40 },
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
    yAxis: { type: 'value' },
    series: [
      { name: '自动生成', type: 'line', smooth: true, data: [120, 200, 150, 80, 170, 110, 90] },
      { name: '人工维护', type: 'bar', barWidth: 20, data: [30, 40, 25, 50, 35, 60, 20] }
    ]
  })
}

function handleResize(): void {
  chart?.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎卡片 -->
    <el-card shadow="never" class="welcome-card">
      <h2>欢迎回来，{{ userStore.username }}（{{ userStore.role }}）</h2>
      <p>精准测试平台 M1：登录认证 · 项目管理 · 统一响应 已就绪；接口识别 / 用例生成 / 关联映射将在后续版本接入。</p>
    </el-card>

    <!-- 统计卡片（占位） -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="item in stats" :key="item.label" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图（占位数据，验证 ECharts 集成） -->
    <el-card shadow="never">
      <div ref="chartRef" class="chart" />
    </el-card>
  </div>
</template>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}

.welcome-card h2 {
  margin: 0 0 8px;
  color: #303133;
}

.welcome-card p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

.chart {
  height: 360px;
}
</style>
