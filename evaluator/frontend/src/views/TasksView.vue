<template>
  <section class="dashboard-page">
    <article class="surface intro-card">
      <div>
        <h2>评测任务</h2>
        <p>创建评测任务、启动、查看运行历史。BT 多模型评测请至少选择 2 个参赛模型和 1 个裁判模型。</p>
      </div>
      <div class="chip-row">
        <span class="chip">结果导向</span>
        <span class="chip">过程导向</span>
        <span class="chip">显式指标 + 模糊评估</span>
        <span class="chip">BT 多模型评测</span>
      </div>
    </article>

    <article class="surface task-board">
      <div class="section-head">
        <h3>评测任务列表</h3>
        <div class="inline-actions">
          <button class="ghost" @click="reloadAll">刷新数据</button>
          <button class="ghost compare-trigger" @click="openCompareSelect">创建对比</button>
          <button class="primary" @click="openCreateTask()">创建任务</button>
        </div>
      </div>

      <div class="filter-bar">
        <select v-model="filters.status">
          <option value="">任务状态</option>
          <option value="READY">就绪</option>
          <option value="RUNNING">运行中</option>
          <option value="SUCCEEDED">成功</option>
          <option value="FAILED">失败</option>
        </select>
        <input v-model.trim="filters.agentVersion" type="text" placeholder="应用版本" />
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
              <th>BT 模式</th>
              <th>应用版本</th>
              <th>数据集</th>
              <th>状态</th>
              <th>样本数</th>
              <th>令牌数</th>
              <th>创建时间</th>
              <th>完成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in filteredTasks" :key="task.taskId">
              <td>{{ task.taskName }}</td>
              <td>{{ evaluationMethodLabel(task.evaluationMethod) }}</td>
              <td>
                <span v-if="task.selectedModelIds && task.selectedModelIds.length >= 2" class="chip">
                  BT × {{ task.selectedModelIds.length }}
                </span>
                <span v-else>-</span>
              </td>
              <td>{{ task.agentVersion }}</td>
              <td class="mono small">{{ task.datasetId }}</td>
              <td><span class="status" :class="statusClass(task.status)">{{ statusLabel(task.status) }}</span></td>
              <td>{{ runByTask[task.taskId]?.totalCount ?? '-' }}</td>
              <td>{{ metricsByTask[task.taskId]?.totalTokens ?? '-' }}</td>
              <td>{{ formatTime(task.createdAt) }}</td>
              <td>{{ formatTime(runByTask[task.taskId]?.endTime) }}</td>
              <td class="actions-cell">
                <div class="table-actions">
                  <button class="link-btn" @click="goToRunDetail(task.taskId)">详情</button>
                  <button
                    v-if="isTaskRunning(task)"
                    class="link-btn danger-link"
                    :disabled="cancelingTaskIds.has(task.taskId)"
                    @click="cancelRunningTask(task)"
                  >
                    {{ cancelingTaskIds.has(task.taskId) ? '取消中..' : '取消' }}
                  </button>
                  <template v-else>
                    <button class="link-btn" @click="startRun(task.taskId)">启动</button>
                    <button class="link-btn danger-link" @click="confirmDelete(task)">删除</button>
                  </template>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredTasks.length">
              <td colspan="11">暂无任务数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <div v-if="compareDrawerOpen" class="modal-mask compare-mask" @click="closeCompareDrawer">
      <article class="compare-drawer" @click.stop>
        <header class="cp-head">
          <div>
            <span class="cp-kicker">{{ compareStep === 1 ? '创建对比' : '对比结果' }}</span>
            <h3>{{ compareStep === 1 ? '开始对比' : '确定性评测对比结果' }}</h3>
            <p class="cp-sub">
              {{
                compareStep === 1
                  ? '请选择两个确定性评测结果中的模型，然后进入结果对比。'
                  : '按维度折叠查看差异；展开后用柱状图对比指标。'
              }}
            </p>
          </div>
          <button type="button" class="ghost icon-close" @click="closeCompareDrawer">×</button>
        </header>

        <div v-if="compareStep === 1" class="cp-body">
          <p class="mode-kicker">第 1 步 / 共 2 步</p>
          <h4>选择两个确定性评测结果中的模型</h4>
          <p class="mode-desc">候选来自任务列表中已完成的确定性单模型运行结果。</p>

          <div v-if="compareCandidates.length" class="compare-pick-grid">
            <label>
              左侧模型
              <select v-model.number="compareLeftRunId">
                <option :value="0">请选择左侧结果</option>
                <option v-for="item in compareCandidates" :key="`left-${item.runId}`" :value="item.runId">
                  {{ formatCandidateLabel(item) }}
                </option>
              </select>
            </label>

            <label>
              右侧模型
              <select v-model.number="compareRightRunId">
                <option :value="0">请选择右侧结果</option>
                <option v-for="item in compareCandidates" :key="`right-${item.runId}`" :value="item.runId">
                  {{ formatCandidateLabel(item) }}
                </option>
              </select>
            </label>
          </div>

          <p v-if="compareNotice" class="notice-text">{{ compareNotice }}</p>
          <p v-else-if="compareCandidatesLoading" class="notice-text">正在加载确定性候选...</p>
        </div>

        <div v-else-if="compareView.left && compareView.right" class="cp-body result-scroll">
          <p class="mode-kicker">第 2 步 / 共 2 步</p>
          <article class="compare-headline">
            <h4>{{ compareView.left.modelTitle }} 对比 {{ compareView.right.modelTitle }}</h4>
            <div class="compare-headline-grid">
              <p><strong>{{ compareView.left.shortTitle }}</strong> · 数据集：{{ compareView.left.datasetId }}</p>
              <p><strong>{{ compareView.right.shortTitle }}</strong> · 数据集：{{ compareView.right.datasetId }}</p>
            </div>
            <p class="compare-meta">左侧运行#{{ compareView.left.runId }} · 右侧运行#{{ compareView.right.runId }}</p>
          </article>

          <details class="compare-block">
            <summary>质量维度（效果 / 安全 / 性能）</summary>
            <div class="metric-list">
              <article v-for="row in compareView.qualityRows" :key="row.key" class="metric-card">
                <div class="metric-top">
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.delta }}</span>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.left.shortTitle }} · {{ row.left }}</label>
                  <div class="metric-track"><span class="metric-fill left" :style="{ width: `${metricBarWidth(row, 'left')}%` }"></span></div>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.right.shortTitle }} · {{ row.right }}</label>
                  <div class="metric-track"><span class="metric-fill right" :style="{ width: `${metricBarWidth(row, 'right')}%` }"></span></div>
                </div>
              </article>
            </div>
          </details>

          <details class="compare-block">
            <summary>性能维度（P95 延迟 / 令牌）</summary>
            <div class="metric-list">
              <article v-for="row in compareView.performanceRows" :key="row.key" class="metric-card">
                <div class="metric-top">
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.delta }}</span>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.left.shortTitle }} · {{ row.left }}</label>
                  <div class="metric-track"><span class="metric-fill left" :style="{ width: `${metricBarWidth(row, 'left')}%` }"></span></div>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.right.shortTitle }} · {{ row.right }}</label>
                  <div class="metric-track"><span class="metric-fill right" :style="{ width: `${metricBarWidth(row, 'right')}%` }"></span></div>
                </div>
              </article>
            </div>
          </details>

          <details class="compare-block">
            <summary>结果维度（通过率 / 成功数 / 失败数）</summary>
            <div class="metric-list">
              <article v-for="row in compareView.outcomeRows" :key="row.key" class="metric-card">
                <div class="metric-top">
                  <strong>{{ row.label }}</strong>
                  <span>{{ row.delta }}</span>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.left.shortTitle }} · {{ row.left }}</label>
                  <div class="metric-track"><span class="metric-fill left" :style="{ width: `${metricBarWidth(row, 'left')}%` }"></span></div>
                </div>
                <div class="metric-line">
                  <label>{{ compareView.right.shortTitle }} · {{ row.right }}</label>
                  <div class="metric-track"><span class="metric-fill right" :style="{ width: `${metricBarWidth(row, 'right')}%` }"></span></div>
                </div>
              </article>
            </div>
          </details>

          <details class="compare-block">
            <summary>样本差异概览</summary>
            <p class="compare-summary">
              总样本 {{ compareView.sampleSummary.total }}，输出或错误信息有差异 {{ compareView.sampleSummary.changed }}
              （{{ compareView.sampleSummary.changedRate }}）
            </p>
            <div class="table-wrap">
              <table class="task-table compare-table">
                <thead>
                  <tr>
                    <th>样本</th>
                    <th>输入</th>
                    <th>{{ compareView.left.shortTitle }}</th>
                    <th>{{ compareView.right.shortTitle }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in compareView.sampleSummary.preview" :key="item.index">
                    <td>{{ item.index }}</td>
                    <td>{{ item.input }}</td>
                    <td>{{ item.left }}</td>
                    <td>{{ item.right }}</td>
                  </tr>
                  <tr v-if="!compareView.sampleSummary.preview.length">
                    <td colspan="4">无差异样本（或两侧样本为空）</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </details>
        </div>
        <div v-else class="cp-body">
          <p class="notice-text">暂无可展示的对比结果，请返回上一步重新选择。</p>
        </div>

        <footer class="cp-foot">
          <template v-if="compareStep === 1">
            <button type="button" class="ghost" @click="closeCompareDrawer">取消</button>
            <button
              type="button"
              class="primary"
              :disabled="compareLoading || compareCandidatesLoading || !compareCandidates.length"
              @click="createCompareFromSelection"
            >
              {{ compareLoading ? '生成中...' : '下一步：查看对比结果' }}
            </button>
          </template>
          <template v-else>
            <button type="button" class="ghost" @click="openCompareSelectFromResult">返回重新选择</button>
            <button type="button" class="primary" @click="closeCompareDrawer">完成</button>
          </template>
        </footer>
      </article>
    </div>

  </section>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  cancelTaskRun,
  getRun,
  getRunMetrics,
  getRunRecords,
  deleteTask,
  listModels,
  listTaskRuns,
  listTasks,
  startTask,
  type EvalRun,
  type EvalTask,
  type ModelProfile,
  type QaRecord,
  type RunMetrics,
  type TaskStatus,
} from '../api/client';

