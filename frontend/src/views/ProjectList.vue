<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'
import {
  createProject,
  deleteProject,
  pageProjects,
  triggerPipeline,
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
    await createProject({ ...form })
    ElMessage.success('项目创建成功')
    dialogVisible.value = false
    query.page = 1
    await loadData()
  } finally {
    submitting.value = false
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

// ---------------- 触发流水线（M2：真实扫描） ----------------
async function handlePipeline(row: Project): Promise<void> {
  try {
    const res = await triggerPipeline(row.id)
    ElMessage.success(`接口识别完成：新增 ${res.importedCount} 个，共 ${res.totalCount} 个`)
    router.push({ name: 'ProjectApis', params: { id: row.id }, query: { name: row.name } })
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
        <el-button type="primary" :icon="Plus" @click="openDialog">新增项目</el-button>
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
        <el-table-column label="操作" width="500" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="VideoPlay" @click="handlePipeline(row)">
              触发流水线
            </el-button>
            <el-button type="success" link @click="router.push({ name: 'ProjectApis', params: { id: row.id }, query: { name: row.name } })">
              查看接口
            </el-button>
            <el-button type="warning" link @click="router.push({ name: 'ProjectCases', params: { id: row.id }, query: { name: row.name } })">
              用例管理
            </el-button>
            <el-button type="danger" link @click="router.push({ name: 'ProjectChangeAnalysis', params: { id: row.id }, query: { name: row.name } })">
              变更分析
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
    <el-dialog v-model="dialogVisible" title="新增项目" width="520px" destroy-on-close>
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
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
