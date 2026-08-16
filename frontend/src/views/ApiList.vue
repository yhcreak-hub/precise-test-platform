<template>
  <div class="api-list">
    <el-page-header :content="`接口管理 - ${projectName}`" @back="router.push('/projects')" />

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>自动识别出的接口清单（共 {{ apis.length }} 个）</span>
          <el-button type="primary" :loading="loading" @click="loadApis">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="apis" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <el-tag :type="methodType(row.httpMethod)" effect="dark" size="small">
              {{ row.httpMethod }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiPath" label="接口路径" min-width="220" show-overflow-tooltip />
        <el-table-column prop="controllerClass" label="Controller" min-width="220" show-overflow-tooltip />
        <el-table-column prop="controllerMethod" label="方法" min-width="140" show-overflow-tooltip />
        <el-table-column prop="filePath" label="源码位置" min-width="200" show-overflow-tooltip />
        <el-table-column prop="lineNo" label="行号" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'confirmed' ? 'success' : 'warning'" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">参数结构</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && apis.length === 0" description="暂无接口，请先在项目管理页触发流水线扫描" />
    </el-card>

    <!-- 参数结构详情 -->
    <el-dialog v-model="detailVisible" :title="`参数结构 - ${current?.httpMethod} ${current?.apiPath}`" width="60%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="请求参数">
          <pre class="schema-pre">{{ pretty(current?.paramSchemaJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="返回结构">
          <pre class="schema-pre">{{ pretty(current?.responseSchemaJson) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getProjectApis, type ApiDefinition } from '@/api/definition'

const route = useRoute()
const router = useRouter()

const projectId = Number(route.params.id)
const projectName = String(route.query.name ?? '项目')

const apis = ref<ApiDefinition[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const current = ref<ApiDefinition | null>(null)

async function loadApis(): Promise<void> {
  loading.value = true
  try {
    apis.value = await getProjectApis(projectId)
  } catch {
    ElMessage.error('加载接口清单失败')
  } finally {
    loading.value = false
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

function statusText(status: string): string {
  switch (status) {
    case 'confirmed': return '已确认'
    case 'ignored': return '已忽略'
    default: return '待确认'
  }
}

function viewDetail(row: ApiDefinition): void {
  current.value = row
  detailVisible.value = true
}

function pretty(json: string | undefined): string {
  if (!json) return '（无）'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

onMounted(loadApis)
</script>

<style scoped>
.api-list {
  padding: 16px;
}

.table-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.schema-pre {
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
