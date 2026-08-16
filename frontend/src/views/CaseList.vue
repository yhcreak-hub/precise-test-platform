<template>
  <div class="case-list">
    <PageHeader :title="projectName" description="分组查看/编辑/手动新增/全量执行" back />

    <!-- 空洞检测卡片 -->
    <el-card class="gap-card">
      <div class="gap-content">
        <div class="gap-item">
          <div class="gap-num">{{ gap?.totalApis ?? '-' }}</div>
          <div class="gap-label">接口总数</div>
        </div>
        <div class="gap-item">
          <div class="gap-num warn">{{ gap?.gapApis ?? '-' }}</div>
          <div class="gap-label">测试空洞（无用例）</div>
        </div>
        <div class="gap-item">
          <div class="gap-num" :class="(gap?.gapRate ?? 0) > 50 ? 'danger' : 'ok'">
            {{ gap?.gapRate ?? '-' }}%
          </div>
          <div class="gap-label">空洞率</div>
        </div>
        <el-button type="primary" :loading="generating" @click="handleGenerate">
          <el-icon><MagicStick /></el-icon>&nbsp;为空洞接口生成用例
        </el-button>
        <el-button type="warning" :loading="mappingBuilding" @click="handleBuildMapping">
          <el-icon><Connection /></el-icon>&nbsp;建立用例-代码关联
        </el-button>
        <el-button type="success" :icon="Plus" @click="openCreateDialog">新增用例</el-button>
        <el-button
          type="danger"
          :icon="VideoPlay"
          :loading="runningAll"
          @click="handleRunAllCases"
        >
          全量执行用例
        </el-button>
      </div>
    </el-card>

    <!-- 按接口分组的用例 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>用例列表（按接口分组，共 {{ totalCases }} 条）</span>
          <el-button :loading="loading" @click="loadAll">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
        </div>
      </template>

      <div v-loading="loading">
        <el-collapse v-model="openApis">
          <el-collapse-item v-for="group in groups" :key="group.api.id" :name="group.api.id">
            <template #title>
              <div class="group-title">
                <el-tag :type="methodType(group.api.httpMethod)" effect="dark" size="small">
                  {{ group.api.httpMethod }}
                </el-tag>
                <span class="group-path">{{ group.api.apiPath }}</span>
                <span class="group-meta">{{ group.cases.length }} 条用例</span>
              </div>
            </template>

            <el-table :data="group.cases" stripe border size="small">
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column label="场景" width="100">
                <template #default="{ row }">
                  <el-tag :type="scenarioType(row.scenarioType)" size="small">
                    {{ scenarioText(row.scenarioType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="用例标题" min-width="200" show-overflow-tooltip />
              <el-table-column label="来源" width="70">
                <template #default="{ row }">
                  <el-tag :type="row.source === 'ai' ? 'danger' : row.source === 'manual' ? 'warning' : 'success'" size="small">
                    {{ row.source === 'ai' ? 'AI' : row.source === 'manual' ? '手动' : '规则' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
                  <el-button
                    type="success"
                    link
                    size="small"
                    :loading="executingId === row.id"
                    @click="handleExecute(row)"
                  >
                    执行
                  </el-button>
                  <el-button type="info" link size="small" @click="viewDetail(row)">详情</el-button>
                  <el-button type="warning" link size="small" @click="openMapping(row)">关联代码</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>

        <el-empty v-if="!loading && groups.length === 0" description="暂无用例，请先点击上方按钮生成" />
      </div>
    </el-card>

    <!-- 新增用例弹窗 -->
    <el-dialog v-model="createVisible" title="手动新增用例（来源：手动）" width="65%">
      <el-form label-width="90px">
        <el-form-item label="所属接口" required>
          <el-select v-model="createForm.apiDefinitionId" placeholder="选择接口" style="width: 100%">
            <el-option
              v-for="api in apis"
              :key="api.id"
              :label="`${api.httpMethod} ${api.apiPath}`"
              :value="api.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="场景类型">
          <el-select v-model="createForm.scenarioType" style="width: 100%">
            <el-option label="正常流程" value="normal" />
            <el-option label="必填校验" value="required" />
            <el-option label="边界值" value="boundary" />
            <el-option label="异常输入" value="exception" />
            <el-option label="业务场景" value="business" />
          </el-select>
        </el-form-item>
        <el-form-item label="用例标题" required>
          <el-input v-model="createForm.title" placeholder="如：查询用户列表-正常流程" />
        </el-form-item>
        <el-form-item label="请求参数">
          <el-input v-model="createForm.requestJson" type="textarea" :rows="6" class="mono" placeholder='{"page":1,"size":10}' />
        </el-form-item>
        <el-form-item label="断言">
          <el-input v-model="createForm.assertsJson" type="textarea" :rows="3" class="mono" placeholder='{"body.code":200}' />
        </el-form-item>
        <el-form-item label="请求头">
          <el-input v-model="createForm.headersJson" type="textarea" :rows="2" class="mono" placeholder='如：{"token":"xxx"}（可选）' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateCase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="`编辑用例 #${editing?.id}`" width="65%">
      <el-form label-width="90px">
        <el-form-item label="用例标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="请求参数">
          <el-input v-model="editForm.requestJson" type="textarea" :rows="8" class="mono" />
        </el-form-item>
        <el-form-item label="请求头">
          <el-input v-model="editForm.headersJson" type="textarea" :rows="2" class="mono" placeholder='如：{"token":"xxx"}' />
        </el-form-item>
        <el-form-item label="断言">
          <el-input v-model="editForm.assertsJson" type="textarea" :rows="4" class="mono" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="resultVisible" :title="`执行结果 - ${result?.title ?? ''}`" width="60%">
      <template v-if="result">
        <el-alert
          :type="result.status === 'PASS' ? 'success' : result.status === 'FAIL' ? 'warning' : 'error'"
          :title="`${result.status}（${result.costMs}ms）`"
          :closable="false"
          class="result-alert"
        />
        <el-descriptions :column="2" border class="result-desc">
          <el-descriptions-item label="请求 URL">{{ result.url }}</el-descriptions-item>
          <el-descriptions-item label="HTTP 状态">{{ result.httpStatus ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="result.errorMsg" class="error-msg">{{ result.errorMsg }}</div>
        <div class="result-section">
          <div class="result-label">断言明细</div>
          <ul class="assert-list">
            <li v-for="(d, i) in result.assertDetails" :key="i">{{ d }}</li>
            <li v-if="result.assertDetails.length === 0">（无断言）</li>
          </ul>
        </div>
        <div class="result-section">
          <div class="result-label">响应体</div>
          <pre class="detail-pre">{{ pretty(result.responseBody) }}</pre>
        </div>
      </template>
    </el-dialog>

    <!-- 关联代码弹窗 -->
    <el-dialog v-model="mappingVisible" :title="`关联代码 - ${mappingCase?.title ?? ''}`" width="55%">
      <el-alert
        type="info"
        title="静态分析关联：用例 → 接口 → Controller 方法（relationType=direct, confidence=static）"
        :closable="false"
        class="result-alert"
      />
      <el-table :data="mappedUnits" stripe border size="small" v-loading="mappingLoading">
        <el-table-column prop="className" label="Controller 类" min-width="220" show-overflow-tooltip />
        <el-table-column prop="methodName" label="方法" min-width="150" show-overflow-tooltip />
        <el-table-column prop="filePath" label="源码位置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行号" width="70" />
      </el-table>
      <el-empty v-if="!mappingLoading && mappedUnits.length === 0" description="暂无关联代码（请先触发「建立用例-代码关联」）" />
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="`用例详情 #${current?.id ?? ''}`" width="55%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="标题">{{ current?.title }}</el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre class="detail-pre">{{ pretty(current?.requestJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="断言">
          <pre class="detail-pre">{{ pretty(current?.assertsJson) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Connection, Plus, VideoPlay } from '@element-plus/icons-vue'
import {
  buildMapping,
  createCase,
  detectGap,
  executeBatch,
  executeCase,
  generateCases,
  groupedCases,
  listCaseMapping,
  updateCase,
  type CodeUnit,
  type ExecuteResult,
  type GapReport,
  type TestCase
} from '@/api/case'
import { getProjectApis, type ApiDefinition } from '@/api/definition'

const route = useRoute()
const router = useRouter()

const projectId = Number(route.params.id)
const projectName = String(route.query.name ?? '项目')

const gap = ref<GapReport | null>(null)
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const executingId = ref<number | null>(null)
const mappingBuilding = ref(false)

const openApis = ref<number[]>([])
const apis = ref<ApiDefinition[]>([])
const caseMap = ref<Record<number, TestCase[]>>({})

interface Group {
  api: ApiDefinition
  cases: TestCase[]
}

const groups = computed<Group[]>(() => {
  const apiById = new Map(apis.value.map((a) => [a.id, a]))
  const result: Group[] = []
  for (const api of apis.value) {
    const cases = caseMap.value[api.id] ?? []
    if (cases.length > 0) {
      result.push({ api, cases })
    }
  }
  return result
})

const totalCases = computed(() => Object.values(caseMap.value).reduce((sum, list) => sum + list.length, 0))

// ---------------- 编辑 ----------------
const editVisible = ref(false)
const editing = ref<TestCase | null>(null)
const editForm = reactive({ title: '', requestJson: '', assertsJson: '', headersJson: '' })

// ---------------- 手动新增用例 ----------------
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  apiDefinitionId: undefined as number | undefined,
  title: '',
  requestJson: '',
  assertsJson: '',
  headersJson: '',
  scenarioType: 'normal'
})

function openCreateDialog(): void {
  createForm.apiDefinitionId = apis.value[0]?.id
  createForm.title = ''
  createForm.requestJson = ''
  createForm.assertsJson = '{"body.code":200}'
  createForm.headersJson = ''
  createForm.scenarioType = 'normal'
  createVisible.value = true
}

async function handleCreateCase(): Promise<void> {
  if (!createForm.apiDefinitionId) {
    ElMessage.warning('请选择所属接口')
    return
  }
  if (!createForm.title) {
    ElMessage.warning('请输入用例标题')
    return
  }
  creating.value = true
  try {
    await createCase(projectId, {
      apiDefinitionId: createForm.apiDefinitionId,
      title: createForm.title,
      requestJson: createForm.requestJson || '{}',
      assertsJson: createForm.assertsJson || '{"body.code":200}',
      headersJson: createForm.headersJson || undefined,
      scenarioType: createForm.scenarioType
    })
    ElMessage.success('手动用例已创建（来源：手动），重新生成用例时不会被覆盖')
    createVisible.value = false
    await loadAll()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

function openEdit(row: TestCase): void {
  editing.value = row
  editForm.title = row.title
  editForm.requestJson = row.requestJson
  editForm.assertsJson = row.assertsJson
  editForm.headersJson = row.headersJson ?? ''
  editVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!editing.value) return
  saving.value = true
  try {
    await updateCase(editing.value.id, {
      title: editForm.title,
      requestJson: editForm.requestJson,
      assertsJson: editForm.assertsJson,
      headersJson: editForm.headersJson
    })
    ElMessage.success('用例已保存')
    editVisible.value = false
    await loadAll()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// ---------------- 执行 ----------------
const resultVisible = ref(false)
const result = ref<ExecuteResult | null>(null)

async function handleExecute(row: TestCase): Promise<void> {
  executingId.value = row.id
  try {
    result.value = await executeCase(row.id)
    resultVisible.value = true
  } catch {
    ElMessage.error('执行失败（请确认项目已配置被测服务地址 baseUrl）')
  } finally {
    executingId.value = null
  }
}

// ---------------- 全量执行用例（生成执行记录） ----------------
const runningAll = ref(false)

async function handleRunAllCases(): Promise<void> {
  // 收集项目下全部用例 id
  const allIds: number[] = []
  for (const list of Object.values(caseMap.value)) {
    for (const tc of list) {
      allIds.push(tc.id)
    }
  }
  if (allIds.length === 0) {
    ElMessage.warning('暂无用例可执行')
    return
  }
  try {
    await ElMessageBox.confirm(`将全量执行 ${allIds.length} 条用例并生成执行记录，确认继续？`, '全量执行', {
      type: 'warning',
      confirmButtonText: '执行',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }

  runningAll.value = true
  try {
    const record = await executeBatch(projectId, {
      caseIds: allIds,
      source: 'all'
    })
    ElMessage.success(`全量执行完成：${record.passed}/${record.total} PASS（已生成执行记录）`)
    // 提示可查看执行报告
    await router.push({ name: 'ProjectExecRecords', params: { id: projectId }, query: { name: projectName } })
  } catch {
    ElMessage.error('全量执行失败（请确认项目已配置被测服务地址 baseUrl）')
  } finally {
    runningAll.value = false
  }
}

// ---------------- 数据加载 ----------------
async function loadGap(): Promise<void> {
  try {
    gap.value = await detectGap(projectId)
  } catch {
    // 忽略
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [apiList, grouped] = await Promise.all([getProjectApis(projectId), groupedCases(projectId)])
    apis.value = apiList
    caseMap.value = grouped
    openApis.value = apiList.map((a) => a.id)
  } catch {
    ElMessage.error('加载用例失败')
  } finally {
    loading.value = false
  }
}

async function handleGenerate(): Promise<void> {
  generating.value = true
  try {
    const count = await generateCases(projectId)
    ElMessage.success(`已为空洞接口生成 ${count} 条用例`)
    await Promise.all([loadGap(), loadAll()])
  } catch {
    ElMessage.error('用例生成失败')
  } finally {
    generating.value = false
  }
}

async function handleBuildMapping(): Promise<void> {
  mappingBuilding.value = true
  try {
    const res = await buildMapping(projectId)
    ElMessage.success(`代码单元 ${res.codeUnits} 个，关联 ${res.mappings} 条`)
  } catch {
    ElMessage.error('建立关联失败（请先扫描接口并生成用例）')
  } finally {
    mappingBuilding.value = false
  }
}

// ---------------- 工具 ----------------
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
    case 'exception': return 'info'
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

const detailVisible = ref(false)
const current = ref<TestCase | null>(null)

function viewDetail(row: TestCase): void {
  current.value = row
  detailVisible.value = true
}

// ---------------- 关联代码 ----------------
const mappingVisible = ref(false)
const mappingLoading = ref(false)
const mappingCase = ref<TestCase | null>(null)
const mappedUnits = ref<CodeUnit[]>([])

async function openMapping(row: TestCase): Promise<void> {
  mappingCase.value = row
  mappingVisible.value = true
  mappingLoading.value = true
  mappedUnits.value = []
  try {
    mappedUnits.value = await listCaseMapping(row.id)
  } catch {
    ElMessage.error('查询关联代码失败')
  } finally {
    mappingLoading.value = false
  }
}

function pretty(json: string | undefined): string {
  if (!json) return '（无）'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

onMounted(() => {
  loadGap()
  loadAll()
})
</script>

<style scoped>
.case-list {
  padding: 16px;
}

.gap-card {
  margin-top: 16px;
}

.gap-content {
  display: flex;
  align-items: center;
  gap: 40px;
}

.gap-item {
  text-align: center;
}

.gap-num {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.gap-num.warn {
  color: #e6a23c;
}

.gap-num.danger {
  color: #f56c6c;
}

.gap-num.ok {
  color: #67c23a;
}

.gap-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.table-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.mono :deep(.el-textarea__inner) {
  font-family: monospace;
}

.result-alert {
  margin-bottom: 12px;
}

.result-desc {
  margin-bottom: 12px;
}

.error-msg {
  color: #f56c6c;
  margin-bottom: 12px;
}

.result-section {
  margin-bottom: 12px;
}

.result-label {
  font-weight: 600;
  margin-bottom: 6px;
}

.assert-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
}

.detail-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
}
</style>