type DeterministicRunData = {
  metrics: RunMetrics | null;
  records: QaRecord[];
  successCount: number;
  failCount: number;
};

type DeterministicCandidate = {
  taskId: number;
  taskName: string;
  runId: number;
  datasetId: string;
  modelProfileId: number | null;
  modelTitle: string;
  shortTitle: string;
  finishedAt: string | null;
  compareData?: DeterministicRunData;
};

type CompareRow = {
  key: string;
  label: string;
  left: string;
  right: string;
  delta: string;
  leftRaw: number | null;
  rightRaw: number | null;
};

type CompareSamplePreview = {
  index: number;
  input: string;
  left: string;
  right: string;
};

const router = useRouter();

const openCreateTask = inject<(preset?: string) => void>('openCreateTask', () => {});

const tasks = ref<EvalTask[]>([]);
const models = ref<ModelProfile[]>([]);
const runByTask = reactive<Record<number, EvalRun>>({});
const metricsByTask = reactive<Record<number, RunMetrics>>({});
const runIdByTask = reactive<Record<number, number>>({});
const noticeText = ref('');
const cancelingTaskIds = ref(new Set<number>());
const compareDrawerOpen = ref(false);
const compareStep = ref<1 | 2>(1);
const compareCandidates = ref<DeterministicCandidate[]>([]);
const compareLeftRunId = ref(0);
const compareRightRunId = ref(0);
const compareCandidatesLoading = ref(false);
const compareLoading = ref(false);
const compareNotice = ref('');

