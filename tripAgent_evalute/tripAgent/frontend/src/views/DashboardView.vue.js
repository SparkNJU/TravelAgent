import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import ChatPanel from '../components/ChatPanel.vue';
import { batchDeleteExportTasks, compareTaskRuns, createRunCompareExportTask, createCustomMetric, createRunEventSource, createStrategy, createStrategyVersion, createTask, deleteExportTask, getExportTask, getRun, getRunMetrics, getRunRecords, listCustomMetrics, listExportTasks, listStrategies, listTaskRuns, listTasks, retryExportTask, startTask, } from '../api/client';
const showCreate = ref(false);
const noticeText = ref('');
const tasks = ref([]);
const runRecords = ref([]);
const runMetrics = ref(null);
const currentRun = ref(null);
const strategies = ref([]);
const customMetrics = ref([]);
const runByTask = reactive({});
const metricsByTask = reactive({});
const runIdByTask = reactive({});
const taskRunPages = reactive({});
const selectedRunId = ref(null);
const selectedTaskId = ref(null);
const runCompareResult = ref(null);
const runErrorSummary = ref('');
const timeline = ref([]);
const traceItems = ref([]);
const runHistoryFilter = reactive({
    status: '',
    page: 0,
    size: 5,
});
const runCompareForm = reactive({
    baselineRunId: null,
    targetRunId: null,
    manualBaselineRunId: null,
    manualTargetRunId: null,
});
const runCompareOptions = reactive({
    changedOnly: true,
});
const compareSort = reactive({
    mode: 'none',
});
const exportForm = reactive({
    format: 'json',
    task: null,
});
const exportHistoryFilter = reactive({
    status: '',
    page: 0,
    size: 5,
});
const exportTaskPage = ref(null);
const selectedExportIds = ref([]);
const currentTaskRunPage = computed(() => {
    if (!selectedTaskId.value) {
        return null;
    }
    return taskRunPages[selectedTaskId.value] || null;
});
let runStream = null;
let exportPollTimer = null;
let filterDebounceTimer = null;
const filters = reactive({
    status: '',
    agentVersion: '',
    keyword: '',
});
const createForm = reactive({
    taskName: '',
    agentVersion: '1.0.0',
    datasetId: 'dataset-trip-001',
    metricSet: '',
    evaluationMode: 'PROCESS',
    evaluationMethod: 'HYBRID',
    evaluationDimensions: 'effectiveness,safety,performance',
    strategyVersion: null,
});
const strategyForm = reactive({
    strategyName: '',
    selectedStrategyId: 0,
    newVersion: null,
    weightConfig: '{"effectiveness":0.5,"safety":0.2,"performance":0.3}',
    thresholdConfig: '{"overallThreshold":0.75,"safetyMin":0.7}',
});
const metricForm = reactive({
    metricName: '',
    metricType: 'DETERMINISTIC',
    scoringLogic: 'completion',
    thresholdValue: 0.7,
});
const filteredTasks = computed(() => {
    if (!filters.keyword) {
        return tasks.value;
    }
    const key = filters.keyword.toLowerCase();
    return tasks.value.filter((item) => item.taskName.toLowerCase().includes(key));
});
const metricCards = computed(() => {
    const metrics = runMetrics.value;
    return [
        {
            label: '任务成功率',
            value: metrics ? `${(metrics.taskCompletionRate * 100).toFixed(1)}%` : '-',
        },
        {
            label: '工具正确性',
            value: metrics ? metrics.toolCorrectnessScore.toFixed(2) : '-',
        },
        {
            label: '工具效率',
            value: metrics ? metrics.toolEfficiencyScore.toFixed(2) : '-',
        },
        {
            label: '首字延迟 P95',
            value: metrics ? `${metrics.firstTokenP95}ms` : '-',
        },
        {
            label: '总 Token',
            value: metrics ? metrics.totalTokens.toLocaleString() : '-',
        },
    ];
});
const sortedMetricDiffs = computed(() => {
    const diffs = runCompareResult.value?.metricDiffs || [];
    if (compareSort.mode === 'none') {
        return diffs;
    }
    const withNumber = [...diffs];
    withNumber.sort((a, b) => {
        const da = a.delta ?? 0;
        const db = b.delta ?? 0;
        return compareSort.mode === 'deltaDesc' ? db - da : da - db;
    });
    return withNumber;
});
onMounted(async () => {
    await reloadAll();
});
watch(() => filters.status, () => {
    void loadTasks();
});
watch(() => filters.agentVersion, () => {
    if (filterDebounceTimer) {
        clearTimeout(filterDebounceTimer);
    }
    filterDebounceTimer = setTimeout(() => {
        void loadTasks();
    }, 300);
});
watch(() => runHistoryFilter.status, () => {
    if (!selectedTaskId.value) {
        return;
    }
    void loadTaskRunPage(selectedTaskId.value, 0);
});
watch(() => exportHistoryFilter.status, () => {
    if (!selectedTaskId.value) {
        return;
    }
    void loadExportTaskPage(0);
});
onBeforeUnmount(() => {
    closeRunStream();
    if (exportPollTimer) {
        clearTimeout(exportPollTimer);
        exportPollTimer = null;
    }
    if (filterDebounceTimer) {
        clearTimeout(filterDebounceTimer);
        filterDebounceTimer = null;
    }
});
function statusClass(status) {
    if (status === 'SUCCEEDED')
        return 'success';
    if (status === 'FAILED')
        return 'failed';
    if (status === 'RUNNING')
        return 'running';
    return '';
}
function formatTime(value) {
    if (!value)
        return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime()))
        return value;
    return date.toLocaleString('zh-CN', { hour12: false });
}
function appendTimeline(title, detail) {
    timeline.value.unshift({
        time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
        title,
        detail,
    });
    if (timeline.value.length > 30) {
        timeline.value = timeline.value.slice(0, 30);
    }
}
function closeRunStream() {
    if (runStream) {
        runStream.close();
        runStream = null;
    }
}
function connectRunStream(runId) {
    closeRunStream();
    runStream = createRunEventSource(runId);
    const bind = (eventName, title) => {
        runStream?.addEventListener(eventName, (evt) => {
            appendTimeline(title, evt.data);
            if (eventName === 'run_state') {
                try {
                    const payload = JSON.parse(evt.data);
                    const status = String(payload?.status || '').toUpperCase();
                    if (status === 'SUCCEEDED' || status === 'FAILED') {
                        refreshCurrentRun();
                        loadTasks();
                    }
                }
                catch {
                    // ignore parse failures
                }
            }
            if (eventName === 'run_done' || eventName === 'run_failed' || eventName === 'run_terminated') {
                closeRunStream();
                refreshCurrentRun();
                loadTasks();
            }
        });
    };
    bind('run_state', '运行状态同步');
    bind('run_started', '任务开始执行');
    bind('sample_start', '样本开始');
    bind('sample_done', '样本完成');
    bind('strategy_applied', '策略计算完成');
    bind('run_done', '运行结束');
    bind('run_failed', '运行异常');
    bind('run_terminated', '运行已结束');
}
function parseTraceItems(records) {
    const output = [];
    records.forEach((record) => {
        if (!record.toolTrace) {
            return;
        }
        try {
            const list = JSON.parse(record.toolTrace);
            if (Array.isArray(list)) {
                list.forEach((item) => {
                    output.push({
                        tool: String(item.tool ?? 'tool'),
                        input: JSON.stringify(item.input ?? '-'),
                        output: JSON.stringify(item.output ?? '-'),
                        cost: item.costMs != null ? `${item.costMs}ms` : '-',
                    });
                });
            }
        }
        catch {
            output.push({
                tool: 'tool_trace',
                input: '-',
                output: record.toolTrace,
                cost: '-',
            });
        }
    });
    return output;
}
async function reloadAll() {
    await Promise.all([loadTasks(), loadStrategyData()]);
}
async function loadTasks() {
    try {
        tasks.value = await listTasks({
            status: filters.status || undefined,
            agentVersion: filters.agentVersion || undefined,
        });
        await syncTaskRunSnapshots();
        noticeText.value = `已加载 ${tasks.value.length} 个任务`;
    }
    catch (error) {
        noticeText.value = `加载任务失败: ${error.message || String(error)}`;
    }
}
async function syncTaskRunSnapshots() {
    const taskIds = new Set(tasks.value.map((item) => item.taskId));
    Object.keys(runByTask).forEach((key) => {
        const id = Number(key);
        if (!taskIds.has(id)) {
            delete runByTask[id];
            delete runIdByTask[id];
            delete metricsByTask[id];
            delete taskRunPages[id];
        }
    });
    const results = await Promise.all(tasks.value.map(async (task) => {
        try {
            const page = await listTaskRuns(task.taskId, { page: 0, size: 1 });
            return { taskId: task.taskId, latestRun: page.items[0] || null, page };
        }
        catch {
            return { taskId: task.taskId, latestRun: null, page: null };
        }
    }));
    results.forEach(({ taskId, latestRun, page }) => {
        if (!latestRun) {
            delete runByTask[taskId];
            delete runIdByTask[taskId];
            if (page) {
                taskRunPages[taskId] = page;
            }
            return;
        }
        runByTask[taskId] = latestRun;
        runIdByTask[taskId] = latestRun.runId;
        if (page) {
            taskRunPages[taskId] = page;
        }
    });
}
async function loadTaskRunPage(taskId, page = 0) {
    try {
        const result = await listTaskRuns(taskId, {
            status: runHistoryFilter.status || undefined,
            page,
            size: runHistoryFilter.size,
        });
        taskRunPages[taskId] = result;
        runHistoryFilter.page = result.page;
        selectedTaskId.value = taskId;
        return result;
    }
    catch {
        noticeText.value = '加载任务运行历史失败';
        return null;
    }
}
async function loadExportTaskPage(page = 0) {
    if (!selectedTaskId.value) {
        return;
    }
    try {
        const result = await listExportTasks({
            taskId: selectedTaskId.value,
            status: exportHistoryFilter.status || undefined,
            page,
            size: exportHistoryFilter.size,
        });
        exportTaskPage.value = result;
        exportHistoryFilter.page = result.page;
        const pageIds = new Set(result.items.map((item) => item.exportId));
        selectedExportIds.value = selectedExportIds.value.filter((id) => pageIds.has(id));
    }
    catch (error) {
        noticeText.value = `加载导出任务失败: ${error.message || String(error)}`;
    }
}
function toggleExportSelection(exportId) {
    if (selectedExportIds.value.includes(exportId)) {
        selectedExportIds.value = selectedExportIds.value.filter((id) => id !== exportId);
    }
    else {
        selectedExportIds.value = [...selectedExportIds.value, exportId];
    }
}
function toggleSelectAllExports() {
    const pageIds = (exportTaskPage.value?.items || []).map((item) => item.exportId);
    if (!pageIds.length) {
        return;
    }
    const allSelected = pageIds.every((id) => selectedExportIds.value.includes(id));
    if (allSelected) {
        selectedExportIds.value = selectedExportIds.value.filter((id) => !pageIds.includes(id));
    }
    else {
        selectedExportIds.value = Array.from(new Set([...selectedExportIds.value, ...pageIds]));
    }
}
async function deleteSingleExportTask(exportId) {
    try {
        await deleteExportTask(exportId);
        selectedExportIds.value = selectedExportIds.value.filter((id) => id !== exportId);
        noticeText.value = `已删除导出任务 #${exportId}`;
        await loadExportTaskPage(exportHistoryFilter.page);
    }
    catch (error) {
        noticeText.value = `删除导出任务失败: ${error.message || String(error)}`;
    }
}
async function batchDeleteSelectedExports() {
    if (!selectedExportIds.value.length) {
        return;
    }
    try {
        const result = await batchDeleteExportTasks(selectedExportIds.value);
        noticeText.value = `批量删除完成：成功 ${result.deleted}，失败 ${result.failed}`;
        selectedExportIds.value = [];
        await loadExportTaskPage(exportHistoryFilter.page);
    }
    catch (error) {
        noticeText.value = `批量删除失败: ${error.message || String(error)}`;
    }
}
async function changeExportPage(nextPage) {
    if (nextPage < 0) {
        return;
    }
    await loadExportTaskPage(nextPage);
}
function resetFilters() {
    filters.status = '';
    filters.agentVersion = '';
    filters.keyword = '';
    loadTasks();
}
async function createTaskOnly() {
    try {
        const created = await createTask({
            taskName: createForm.taskName,
            agentVersion: createForm.agentVersion,
            datasetId: createForm.datasetId,
            metricSet: createForm.metricSet || undefined,
            evaluationMode: createForm.evaluationMode,
            evaluationMethod: createForm.evaluationMethod,
            evaluationDimensions: createForm.evaluationDimensions,
            strategyVersion: createForm.strategyVersion || undefined,
        });
        showCreate.value = false;
        noticeText.value = `任务创建成功: ${created.taskName}`;
        await loadTasks();
        return created;
    }
    catch (error) {
        noticeText.value = `创建任务失败: ${error.message || String(error)}`;
        return null;
    }
}
async function createAndStart() {
    const created = await createTaskOnly();
    if (created) {
        await startRun(created.taskId);
    }
}
async function startRun(taskId) {
    try {
        const run = await startTask(taskId);
        selectedRunId.value = run.runId;
        runIdByTask[taskId] = run.runId;
        runByTask[taskId] = run;
        noticeText.value = `任务已启动，runId=${run.runId}`;
        connectRunStream(run.runId);
        await refreshCurrentRun();
    }
    catch (error) {
        noticeText.value = `启动任务失败: ${error.message || String(error)}`;
    }
}
async function viewRunDetail(taskId) {
    selectedTaskId.value = taskId;
    let runId = runIdByTask[taskId];
    if (!runId) {
        const page = await loadTaskRunPage(taskId, 0);
        const latest = page?.items[0];
        if (latest) {
            runByTask[taskId] = latest;
            runIdByTask[taskId] = latest.runId;
            runId = latest.runId;
        }
    }
    if (!runId) {
        noticeText.value = '该任务暂无运行记录，请先点击启动';
        return;
    }
    selectedRunId.value = runId;
    connectRunStream(runId);
    await refreshCurrentRun();
    await loadTaskRunPage(taskId, runHistoryFilter.page);
    await loadExportTaskPage(0);
}
async function applyRunHistoryFilter() {
    if (!selectedTaskId.value) {
        noticeText.value = '请先选择一个任务详情';
        return;
    }
    await loadTaskRunPage(selectedTaskId.value, 0);
}
async function changeRunHistoryPage(nextPage) {
    if (!selectedTaskId.value) {
        return;
    }
    if (nextPage < 0) {
        return;
    }
    await loadTaskRunPage(selectedTaskId.value, nextPage);
}
async function openHistoryRun(run) {
    selectedTaskId.value = run.taskId;
    selectedRunId.value = run.runId;
    runByTask[run.taskId] = run;
    runIdByTask[run.taskId] = run.runId;
    connectRunStream(run.runId);
    await refreshCurrentRun();
}
function resolveCompareIds() {
    return {
        baselineRunId: runCompareForm.manualBaselineRunId || runCompareForm.baselineRunId,
        targetRunId: runCompareForm.manualTargetRunId || runCompareForm.targetRunId,
    };
}
async function runCompareAction() {
    if (!selectedTaskId.value) {
        noticeText.value = '请先选择任务并加载运行历史';
        return;
    }
    const { baselineRunId, targetRunId } = resolveCompareIds();
    if (!baselineRunId || !targetRunId) {
        noticeText.value = '请先选择 baseline/target run';
        return;
    }
    if (baselineRunId === targetRunId) {
        noticeText.value = 'baseline 与 target 不能相同';
        return;
    }
    try {
        runCompareResult.value = await compareTaskRuns(selectedTaskId.value, baselineRunId, targetRunId, runCompareOptions.changedOnly);
        exportForm.task = null;
        noticeText.value = `对比完成：变化样本 ${runCompareResult.value.changedSamples}/${runCompareResult.value.totalSamples}`;
    }
    catch (error) {
        noticeText.value = `运行对比失败: ${error.message || String(error)}`;
    }
}
async function createExportTaskAction() {
    if (!selectedTaskId.value) {
        noticeText.value = '请先选择任务';
        return;
    }
    const { baselineRunId, targetRunId } = resolveCompareIds();
    if (!baselineRunId || !targetRunId) {
        noticeText.value = '请先选择 baseline/target run';
        return;
    }
    if (baselineRunId === targetRunId) {
        noticeText.value = 'baseline 与 target 不能相同';
        return;
    }
    try {
        if (exportPollTimer) {
            clearTimeout(exportPollTimer);
            exportPollTimer = null;
        }
        const task = await createRunCompareExportTask(selectedTaskId.value, baselineRunId, targetRunId, runCompareOptions.changedOnly, exportForm.format);
        exportForm.task = task;
        noticeText.value = `导出任务已创建: #${task.exportId}`;
        await loadExportTaskPage(0);
        await pollExportTask(task.exportId, 0);
    }
    catch (error) {
        noticeText.value = `创建导出任务失败: ${error.message || String(error)}`;
    }
}
async function retryFailedExportTask(exportId) {
    try {
        const task = await retryExportTask(exportId);
        exportForm.task = task;
        noticeText.value = `导出任务已重试: #${task.exportId}`;
        await loadExportTaskPage(exportHistoryFilter.page);
        await pollExportTask(task.exportId, 0);
    }
    catch (error) {
        noticeText.value = `重试导出任务失败: ${error.message || String(error)}`;
    }
}
async function pollExportTask(exportId, round) {
    if (round > 30) {
        if (exportPollTimer) {
            clearTimeout(exportPollTimer);
            exportPollTimer = null;
        }
        noticeText.value = '导出任务轮询超时，请稍后手动刷新状态';
        return;
    }
    try {
        const task = await getExportTask(exportId);
        exportForm.task = task;
        if (task.status === 'SUCCEEDED') {
            if (exportPollTimer) {
                clearTimeout(exportPollTimer);
                exportPollTimer = null;
            }
            noticeText.value = `导出完成: #${task.exportId}`;
            if (task.downloadUrl) {
                downloadExportFile(task.downloadUrl);
            }
            return;
        }
        if (task.status === 'FAILED') {
            if (exportPollTimer) {
                clearTimeout(exportPollTimer);
                exportPollTimer = null;
            }
            noticeText.value = `导出失败: ${task.message || '未知错误'}`;
            return;
        }
        exportPollTimer = setTimeout(() => {
            pollExportTask(exportId, round + 1);
        }, 1000);
    }
    catch (error) {
        noticeText.value = `查询导出状态失败: ${error.message || String(error)}`;
    }
}
function downloadExportFile(downloadUrl) {
    window.open(`${window.location.origin}${downloadUrl}`, '_blank');
}
function deltaClass(delta) {
    if (delta == null || delta === 0) {
        return '';
    }
    return delta > 0 ? 'delta-up' : 'delta-down';
}
async function refreshCurrentRun() {
    if (!selectedRunId.value) {
        return;
    }
    try {
        const runId = selectedRunId.value;
        const [run, records] = await Promise.all([getRun(runId), getRunRecords(runId)]);
        currentRun.value = run;
        runByTask[run.taskId] = run;
        runIdByTask[run.taskId] = run.runId;
        runRecords.value = records;
        traceItems.value = parseTraceItems(records);
        runErrorSummary.value = records.find((item) => item.errorMessage)?.errorMessage || '';
        try {
            const metrics = await getRunMetrics(runId);
            runMetrics.value = metrics;
            metricsByTask[run.taskId] = metrics;
        }
        catch {
            runMetrics.value = null;
        }
    }
    catch (error) {
        noticeText.value = `刷新运行失败: ${error.message || String(error)}`;
    }
}
async function loadStrategyData() {
    try {
        const [strategyList, metricList] = await Promise.all([
            listStrategies(),
            listCustomMetrics(),
        ]);
        strategies.value = strategyList;
        customMetrics.value = metricList;
    }
    catch (error) {
        noticeText.value = `加载策略配置失败: ${error.message || String(error)}`;
    }
}
async function createStrategyAction() {
    if (!strategyForm.strategyName) {
        noticeText.value = '请先输入策略名称';
        return;
    }
    try {
        const result = await createStrategy({
            strategyName: strategyForm.strategyName,
            weightConfig: strategyForm.weightConfig,
            thresholdConfig: strategyForm.thresholdConfig,
        });
        strategyForm.selectedStrategyId = result.strategyId;
        noticeText.value = `策略创建成功: ${result.strategyName}`;
        await loadStrategyData();
    }
    catch (error) {
        noticeText.value = `创建策略失败: ${error.message || String(error)}`;
    }
}
async function createStrategyVersionAction() {
    if (!strategyForm.selectedStrategyId) {
        noticeText.value = '请先选择策略';
        return;
    }
    try {
        const result = await createStrategyVersion(strategyForm.selectedStrategyId, {
            version: strategyForm.newVersion || undefined,
            weightConfig: strategyForm.weightConfig,
            thresholdConfig: strategyForm.thresholdConfig,
        });
        noticeText.value = `策略版本保存成功: version=${result.version}, id=${result.strategyVersionId}`;
        createForm.strategyVersion = result.strategyVersionId;
        await loadStrategyData();
    }
    catch (error) {
        noticeText.value = `保存策略版本失败: ${error.message || String(error)}`;
    }
}
async function createCustomMetricAction() {
    if (!metricForm.metricName) {
        noticeText.value = '请先输入自定义指标名称';
        return;
    }
    try {
        const metric = await createCustomMetric({
            metricName: metricForm.metricName,
            metricType: metricForm.metricType,
            scoringLogic: metricForm.scoringLogic,
            thresholdValue: metricForm.thresholdValue,
            enabled: true,
        });
        noticeText.value = `自定义指标已注册: ${metric.metricName} (#${metric.customMetricId})`;
        const ids = customMetrics.value.filter((x) => x.enabled).map((x) => x.customMetricId);
        ids.push(metric.customMetricId);
        createForm.metricSet = JSON.stringify(Array.from(new Set(ids)));
        await loadStrategyData();
    }
    catch (error) {
        noticeText.value = `注册自定义指标失败: ${error.message || String(error)}`;
    }
}
function scrollToSection(section) {
    const map = {
        overview: 'section-overview',
        tasks: 'section-tasks',
        config: 'section-config',
        detail: 'section-detail',
        monitor: 'section-monitor',
    };
    const target = document.getElementById(map[section]);
    target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}
