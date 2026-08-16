<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus, Refresh, VideoPlay, CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import {
  importProject,
  analyzeProject,
  getImportStatus,
  generateProjectCases,
  deleteProject,
  pageProjects,
  type BuildType,
  type Project
} from '@/api/project'

const router = useRouter()

// ---------------- 列表 ----------------
const loading = ref(false)
const list = ref<Project[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageProjects({
      page: query.page,
      size: query.size,
      name: query.name || undefined
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.page = 1
  void loadData()
}

function handleReset(): void {
  query.name = ''
  query.page = 1
  void loadData()
}

function handlePageChange(page: number): void {
  query.page = page
  void loadData()
}

function handleSizeChange(size: number): void {
  query.size = size
  query.page = 1
  void loadData()
}

// ---------------- 新增项目 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  gitUrl: '',
  branch: 'master',
  buildType: 'maven' as BuildType,
  baseUrl: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  gitUrl: [{ required: true, message: '请输入 Git 仓库地址', trigger: 'blur' }]
}

function openDialog(): void {
  form.name = ''
  form.gitUrl = ''
  form.branch = 'master'
  form.buildType = 'maven'
  form.baseUrl = ''
  dialogVisible.value = true
}

async function handleCreate(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await importProject({ ...form })
    dialogVisible.value = false
    query.page = 1
    await loadData()
    ElMessage.success(`项目「${res.project.name}」导入成功，点击「分析接口」开始接口识别`)
  } catch (err) {
    ElMessage.error('项目导入失败')
  } finally {
    submitting.value = false
  }
}

// ---------------- 异步分析接口（项目列表按钮） ----------------
const analyzingId = ref<number | null>(null)
const analyzeProgress = ref(0)
const analyzeVisible = ref(false)
const analyzeStatus = ref('queued')
let analyzeTimer: ReturnType<typeof setInterval> | null = null

async function handleAnalyzeProject(row: Project): Promise<void> {
  try {
    const res = await analyzeProject(row.id)
    analyzingId.value = row.id
    analyzeVisible.value = true
    analyzeProgress.value = 0
    analyzeStatus.value = 'queued'
    pollAnalyzeStatus(res.taskId)
  } catch {
    ElMessage.error('接口分析触发失败')
  }
}

function pollAnalyzeStatus(taskId: number): void {
  if (analyzeTimer) clearInterval(analyzeTimer)
  analyzeTimer = setInterval(async () => {
    try {
      const s = await getImportStatus(taskId)
      analyzeProgress.value = s.progress
      analyzeStatus.value = s.status
      if (s.status === 'success' || s.status === 'failed') {
        if (analyzeTimer) clearInterval(analyzeTimer)
        if (s.status === 'success') {
          ElMessage.success('接口分析完成')
          analyzeVisible.value = false
          await loadData()
        } else {
          ElMessage.warning('接口分析失败')
          analyzeVisible.value = false
        }
      }
    } catch {
      if (analyzeTimer) clearInterval(analyzeTimer)
      analyzeVisible.value = false
    }
  }, 1000)
}

// ---------------- 生成用例（项目列表按钮） ----------------
const generatingId = ref<number | null>(null)

async function handleGenerateProject(row: Project): Promise<void> {
  generatingId.value = row.id
  try {
    const count = await generateProjectCases(row.id)
    ElMessage.success(`已生成 ${count} 条用例，可到用例管理查看`)
  } catch {
    ElMessage.error('用例生成失败（请先分析接口）')
  } finally {
    generatingId.value = null
  }
}

function statusText(status: string): string {
  switch (status) {
    case 'queued': return '排队中...'
    case 'running': return '分析执行中...'
    case 'success': return '分析完成'
    case 'failed': return '分析失败'
    default: return status
  }
}

// ---------------- 删除 ----------------
async function handleDelete(row: Project): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  try {
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // 错误提示已由响应拦截器处理
  }
}

onMounted(loadData)
</script>

