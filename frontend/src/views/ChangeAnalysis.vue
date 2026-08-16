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
            <div class="ov-label">变更类</div>
          </div>
          <div class="ov-item">
            <div class="ov-num">{{ result.affectedControllerMethods.length }}</div>
            <div class="ov-label">受影响接口方法</div>
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

      <!-- 变更类（树形） -->
      <el-card class="table-card">
        <template #header><span>变更的类（{{ result.changedClasses.length }} 个）</span></template>
        <el-tree
          :data="classTree"
          :props="{ label: 'label', children: 'children' }"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <el-tag :type="data.type" size="small" effect="plain">{{ data.tag }}</el-tag>
              <span class="tree-label">{{ data.label }}</span>
            </span>
          </template>
        </el-tree>
        <el-empty v-if="classTree.length === 0" description="未识别到变更的类" />
      </el-card>

      <!-- 受影响接口（含调用链追溯 + 无覆盖补用例） -->
      <el-card class="table-card">
        <template #header>
          <span>受影响接口（{{ affectedApiRows.length }} 个，含调用链追溯）</span>
        </template>
        <el-table :data="affectedApiRows" stripe border size="small">
          <el-table-column label="方法" width="110">
            <template #default="{ row }">
              <el-tag :type="methodType(row.httpMethod)" effect="dark" size="small">{{ row.httpMethod }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="apiPath" label="接口路径" min-width="200" show-overflow-tooltip />
          <el-table-column prop="controllerMethod" label="Controller 方法" min-width="200" show-overflow-tooltip />
          <el-table-column label="用例覆盖" width="110">
            <template #default="{ row }">
              <el-tag :type="row.caseCount > 0 ? 'success' : 'warning'" size="small">
                {{ row.caseCount > 0 ? `${row.caseCount} 条` : '无覆盖' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.caseCount === 0"
                type="primary"
                link
                size="small"
                :loading="generatingApiId === row.apiId"
                @click="handleGenerateForApi(row)"
              >
                生成用例
              </el-button>
              <el-button v-else type="info" link size="small" @click="expandMatchedCases(row)">
                查看用例
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="callchain-tip">
          说明：变更类为 Controller 时直接命中；为 Service/DAO/Util 时通过方法调用链反向追溯至 Controller 接口
        </div>
      </el-card>

      <!-- 命中用例（按接口分类） -->
      <el-card class="table-card">
        <template #header>
          <span>命中用例（{{ result.matchedCases.length }} 条，按接口分类）</span>
          <el-button v-if="result.matchedCases.length > 0" type="primary" size="small" :loading="batchRunning" @click="runAllMatched">
            <el-icon><VideoPlay /></el-icon>&nbsp;批量执行命中用例
          </el-button>
        </template>
        <el-collapse v-model="openApiIds">
          <el-collapse-item v-for="group in matchedCaseGroups" :key="group.apiId" :name="group.apiId">
            <template #title>
              <div class="group-title">
                <el-tag :type="methodType(group.httpMethod)" effect="dark" size="small">{{ group.httpMethod }}</el-tag>
                <span class="group-path">{{ group.apiPath }}</span>
                <span class="group-meta">{{ group.cases.length }} 条用例</span>
              </div>
            </template>
            <el-table :data="group.cases" stripe border size="small">
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
          </el-collapse-item>
        </el-collapse>
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
import {
  analyzeChange,
  executeBatch,
  executeCase,
  generateCasesForApi,
  type ChangeAnalysisResult,
  type TestCase
} from '@/api/case'

const route = useRoute()
const router = useRouter()

const projectId = Number(route.params.id)
const projectName = String(route.query.name ?? '项目')

// ---------------- 变更类树形 ----------------
const classTree = computed(() => {
  if (!result.value) return []
  const groups: Record<string, { controller: string[]; other: string[] }> = {}
  for (const cls of result.value.changedClasses) {
    const pkg = cls.includes('.') ? cls.substring(0, cls.lastIndexOf('.')) : '(默认包)'
    if (!groups[pkg]) groups[pkg] = { controller: [], other: [] }
    if (cls.includes('Controller')) {
      groups[pkg].controller.push(cls)
    } else {
      groups[pkg].other.push(cls)
    }
  }
  const idCounter = { n: 0 }
  const nextId = () => `n${idCounter.n++}`
  return Object.entries(groups).map(([pkg, g]) => ({
    id: nextId(),
    label: pkg,
    tag: '包',
    type: 'info',
    children: [
      ...g.controller.map((cls) => ({ id: nextId(), label: cls, tag: 'Controller', type: 'danger' })),
      ...g.other.map((cls) => ({ id: nextId(), label: cls, tag: '其他', type: 'warning' }))
    ]
  }))
})

// ---------------- 受影响接口 ----------------
interface AffectedApiRow {
  apiId: number
  apiPath: string
  httpMethod: string
  controllerMethod: string
  caseCount: number
}

const affectedApiRows = computed<AffectedApiRow[]>(() => {
  if (!result.value) return []
  const rows: AffectedApiRow[] = []
  const caseApiIds = new Set(result.value.matchedCases.map((c) => c.apiDefinitionId))
  for (const [controllerMethod, apis] of Object.entries(result.value.affectedApis ?? {})) {
    for (const api of apis) {
      rows.push({
        apiId: api.id,
        apiPath: api.apiPath,
        httpMethod: api.httpMethod,
        controllerMethod: controllerMethod.split('.').pop() ?? '',
        caseCount: caseApiIds.has(api.id)
          ? result.value.matchedCases.filter((c) => c.apiDefinitionId === api.id).length
          : 0
      })
    }
  }
  return rows
})

// ---------------- 命中用例按接口分类 ----------------
interface CaseGroup {
  apiId: number
  apiPath: string
  httpMethod: string
  cases: TestCase[]
}

const matchedCaseGroups = computed<CaseGroup[]>(() => {
  if (!result.value) return []
  const groupMap = new Map<number, CaseGroup>()
  for (const tc of result.value.matchedCases) {
    if (!groupMap.has(tc.apiDefinitionId)) {
      const api = (result.value.affectedApis ? Object.values(result.value.affectedApis).flat().find((a) => a.id === tc.apiDefinitionId) : null)
      groupMap.set(tc.apiDefinitionId, {
        apiId: tc.apiDefinitionId,
        apiPath: api?.apiPath ?? `接口#${tc.apiDefinitionId}`,
        httpMethod: api?.httpMethod ?? 'API',
        cases: []
      })
    }
    groupMap.get(tc.apiDefinitionId)!.cases.push(tc)
  }
  return [...groupMap.values()]
})

const openApiIds = ref<number[]>([])

function expandMatchedCases(row: AffectedApiRow): void {
  if (!openApiIds.value.includes(row.apiId)) {
    openApiIds.value = [...openApiIds.value, row.apiId]
  }
}

// ---------------- 无覆盖补用例 ----------------
const generatingApiId = ref<number | null>(null)

async function handleGenerateForApi(row: AffectedApiRow): Promise<void> {
  generatingApiId.value = row.apiId
  try {
    const count = await generateCasesForApi(projectId, row.apiId)
    ElMessage.success(`已为 ${row.apiPath} 生成 ${count} 条用例`)
    // 重新分析以刷新覆盖状态
    await handleAnalyze()
  } catch {
    ElMessage.error('用例生成失败')
  } finally {
    generatingApiId.value = null
  }
}

const form = reactive({ baseVersion: 'master', nowVersion: 'dev_20210910_getApiByMethod' })
const analyzing = ref(false)
const result = ref<ChangeAnalysisResult | null>(null)
const executingId = ref<number | null>(null)
const batchRunning = ref(false)

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
  batchRunning.value = true
  try {
    const record = await executeBatch(projectId, {
      caseIds: result.value.matchedCases.map((c) => c.id),
      source: 'change_analysis',
      baseVersion: result.value.baseVersion,
      nowVersion: result.value.nowVersion
    })
    ElMessage.success(`批量执行完成：${record.passed}/${record.total} PASS（已记录到执行报告）`)
    // 跳转到执行记录页查看详情报告
    router.push({ name: 'ProjectExecRecords', params: { id: projectId }, query: { name: projectName } })
  } catch {
    ElMessage.error('批量执行失败')
  } finally {
    batchRunning.value = false
  }
}

function methodType(method: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (method) {
    case 'GET': return 'success'
    case 'POST': return 'warning'
    case 'PUT': return 'primary' as never
    case 'DELETE': return 'danger'
    default: return 'info'
  }
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

.callchain-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 8px 12px;
  border-radius: 4px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tree-label {
  font-size: 13px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.group-path {
  font-weight: 600;
  font-family: monospace;
}

.group-meta {
  font-size: 12px;
  color: #909399;
}
</style>
