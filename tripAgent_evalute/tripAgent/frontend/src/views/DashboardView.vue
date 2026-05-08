<template>
  <section class="dashboard-page">
    <article id="section-overview" class="surface intro-card">
      <div>
        <h2>批量测试</h2>
        <p>可针对指定 Agent 版本创建评测任务，查看执行结果并追踪样本级过程日志。</p>
      </div>
      <div class="chip-row">
        <span class="chip">结果导向</span>
        <span class="chip">过程导向</span>
        <span class="chip">显式指标 + 模糊评估</span>
      </div>
    </article>

    <article id="section-tasks" class="surface task-board">
      <div class="section-head">
        <h3>评测任务列表</h3>
        <div class="inline-actions">
          <button class="ghost" @click="reloadAll">刷新数据</button>
          <button class="primary" @click="showCreate = true">创建任务</button>
        </div>
      </div>

      <div class="filter-bar">
        <select v-model="filters.status">
          <option value="">任务状态</option>
          <option value="READY">READY</option>
          <option value="RUNNING">RUNNING</option>
          <option value="SUCCEEDED">SUCCEEDED</option>
          <option value="FAILED">FAILED</option>
        </select>
        <input v-model.trim="filters.agentVersion" type="text" placeholder="Agent 版本" />
        <input v-model.trim="filters.keyword" type="text" placeholder="测试任务名称" />
        <button class="ghost" @click="loadTasks">搜索</button>
        <button class="ghost" @click="resetFilters">重置</button>
      </div>

      <p v-if="noticeText" class="notice-text">{{ noticeText }}</p>

      <div class="table-wrap">
        <table class="task-table">
          <thead>
            <tr>
              <th>测试任务名称</th>
              <th>评估方式</th>
              <th>Agent 版本</th>
              <th>状态</th>
              <th>测试数据</th>
              <th>Token 消耗</th>
              <th>创建时间</th>
              <th>完成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in filteredTasks" :key="task.taskId">
              <td>{{ task.taskName }}</td>
              <td>{{ task.evaluationMethod }}</td>
              <td>{{ task.agentVersion }}</td>
              <td><span class="status" :class="statusClass(task.status)">{{ task.status }}</span></td>
              <td>{{ runByTask[task.taskId]?.totalCount ?? '-' }}</td>
              <td>{{ metricsByTask[task.taskId]?.totalTokens ?? '-' }}</td>
              <td>{{ formatTime(task.createdAt) }}</td>
              <td>{{ formatTime(runByTask[task.taskId]?.endTime) }}</td>
              <td>
                <button class="link-btn" @click="viewRunDetail(task.taskId)">详情</button>
                <button class="link-btn" @click="startRun(task.taskId)">启动</button>
              </td>
            </tr>
            <tr v-if="!filteredTasks.length">
              <td colspan="9">暂无任务数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <section class="metric-grid">
      <article class="surface metric-card" v-for="metric in metricCards" :key="metric.label">
        <p>{{ metric.label }}</p>
        <strong>{{ metric.value }}</strong>
      </article>
    </section>

    <article id="section-detail" class="surface detail-board">
      <div class="section-head">
        <h3>运行详情</h3>
        <div class="inline-actions">
          <select v-model="runHistoryFilter.status">
            <option value="">运行状态</option>
            <option value="RUNNING">RUNNING</option>
            <option value="SUCCEEDED">SUCCEEDED</option>
            <option value="FAILED">FAILED</option>
          </select>
          <button class="ghost" @click="applyRunHistoryFilter">筛选历史</button>
          <button class="ghost" @click="refreshCurrentRun">刷新运行</button>
        </div>
      </div>

      <div v-if="currentTaskRunPage" class="run-history-panel">
        <div class="run-history-head">
          <span>任务 {{ selectedTaskId }} 运行历史（共 {{ currentTaskRunPage.total }} 条）</span>
          <div class="inline-actions">
            <button class="ghost" :disabled="runHistoryFilter.page <= 0" @click="changeRunHistoryPage(runHistoryFilter.page - 1)">上一页</button>
            <button class="ghost" :disabled="!currentTaskRunPage.hasNext" @click="changeRunHistoryPage(runHistoryFilter.page + 1)">下一页</button>
          </div>
        </div>
        <div class="run-history-list">
          <button
            v-for="item in currentTaskRunPage.items"
            :key="item.runId"
            class="link-btn"
            @click="openHistoryRun(item)"
          >
            #{{ item.runId }} · {{ item.status }} · {{ formatTime(item.startTime) }}
          </button>
          <span v-if="!currentTaskRunPage.items.length" class="notice-text">该筛选条件下暂无运行记录</span>
        </div>
      </div>

      <div v-if="currentTaskRunPage" class="run-compare-panel">
        <div class="run-history-head">
          <span>运行对比（baseline vs target）</span>
          <div class="inline-actions">
            <label class="inline-check">
              <input v-model="runCompareOptions.changedOnly" type="checkbox" />
              仅显示变化样本
            </label>
            <input v-model.number="runCompareForm.manualBaselineRunId" type="number" min="1" placeholder="手动 baselineRunId" />
            <input v-model.number="runCompareForm.manualTargetRunId" type="number" min="1" placeholder="手动 targetRunId" />
            <select v-model.number="runCompareForm.baselineRunId">
              <option :value="null">选择 baseline run</option>
              <option v-for="item in currentTaskRunPage.items" :key="`b-${item.runId}`" :value="item.runId">
                #{{ item.runId }} · {{ item.status }}
              </option>
            </select>
            <select v-model.number="runCompareForm.targetRunId">
              <option :value="null">选择 target run</option>
              <option v-for="item in currentTaskRunPage.items" :key="`t-${item.runId}`" :value="item.runId">
                #{{ item.runId }} · {{ item.status }}
              </option>
            </select>
            <button class="ghost" @click="runCompareAction">对比</button>
            <select v-model="exportForm.format">
              <option value="json">json</option>
              <option value="csv">csv</option>
            </select>
            <button class="ghost" :disabled="!runCompareResult" @click="createExportTaskAction">异步导出</button>
          </div>
        </div>

        <div v-if="exportForm.task" class="compare-summary">
          <strong>导出任务 #{{ exportForm.task.exportId }}：{{ exportForm.task.status }}</strong>
          <span v-if="exportForm.task.message">（{{ exportForm.task.message }}）</span>
          <button
            v-if="exportForm.task.status === 'SUCCEEDED' && exportForm.task.downloadUrl"
            class="link-btn"
            @click="downloadExportFile(exportForm.task.downloadUrl)"
          >
            下载文件
          </button>
        </div>

        <div v-if="runCompareResult" class="compare-summary">
          <strong>样本变化: {{ runCompareResult.changedSamples }}/{{ runCompareResult.totalSamples }}</strong>
        </div>

        <div v-if="runCompareResult" class="inline-actions compare-sort-row">
          <span>指标排序</span>
          <select v-model="compareSort.mode">
            <option value="none">默认</option>
            <option value="deltaDesc">按 delta 降序</option>
            <option value="deltaAsc">按 delta 升序</option>
          </select>
        </div>

        <div v-if="runCompareResult" class="table-wrap">
          <table class="task-table detail-table">
            <thead>
              <tr>
                <th>指标</th>
                <th>Baseline</th>
                <th>Target</th>
                <th>Delta</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in sortedMetricDiffs" :key="item.metric">
                <td>{{ item.metric }}</td>
                <td>{{ item.baseline ?? '-' }}</td>
                <td>{{ item.target ?? '-' }}</td>
                <td :class="deltaClass(item.delta)">{{ item.delta ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="runCompareResult" class="table-wrap">
          <table class="task-table detail-table">
            <thead>
              <tr>
                <th>样本序号</th>
                <th>输入</th>
                <th>Baseline 输出</th>
                <th>Target 输出</th>
                <th>Baseline 错误</th>
                <th>Target 错误</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in runCompareResult.sampleDiffs" :key="`diff-${item.index}`">
                <td>{{ item.index }}</td>
                <td>{{ item.input }}</td>
                <td>{{ item.baselineOutput || '-' }}</td>
                <td>{{ item.targetOutput || '-' }}</td>
                <td>{{ item.baselineError || '-' }}</td>
                <td>{{ item.targetError || '-' }}</td>
              </tr>
              <tr v-if="!runCompareResult.sampleDiffs.length">
                <td colspan="6">两次运行样本结果无差异</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="run-history-panel export-history-panel">
          <div class="run-history-head">
            <span>导出任务（共 {{ exportTaskPage?.total || 0 }} 条）</span>
            <div class="inline-actions">
              <select v-model="exportHistoryFilter.status">
                <option value="">全部状态</option>
                <option value="PENDING">PENDING</option>
                <option value="RUNNING">RUNNING</option>
                <option value="SUCCEEDED">SUCCEEDED</option>
                <option value="FAILED">FAILED</option>
              </select>
              <button class="ghost" :disabled="!(exportTaskPage?.items?.length)" @click="toggleSelectAllExports">全选本页</button>
              <button class="ghost" :disabled="!selectedExportIds.length" @click="batchDeleteSelectedExports">批量删除</button>
              <button class="ghost" @click="loadExportTaskPage(0)">刷新导出</button>
              <button class="ghost" :disabled="exportHistoryFilter.page <= 0" @click="changeExportPage(exportHistoryFilter.page - 1)">上一页</button>
              <button class="ghost" :disabled="!(exportTaskPage?.hasNext)" @click="changeExportPage(exportHistoryFilter.page + 1)">下一页</button>
            </div>
          </div>
          <div class="table-wrap">
            <table class="task-table detail-table">
              <thead>
                <tr>
                  <th>选择</th>
                  <th>ExportId</th>
                  <th>格式</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in (exportTaskPage?.items || [])" :key="`export-${item.exportId}`">
                  <td>
                    <input type="checkbox" :checked="selectedExportIds.includes(item.exportId)" @change="toggleExportSelection(item.exportId)" />
                  </td>
                  <td>#{{ item.exportId }}</td>
                  <td>{{ item.format }}</td>
                  <td><span class="status" :class="statusClass(item.status as any)">{{ item.status }}</span></td>
                  <td>{{ formatTime(item.createdAt) }}</td>
                  <td>{{ formatTime(item.completedAt) }}</td>
                  <td>
                    <button class="link-btn" v-if="item.downloadUrl" @click="downloadExportFile(item.downloadUrl)">下载</button>
                    <button class="link-btn" v-if="item.status === 'FAILED'" @click="retryFailedExportTask(item.exportId)">重试</button>
                    <button class="link-btn" @click="deleteSingleExportTask(item.exportId)">删除</button>
                  </td>
                </tr>
                <tr v-if="!(exportTaskPage?.items || []).length">
                  <td colspan="7">暂无导出任务</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="table-wrap">
        <table class="task-table detail-table">
          <thead>
            <tr>
              <th>序号</th>
              <th>输入</th>
              <th>期望输出</th>
              <th>实际输出</th>
              <th>评测结果</th>
              <th>评测说明</th>
              <th>其他信息</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(record, idx) in runRecords" :key="record.qaId">
              <td>{{ idx + 1 }}</td>
              <td>{{ record.input }}</td>
              <td>{{ record.expectedOutput }}</td>
              <td>{{ record.actualOutput }}</td>
              <td>{{ record.errorCode ? '失败' : '通过' }}</td>
              <td>{{ record.errorMessage || '与预期一致' }}</td>
              <td>{{ record.tokenUsage || '-' }}</td>
            </tr>
            <tr v-if="!runRecords.length">
              <td colspan="7">请先启动任务并选择运行详情</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <section class="lower-grid">
      <article id="section-config" class="surface strategy-card">
        <div class="section-head">
          <h3>AI 评测配置</h3>
          <button class="ghost" @click="loadStrategyData">刷新策略</button>
        </div>

        <div class="form-layout">
          <label>
            新建策略名称
            <input v-model.trim="strategyForm.strategyName" type="text" placeholder="如：默认生产策略" />
          </label>
          <label>
            权重配置(JSON)
            <textarea v-model="strategyForm.weightConfig" rows="3" placeholder='{"effectiveness":0.5,"safety":0.2,"performance":0.3}' />
          </label>
          <label>
            门限配置(JSON)
            <textarea v-model="strategyForm.thresholdConfig" rows="3" placeholder='{"overallThreshold":0.75,"safetyMin":0.7}' />
          </label>
          <div class="inline-actions">
            <button class="ghost" @click="createStrategyAction">创建策略</button>
          </div>

          <label>
            选择策略
            <select v-model.number="strategyForm.selectedStrategyId">
              <option :value="0">请选择策略</option>
              <option v-for="item in strategies" :key="item.strategyId" :value="item.strategyId">
                {{ item.strategyName }} (latest={{ item.latestVersion || '-' }})
              </option>
            </select>
          </label>
          <label>
            新版本号(可选)
            <input v-model.number="strategyForm.newVersion" type="number" min="1" placeholder="留空自动递增" />
          </label>
          <div class="inline-actions">
            <button class="ghost" @click="createStrategyVersionAction">保存版本</button>
          </div>

          <label>
            自定义指标名称
            <input v-model.trim="metricForm.metricName" type="text" placeholder="如：响应稳定性" />
          </label>
          <label>
            指标类型
            <select v-model="metricForm.metricType">
              <option value="DETERMINISTIC">DETERMINISTIC</option>
              <option value="JUDGE">JUDGE</option>
            </select>
          </label>
          <label>
            评分逻辑
            <input v-model.trim="metricForm.scoringLogic" type="text" placeholder="completion / latency / performance" />
          </label>
          <label>
            阈值
            <input v-model.number="metricForm.thresholdValue" type="number" step="0.01" min="0" max="1" />
          </label>
          <div class="inline-actions">
            <button class="ghost" @click="createCustomMetricAction">注册自定义指标</button>
            <span>已启用指标: {{ customMetrics.filter((x) => x.enabled).length }}</span>
          </div>
        </div>
      </article>

      <div id="section-monitor">
        <ChatPanel
          :timeline="timeline"
          :traces="traceItems"
          :error-summary="runErrorSummary"
          @refresh="refreshCurrentRun"
        />
      </div>
    </section>

    <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
      <article class="create-modal">
        <div class="section-head">
          <h3>创建任务</h3>
          <button class="link-btn" @click="showCreate = false">关闭</button>
        </div>

        <div class="form-layout">
          <label>
            任务名称
            <input v-model.trim="createForm.taskName" type="text" placeholder="请输入测试任务名称" />
          </label>
          <label>
            Agent 版本
            <input v-model.trim="createForm.agentVersion" type="text" placeholder="如 1.0.0" />
          </label>
          <label>
            数据集 ID
            <input v-model.trim="createForm.datasetId" type="text" placeholder="如 dataset-trip-001" />
          </label>
          <label>
            评估模式
            <select v-model="createForm.evaluationMode">
              <option value="RESULT">RESULT</option>
              <option value="PROCESS">PROCESS</option>
            </select>
          </label>
          <label>
            评估方式
            <select v-model="createForm.evaluationMethod">
              <option value="DETERMINISTIC">DETERMINISTIC</option>
              <option value="JUDGE">JUDGE</option>
              <option value="HYBRID">HYBRID</option>
            </select>
          </label>
          <label>
            评估维度
            <input v-model.trim="createForm.evaluationDimensions" type="text" placeholder="effectiveness,safety,performance" />
          </label>
          <label>
            绑定策略版本 ID(可选)
            <input v-model.number="createForm.strategyVersion" type="number" min="1" />
          </label>
          <label>
            metricSet(可选)
            <input v-model.trim="createForm.metricSet" type="text" placeholder="例如 [1,2] 或 1,2" />
          </label>
        </div>

        <div class="modal-actions">
          <button class="ghost" @click="showCreate = false">取消</button>
          <button class="ghost" @click="createTaskOnly">保存</button>
          <button class="primary" @click="createAndStart">保存并立即执行</button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import ChatPanel from '../components/ChatPanel.vue';
import {
  batchDeleteExportTasks,
  compareTaskRuns,
  createRunCompareExportTask,
  createCustomMetric,
  createRunEventSource,
  createStrategy,
  createStrategyVersion,
  createTask,
  deleteExportTask,
  getExportTask,
  getRun,
  getRunMetrics,
  getRunRecords,
  listCustomMetrics,
  listExportTasks,
  listStrategies,
  listTaskRuns,
  listTasks,
  retryExportTask,
  startTask,
  type CustomMetric,
  type EvalRun,
  type EvalTask,
  type ExportTaskPageResult,
  type ExportTaskResult,
  type RunCompareResult,
  type RunMetrics,
  type TaskRunsPage,
  type TaskStatus,
} from '../api/client';

type TimelineItem = {
  time: string;
  title: string;
  detail: string;
};

type TraceItem = {
  tool: string;
  input: string;
  output: string;
  cost: string;
};

const showCreate = ref(false);
const noticeText = ref('');

const tasks = ref<EvalTask[]>([]);
const runRecords = ref<any[]>([]);
const runMetrics = ref<RunMetrics | null>(null);
const currentRun = ref<EvalRun | null>(null);

const strategies = ref<any[]>([]);
const customMetrics = ref<CustomMetric[]>([]);

const runByTask = reactive<Record<number, EvalRun>>({});
const metricsByTask = reactive<Record<number, RunMetrics>>({});
const runIdByTask = reactive<Record<number, number>>({});
const taskRunPages = reactive<Record<number, TaskRunsPage>>({});

const selectedRunId = ref<number | null>(null);
const selectedTaskId = ref<number | null>(null);
const runCompareResult = ref<RunCompareResult | null>(null);
const runErrorSummary = ref('');
const timeline = ref<TimelineItem[]>([]);
const traceItems = ref<TraceItem[]>([]);

const runHistoryFilter = reactive({
  status: '',
  page: 0,
  size: 5,
});

const runCompareForm = reactive({
  baselineRunId: null as number | null,
  targetRunId: null as number | null,
  manualBaselineRunId: null as number | null,
  manualTargetRunId: null as number | null,
});

const runCompareOptions = reactive({
  changedOnly: true,
});

const compareSort = reactive({
  mode: 'none' as 'none' | 'deltaDesc' | 'deltaAsc',
});

const exportForm = reactive({
  format: 'json' as 'json' | 'csv',
  task: null as ExportTaskResult | null,
});

const exportHistoryFilter = reactive({
  status: '' as '' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED',
  page: 0,
  size: 5,
});

const exportTaskPage = ref<ExportTaskPageResult | null>(null);
const selectedExportIds = ref<number[]>([]);

const currentTaskRunPage = computed(() => {
  if (!selectedTaskId.value) {
    return null;
  }
  return taskRunPages[selectedTaskId.value] || null;
});

let runStream: EventSource | null = null;
let exportPollTimer: ReturnType<typeof setTimeout> | null = null;
let filterDebounceTimer: ReturnType<typeof setTimeout> | null = null;

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
  strategyVersion: null as number | null,
});

