<template>
  <div class="exec-records">
    <el-page-header :content="`执行记录 - ${projectName}`" @back="router.push('/projects')" />

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>用例执行记录（共 {{ total }} 条）</span>
          <el-button :loading="loading" @click="loadData">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="来源" width="130">
          <template #default="{ row }">
            <el-tag :type="row.source === 'change_analysis' ? 'danger' : 'primary'" size="small">
              {{ sourceText(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" min-width="200">
          <template #default="{ row }">
            <template v-if="row.baseVersion">
              <el-tag type="info" size="small">{{ row.baseVersion }}</el-tag>
              <span class="arrow">→</span>
              <el-tag type="warning" size="small">{{ row.nowVersion }}</el-tag>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="结果统计" min-width="220">
          <template #default="{ row }">
            <span class="stat total">{{ row.total }}</span> 总 /
            <span class="stat pass">{{ row.passed }}</span> 通过 /
            <span class="stat fail">{{ row.failed }}</span> 失败 /
            <span class="stat err">{{ row.errorCount }}</span> 错误
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="130">
          <template #default="{ row }">
            <el-progress
              :percentage="row.total ? Math.round((row.passed / row.total) * 100) : 0"
              :status="row.total && row.passed === row.total ? 'success' : 'warning'"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时" width="100">
          <template #default="{ row }">{{ row.costMs }}ms</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="执行时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情报告</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && records.length === 0" description="暂无执行记录，可在变更分析页批量执行命中用例" />

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情报告弹窗 -->
    <el-dialog v-model="detailVisible" :title="`执行详情报告 #${detailRecord?.id ?? ''}`" width="75%">
      <template v-if="detailRecord">
        <el-descriptions :column="4" border class="detail-desc">
          <el-descriptions-item label="来源">{{ sourceText(detailRecord.source) }}</el-descriptions-item>
          <el-descriptions-item label="用例数">{{ detailRecord.total }}</el-descriptions-item>
          <el-descriptions-item label="通过率">
            {{ detailRecord.total ? Math.round((detailRecord.passed / detailRecord.total) * 100) : 0 }}%
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detailRecord.costMs }}ms</el-descriptions-item>
        </el-descriptions>
        <template v-if="detailRecord.baseVersion">
          <div class="version-line">
            版本：<el-tag type="info" size="small">{{ detailRecord.baseVersion }}</el-tag>
            <span class="arrow">→</span>
            <el-tag type="warning" size="small">{{ detailRecord.nowVersion }}</el-tag>
          </div>
        </template>

        <el-table :data="details" stripe border size="small" class="detail-table">
          <el-table-column prop="testCaseId" label="用例ID" width="70" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PASS' ? 'success' : row.status === 'FAIL' ? 'warning' : 'danger'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="apiPath" label="接口" min-width="180" show-overflow-tooltip />
          <el-table-column prop="caseTitle" label="用例标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="httpStatus" label="HTTP" width="70" />
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">{{ row.costMs }}ms</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="viewRowDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 单条用例详情 -->
    <el-dialog v-model="rowDetailVisible" :title="`用例 #${currentDetail?.testCaseId ?? ''} 执行详情`" width="60%">
      <template v-if="currentDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用例">{{ currentDetail.caseTitle }}</el-descriptions-item>
          <el-descriptions-item label="接口">{{ currentDetail.apiPath }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentDetail.status === 'PASS' ? 'success' : currentDetail.status === 'FAIL' ? 'warning' : 'danger'" size="small">
              {{ currentDetail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="HTTP">{{ currentDetail.httpStatus ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="currentDetail.errorMsg" class="error-msg">{{ currentDetail.errorMsg }}</div>
        <div class="section">
          <div class="section-label">请求入参</div>
          <pre class="pre">{{ pretty(currentDetail.requestJson) }}</pre>
        </div>
        <div class="section">
          <div class="section-label">断言明细</div>
          <ul class="assert-list">
            <li v-for="(a, i) in assertList" :key="i">{{ a }}</li>
            <li v-if="assertList.length === 0">（无断言）</li>
          </ul>
        </div>
        <div class="section">
          <div class="section-label">响应体</div>
          <pre class="pre">{{ pretty(currentDetail.responseBody) }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getExecRecordDetail,
  pageExecRecords,
  type ExecRecord,
  type ExecRecordDetail
} from '@/api/case'

const route = useRoute()
const router = useRouter()

const projectId = Number(route.params.id)
const projectName = String(route.query.name ?? '项目')

const records = ref<ExecRecord[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref({ page: 1, size: 20 })

async function loadData(): Promise<void> {
  loading.value = true
  try {
    const res = await pageExecRecords(projectId, query.value.page, query.value.size)
    records.value = res.records
    total.value = res.total
  } catch {
    ElMessage.error('加载执行记录失败')
  } finally {
    loading.value = false
  }
}

// ---------------- 详情报告 ----------------
const detailVisible = ref(false)
const detailRecord = ref<ExecRecord | null>(null)
const details = ref<ExecRecordDetail[]>([])

async function openDetail(row: ExecRecord): Promise<void> {
  detailVisible.value = true
  detailRecord.value = row
  details.value = []
  try {
    const res = await getExecRecordDetail(row.id)
    detailRecord.value = res.record
    details.value = res.details
  } catch {
    ElMessage.error('加载详情失败')
  }
}

// ---------------- 单条详情 ----------------
const rowDetailVisible = ref(false)
const currentDetail = ref<ExecRecordDetail | null>(null)

const assertList = computed(() => {
  if (!currentDetail.value?.assertDetails) return []
  try {
    return JSON.parse(currentDetail.value.assertDetails) as string[]
  } catch {
    return []
  }
})

function viewRowDetail(row: ExecRecordDetail): void {
  currentDetail.value = row
  rowDetailVisible.value = true
}

function sourceText(source: string): string {
  switch (source) {
    case 'change_analysis': return '变更分析'
    case 'all': return '全部用例'
    default: return '手动执行'
  }
}

function pretty(json: string | undefined): string {
  if (!json) return '（无响应）'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

onMounted(loadData)
</script>

<style scoped>
.exec-records {
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

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.stat {
  font-weight: 600;
}

.stat.total { color: #606266; }
.stat.pass { color: #67c23a; }
.stat.fail { color: #e6a23c; }
.stat.err { color: #f56c6c; }

.muted {
  color: #c0c4cc;
}

.arrow {
  margin: 0 6px;
  color: #909399;
}

.detail-desc {
  margin-bottom: 12px;
}

.version-line {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}

.detail-table {
  margin-top: 12px;
}

.error-msg {
  color: #f56c6c;
  margin: 12px 0;
}

.section {
  margin-top: 12px;
}

.section-label {
  font-weight: 600;
  margin-bottom: 6px;
}

.assert-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
}

.pre {
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