const filters = reactive({
  status: '',
  agentVersion: '',
  keyword: '',
});

const filteredTasks = computed(() => {
  if (!filters.keyword) return tasks.value;
  const key = filters.keyword.toLowerCase();
  return tasks.value.filter((item) => item.taskName.toLowerCase().includes(key));
});

const compareCandidateMap = computed(() => {
  const map = new Map<number, DeterministicCandidate>();
  for (const item of compareCandidates.value) {
    map.set(item.runId, item);
  }
  return map;
});

const compareView = computed(() => {
  const left = compareCandidateMap.value.get(compareLeftRunId.value) ?? null;
  const right = compareCandidateMap.value.get(compareRightRunId.value) ?? null;
  if (!left || !right || !left.compareData || !right.compareData) {
    return {
      left: null as DeterministicCandidate | null,
      right: null as DeterministicCandidate | null,
      qualityRows: [] as CompareRow[],
      performanceRows: [] as CompareRow[],
      outcomeRows: [] as CompareRow[],
      sampleSummary: {
        total: 0,
        changed: 0,
        changedRate: '0.0%',
        preview: [] as CompareSamplePreview[],
      },
    };
  }

  const qualityRows: CompareRow[] = [
    formatCompareRow('effectiveness', 'Efficiency', left.compareData.metrics?.effectivenessScore, right.compareData.metrics?.effectivenessScore, true, true),
    formatCompareRow('safety', 'Safety', left.compareData.metrics?.safetyScore, right.compareData.metrics?.safetyScore, true, true),
    formatCompareRow('performance', 'Performance', left.compareData.metrics?.performanceScore, right.compareData.metrics?.performanceScore, true, true),
  ];
  const performanceRows: CompareRow[] = [
    formatCompareRow('first_token', 'P95 首字延迟', left.compareData.metrics?.firstTokenP95, right.compareData.metrics?.firstTokenP95, false, false, 'ms'),
    formatCompareRow('end_to_end', 'P95 端到端延迟', left.compareData.metrics?.endToEndP95, right.compareData.metrics?.endToEndP95, false, false, 'ms'),
    formatCompareRow('tokens', 'Token消耗', left.compareData.metrics?.totalTokens, right.compareData.metrics?.totalTokens, false, false),
  ];
  const outcomeRows: CompareRow[] = [
    formatCompareRow('completion', '完成率', left.compareData.metrics?.taskCompletionRate, right.compareData.metrics?.taskCompletionRate, true, true),
    formatCompareRow('success', '成功样本', left.compareData.successCount, right.compareData.successCount, false, true),
    formatCompareRow('failed', '失败样本', left.compareData.failCount, right.compareData.failCount, false, false),
  ];

  return {
    left,
    right,
    qualityRows,
    performanceRows,
    outcomeRows,
    sampleSummary: summarizeSampleDiff(left.compareData.records, right.compareData.records),
  };
});

