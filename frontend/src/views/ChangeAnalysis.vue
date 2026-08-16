<template>
  <div class="change-analysis">
    <el-page-header :content="`变更分析 - ${projectName}`" @back="router.push('/projects')" />

    <el-card class="query-card">
      <el-form :inline="true" class="query-form">
        <el-form-item label="基线版本">
          <el-input v-model="form.baseVersion" placeholder="如 master" style="width: 220px" />
        </el-form-item>
        <el-form-item label="当前版本">
          <el-input v-model="form.nowVersion" placeholder="如 dev_xxx" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="analyzing" @click="handleAnalyze">
            <el-icon><Search /></el-icon>&nbsp;分析变更
          </el-button>
        </el-form-item>
      </el-form>
      <div class="tip">
        分析两版本间代码差异 → 定位变更的 Controller → 筛选需要回归的用例（精准回归）
      </div>
    </el-card>

    <template v-if="result">
      <!-- 概览 -->
      <el-card class="overview-card">
        <div class="overview">
          <div class="ov-item">
            <div class="ov-num">{{ result.changedFileCount }}</div>
            <div class="ov-label">变更文件</div>
          </div>
          <div class="ov-item">
            <div class="ov-num">{{ result.changedClasses.length }}</div>
            <div class="ov-label">变更 Controller 类</div>
          </div>
          <div class="ov-item">
            <div class="ov-num ok">{{ result.matchedCases.length }}</div>
            <div class="ov-label">命中用例（需回归）</div>
          </div>
          <div class="ov-versions">
            <el-tag type="info">{{ result.baseVersion }}</el-tag>
            <span class="arrow">→</span>
            <el-tag type="warning">{{ result.nowVersion }}</el-tag>
          </div>
        </div>
      </el-card>

      <!-- 变更类 -->
      <el-card class="table-card">
        <template #header><span>变更的 Controller 类</span></template>
        <el-table :data="classRows" stripe border size="small">
          <el-table-column prop="className" label="类全名" min-width="320" show-overflow-tooltip />
          <el-table-column label="命中代码单元" width="120">
            <template #default="{ row }">
              <el-tag :type="row.matched ? 'success' : 'danger'" size="small">
                {{ row.matched ? '已匹配' : '未覆盖' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="classRows.length === 0" description="未识别到变更的 Controller 类" />
      </el-card>

      <!-- 命中用例 -->
      <el-card class="table-card">
        <template #header>
          <span>命中用例（{{ result.matchedCases.length }} 条，建议回归范围）</span>
          <el-button v-if="result.matchedCases.length > 0" type="primary" size="small" @click="runAllMatched">
            <el-icon><VideoPlay /></el-icon>&nbsp;批量执行命中用例
          </el-button>
        </template>
        <el-table :data="result.matchedCases" stripe border size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="场景" width="100">
            <template #default="{ row }">
              <el-tag :type="scenarioType(row.scenarioType)" size="small">{{ scenarioText(row.scenarioType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="用例标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="执行" width="160">
            <template #default="{ row }">
              <el-button type="success" link size="small" :loading="executingId === row.id" @click="runCase(row)">
                执行
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="result.matchedCases.length === 0" description="未命中用例（变更代码未被任何用例覆盖 → 测试空洞）" />
      </el-card>
    </template>

    <el-empty v-else description="输入两个版本号，点击「分析变更」筛选回归用例" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, VideoPlay } from '@element-plus/icons-vue'
import { analyzeChange, executeCase, type ChangeAnalysisResult, type TestCase } from '@/api/case'

const route = useRoute()
const router = useRouter()

const projectId = Number(route.params.id)
const projectName = String(route.query.name ?? '项目')

const form = reactive({ baseVersion: 'master', nowVersion: 'dev_20210910_getApiByMethod' })
const analyzing = ref(false)
const result = ref<ChangeAnalysisResult | null>(null)
const executingId = ref<number | null>(null)

const classRows = computed(() => {
  if (!result.value) return []
  const matchedNames = new Set(result.value.matchedUnits.map((u) => u.className))
  return result.value.changedClasses.map((className) => ({
    className,
    matched: matchedNames.has(className)
  }))
})

async function handleAnalyze(): Promise<void> {
  if (!form.baseVersion || !form.nowVersion) {
    ElMessage.warning('请填写基线版本和当前版本')
    return
  }
  analyzing.value = true
  try {
    result.value = await analyzeChange(projectId, { ...form })
    ElMessage.success('变更分析完成')
  } catch {
    ElMessage.error('变更分析失败（请确认分支/提交存在且已建立用例-代码关联）')
  } finally {
    analyzing.value = false
  }
}

async function runCase(row: TestCase): Promise<void> {
  executingId.value = row.id
  try {
    const r = await executeCase(row.id)
    ElMessage.success(`用例 #${row.id}：${r.status}（${r.costMs}ms）`)
  } catch {
    ElMessage.error('执行失败')
  } finally {
    executingId.value = null
  }
}

async function runAllMatched(): Promise<void> {
  if (!result.value || result.value.matchedCases.length === 0) return
  const cases = result.value.matchedCases
  let pass = 0
  for (const c of cases) {
    try {
      const r = await executeCase(c.id)
      if (r.status === 'PASS') pass++
    } catch {
      // 忽略单个失败
    }
  }
  ElMessage.success(`批量执行完成：${pass}/${cases.length} PASS`)
}

function scenarioType(t: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (t) {
    case 'normal': return 'success'
    case 'required': return 'warning'
    case 'boundary': return 'danger'
    default: return 'info'
  }
}

function scenarioText(t: string): string {
  switch (t) {
    case 'normal': return '正常流程'
    case 'required': return '必填校验'
    case 'boundary': return '边界值'
    case 'exception': return '异常输入'
    default: return t
  }
}

onMounted(() => {
  // 可选：默认触发一次分析
})
</script>

<style scoped>
.change-analysis {
  padding: 16px;
}

.query-card {
  margin-top: 16px;
}

.query-form {
  margin-bottom: 4px;
}

.tip {
  font-size: 12px;
  color: #909399;
}

.overview-card {
  margin-top: 16px;
}

.overview {
  display: flex;
  align-items: center;
  gap: 50px;
}

.ov-item {
  text-align: center;
}

.ov-num {
  font-size: 30px;
  font-weight: 700;
  color: #409eff;
}

.ov-num.ok {
  color: #67c23a;
}

.ov-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.ov-versions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.arrow {
  color: #909399;
}

.table-card {
  margin-top: 16px;
}
</style>
