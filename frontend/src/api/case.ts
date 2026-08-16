import { http } from './request'

/** 测试用例（与后端 TestCase 实体字段一致） */
export interface TestCase {
  id: number
  projectId: number
  apiDefinitionId: number
  title: string
  requestJson: string
  assertsJson: string
  headersJson?: string
  scenarioType: string
  source: string
  confidence: string
  status: string
  createdAt: string
  updatedAt: string
}

/** 空洞检测报告 */
export interface GapReport {
  totalApis: number
  gapApis: number
  gapRate: number
}

/** 测试空洞检测：GET /api/projects/{id}/gap */
export function detectGap(projectId: number): Promise<GapReport> {
  return http.get<GapReport>(`/projects/${projectId}/gap`)
}

/** 为空洞接口生成用例：POST /api/projects/{id}/generate-cases */
export function generateCases(projectId: number): Promise<number> {
  return http.post<number>(`/projects/${projectId}/generate-cases`)
}

/** 分页查询用例：GET /api/projects/{id}/cases */
export function pageCases(projectId: number, page: number, size: number): Promise<PageResult<TestCase>> {
  return http.get<PageResult<TestCase>>(`/projects/${projectId}/cases`, { page, size })
}

/** 按接口分组查询用例：GET /api/projects/{id}/cases/grouped */
export function groupedCases(projectId: number): Promise<Record<number, TestCase[]>> {
  return http.get<Record<number, TestCase[]>>(`/projects/${projectId}/cases/grouped`)
}

/** 编辑用例：PUT /api/projects/cases/{id} */
export function updateCase(
  id: number,
  data: { title?: string; requestJson?: string; assertsJson?: string; headersJson?: string }
): Promise<TestCase> {
  return http.put<TestCase>(`/projects/cases/${id}`, data)
}

/** 手动新增用例（source=manual）：POST /api/projects/{id}/cases */
export function createCase(
  projectId: number,
  data: { apiDefinitionId: number; title: string; requestJson: string; assertsJson: string; headersJson?: string; scenarioType?: string }
): Promise<TestCase> {
  return http.post<TestCase>(`/projects/${projectId}/cases`, data)
}

/** 手动执行用例：POST /api/projects/cases/{id}/execute */
export function executeCase(id: number): Promise<ExecuteResult> {
  return http.post<ExecuteResult>(`/projects/cases/${id}/execute`)
}

/** 触发用例-代码关联建立：POST /api/projects/{id}/build-mapping */
export function buildMapping(projectId: number): Promise<{ codeUnits: number; mappings: number }> {
  return http.post<{ codeUnits: number; mappings: number }>(`/projects/${projectId}/build-mapping`)
}

/** 查询用例关联的代码单元：GET /api/projects/cases/{id}/mapping */
export function listCaseMapping(id: number): Promise<CodeUnit[]> {
  return http.get<CodeUnit[]>(`/projects/cases/${id}/mapping`)
}

/** 查询项目代码单元清单：GET /api/projects/{id}/code-units */
export function listCodeUnits(projectId: number): Promise<CodeUnit[]> {
  return http.get<CodeUnit[]>(`/projects/${projectId}/code-units`)
}

/** 代码单元（与后端 CodeUnit 实体一致） */
export interface CodeUnit {
  id: number
  projectId: number
  className: string
  methodName: string
  signature: string
  filePath: string
  lineNo: number
  codeHash: string
}

/** 变更分析请求 */
export interface ChangeAnalysisRequest {
  baseVersion: string
  nowVersion: string
}

/** 变更分析结果 */
export interface ChangeAnalysisResult {
  baseVersion: string
  nowVersion: string
  changedFileCount: number
  changedClasses: string[]
  affectedControllerMethods: string[]
  affectedApis: Record<string, ApiDefinition[]>
  matchedUnits: CodeUnit[]
  matchedCases: TestCase[]
}

/** 为单接口生成用例：POST /api/projects/{id}/apis/{apiId}/generate-cases */
export function generateCasesForApi(projectId: number, apiId: number): Promise<number> {
  return http.post<number>(`/projects/${projectId}/apis/${apiId}/generate-cases`)
}

/** 版本变更分析：POST /api/projects/{id}/analyze-change */
export function analyzeChange(projectId: number, data: ChangeAnalysisRequest): Promise<ChangeAnalysisResult> {
  return http.post<ChangeAnalysisResult>(`/projects/${projectId}/analyze-change`, data)
}

/** 用例执行结果 */
export interface ExecuteResult {
  caseId: number
  title: string
  url: string
  httpStatus?: number
  responseBody?: string
  assertDetails: string[]
  status: 'PASS' | 'FAIL' | 'ERROR'
  errorMsg?: string
  costMs: number
}

/** 执行记录（批次） */
export interface ExecRecord {
  id: number
  projectId: number
  source: string
  baseVersion?: string
  nowVersion?: string
  total: number
  passed: number
  failed: number
  errorCount: number
  costMs: number
  createdAt: string
}

/** 执行记录明细 */
export interface ExecRecordDetail {
  id: number
  execRecordId: number
  testCaseId: number
  caseTitle: string
  apiPath: string
  requestJson?: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  httpStatus?: number
  responseBody?: string
  assertDetails?: string
  errorMsg?: string
  costMs: number
}

/** 批量执行用例：POST /api/projects/{id}/execute-batch */
export function executeBatch(
  projectId: number,
  data: { caseIds: number[]; source: string; baseVersion?: string; nowVersion?: string }
): Promise<ExecRecord> {
  return http.post<ExecRecord>(`/projects/${projectId}/execute-batch`, data)
}

/** 分页查询执行记录：GET /api/projects/{id}/exec-records */
export function pageExecRecords(projectId: number, page: number, size: number): Promise<PageResult<ExecRecord>> {
  return http.get<PageResult<ExecRecord>>(`/projects/${projectId}/exec-records`, { page, size })
}

/** 查询执行记录详情：GET /api/projects/exec-records/{recordId}/detail */
export function getExecRecordDetail(recordId: number): Promise<{ record: ExecRecord; details: ExecRecordDetail[] }> {
  return http.get<{ record: ExecRecord; details: ExecRecordDetail[] }>(`/projects/exec-records/${recordId}/detail`)
}

/** 分页结果（与后端 Page 结构一致） */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