let filterDebounceTimer: ReturnType<typeof setTimeout> | null = null;
watch(() => filters.status, () => void loadTasks());
watch(() => filters.agentVersion, () => {
  if (filterDebounceTimer) clearTimeout(filterDebounceTimer);
  filterDebounceTimer = setTimeout(() => void loadTasks(), 300);
});
watch(compareDrawerOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : '';
});

function onTasksReload(): void {
  void reloadAll();
}

onMounted(async () => {
  window.addEventListener('tripagent-tasks-reload', onTasksReload);
  await reloadAll();
});

onBeforeUnmount(() => {
  window.removeEventListener('tripagent-tasks-reload', onTasksReload);
  document.body.style.overflow = '';
});

async function reloadAll(): Promise<void> {
  void loadTasks();
}

async function loadTasks(): Promise<void> {
  try {
    tasks.value = await listTasks({
      status: filters.status || undefined,
      agentVersion: filters.agentVersion || undefined,
    });
    await syncTaskRunSnapshots();
    noticeText.value = `已加载 ${tasks.value.length} 个任务`;
  } catch (err: any) {
    noticeText.value = `加载任务失败：${err.message || String(err)}`;
  }
}

async function syncTaskRunSnapshots(): Promise<void> {
  const taskIds = new Set(tasks.value.map((t) => t.taskId));
  Object.keys(runByTask).forEach((key) => {
    const id = Number(key);
    if (!taskIds.has(id)) {
      delete runByTask[id];
      delete runIdByTask[id];
      delete metricsByTask[id];
    }
  });

  const results = await Promise.all(
    tasks.value.map(async (task) => {
      try {
        const page = await listTaskRuns(task.taskId, { page: 0, size: 1 });
        return { taskId: task.taskId, latestRun: page.items[0] || null };
      } catch {
        return { taskId: task.taskId, latestRun: null };
      }
    }),
  );

  results.forEach(({ taskId, latestRun }) => {
    if (latestRun) {
      runByTask[taskId] = latestRun;
      runIdByTask[taskId] = latestRun.runId;
    } else {
      delete runByTask[taskId];
      delete runIdByTask[taskId];
    }
  });
}