const strategyForm = reactive({
  strategyName: '',
  selectedStrategyId: 0,
  newVersion: null as number | null,
  weightConfig: '{"effectiveness":0.5,"safety":0.2,"performance":0.3}',
  thresholdConfig: '{"overallThreshold":0.75,"safetyMin":0.7}',
});

const metricForm = reactive({
  metricName: '',
  metricType: 'DETERMINISTIC' as 'DETERMINISTIC' | 'JUDGE',
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

watch(
  () => filters.status,
  () => {
    void loadTasks();
  },
);

watch(
  () => filters.agentVersion,
  () => {
    if (filterDebounceTimer) {
      clearTimeout(filterDebounceTimer);
    }
    filterDebounceTimer = setTimeout(() => {
      void loadTasks();
    }, 300);
  },
);

watch(
  () => runHistoryFilter.status,
  () => {
    if (!selectedTaskId.value) {
      return;
    }
    void loadTaskRunPage(selectedTaskId.value, 0);
  },
);

watch(
  () => exportHistoryFilter.status,
  () => {
    if (!selectedTaskId.value) {
      return;
    }
    void loadExportTaskPage(0);
  },
);

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

function statusClass(status: TaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'failed';
  if (status === 'RUNNING') return 'running';
  return '';
}

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function appendTimeline(title: string, detail: string): void {
  timeline.value.unshift({
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    title,
    detail,
  });
  if (timeline.value.length > 30) {
    timeline.value = timeline.value.slice(0, 30);
  }
}

function closeRunStream(): void {
  if (runStream) {
    runStream.close();
    runStream = null;
  }
}

function connectRunStream(runId: number): void {
  closeRunStream();
  runStream = createRunEventSource(runId);

  const bind = (eventName: string, title: string) => {
    runStream?.addEventListener(eventName, (evt: MessageEvent) => {
      appendTimeline(title, evt.data);
      if (eventName === 'run_state') {
        try {
          const payload = JSON.parse(evt.data);
          const status = String(payload?.status || '').toUpperCase();
          if (status === 'SUCCEEDED' || status === 'FAILED') {
            refreshCurrentRun();
            loadTasks();
          }
        } catch {
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

function parseTraceItems(records: any[]): TraceItem[] {
  const output: TraceItem[] = [];
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
    } catch {
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

async function reloadAll(): Promise<void> {
  await Promise.all([loadTasks(), loadStrategyData()]);
}

async function loadTasks(): Promise<void> {
  try {
    tasks.value = await listTasks({
      status: filters.status || undefined,
      agentVersion: filters.agentVersion || undefined,
    });
    await syncTaskRunSnapshots();
    noticeText.value = `已加载 ${tasks.value.length} 个任务`;
  } catch (error: any) {
    noticeText.value = `加载任务失败: ${error.message || String(error)}`;
  }
}

async function syncTaskRunSnapshots(): Promise<void> {
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

  const results = await Promise.all(
    tasks.value.map(async (task) => {
      try {
        const page = await listTaskRuns(task.taskId, { page: 0, size: 1 });
        return { taskId: task.taskId, latestRun: page.items[0] || null, page };
      } catch {
        return { taskId: task.taskId, latestRun: null, page: null };
      }
    }),
  );

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

async function loadTaskRunPage(taskId: number, page = 0): Promise<TaskRunsPage | null> {
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
  } catch {
    noticeText.value = '加载任务运行历史失败';
    return null;
  }
}

async function loadExportTaskPage(page = 0): Promise<void> {
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
  } catch (error: any) {
    noticeText.value = `加载导出任务失败: ${error.message || String(error)}`;
  }
}

function toggleExportSelection(exportId: number): void {
  if (selectedExportIds.value.includes(exportId)) {
    selectedExportIds.value = selectedExportIds.value.filter((id) => id !== exportId);
  } else {
    selectedExportIds.value = [...selectedExportIds.value, exportId];
  }
}

function toggleSelectAllExports(): void {
  const pageIds = (exportTaskPage.value?.items || []).map((item) => item.exportId);
  if (!pageIds.length) {
    return;
  }
  const allSelected = pageIds.every((id) => selectedExportIds.value.includes(id));
  if (allSelected) {
    selectedExportIds.value = selectedExportIds.value.filter((id) => !pageIds.includes(id));
  } else {
    selectedExportIds.value = Array.from(new Set([...selectedExportIds.value, ...pageIds]));
  }
}

async function deleteSingleExportTask(exportId: number): Promise<void> {
  try {
    await deleteExportTask(exportId);
    selectedExportIds.value = selectedExportIds.value.filter((id) => id !== exportId);
    noticeText.value = `已删除导出任务 #${exportId}`;
    await loadExportTaskPage(exportHistoryFilter.page);
  } catch (error: any) {
    noticeText.value = `删除导出任务失败: ${error.message || String(error)}`;
  }
}

async function batchDeleteSelectedExports(): Promise<void> {
  if (!selectedExportIds.value.length) {
    return;
  }
  try {
    const result = await batchDeleteExportTasks(selectedExportIds.value);
    noticeText.value = `批量删除完成：成功 ${result.deleted}，失败 ${result.failed}`;
    selectedExportIds.value = [];
    await loadExportTaskPage(exportHistoryFilter.page);
  } catch (error: any) {
    noticeText.value = `批量删除失败: ${error.message || String(error)}`;
  }
}

async function changeExportPage(nextPage: number): Promise<void> {
  if (nextPage < 0) {
    return;
  }
  await loadExportTaskPage(nextPage);
}

function resetFilters(): void {
  filters.status = '';
  filters.agentVersion = '';
  filters.keyword = '';
  loadTasks();
}

async function createTaskOnly(): Promise<EvalTask | null> {
  try {
    const created = await createTask({
      taskName: createForm.taskName,
      agentVersion: createForm.agentVersion,
      datasetId: createForm.datasetId,
      metricSet: createForm.metricSet || undefined,
      evaluationMode: createForm.evaluationMode as any,
      evaluationMethod: createForm.evaluationMethod as any,
      evaluationDimensions: createForm.evaluationDimensions,
      strategyVersion: createForm.strategyVersion || undefined,
    });
    showCreate.value = false;
    noticeText.value = `任务创建成功: ${created.taskName}`;
    await loadTasks();
    return created;
  } catch (error: any) {
    noticeText.value = `创建任务失败: ${error.message || String(error)}`;
    return null;
  }
}

async function createAndStart(): Promise<void> {
  const created = await createTaskOnly();
  if (created) {
    await startRun(created.taskId);
  }
}

async function startRun(taskId: number): Promise<void> {
  try {
    const run = await startTask(taskId);
    selectedRunId.value = run.runId;
    runIdByTask[taskId] = run.runId;
    runByTask[taskId] = run;
    noticeText.value = `任务已启动，runId=${run.runId}`;
    connectRunStream(run.runId);
    await refreshCurrentRun();
  } catch (error: any) {
    noticeText.value = `启动任务失败: ${error.message || String(error)}`;
  }
}

async function viewRunDetail(taskId: number): Promise<void> {
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

async function applyRunHistoryFilter(): Promise<void> {
  if (!selectedTaskId.value) {
    noticeText.value = '请先选择一个任务详情';
    return;
  }
  await loadTaskRunPage(selectedTaskId.value, 0);
}

async function changeRunHistoryPage(nextPage: number): Promise<void> {
  if (!selectedTaskId.value) {
    return;
  }
  if (nextPage < 0) {
    return;
  }
  await loadTaskRunPage(selectedTaskId.value, nextPage);
}

async function openHistoryRun(run: EvalRun): Promise<void> {
  selectedTaskId.value = run.taskId;
  selectedRunId.value = run.runId;
  runByTask[run.taskId] = run;
  runIdByTask[run.taskId] = run.runId;
  connectRunStream(run.runId);
  await refreshCurrentRun();
}

function resolveCompareIds(): { baselineRunId: number | null; targetRunId: number | null } {
  return {
    baselineRunId: runCompareForm.manualBaselineRunId || runCompareForm.baselineRunId,
    targetRunId: runCompareForm.manualTargetRunId || runCompareForm.targetRunId,
  };
}

async function runCompareAction(): Promise<void> {
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
    runCompareResult.value = await compareTaskRuns(
      selectedTaskId.value,
      baselineRunId,
      targetRunId,
      runCompareOptions.changedOnly,
    );
    exportForm.task = null;
    noticeText.value = `对比完成：变化样本 ${runCompareResult.value.changedSamples}/${runCompareResult.value.totalSamples}`;
  } catch (error: any) {
    noticeText.value = `运行对比失败: ${error.message || String(error)}`;
  }
}

async function createExportTaskAction(): Promise<void> {
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
    const task = await createRunCompareExportTask(
      selectedTaskId.value,
      baselineRunId,
      targetRunId,
      runCompareOptions.changedOnly,
      exportForm.format,
    );
    exportForm.task = task;
    noticeText.value = `导出任务已创建: #${task.exportId}`;
    await loadExportTaskPage(0);
    await pollExportTask(task.exportId, 0);
  } catch (error: any) {
    noticeText.value = `创建导出任务失败: ${error.message || String(error)}`;
  }
}

async function retryFailedExportTask(exportId: number): Promise<void> {
  try {
    const task = await retryExportTask(exportId);
    exportForm.task = task;
    noticeText.value = `导出任务已重试: #${task.exportId}`;
    await loadExportTaskPage(exportHistoryFilter.page);
    await pollExportTask(task.exportId, 0);
  } catch (error: any) {
    noticeText.value = `重试导出任务失败: ${error.message || String(error)}`;
  }
}

async function pollExportTask(exportId: number, round: number): Promise<void> {
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
  } catch (error: any) {
    noticeText.value = `查询导出状态失败: ${error.message || String(error)}`;
  }
}

function downloadExportFile(downloadUrl: string): void {
  window.open(`${window.location.origin}${downloadUrl}`, '_blank');
}

function deltaClass(delta: number | null): string {
  if (delta == null || delta === 0) {
    return '';
  }
  return delta > 0 ? 'delta-up' : 'delta-down';
}

async function refreshCurrentRun(): Promise<void> {
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

    if (run.status !== 'SUCCEEDED') {
      runMetrics.value = null;
      delete metricsByTask[run.taskId];
      return;
    }

    try {
      const metrics = await getRunMetrics(runId);
      runMetrics.value = metrics;
      metricsByTask[run.taskId] = metrics;
    } catch {
      runMetrics.value = null;
    }
  } catch (error: any) {
    noticeText.value = `刷新运行失败: ${error.message || String(error)}`;
  }
}

async function loadStrategyData(): Promise<void> {
  try {
    const [strategyList, metricList] = await Promise.all([
      listStrategies(),
      listCustomMetrics(),
    ]);
    strategies.value = strategyList;
    customMetrics.value = metricList;
  } catch (error: any) {
    noticeText.value = `加载策略配置失败: ${error.message || String(error)}`;
  }
}

async function createStrategyAction(): Promise<void> {
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
  } catch (error: any) {
    noticeText.value = `创建策略失败: ${error.message || String(error)}`;
  }
}

async function createStrategyVersionAction(): Promise<void> {
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
  } catch (error: any) {
    noticeText.value = `保存策略版本失败: ${error.message || String(error)}`;
  }
}

async function createCustomMetricAction(): Promise<void> {
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
  } catch (error: any) {
    noticeText.value = `注册自定义指标失败: ${error.message || String(error)}`;
  }
}

function scrollToSection(section: 'overview' | 'tasks' | 'config' | 'detail' | 'monitor'): void {
  const map: Record<typeof section, string> = {
    overview: 'section-overview',
    tasks: 'section-tasks',
    config: 'section-config',
    detail: 'section-detail',
    monitor: 'section-monitor',
  };
  const target = document.getElementById(map[section]);
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function openCreateDialog(): void {
  showCreate.value = true;
}

async function reloadDashboard(): Promise<void> {
  await reloadAll();
}

defineExpose({
  scrollToSection,
  openCreateDialog,
  reloadDashboard,
});
</script>