<template>
  <div class="project-page">
    <!-- 工具栏 -->
    <el-card shadow="never" class="toolbar">
      <div class="toolbar-row">
        <div class="toolbar-left">
          <el-input
            v-model="query.name"
            placeholder="按项目名称搜索"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" :icon="Refresh" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button type="primary" :icon="Plus" @click="openDialog">导入项目</el-button>
      </div>
    </el-card>

    <!-- 项目表格 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="项目名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="gitUrl" label="Git 地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="branch" label="分支" width="110" />
        <el-table-column prop="buildType" label="构建类型" width="100" />
        <el-table-column prop="baseUrl" label="被测服务地址" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="640" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="VideoPlay"
              :loading="analyzingId === row.id"
              @click="handleAnalyzeProject(row)"
            >
              分析接口
            </el-button>
            <el-button
              type="success"
              link
              :loading="generatingId === row.id"
              @click="handleGenerateProject(row)"
            >
              生成用例
            </el-button>
            <el-button type="info" link @click="router.push({ name: 'ProjectApis', params: { id: row.id }, query: { name: row.name } })">
              查看接口
            </el-button>
            <el-button type="warning" link @click="router.push({ name: 'ProjectCases', params: { id: row.id }, query: { name: row.name } })">
              用例管理
            </el-button>
            <el-button type="danger" link @click="router.push({ name: 'ProjectChangeAnalysis', params: { id: row.id }, query: { name: row.name } })">
              变更分析
            </el-button>
            <el-button type="info" link @click="router.push({ name: 'ProjectExecRecords', params: { id: row.id }, query: { name: row.name } })">
              执行记录
            </el-button>
            <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 新增项目对话框 -->
    <el-dialog v-model="dialogVisible" title="导入项目" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="如：order-service" />
        </el-form-item>
        <el-form-item label="Git 地址" prop="gitUrl">
          <el-input v-model="form.gitUrl" placeholder="如：https://github.com/xxx/order-service.git" />
        </el-form-item>
        <el-form-item label="默认分支">
          <el-input v-model="form.branch" placeholder="master" />
        </el-form-item>
        <el-form-item label="构建类型">
          <el-select v-model="form.buildType" style="width: 100%">
            <el-option label="Maven" value="maven" />
            <el-option label="Gradle" value="gradle" />
          </el-select>
        </el-form-item>
        <el-form-item label="被测服务地址">
          <el-input v-model="form.baseUrl" placeholder="如：http://localhost:8899（执行用例用，可后补）" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="导入仅校验 Git 仓库可达；接口分析与用例生成请在项目列表手动触发"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 接口分析进度弹窗 -->
    <el-dialog v-model="analyzeVisible" title="接口分析中" width="420px" :close-on-click-modal="false" :close-on-press-escape="false" :show-close="false">
      <div class="import-progress">
        <div class="import-status">
          <el-icon v-if="analyzeStatus === 'success'" class="is-success"><CircleCheck /></el-icon>
          <el-icon v-else-if="analyzeStatus === 'failed'" class="is-error"><CircleClose /></el-icon>
          <el-icon v-else class="is-loading"><Loading /></el-icon>
          <span>{{ statusText(analyzeStatus) }}</span>
        </div>
        <el-progress :percentage="analyzeProgress" :status="analyzeStatus === 'success' ? 'success' : analyzeStatus === 'failed' ? 'exception' : undefined" />
        <div class="import-tip">正在执行：拉取代码 → 扫描分析接口（大项目可能需要数分钟）</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.import-progress {
  padding: 8px 4px;
}

.import-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  color: #606266;
}

.import-status .is-success {
  color: #67c23a;
  font-size: 20px;
}

.import-status .is-error {
  color: #f56c6c;
  font-size: 20px;
}

.import-status .is-loading {
  color: #409eff;
  font-size: 20px;
}

.import-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
.toolbar {
  margin-bottom: 16px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