function resetFilters(): void {
  filters.status = '';
  filters.agentVersion = '';
  filters.keyword = '';
  void loadTasks();
}

function openCompareSelect(): void {
  compareDrawerOpen.value = true;
  compareStep.value = 1;
  compareNotice.value = '';
  void loadDeterministicCompareCandidates();
}

function closeCompareDrawer(): void {
  if (compareLoading.value) return;
  compareDrawerOpen.value = false;
  compareStep.value = 1;
}

function openCompareSelectFromResult(): void {
  compareStep.value = 1;
  if (!compareCandidates.value.length) {
    void loadDeterministicCompareCandidates();
  }
}

async function loadDeterministicCompareCandidates(): Promise<void> {
  compareCandidatesLoading.value = true;
  compareNotice.value = '正在加载确定性候选...';
  try {
    const [allTasks, modelList] = await Promise.all([
      listTasks(),
      models.value.length ? Promise.resolve(models.value) : listModels().catch(() => [] as ModelProfile[]),
    ]);
    if (!models.value.length && modelList.length) {
      models.value = modelList;
    }
    const modelMap = new Map(modelList.map((m) => [m.modelProfileId, m]));
    const deterministicTasks = allTasks.filter((t) =>
      t.evaluationMethod === 'DETERMINISTIC' && (t.selectedModelIds?.length ?? 0) === 1 && t.judgeModelId == null,
    );
    const groups = await Promise.all(
      deterministicTasks.map(async (t) => {
        const page = await listTaskRuns(t.taskId, { status: 'SUCCEEDED', page: 0, size: 20 }).catch(() => null);
        const runs = page?.items ?? [];
        if (!runs.length) return [] as DeterministicCandidate[];
        const modelProfileId = t.selectedModelIds?.[0] ?? null;
        const profile = modelProfileId != null ? modelMap.get(modelProfileId) ?? null : null;
        const modelTitle = profile
          ? `${profile.displayName || shortModelTitle(profile.modelId)} (${profile.modelId})`
          : `模型#${modelProfileId ?? '未知'}`;
        const shortTitle = shortModelTitle(profile?.modelId || modelTitle);
        return runs.map((run) => ({
          taskId: t.taskId,
          taskName: t.taskName,
          runId: run.runId,
          datasetId: t.datasetId,
          modelProfileId,
          modelTitle,
          shortTitle,
          finishedAt: run.endTime,
        }));
      }),
    );

    compareCandidates.value = groups
      .flat()
      .sort((a, b) => {
        const bTime = b.finishedAt ? Date.parse(b.finishedAt) : 0;
        const aTime = a.finishedAt ? Date.parse(a.finishedAt) : 0;
        return (Number.isFinite(bTime) ? bTime : 0) - (Number.isFinite(aTime) ? aTime : 0) || b.runId - a.runId;
      });

    if (!compareCandidates.value.length) {
      compareNotice.value = '没有可用于对比的确定性成功结果';
      compareLeftRunId.value = 0;
      compareRightRunId.value = 0;
      return;
    }
    if (!compareCandidateMap.value.has(compareLeftRunId.value)) {
      compareLeftRunId.value = compareCandidates.value[0].runId;
    }
    if (!compareCandidateMap.value.has(compareRightRunId.value) || compareRightRunId.value === compareLeftRunId.value) {
      const left = compareCandidateMap.value.get(compareLeftRunId.value);
      const another = compareCandidates.value.find((item) => !left || !isSameModelCandidate(left, item));
      compareRightRunId.value = another?.runId && another.runId !== compareLeftRunId.value ? another.runId : 0;
    }
    compareNotice.value = `已加载 ${compareCandidates.value.length} 条确定性结果`;
  } catch (err: any) {
    compareNotice.value = `加载确定性候选失败: ${err.message || String(err)}`;
  } finally {
    compareCandidatesLoading.value = false;
  }
}

