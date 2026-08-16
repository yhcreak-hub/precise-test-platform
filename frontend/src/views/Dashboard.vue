<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Collection, Connection, DocumentChecked, Files, List, VideoPlay } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import { getDashboardStats, type DashboardStats } from '@/api/dashboard'

const router = useRouter()

const stats = ref<DashboardStats | null>(null)

async function loadStats(): Promise<void> {
  try {
    stats.value = await getDashboardStats()
  } catch {
    // 忽略统计加载失败
  }
}

// ---------------- ECharts 趋势图 ----------------
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const trendData = computed(() => {
  if (!stats.value?.recentExecs || stats.value.recentExecs.length === 0) {
    return { labels: [], passed: [], total: [] }
  }
  // 最近执行记录倒序展示（旧的在前）
  const list = [...stats.value.recentExecs].reverse()
  return {
    labels: list.map((e) => `#${e.id}`),
    passed: list.map((e) => e.passed),
    total: list.map((e) => e.total)
  }
})

function renderChart(): void {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  const data = trendData.value
  chart.setOption({
    title: { text: '最近执行趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['通过', '总数'], bottom: 0 },
    grid: { left: 40, right: 20, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: data.labels },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '通过', type: 'bar', barWidth: 18, itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] }, data: data.passed },
      { name: '总数', type: 'line', smooth: true, itemStyle: { color: '#409eff' }, data: data.total }
    ]
  })
}

function handleResize(): void {
  chart?.resize()
}

onMounted(async () => {
  await loadStats()
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
    <h2 class="welcome">欢迎回来，精准测试工作台</h2>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <StatCard :value="stats?.projectCount ?? 0" label="项目总数" icon="Collection" color="#409eff" />
      <StatCard :value="stats?.apiCount ?? 0" label="接口总数" icon="List" color="#67c23a" />
      <StatCard :value="stats?.caseCount ?? 0" label="用例总数" icon="DocumentChecked" color="#e6a23c" />
      <StatCard :value="stats?.mappingCount ?? 0" label="用例-代码关联" icon="Connection" color="#909399" />
      <StatCard :value="stats?.execRecordCount ?? 0" label="执行记录" icon="Files" color="#f56c6c" />
    </div>

    <!-- 图表 + 快捷入口 -->
    <div class="content-row">
      <el-card class="chart-card">
        <div ref="chartRef" class="chart" />
        <div v-if="stats && stats.recentExecs && stats.recentExecs.length === 0" class="chart-empty">
          暂无执行记录，去「项目管理」执行用例后查看趋势
        </div>
      </el-card>

      <el-card class="quick-card">
        <template #header><span>快捷操作</span></template>
        <div class="quick-list">
          <div class="quick-item" @click="router.push('/projects')">
            <el-icon class="quick-icon blue"><Collection /></el-icon>
            <div>
              <div class="quick-title">导入项目</div>
              <div class="quick-desc">校验 Git 仓库后导入</div>
            </div>
          </div>
          <div class="quick-item" @click="router.push('/projects')">
            <el-icon class="quick-icon green"><VideoPlay /></el-icon>
            <div>
              <div class="quick-title">分析接口</div>
              <div class="quick-desc">项目列表触发异步分析</div>
            </div>
          </div>
          <div class="quick-item" @click="router.push('/projects')">
            <el-icon class="quick-icon orange"><DocumentChecked /></el-icon>
            <div>
              <div class="quick-title">用例管理</div>
              <div class="quick-desc">分组查看/编辑/执行</div>
            </div>
          </div>
        </div>
        <div class="pass-rate" v-if="stats">
          <div class="pass-rate-label">最近执行通过率</div>
          <el-progress
            :percentage="stats.recentPassRate"
            :status="stats.recentPassRate >= 80 ? 'success' : 'warning'"
            :stroke-width="14"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 4px;
}

.welcome {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.content-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

@media (max-width: 1100px) {
  .content-row {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  margin-bottom: 0;
}

.chart {
  height: 320px;
}

.chart-empty {
  text-align: center;
  color: #909399;
  padding: 12px;
  font-size: 13px;
}

.quick-card {
  margin-bottom: 0;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.quick-item:hover {
  background: #f5f7fa;
}

.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #fff;
  flex-shrink: 0;
}

.quick-icon.blue { background: #409eff; }
.quick-icon.green { background: #67c23a; }
.quick-icon.orange { background: #e6a23c; }

.quick-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.quick-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.pass-rate {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.pass-rate-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
</style>
