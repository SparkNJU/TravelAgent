import axios from 'axios';

export const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 15000,
});

export function createChatEventSource(sessionId: string, question: string): EventSource {
  const q = encodeURIComponent(question);
  return new EventSource(`http://localhost:8080/api/chat/stream?sessionId=${sessionId}&question=${q}`);
}

export type TaskStatus = 'READY' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type EvaluationMode = 'RESULT' | 'PROCESS';
export type EvaluationMethod = 'DETERMINISTIC' | 'JUDGE' | 'HYBRID';
export type CustomMetricType = 'DETERMINISTIC' | 'JUDGE';

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface EvalTask {
  taskId: number;
  taskName: string;
  agentVersion: string;
  datasetId: string;
  metricSet: string | null;
  status: TaskStatus;
  createdAt: string;
  evaluationMode: EvaluationMode;
  evaluationMethod: EvaluationMethod;
  evaluationDimensions: string;
  strategyConfig: string | null;
  strategyVersion: number | null;
}

export interface EvalRun {
  runId: number;
  taskId: number;
  status: TaskStatus;
  startTime: string | null;
  endTime: string | null;
  totalCount: number | null;
  successCount: number | null;
  failCount: number | null;
}

export interface TaskRunsPage {
  items: EvalRun[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
}

export interface QaRecord {
  qaId: number;
  runId: number;
  input: string;
  expectedOutput: string;
  actualOutput: string;
  toolTrace: string | null;
  firstTokenLatencyMs: number | null;
  endToEndLatencyMs: number | null;
  tokenUsage: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

export interface RunMetrics {
  runId: number;
  taskCompletionRate: number;
  toolCorrectnessScore: number;
  toolEfficiencyScore: number;
  firstTokenP95: number;
  endToEndP95: number;
  totalTokens: number;
  effectivenessScore: number;
  safetyScore: number;
  performanceScore: number;
  judgeReason: string;
}

export interface RunMetricDiff {
  metric: string;
  baseline: number | null;
  target: number | null;
  delta: number | null;
}

export interface RunSampleDiff {
  index: number;
  input: string;
  baselineOutput: string | null;
  targetOutput: string | null;
  baselineError: string | null;
  targetError: string | null;
  changed: boolean;
}

export interface RunCompareResult {
  taskId: number;
  baselineRunId: number;
  targetRunId: number;
  totalSamples: number;
  changedSamples: number;
  metricDiffs: RunMetricDiff[];
  sampleDiffs: RunSampleDiff[];
}

export interface ExportTaskResult {
  exportId: number;
  taskId: number;
  baselineRunId: number;
  targetRunId: number;
  format: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  fileName: string | null;
  downloadUrl: string | null;
  message: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface ExportTaskPageResult {
  items: ExportTaskResult[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ExportTaskBatchDeleteResult {
  requested: number;
  deleted: number;
  failed: number;
  deletedIds: number[];
  errors: string[];
}

export interface EvalStrategy {
  strategyId: number;
  strategyName: string;
  metricDefinitions: string | null;
  weightConfig: string | null;
  thresholdConfig: string | null;
  createdAt: string;
  latestVersion: number | null;
}

export interface EvalStrategyVersion {
  strategyVersionId: number;
  strategyId: number;
  version: number;
  metricDefinitions: string | null;
  weightConfig: string | null;
  thresholdConfig: string | null;
  createdAt: string;
}

export interface CustomMetric {
  customMetricId: number;
  metricName: string;
  metricType: CustomMetricType;
  inputFields: string | null;
  scoringLogic: string | null;
  thresholdValue: number | null;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskPayload {
  taskName: string;
  agentVersion: string;
  datasetId: string;
  metricSet?: string;
  evaluationMode: EvaluationMode;
  evaluationMethod: EvaluationMethod;
  evaluationDimensions: string;
  strategyConfig?: string;
  strategyVersion?: number;
}

export interface CreateStrategyPayload {
  strategyName: string;
  metricDefinitions?: string;
  weightConfig?: string;
  thresholdConfig?: string;
}

export interface CreateStrategyVersionPayload {
  version?: number;
  metricDefinitions?: string;
  weightConfig?: string;
  thresholdConfig?: string;
}

export interface CreateCustomMetricPayload {
  metricName: string;
  metricType: CustomMetricType;
  inputFields?: string;
  scoringLogic?: string;
  thresholdValue?: number;
  enabled?: boolean;
}

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await promise;
  if (data.code !== 200) {
    throw new Error(data.message || 'Request failed');
  }
  return data.data;
}

export function listTasks(params?: { status?: string; agentVersion?: string }): Promise<EvalTask[]> {
  return unwrap(apiClient.get<ApiResponse<EvalTask[]>>('/eval/tasks', { params }));
}

export function createTask(payload: CreateTaskPayload): Promise<EvalTask> {
  return unwrap(apiClient.post<ApiResponse<EvalTask>>('/eval/tasks', payload));
}

export function updateTask(taskId: number, payload: Partial<CreateTaskPayload>): Promise<EvalTask> {
  return unwrap(apiClient.put<ApiResponse<EvalTask>>(`/eval/tasks/${taskId}`, payload));
}

export function startTask(taskId: number): Promise<EvalRun> {
  return unwrap(apiClient.post<ApiResponse<EvalRun>>(`/eval/tasks/${taskId}/start`));
}

export function getRun(runId: number): Promise<EvalRun> {
  return unwrap(apiClient.get<ApiResponse<EvalRun>>(`/eval/runs/${runId}`));
}

export function listTaskRuns(
  taskId: number,
  params?: { status?: string; page?: number; size?: number },
): Promise<TaskRunsPage> {
  return unwrap(apiClient.get<ApiResponse<TaskRunsPage>>(`/eval/tasks/${taskId}/runs`, { params }));
}

export function getRunRecords(runId: number): Promise<QaRecord[]> {
  return unwrap(apiClient.get<ApiResponse<QaRecord[]>>(`/eval/runs/${runId}/records`));
}

export function getRunMetrics(runId: number): Promise<RunMetrics> {
  return unwrap(apiClient.get<ApiResponse<RunMetrics>>(`/eval/runs/${runId}/metrics`));
}

export function compareTaskRuns(
  taskId: number,
  baselineRunId: number,
  targetRunId: number,
  changedOnly = true,
): Promise<RunCompareResult> {
  return unwrap(apiClient.get<ApiResponse<RunCompareResult>>(`/eval/tasks/${taskId}/runs/compare`, {
    params: { baselineRunId, targetRunId, changedOnly },
  }));
}

export function createRunCompareExportTask(
  taskId: number,
  baselineRunId: number,
  targetRunId: number,
  changedOnly = true,
  format: 'json' | 'csv' = 'json',
): Promise<ExportTaskResult> {
  return unwrap(apiClient.post<ApiResponse<ExportTaskResult>>(`/eval/tasks/${taskId}/runs/compare/export`, null, {
    params: { baselineRunId, targetRunId, changedOnly, format },
  }));
}

export function getExportTask(exportId: number): Promise<ExportTaskResult> {
  return unwrap(apiClient.get<ApiResponse<ExportTaskResult>>(`/eval/exports/${exportId}`));
}

export function listExportTasks(params?: {
  taskId?: number;
  status?: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  page?: number;
  size?: number;
}): Promise<ExportTaskPageResult> {
  return unwrap(apiClient.get<ApiResponse<ExportTaskPageResult>>('/eval/exports', { params }));
}

export function retryExportTask(exportId: number): Promise<ExportTaskResult> {
  return unwrap(apiClient.post<ApiResponse<ExportTaskResult>>(`/eval/exports/${exportId}/retry`));
}

export function deleteExportTask(exportId: number): Promise<boolean> {
  return unwrap(apiClient.delete<ApiResponse<boolean>>(`/eval/exports/${exportId}`));
}

export function batchDeleteExportTasks(exportIds: number[]): Promise<ExportTaskBatchDeleteResult> {
  return unwrap(apiClient.post<ApiResponse<ExportTaskBatchDeleteResult>>('/eval/exports/batch-delete', { exportIds }));
}

export function createRunEventSource(runId: number): EventSource {
  return new EventSource(`http://localhost:8080/api/eval/runs/${runId}/stream`);
}

export function listStrategies(): Promise<EvalStrategy[]> {
  return unwrap(apiClient.get<ApiResponse<EvalStrategy[]>>('/eval/strategies'));
}

export function getStrategy(strategyId: number): Promise<EvalStrategy> {
  return unwrap(apiClient.get<ApiResponse<EvalStrategy>>(`/eval/strategies/${strategyId}`));
}

export function createStrategy(payload: CreateStrategyPayload): Promise<EvalStrategy> {
  return unwrap(apiClient.post<ApiResponse<EvalStrategy>>('/eval/strategies', payload));
}

export function createStrategyVersion(
  strategyId: number,
  payload: CreateStrategyVersionPayload,
): Promise<EvalStrategyVersion> {
  return unwrap(apiClient.post<ApiResponse<EvalStrategyVersion>>(`/eval/strategies/${strategyId}/versions`, payload));
}

export function listCustomMetrics(enabledOnly?: boolean): Promise<CustomMetric[]> {
  return unwrap(apiClient.get<ApiResponse<CustomMetric[]>>('/eval/metrics/custom', { params: { enabledOnly } }));
}

export function createCustomMetric(payload: CreateCustomMetricPayload): Promise<CustomMetric> {
  return unwrap(apiClient.post<ApiResponse<CustomMetric>>('/eval/metrics/custom', payload));
}