async function createCompareFromSelection(): Promise<void> {
  if (!compareLeftRunId.value || !compareRightRunId.value) {
    compareNotice.value = '请先选择两条确定性结果';
    return;
  }
  if (compareLeftRunId.value === compareRightRunId.value) {
    compareNotice.value = '左右两侧不能选择同一个结果';
    return;
  }
  const left = compareCandidateMap.value.get(compareLeftRunId.value);
  const right = compareCandidateMap.value.get(compareRightRunId.value);
  if (!left || !right) {
    compareNotice.value = '选择无效，请重新选择';
    return;
  }
  if (isSameModelCandidate(left, right)) {
    compareNotice.value = '请选两个不同模型的确定性结果';
    return;
  }
  compareLoading.value = true;
  compareNotice.value = '正在生成对比结果...';
  try {
    const [leftData, rightData] = await Promise.all([
      loadDeterministicRunData(left.runId),
      loadDeterministicRunData(right.runId),
    ]);
    compareCandidates.value = compareCandidates.value.map((item) => {
      if (item.runId === left.runId) return { ...item, compareData: leftData };
      if (item.runId === right.runId) return { ...item, compareData: rightData };
      return item;
    });
    compareStep.value = 2;
    compareNotice.value = `对比完成：${left.shortTitle} vs ${right.shortTitle}`;
  } catch (err: any) {
    compareNotice.value = `生成对比失败: ${err.message || String(err)}`;
  } finally {
    compareLoading.value = false;
  }
}

async function loadDeterministicRunData(runId: number): Promise<DeterministicRunData> {
  const [run, records, metrics] = await Promise.all([
    getRun(runId),
    getRunRecords(runId),
    getRunMetrics(runId).catch(() => null as RunMetrics | null),
  ]);
  return {
    metrics,
    records,
    successCount: run.successCount ?? records.filter((x) => !x.errorCode).length,
    failCount: run.failCount ?? records.filter((x) => !!x.errorCode).length,
  };
}

async function startRun(taskId: number): Promise<void> {
  try {
    const run = await startTask(taskId);
    runByTask[taskId] = run;
    runIdByTask[taskId] = run.runId;
    noticeText.value = `任务已启动，运行ID=${run.runId}，正在跳转到运行详情...`;
    router.push({ name: 'RunDetail', params: { runId: run.runId } });
  } catch (err: any) {
    noticeText.value = `启动任务失败：${err.message || String(err)}`;
  }
}

function isTaskRunning(task: EvalTask): boolean {
  return task.status === 'RUNNING' || runByTask[task.taskId]?.status === 'RUNNING';
}

async function cancelRunningTask(task: EvalTask): Promise<void> {
  if (!isTaskRunning(task)) {
    noticeText.value = `任务 #${task.taskId} 当前不是运行中`;
    return;
  }
  const ok = window.confirm(`确认取消运行中的任务「${task.taskName}」吗？`);
  if (!ok) return;
  cancelingTaskIds.value.add(task.taskId);
  try {
    const run = await cancelTaskRun(task.taskId);
    noticeText.value = `已发送取消请求，运行ID=${run.runId}，请等待状态刷新为“失败”`;
    await loadTasks();
  } catch (err: any) {
    noticeText.value = `取消失败：${err.message || String(err)}`;
  } finally {
    cancelingTaskIds.value.delete(task.taskId);
  }
}

async function confirmDelete(task: EvalTask): Promise<void> {
  if (isTaskRunning(task)) {
    noticeText.value = `任务 #${task.taskId} 正在运行,无法删除`;
    return;
  }
  const ok = window.confirm(
    `确定要删除任务「${task.taskName}」吗?\n会同时清空该任务的所有运行记录、对比、评分与样本数据,且不可恢复。`,
  );
  if (!ok) return;
  try {
    await deleteTask(task.taskId);
    noticeText.value = `已删除任务 #${task.taskId}`;
    delete runByTask[task.taskId];
    delete runIdByTask[task.taskId];
    delete metricsByTask[task.taskId];
    await loadTasks();
  } catch (err: any) {
    noticeText.value = `删除失败：${err.message || String(err)}`;
  }
}

async function goToRunDetail(taskId: number): Promise<void> {
  let runId = runIdByTask[taskId];
  if (!runId) {
    try {
      const page = await listTaskRuns(taskId, { page: 0, size: 1 });
      runId = page.items[0]?.runId;
    } catch {
      // ignore
    }
  }
  if (!runId) {
    noticeText.value = '该任务暂无运行记录，请先点击启动';
    return;
  }
  router.push({ name: 'RunDetail', params: { runId } });
}

