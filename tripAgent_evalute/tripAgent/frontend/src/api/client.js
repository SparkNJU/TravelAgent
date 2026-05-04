import axios from 'axios';
export const apiClient = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 15000,
});
export function createChatEventSource(sessionId, question) {
    const q = encodeURIComponent(question);
    return new EventSource(`http://localhost:8080/api/chat/stream?sessionId=${sessionId}&question=${q}`);
}
async function unwrap(promise) {
    const { data } = await promise;
    if (data.code !== 200) {
        throw new Error(data.message || 'Request failed');
    }
    return data.data;
}
export function listTasks(params) {
    return unwrap(apiClient.get('/eval/tasks', { params }));
}
export function createTask(payload) {
    return unwrap(apiClient.post('/eval/tasks', payload));
}
export function updateTask(taskId, payload) {
    return unwrap(apiClient.put(`/eval/tasks/${taskId}`, payload));
}
export function startTask(taskId) {
    return unwrap(apiClient.post(`/eval/tasks/${taskId}/start`));
}
export function getRun(runId) {
    return unwrap(apiClient.get(`/eval/runs/${runId}`));
}
export function listTaskRuns(taskId, params) {
    return unwrap(apiClient.get(`/eval/tasks/${taskId}/runs`, { params }));
}
export function getRunRecords(runId) {
    return unwrap(apiClient.get(`/eval/runs/${runId}/records`));
}
export function getRunMetrics(runId) {
    return unwrap(apiClient.get(`/eval/runs/${runId}/metrics`));
}
export function compareTaskRuns(taskId, baselineRunId, targetRunId, changedOnly = true) {
    return unwrap(apiClient.get(`/eval/tasks/${taskId}/runs/compare`, {
        params: { baselineRunId, targetRunId, changedOnly },
    }));
}
export function createRunCompareExportTask(taskId, baselineRunId, targetRunId, changedOnly = true, format = 'json') {
    return unwrap(apiClient.post(`/eval/tasks/${taskId}/runs/compare/export`, null, {
        params: { baselineRunId, targetRunId, changedOnly, format },
    }));
}
export function getExportTask(exportId) {
    return unwrap(apiClient.get(`/eval/exports/${exportId}`));
}
export function listExportTasks(params) {
    return unwrap(apiClient.get('/eval/exports', { params }));
}
export function retryExportTask(exportId) {
    return unwrap(apiClient.post(`/eval/exports/${exportId}/retry`));
}
export function deleteExportTask(exportId) {
    return unwrap(apiClient.delete(`/eval/exports/${exportId}`));
}
export function batchDeleteExportTasks(exportIds) {
    return unwrap(apiClient.post('/eval/exports/batch-delete', { exportIds }));
}
export function createRunEventSource(runId) {
    return new EventSource(`http://localhost:8080/api/eval/runs/${runId}/stream`);
}
export function listStrategies() {
    return unwrap(apiClient.get('/eval/strategies'));
}
export function getStrategy(strategyId) {
    return unwrap(apiClient.get(`/eval/strategies/${strategyId}`));
}
export function createStrategy(payload) {
    return unwrap(apiClient.post('/eval/strategies', payload));
}
export function createStrategyVersion(strategyId, payload) {
    return unwrap(apiClient.post(`/eval/strategies/${strategyId}/versions`, payload));
}
export function listCustomMetrics(enabledOnly) {
    return unwrap(apiClient.get('/eval/metrics/custom', { params: { enabledOnly } }));
}
export function createCustomMetric(payload) {
    return unwrap(apiClient.post('/eval/metrics/custom', payload));
}
