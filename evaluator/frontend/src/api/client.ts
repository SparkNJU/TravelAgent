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

// 扩展：EvalTask 字段同步
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
  selectedModelIds: number[] | null;
  judgeModelId: number | null;
  comparisonSamplingStrategy: 'ALL_PAIRS' | null;
  positionSwapEnabled: boolean | null;
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
  modelProfileId: number | null;
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
  selectedModelIds?: number[];
  judgeModelId?: number;
  comparisonSamplingStrategy?: 'ALL_PAIRS';
  positionSwapEnabled?: boolean;
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
  try {
    const { data } = await promise;
    if (data.code !== 200) {
      throw new Error(data.message || 'Request failed');
    }
    return data.data;
  } catch (err: any) {
    const message =
      err.response?.data?.message ||
      err.response?.data?.error ||
      err.message ||
      'Request failed';
    throw new Error(message);
  }
}

export function listTasks(params?: { status?: string; agentVersion?: string }): Promise<EvalTask[]> {
  return unwrap(apiClient.get<ApiResponse<EvalTask[]>>('/eval/tasks', { params }));
}

export function getTask(taskId: number): Promise<EvalTask> {
  return unwrap(apiClient.get<ApiResponse<EvalTask>>(`/eval/tasks/${taskId}`));
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

export function deleteTask(taskId: number): Promise<boolean> {
  return unwrap(apiClient.delete<ApiResponse<boolean>>(`/eval/tasks/${taskId}`));
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

// =====================================================================
// Phase 2 扩展：ModelProfile / Dataset / BT Ratings
// =====================================================================

export type ModelRole = 'PLAYER' | 'JUDGE' | 'BOTH';

export interface ModelProfile {
  modelProfileId: number;
  modelId: string;
  displayName: string;
  provider: string;
  apiBaseUrl: string | null;
  apiKeyRef: string | null;
  role: ModelRole;
  defaultParams: string | null;
  enabled: boolean;
  createdAt: string;
}

export interface CreateModelProfilePayload {
  modelId: string;
  displayName: string;
  provider?: string;
  apiBaseUrl?: string;
  apiKeyRef?: string;
  role: ModelRole;
  defaultParams?: string;
  enabled?: boolean;
}

export interface UpdateModelProfilePayload {
  displayName?: string;
  provider?: string;
  apiBaseUrl?: string;
  apiKeyRef?: string;
  role?: ModelRole;
  defaultParams?: string;
  enabled?: boolean;
}

export interface PingResult {
  ok: boolean;
  modelProfileId: number;
  modelId: string;
  text: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  latencyMs: number;
  finishReason: string;
}

export function listModels(params?: { role?: ModelRole; enabledOnly?: boolean }): Promise<ModelProfile[]> {
  return unwrap(apiClient.get<ApiResponse<ModelProfile[]>>('/eval/models', { params }));
}

export function getModel(id: number): Promise<ModelProfile> {
  return unwrap(apiClient.get<ApiResponse<ModelProfile>>(`/eval/models/${id}`));
}

export function createModel(payload: CreateModelProfilePayload): Promise<ModelProfile> {
  return unwrap(apiClient.post<ApiResponse<ModelProfile>>('/eval/models', payload));
}

export function updateModel(id: number, payload: UpdateModelProfilePayload): Promise<ModelProfile> {
  return unwrap(apiClient.put<ApiResponse<ModelProfile>>(`/eval/models/${id}`, payload));
}

export function deleteModel(id: number): Promise<boolean> {
  return unwrap(apiClient.delete<ApiResponse<boolean>>(`/eval/models/${id}`));
}

export function hardDeleteModel(id: number): Promise<boolean> {
  return unwrap(apiClient.delete<ApiResponse<boolean>>(`/eval/models/${id}/hard`));
}

export function pingModel(modelProfileId: number, prompt?: string): Promise<PingResult> {
  return unwrap(apiClient.get<ApiResponse<PingResult>>('/eval/llm/ping', {
    params: { modelProfileId, prompt },
  }));
}

// ----- Dataset -----

export type DatasetSource = 'BUILTIN' | 'USER';

export interface Dataset {
  datasetId: number;
  name: string;
  displayName: string | null;
  source: DatasetSource;
  owner: string | null;
  sampleCount: number | null;
  description: string | null;
  enabled: boolean;
  createdAt: string;
}

export interface DatasetSamplePreview {
  sampleId: number;
  datasetId: number;
  sampleKey: string | null;
  input: string;
  expectedOutput: string | null;
  sortOrder: number | null;
}

export function listDatasets(params?: {
  source?: DatasetSource;
  enabledOnly?: boolean;
}): Promise<Dataset[]> {
  return unwrap(apiClient.get<ApiResponse<Dataset[]>>('/eval/datasets', { params }));
}

export function getDataset(id: number): Promise<Dataset> {
  return unwrap(apiClient.get<ApiResponse<Dataset>>(`/eval/datasets/${id}`));
}

export function getDatasetSamples(id: number, limit = 10): Promise<DatasetSamplePreview[]> {
  return unwrap(apiClient.get<ApiResponse<DatasetSamplePreview[]>>(
    `/eval/datasets/${id}/samples`,
    { params: { limit } },
  ));
}

export function uploadDataset(payload: {
  file: File;
  name: string;
  displayName?: string;
  description?: string;
  owner?: string;
}): Promise<Dataset> {
  const form = new FormData();
  form.append('file', payload.file);
  form.append('name', payload.name);
  if (payload.displayName) form.append('displayName', payload.displayName);
  if (payload.description) form.append('description', payload.description);
  if (payload.owner) form.append('owner', payload.owner);
  return unwrap(apiClient.post<ApiResponse<Dataset>>('/eval/datasets', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }));
}

export function deleteDataset(id: number): Promise<boolean> {
  return unwrap(apiClient.delete<ApiResponse<boolean>>(`/eval/datasets/${id}`));
}

// ----- BT Ratings -----

export type EvaluationDimension = 'EFFECTIVENESS' | 'SAFETY' | 'PERFORMANCE' | 'OVERALL';
export type SortBy =
  | 'elo'
  | 'winRate'
  | 'latency'
  | 'tokens'
  | 'completionRate'
  | 'safetyElo';

export interface ModelRating {
  ratingId: number;
  runId: number;
  modelProfileId: number;
  modelId: string | null;
  displayName: string | null;
  dimension: EvaluationDimension;
  theta: number | null;
  elo: number | null;
  lowerCi95: number | null;
  upperCi95: number | null;
  nComparisons: number | null;
  nWins: number | null;
  winRate: number | null;
  avgLatencyMs: number | null;
  avgTokens: number | null;
  completionRate: number | null;
}

export interface RankedModelsResult {
  runId: number;
  sortBy: string;
  dimension: EvaluationDimension | null;
  order: 'asc' | 'desc';
  total: number;
  ranked: ModelRating[];
}

export function getRunRatings(runId: number): Promise<ModelRating[]> {
  return unwrap(apiClient.get<ApiResponse<ModelRating[]>>(`/eval/runs/${runId}/ratings`));
}

export function getRunRanked(
  runId: number,
  sortBy: SortBy,
  opts?: { dimension?: EvaluationDimension; order?: 'asc' | 'desc'; limit?: number },
): Promise<RankedModelsResult> {
  return unwrap(apiClient.get<ApiResponse<RankedModelsResult>>(`/eval/runs/${runId}/ranked`, {
    params: { sortBy, ...opts },
  }));
}

// ----- Model Catalog (推荐 modelId 清单) -----

export interface ModelCatalogItem {
  modelId: string;
  displayName: string;
  tags: string[];
}

export interface ModelCatalog {
  players: ModelCatalogItem[];
  judges: ModelCatalogItem[];
  providersNote: string;
}

export function getModelCatalog(): Promise<ModelCatalog> {
  return unwrap(apiClient.get<ApiResponse<ModelCatalog>>('/eval/models/catalog'));
}