function isSameModelCandidate(a: DeterministicCandidate, b: DeterministicCandidate): boolean {
  if (a.modelProfileId != null && b.modelProfileId != null) {
    return a.modelProfileId === b.modelProfileId;
  }
  return a.modelTitle.trim().toLowerCase() === b.modelTitle.trim().toLowerCase();
}

function shortModelTitle(full: string): string {
  const normalized = full.trim();
  if (!normalized) return '未知';
  const fromParen = normalized.split('(')[0].trim();
  const slashParts = fromParen.split('/');
  return slashParts[slashParts.length - 1] || fromParen;
}

function formatCandidateLabel(item: DeterministicCandidate): string {
  const when = formatTime(item.finishedAt);
  return `${item.shortTitle} | ${item.datasetId} | ${item.taskName} | 运行#${item.runId} | ${when}`;
}

function formatCompareRow(
  key: string,
  label: string,
  leftValue: number | null | undefined,
  rightValue: number | null | undefined,
  asPercent: boolean,
  higherBetter: boolean,
  unit = '',
): CompareRow {
  const left = typeof leftValue === 'number' && Number.isFinite(leftValue) ? leftValue : null;
  const right = typeof rightValue === 'number' && Number.isFinite(rightValue) ? rightValue : null;
  const leftText = left == null ? '-' : asPercent ? `${(left * 100).toFixed(1)}%` : `${Math.round(left * 10) / 10}${unit}`;
  const rightText = right == null ? '-' : asPercent ? `${(right * 100).toFixed(1)}%` : `${Math.round(right * 10) / 10}${unit}`;
  if (left == null || right == null) {
    return { key, label, left: leftText, right: rightText, delta: '-', leftRaw: left, rightRaw: right };
  }
  const delta = right - left;
  const arrow = higherBetter ? (delta >= 0 ? '↑' : '↓') : (delta <= 0 ? '↑' : '↓');
  const deltaText = asPercent
    ? `${arrow} ${(delta * 100).toFixed(1)}%`
    : `${arrow} ${Math.round(delta * 10) / 10}${unit}`;
  return { key, label, left: leftText, right: rightText, delta: deltaText, leftRaw: left, rightRaw: right };
}

function metricBarWidth(row: CompareRow, side: 'left' | 'right'): number {
  const value = side === 'left' ? row.leftRaw : row.rightRaw;
  if (value == null || !Number.isFinite(value)) return 0;
  const left = row.leftRaw != null && Number.isFinite(row.leftRaw) ? Math.abs(row.leftRaw) : 0;
  const right = row.rightRaw != null && Number.isFinite(row.rightRaw) ? Math.abs(row.rightRaw) : 0;
  const base = Math.max(left, right, 0.000001);
  const pct = (Math.abs(value) / base) * 100;
  return Math.max(10, Math.min(100, Math.round(pct * 10) / 10));
}

function summarizeSampleDiff(left: QaRecord[], right: QaRecord[]) {
  const total = Math.max(left.length, right.length);
  if (total <= 0) {
    return { total: 0, changed: 0, changedRate: '0.0%', preview: [] as CompareSamplePreview[] };
  }
  const preview: CompareSamplePreview[] = [];
  let changed = 0;
  for (let i = 0; i < total; i++) {
    const l = left[i];
    const r = right[i];
    const lSign = l
      ? `${(l.actualOutput || '').trim()}|${(l.errorCode || '').trim()}|${(l.errorMessage || '').trim()}`
      : '__missing__';
    const rSign = r
      ? `${(r.actualOutput || '').trim()}|${(r.errorCode || '').trim()}|${(r.errorMessage || '').trim()}`
      : '__missing__';
    if (lSign !== rSign) {
      changed++;
      if (preview.length < 12) {
        preview.push({
          index: i + 1,
          input: truncate(l?.input || r?.input || '-', 48),
          left: l ? truncate((l.errorMessage || l.actualOutput || '-').replace(/\s+/g, ' '), 80) : '（缺失）',
          right: r ? truncate((r.errorMessage || r.actualOutput || '-').replace(/\s+/g, ' '), 80) : '（缺失）',
        });
      }
    }
  }
  return {
    total,
    changed,
    changedRate: `${((changed / total) * 100).toFixed(1)}%`,
    preview,
  };
}