function openCreateDialog() {
    showCreate.value = true;
}
async function reloadDashboard() {
    await reloadAll();
}
const __VLS_exposed = {
    scrollToSection,
    openCreateDialog,
    reloadDashboard,
};
defineExpose(__VLS_exposed);
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "dashboard-page" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
    id: "section-overview",
    ...{ class: "surface intro-card" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "chip-row" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "chip" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "chip" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
    ...{ class: "chip" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
    id: "section-tasks",
    ...{ class: "surface task-board" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "section-head" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "inline-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.reloadAll) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (...[$event]) => {
            __VLS_ctx.showCreate = true;
        } },
    ...{ class: "primary" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "filter-bar" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    value: (__VLS_ctx.filters.status),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "READY",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "RUNNING",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "SUCCEEDED",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "FAILED",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    value: (__VLS_ctx.filters.agentVersion),
    type: "text",
    placeholder: "Agent 版本",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    value: (__VLS_ctx.filters.keyword),
    type: "text",
    placeholder: "测试任务名称",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.loadTasks) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.resetFilters) },
    ...{ class: "ghost" },
});
if (__VLS_ctx.noticeText) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ class: "notice-text" },
    });
    (__VLS_ctx.noticeText);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "table-wrap" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
    ...{ class: "task-table" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
for (const [task] of __VLS_getVForSourceType((__VLS_ctx.filteredTasks))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
        key: (task.taskId),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (task.taskName);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (task.evaluationMethod);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (task.agentVersion);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
        ...{ class: "status" },
        ...{ class: (__VLS_ctx.statusClass(task.status)) },
    });
    (task.status);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (__VLS_ctx.runByTask[task.taskId]?.totalCount ?? '-');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (__VLS_ctx.metricsByTask[task.taskId]?.totalTokens ?? '-');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (__VLS_ctx.formatTime(task.createdAt));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (__VLS_ctx.formatTime(__VLS_ctx.runByTask[task.taskId]?.endTime));
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.viewRunDetail(task.taskId);
            } },
        ...{ class: "link-btn" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                __VLS_ctx.startRun(task.taskId);
            } },
        ...{ class: "link-btn" },
    });
}
if (!__VLS_ctx.filteredTasks.length) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
        colspan: "9",
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "metric-grid" },
});
for (const [metric] of __VLS_getVForSourceType((__VLS_ctx.metricCards))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
        ...{ class: "surface metric-card" },
        key: (metric.label),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    (metric.label);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
    (metric.value);
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
    id: "section-detail",
    ...{ class: "surface detail-board" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "section-head" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "inline-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    value: (__VLS_ctx.runHistoryFilter.status),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "RUNNING",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "SUCCEEDED",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "FAILED",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.applyRunHistoryFilter) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.refreshCurrentRun) },
    ...{ class: "ghost" },
});
if (__VLS_ctx.currentTaskRunPage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-head" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.selectedTaskId);
    (__VLS_ctx.currentTaskRunPage.total);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "inline-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentTaskRunPage))
                    return;
                __VLS_ctx.changeRunHistoryPage(__VLS_ctx.runHistoryFilter.page - 1);
            } },
        ...{ class: "ghost" },
        disabled: (__VLS_ctx.runHistoryFilter.page <= 0),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentTaskRunPage))
                    return;
                __VLS_ctx.changeRunHistoryPage(__VLS_ctx.runHistoryFilter.page + 1);
            } },
        ...{ class: "ghost" },
        disabled: (!__VLS_ctx.currentTaskRunPage.hasNext),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-list" },
    });
    for (const [item] of __VLS_getVForSourceType((__VLS_ctx.currentTaskRunPage.items))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(__VLS_ctx.currentTaskRunPage))
                        return;
                    __VLS_ctx.openHistoryRun(item);
                } },
            key: (item.runId),
            ...{ class: "link-btn" },
        });
        (item.runId);
        (item.status);
        (__VLS_ctx.formatTime(item.startTime));
    }
    if (!__VLS_ctx.currentTaskRunPage.items.length) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "notice-text" },
        });
    }
}
if (__VLS_ctx.currentTaskRunPage) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-compare-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-head" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "inline-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({
        ...{ class: "inline-check" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "checkbox",
    });
    (__VLS_ctx.runCompareOptions.changedOnly);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "number",
        min: "1",
        placeholder: "手动 baselineRunId",
    });
    (__VLS_ctx.runCompareForm.manualBaselineRunId);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "number",
        min: "1",
        placeholder: "手动 targetRunId",
    });
    (__VLS_ctx.runCompareForm.manualTargetRunId);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.runCompareForm.baselineRunId),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: (null),
    });
    for (const [item] of __VLS_getVForSourceType((__VLS_ctx.currentTaskRunPage.items))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            key: (`b-${item.runId}`),
            value: (item.runId),
        });
        (item.runId);
        (item.status);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.runCompareForm.targetRunId),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: (null),
    });
    for (const [item] of __VLS_getVForSourceType((__VLS_ctx.currentTaskRunPage.items))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            key: (`t-${item.runId}`),
            value: (item.runId),
        });
        (item.runId);
        (item.status);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.runCompareAction) },
        ...{ class: "ghost" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.exportForm.format),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "json",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "csv",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.createExportTaskAction) },
        ...{ class: "ghost" },
        disabled: (!__VLS_ctx.runCompareResult),
    });
    if (__VLS_ctx.exportForm.task) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "compare-summary" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.exportForm.task.exportId);
        (__VLS_ctx.exportForm.task.status);
        if (__VLS_ctx.exportForm.task.message) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
            (__VLS_ctx.exportForm.task.message);
        }
        if (__VLS_ctx.exportForm.task.status === 'SUCCEEDED' && __VLS_ctx.exportForm.task.downloadUrl) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                ...{ onClick: (...[$event]) => {
                        if (!(__VLS_ctx.currentTaskRunPage))
                            return;
                        if (!(__VLS_ctx.exportForm.task))
                            return;
                        if (!(__VLS_ctx.exportForm.task.status === 'SUCCEEDED' && __VLS_ctx.exportForm.task.downloadUrl))
                            return;
                        __VLS_ctx.downloadExportFile(__VLS_ctx.exportForm.task.downloadUrl);
                    } },
                ...{ class: "link-btn" },
            });
        }
    }
    if (__VLS_ctx.runCompareResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "compare-summary" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.runCompareResult.changedSamples);
        (__VLS_ctx.runCompareResult.totalSamples);
    }
    if (__VLS_ctx.runCompareResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "inline-actions compare-sort-row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.compareSort.mode),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "none",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "deltaDesc",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "deltaAsc",
        });
    }
    if (__VLS_ctx.runCompareResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "table-wrap" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
            ...{ class: "task-table detail-table" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
        for (const [item] of __VLS_getVForSourceType((__VLS_ctx.sortedMetricDiffs))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
                key: (item.metric),
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.metric);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.baseline ?? '-');
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.target ?? '-');
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
                ...{ class: (__VLS_ctx.deltaClass(item.delta)) },
            });
            (item.delta ?? '-');
        }
    }
    if (__VLS_ctx.runCompareResult) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "table-wrap" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
            ...{ class: "task-table detail-table" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
        for (const [item] of __VLS_getVForSourceType((__VLS_ctx.runCompareResult.sampleDiffs))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
                key: (`diff-${item.index}`),
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.index);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.input);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.baselineOutput || '-');
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.targetOutput || '-');
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.baselineError || '-');
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
            (item.targetError || '-');
        }
        if (!__VLS_ctx.runCompareResult.sampleDiffs.length) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
                colspan: "6",
            });
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-panel export-history-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "run-history-head" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
    (__VLS_ctx.exportTaskPage?.total || 0);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "inline-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.exportHistoryFilter.status),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "PENDING",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "RUNNING",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "SUCCEEDED",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "FAILED",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.toggleSelectAllExports) },
        ...{ class: "ghost" },
        disabled: (!(__VLS_ctx.exportTaskPage?.items?.length)),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.batchDeleteSelectedExports) },
        ...{ class: "ghost" },
        disabled: (!__VLS_ctx.selectedExportIds.length),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentTaskRunPage))
                    return;
                __VLS_ctx.loadExportTaskPage(0);
            } },
        ...{ class: "ghost" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentTaskRunPage))
                    return;
                __VLS_ctx.changeExportPage(__VLS_ctx.exportHistoryFilter.page - 1);
            } },
        ...{ class: "ghost" },
        disabled: (__VLS_ctx.exportHistoryFilter.page <= 0),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.currentTaskRunPage))
                    return;
                __VLS_ctx.changeExportPage(__VLS_ctx.exportHistoryFilter.page + 1);
            } },
        ...{ class: "ghost" },
        disabled: (!(__VLS_ctx.exportTaskPage?.hasNext)),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "table-wrap" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
        ...{ class: "task-table detail-table" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
    for (const [item] of __VLS_getVForSourceType(((__VLS_ctx.exportTaskPage?.items || [])))) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
            key: (`export-${item.exportId}`),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (...[$event]) => {
                    if (!(__VLS_ctx.currentTaskRunPage))
                        return;
                    __VLS_ctx.toggleExportSelection(item.exportId);
                } },
            type: "checkbox",
            checked: (__VLS_ctx.selectedExportIds.includes(item.exportId)),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (item.exportId);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (item.format);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "status" },
            ...{ class: (__VLS_ctx.statusClass(item.status)) },
        });
        (item.status);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (__VLS_ctx.formatTime(item.createdAt));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        (__VLS_ctx.formatTime(item.completedAt));
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
        if (item.downloadUrl) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                ...{ onClick: (...[$event]) => {
                        if (!(__VLS_ctx.currentTaskRunPage))
                            return;
                        if (!(item.downloadUrl))
                            return;
                        __VLS_ctx.downloadExportFile(item.downloadUrl);
                    } },
                ...{ class: "link-btn" },
            });
        }
        if (item.status === 'FAILED') {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                ...{ onClick: (...[$event]) => {
                        if (!(__VLS_ctx.currentTaskRunPage))
                            return;
                        if (!(item.status === 'FAILED'))
                            return;
                        __VLS_ctx.retryFailedExportTask(item.exportId);
                    } },
                ...{ class: "link-btn" },
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!(__VLS_ctx.currentTaskRunPage))
                        return;
                    __VLS_ctx.deleteSingleExportTask(item.exportId);
                } },
            ...{ class: "link-btn" },
        });
    }
    if (!(__VLS_ctx.exportTaskPage?.items || []).length) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
            colspan: "7",
        });
    }
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "table-wrap" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
    ...{ class: "task-table detail-table" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
for (const [record, idx] of __VLS_getVForSourceType((__VLS_ctx.runRecords))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
        key: (record.qaId),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (idx + 1);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.input);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.expectedOutput);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.actualOutput);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.errorCode ? '失败' : '通过');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.errorMessage || '与预期一致');
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
    (record.tokenUsage || '-');
}
if (!__VLS_ctx.runRecords.length) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({
        colspan: "7",
    });
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
    ...{ class: "lower-grid" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
    id: "section-config",
    ...{ class: "surface strategy-card" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "section-head" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.loadStrategyData) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "form-layout" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    value: (__VLS_ctx.strategyForm.strategyName),
    type: "text",
    placeholder: "如：默认生产策略",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.textarea)({
    value: (__VLS_ctx.strategyForm.weightConfig),
    rows: "3",
    placeholder: '{"effectiveness":0.5,"safety":0.2,"performance":0.3}',
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.textarea)({
    value: (__VLS_ctx.strategyForm.thresholdConfig),
    rows: "3",
    placeholder: '{"overallThreshold":0.75,"safetyMin":0.7}',
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "inline-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.createStrategyAction) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    value: (__VLS_ctx.strategyForm.selectedStrategyId),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: (0),
});
for (const [item] of __VLS_getVForSourceType((__VLS_ctx.strategies))) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        key: (item.strategyId),
        value: (item.strategyId),
    });
    (item.strategyName);
    (item.latestVersion || '-');
}
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    type: "number",
    min: "1",
    placeholder: "留空自动递增",
});
(__VLS_ctx.strategyForm.newVersion);
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "inline-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.createStrategyVersionAction) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    value: (__VLS_ctx.metricForm.metricName),
    type: "text",
    placeholder: "如：响应稳定性",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
    value: (__VLS_ctx.metricForm.metricType),
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "DETERMINISTIC",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
    value: "JUDGE",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    value: (__VLS_ctx.metricForm.scoringLogic),
    type: "text",
    placeholder: "completion / latency / performance",
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
__VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
    type: "number",
    step: "0.01",
    min: "0",
    max: "1",
});
(__VLS_ctx.metricForm.thresholdValue);
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    ...{ class: "inline-actions" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
    ...{ onClick: (__VLS_ctx.createCustomMetricAction) },
    ...{ class: "ghost" },
});
__VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
(__VLS_ctx.customMetrics.filter((x) => x.enabled).length);
__VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
    id: "section-monitor",
});
/** @type {[typeof ChatPanel, ]} */ ;
// @ts-ignore
const __VLS_0 = __VLS_asFunctionalComponent(ChatPanel, new ChatPanel({
    ...{ 'onRefresh': {} },
    timeline: (__VLS_ctx.timeline),
    traces: (__VLS_ctx.traceItems),
    errorSummary: (__VLS_ctx.runErrorSummary),
}));
const __VLS_1 = __VLS_0({
    ...{ 'onRefresh': {} },
    timeline: (__VLS_ctx.timeline),
    traces: (__VLS_ctx.traceItems),
    errorSummary: (__VLS_ctx.runErrorSummary),
}, ...__VLS_functionalComponentArgsRest(__VLS_0));
let __VLS_3;
let __VLS_4;
let __VLS_5;
const __VLS_6 = {
    onRefresh: (__VLS_ctx.refreshCurrentRun)
};
var __VLS_2;
if (__VLS_ctx.showCreate) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.showCreate))
                    return;
                __VLS_ctx.showCreate = false;
            } },
        ...{ class: "modal-mask" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.article, __VLS_intrinsicElements.article)({
        ...{ class: "create-modal" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "section-head" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.showCreate))
                    return;
                __VLS_ctx.showCreate = false;
            } },
        ...{ class: "link-btn" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "form-layout" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.createForm.taskName),
        type: "text",
        placeholder: "请输入测试任务名称",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.createForm.agentVersion),
        type: "text",
        placeholder: "如 1.0.0",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.createForm.datasetId),
        type: "text",
        placeholder: "如 dataset-trip-001",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.createForm.evaluationMode),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "RESULT",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "PROCESS",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
        value: (__VLS_ctx.createForm.evaluationMethod),
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "DETERMINISTIC",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "JUDGE",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
        value: "HYBRID",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.createForm.evaluationDimensions),
        type: "text",
        placeholder: "effectiveness,safety,performance",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        type: "number",
        min: "1",
    });
    (__VLS_ctx.createForm.strategyVersion);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
        value: (__VLS_ctx.createForm.metricSet),
        type: "text",
        placeholder: "例如 [1,2] 或 1,2",
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "modal-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(__VLS_ctx.showCreate))
                    return;
                __VLS_ctx.showCreate = false;
            } },
        ...{ class: "ghost" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.createTaskOnly) },
        ...{ class: "ghost" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.createAndStart) },
        ...{ class: "primary" },
    });
}
/** @type {__VLS_StyleScopedClasses['dashboard-page']} */ ;
/** @type {__VLS_StyleScopedClasses['surface']} */ ;
/** @type {__VLS_StyleScopedClasses['intro-card']} */ ;
/** @type {__VLS_StyleScopedClasses['chip-row']} */ ;
/** @type {__VLS_StyleScopedClasses['chip']} */ ;
/** @type {__VLS_StyleScopedClasses['chip']} */ ;
/** @type {__VLS_StyleScopedClasses['chip']} */ ;
/** @type {__VLS_StyleScopedClasses['surface']} */ ;
/** @type {__VLS_StyleScopedClasses['task-board']} */ ;
/** @type {__VLS_StyleScopedClasses['section-head']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['primary']} */ ;
/** @type {__VLS_StyleScopedClasses['filter-bar']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['notice-text']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['task-table']} */ ;
/** @type {__VLS_StyleScopedClasses['status']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['metric-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['surface']} */ ;
/** @type {__VLS_StyleScopedClasses['metric-card']} */ ;
/** @type {__VLS_StyleScopedClasses['surface']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-board']} */ ;
/** @type {__VLS_StyleScopedClasses['section-head']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-head']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-list']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['notice-text']} */ ;
/** @type {__VLS_StyleScopedClasses['run-compare-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-head']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-check']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['compare-summary']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['compare-summary']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['compare-sort-row']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['task-table']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-table']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['task-table']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-table']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['export-history-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['run-history-head']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['task-table']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-table']} */ ;
/** @type {__VLS_StyleScopedClasses['status']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['table-wrap']} */ ;
/** @type {__VLS_StyleScopedClasses['task-table']} */ ;
/** @type {__VLS_StyleScopedClasses['detail-table']} */ ;
/** @type {__VLS_StyleScopedClasses['lower-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['surface']} */ ;
/** @type {__VLS_StyleScopedClasses['strategy-card']} */ ;
/** @type {__VLS_StyleScopedClasses['section-head']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['form-layout']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['inline-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-mask']} */ ;
/** @type {__VLS_StyleScopedClasses['create-modal']} */ ;
/** @type {__VLS_StyleScopedClasses['section-head']} */ ;
/** @type {__VLS_StyleScopedClasses['link-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['form-layout']} */ ;
/** @type {__VLS_StyleScopedClasses['modal-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['ghost']} */ ;
/** @type {__VLS_StyleScopedClasses['primary']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            ChatPanel: ChatPanel,
            showCreate: showCreate,
            noticeText: noticeText,
            runRecords: runRecords,
            strategies: strategies,
            customMetrics: customMetrics,
            runByTask: runByTask,
            metricsByTask: metricsByTask,
            selectedTaskId: selectedTaskId,
            runCompareResult: runCompareResult,
            runErrorSummary: runErrorSummary,
            timeline: timeline,
            traceItems: traceItems,
            runHistoryFilter: runHistoryFilter,
            runCompareForm: runCompareForm,
            runCompareOptions: runCompareOptions,
            compareSort: compareSort,
            exportForm: exportForm,
            exportHistoryFilter: exportHistoryFilter,
            exportTaskPage: exportTaskPage,
            selectedExportIds: selectedExportIds,
            currentTaskRunPage: currentTaskRunPage,
            filters: filters,
            createForm: createForm,
            strategyForm: strategyForm,
            metricForm: metricForm,
            filteredTasks: filteredTasks,
            metricCards: metricCards,
            sortedMetricDiffs: sortedMetricDiffs,
            statusClass: statusClass,
            formatTime: formatTime,
            reloadAll: reloadAll,
            loadTasks: loadTasks,
            loadExportTaskPage: loadExportTaskPage,
            toggleExportSelection: toggleExportSelection,
            toggleSelectAllExports: toggleSelectAllExports,
            deleteSingleExportTask: deleteSingleExportTask,
            batchDeleteSelectedExports: batchDeleteSelectedExports,
            changeExportPage: changeExportPage,
            resetFilters: resetFilters,
            createTaskOnly: createTaskOnly,
            createAndStart: createAndStart,
            startRun: startRun,
            viewRunDetail: viewRunDetail,
            applyRunHistoryFilter: applyRunHistoryFilter,
            changeRunHistoryPage: changeRunHistoryPage,
            openHistoryRun: openHistoryRun,
            runCompareAction: runCompareAction,
            createExportTaskAction: createExportTaskAction,
            retryFailedExportTask: retryFailedExportTask,
            downloadExportFile: downloadExportFile,
            deltaClass: deltaClass,
            refreshCurrentRun: refreshCurrentRun,
            loadStrategyData: loadStrategyData,
            createStrategyAction: createStrategyAction,
            createStrategyVersionAction: createStrategyVersionAction,
            createCustomMetricAction: createCustomMetricAction,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {
            ...__VLS_exposed,
        };
    },
});
; /* PartiallyEnd: #4569/main.vue */