function truncate(text: string, max: number): string {
  if (!text) return '-';
  return text.length <= max ? text : text.slice(0, max) + '...';
}

function statusClass(status: TaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'failed';
  if (status === 'RUNNING') return 'running';
  return '';
}

function statusLabel(status?: string | null): string {
  if (!status) return '-';
  if (status === 'READY') return '就绪';
  if (status === 'RUNNING') return '运行中';
  if (status === 'SUCCEEDED') return '成功';
  if (status === 'FAILED') return '失败';
  return status;
}

function evaluationMethodLabel(method?: string | null): string {
  if (!method) return '-';
  if (method === 'DETERMINISTIC') return '确定性评测';
  if (method === 'JUDGE') return '裁判评测';
  if (method === 'HYBRID') return '混合评测';
  return method;
}

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}
</script>

<style scoped>
.intro-card .chip-row {
  display: none;
}

.mono {
  font-family: monospace;
}
.small {
  font-size: 12px;
}
.actions-cell {
  min-width: 160px;
}
.table-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.table-actions .link-btn {
  line-height: 1;
  padding: 2px 0;
}
.danger-link {
  color: #b91c1c;
}
.danger-link:hover {
  color: #7f1d1d;
}

.compare-trigger {
  min-width: 96px;
}

.compare-mask {
  z-index: 70;
  display: flex;
  justify-content: flex-end;
  align-items: stretch;
  background: rgba(34, 16, 16, 0.42);
  backdrop-filter: blur(3px);
}

.compare-drawer {
  width: min(1140px, calc(100vw - 8px));
  height: 100vh;
  background: #fff;
  border-left: 1px solid #f5cdcf;
  box-shadow: -20px 0 46px rgba(127, 29, 29, 0.2);
  display: flex;
  flex-direction: column;
  animation: drawer-in 0.2s ease-out;
}

.cp-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 22px 14px;
  border-bottom: 1px solid var(--line);
}

.cp-kicker {
  display: inline-block;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--brand);
}

.cp-head h3 {
  margin: 6px 0 0;
  font-size: 34px;
  line-height: 1.12;
}

.cp-sub {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.icon-close {
  width: 34px;
  height: 34px;
  font-size: 20px;
  line-height: 1;
  padding: 0;
}

.cp-body {
  padding: 18px 22px 12px;
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.cp-body::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.mode-kicker {
  margin: 0;
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.cp-body h4 {
  margin: 10px 0 0;
  font-size: 32px;
  line-height: 1.15;
}

.mode-desc {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.result-scroll {
  display: grid;
  gap: 10px;
}

.compare-pick-grid {
  margin-top: 16px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.compare-pick-grid label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.cp-foot {
  border-top: 1px solid var(--line);
  padding: 12px 22px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #fffdfd;
}

.compare-headline {
  border: 1px solid #f3d4d6;
  border-radius: 10px;
  background: #fff8f8;
  padding: 10px 12px;
}

.compare-headline h4 {
  margin: 0;
  font-size: 18px;
}

.compare-headline-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px 12px;
}

.compare-headline-grid p {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.compare-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.compare-block {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  padding: 8px 10px;
}

.compare-block > summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  color: #7f1d1d;
  margin-bottom: 10px;
}

.compare-table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
}

.compare-table th,
.compare-table td {
  word-break: break-word;
}

.metric-list {
  display: grid;
  gap: 10px;
}

.metric-card {
  border: 1px solid #f3d4d6;
  border-radius: 10px;
  padding: 10px;
  background: #fffaf9;
  display: grid;
  gap: 8px;
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.metric-line {
  display: grid;
  gap: 5px;
}

.metric-line label {
  font-size: 12px;
  color: var(--text-secondary);
}

.metric-track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #f8d7da;
  overflow: hidden;
}

.metric-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.metric-fill.left {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.metric-fill.right {
  background: linear-gradient(90deg, #fb7185, #e11d48);
}

.compare-summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

@keyframes drawer-in {
  from {
    transform: translateX(24px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@media (max-width: 800px) {
  .compare-pick-grid {
    grid-template-columns: 1fr;
  }

  .cp-head h3 {
    font-size: 28px;
  }
}
</style>
