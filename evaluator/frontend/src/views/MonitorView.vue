<template>
  <section class="dashboard-page monitor-workspace">
    <article class="surface monitor-hero">
      <div class="monitor-hero-main">
        <div class="monitor-kicker">Run Observatory</div>
        <h2>样本监控 · Run #{{ runId }}</h2>
        <p>
          实时追踪执行进度、事件流、样本结果和工具轨迹，重点定位失败样本与耗时异常。
        </p>
      </div>
      <div class="monitor-hero-actions">
        <span class="live-pill" :class="currentRun?.status === 'RUNNING' ? 'is-live' : 'is-static'">
          {{ currentRun?.status === 'RUNNING' ? 'LIVE' : currentRun?.status || 'IDLE' }}
        </span>
        <RouterLink :to="`/runs/${runId}`" class="ghost monitor-link">返回运行详情</RouterLink>
        <button type="button" class="ghost" @click="reload">刷新</button>
      </div>
    </article>

    <section class="monitor-summary-grid">
      <article class="surface summary-card emphasis">
        <span class="summary-label">任务状态</span>
        <strong>{{ currentRun?.status || '-' }}</strong>
        <small>{{ taskName || '未绑定任务名称' }}</small>
      </article>
      <article class="surface summary-card">
        <span class="summary-label">执行进度</span>
        <strong>{{ progressText }}</strong>
        <small>成功 {{ currentRun?.successCount ?? 0 }} / 失败 {{ currentRun?.failCount ?? 0 }}</small>
      </article>
      <article class="surface summary-card">
        <span class="summary-label">失败样本</span>
        <strong>{{ failedCount }}</strong>
        <small>当前筛选共 {{ filteredRecords.length }} 条</small>
      </article>
      <article class="surface summary-card">
        <span class="summary-label">工具调用</span>
        <strong>{{ totalTraceSteps }}</strong>
        <small>覆盖 {{ toolCoverageText }}</small>
      </article>
      <article class="surface summary-card">
        <span class="summary-label">P95 延迟</span>
        <strong>{{ metrics?.endToEndP95 != null ? `${metrics.endToEndP95} ms` : '-' }}</strong>
        <small>首字 {{ metrics?.firstTokenP95 != null ? `${metrics.firstTokenP95} ms` : '-' }}</small>
      </article>
      <article class="surface summary-card">
        <span class="summary-label">总 Token</span>
        <strong>{{ metrics?.totalTokens?.toLocaleString?.() ?? '-' }}</strong>
        <small>{{ metricsHint }}</small>
      </article>
    </section>

    <section class="monitor-layout">
      <article class="surface event-pane">
        <div class="pane-head">
          <div>
            <h3>运行事件流</h3>
            <p class="pane-sub">按时间顺序查看当前 run 的状态推进和关键节点。</p>
          </div>
          <span class="mini-badge">{{ timeline.length }} events</span>
        </div>

        <ol class="event-feed">
          <li v-for="item in timeline" :key="item.key" class="event-row">
            <span class="event-dot" :class="item.tone"></span>
            <div class="event-card">
              <div class="event-card-head">
                <strong>{{ item.title }}</strong>
                <span>{{ item.time }}</span>
              </div>
              <p>{{ item.summary }}</p>
              <pre v-if="item.payload" class="payload-box">{{ item.payload }}</pre>
            </div>
          </li>
          <li v-if="!timeline.length" class="event-row empty">
            <span class="event-dot idle"></span>
            <div class="event-card">
              <div class="event-card-head">
                <strong>暂无事件</strong>
                <span>-</span>
              </div>
              <p>任务开始后，这里会显示样本启动、采集、评分和结束事件。</p>
            </div>
          </li>
        </ol>
      </article>

      <article class="surface sample-pane">
        <div class="pane-head">
          <div>
            <h3>样本列表</h3>
            <p class="pane-sub">优先定位失败样本，再进入右侧查看输入、输出和工具轨迹。</p>
          </div>
          <div class="sample-toolbar">
            <select v-model="recordFilter">
              <option value="all">全部样本</option>
              <option value="failed">仅失败</option>
              <option value="passed">仅通过</option>
            </select>
          </div>
        </div>

        <div class="sample-list">
          <button
            v-for="record in filteredRecords"
            :key="record.qaId"
            type="button"
            class="sample-row"
            :class="{ active: selectedQaId === record.qaId, failed: !record.passed }"
            @click="selectedQaId = record.qaId"
          >
            <div class="sample-row-head">
              <strong>#{{ record.index }}</strong>
              <span class="sample-status" :class="record.passed ? 'passed' : 'failed'">
                {{ record.passed ? '通过' : '失败' }}
              </span>
            </div>
            <p>{{ record.preview }}</p>
            <div class="sample-meta">
              <span>{{ record.traceSteps.length }} tools</span>
              <span>{{ record.endToEndLatencyMs != null ? `${record.endToEndLatencyMs} ms` : '-' }}</span>
              <span>{{ record.tokenSummary }}</span>
            </div>
          </button>
          <div v-if="!filteredRecords.length" class="sample-empty">
            当前筛选下没有样本记录。
          </div>
        </div>
      </article>

      <article class="surface detail-pane">
        <div class="pane-head">
          <div>
            <h3>样本详情</h3>
            <p class="pane-sub">参考 LangSmith 的问题定位思路，集中展示输入、响应、错误和工具链路。</p>
          </div>
          <span v-if="selectedRecord" class="mini-badge">
            Sample #{{ selectedRecord.index }}
          </span>
        </div>

        <template v-if="selectedRecord">
          <div class="detail-topline">
            <span class="detail-chip" :class="selectedRecord.passed ? 'passed' : 'failed'">
              {{ selectedRecord.passed ? '通过' : '失败' }}
            </span>
            <span class="detail-chip neutral">{{ selectedRecord.endToEndLatencyMs != null ? `${selectedRecord.endToEndLatencyMs} ms` : '无延迟数据' }}</span>
            <span class="detail-chip neutral">{{ selectedRecord.tokenSummary }}</span>
            <span v-if="selectedRecord.modelProfileId != null" class="detail-chip neutral">
              model #{{ selectedRecord.modelProfileId }}
            </span>
          </div>

          <div class="detail-block">
            <span class="detail-label">用户输入</span>
            <div class="detail-content">{{ selectedRecord.input }}</div>
          </div>

          <div class="detail-grid">
            <div class="detail-block">
              <span class="detail-label">期望输出</span>
              <div class="detail-content muted">{{ selectedRecord.expectedOutput || '未提供期望输出' }}</div>
            </div>
            <div class="detail-block">
              <span class="detail-label">实际输出</span>
              <div class="detail-content">{{ selectedRecord.actualOutput || '无输出' }}</div>
            </div>
          </div>

          <div v-if="selectedRecord.errorMessage" class="detail-block error">
            <span class="detail-label">失败原因</span>
            <div class="detail-content">
              <strong>{{ selectedRecord.errorCode || 'RUN_FAILED' }}</strong>
              <p>{{ selectedRecord.errorMessage }}</p>
            </div>
          </div>

          <div class="detail-block">
            <div class="trace-headline">
              <span class="detail-label">工具轨迹</span>
              <span class="trace-count">{{ selectedRecord.traceSteps.length }} steps</span>
            </div>
            <div v-if="selectedRecord.traceSteps.length" class="trace-stack">
              <div v-for="(step, idx) in selectedRecord.traceSteps" :key="`${selectedRecord.qaId}-${idx}`" class="trace-step">
                <div class="trace-step-head">
                  <strong>{{ idx + 1 }}. {{ step.tool }}</strong>
                  <span>{{ step.costLabel }}</span>
                </div>
                <div class="trace-columns">
                  <div>
                    <span class="trace-label">输入</span>
                    <pre>{{ step.input }}</pre>
                  </div>
                  <div>
                    <span class="trace-label">输出</span>
                    <pre>{{ step.output }}</pre>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="empty-hint">该样本没有可解析的工具轨迹。</div>
          </div>
        </template>

        <div v-else class="detail-placeholder">
          还没有可查看的样本，请先运行任务或等待第一条记录落库。
        </div>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import {
  createRunEventSource,
  getRun,
  getRunMetrics,
  getRunRecords,
  getTask,
  type EvalRun,
  type QaRecord,
  type RunMetrics,
  type TaskStatus,
} from '../api/client';

type TimelineTone = 'info' | 'success' | 'warning' | 'danger' | 'idle';

type MonitorEvent = {
  key: string;
  time: string;
  title: string;
  summary: string;
  payload: string;
  tone: TimelineTone;
};

type TraceStep = {
  tool: string;
  input: string;
  output: string;
  costLabel: string;
};

type SampleRecord = QaRecord & {
  index: number;
  passed: boolean;
  preview: string;
  tokenSummary: string;
  traceSteps: TraceStep[];
};

const props = defineProps<{ runId: number }>();

const currentRun = ref<EvalRun | null>(null);
const taskName = ref('');
const metrics = ref<RunMetrics | null>(null);
const records = ref<SampleRecord[]>([]);
const timeline = ref<MonitorEvent[]>([]);
const selectedQaId = ref<number | null>(null);
const recordFilter = ref<'all' | 'failed' | 'passed'>('all');

let stream: EventSource | null = null;
let sequence = 0;

const filteredRecords = computed(() => {
  if (recordFilter.value === 'failed') return records.value.filter((item) => !item.passed);
  if (recordFilter.value === 'passed') return records.value.filter((item) => item.passed);
  return records.value;
});

const selectedRecord = computed(() => {
  if (!filteredRecords.value.length) return null;
  const matched = filteredRecords.value.find((item) => item.qaId === selectedQaId.value);
  return matched ?? filteredRecords.value[0];
});

const failedCount = computed(() => records.value.filter((item) => !item.passed).length);
const totalTraceSteps = computed(() => records.value.reduce((sum, item) => sum + item.traceSteps.length, 0));
const toolCoverageText = computed(() => `${records.value.filter((item) => item.traceSteps.length > 0).length}/${records.value.length || 0} 样本`);
const progressText = computed(() => {
  const success = currentRun.value?.successCount ?? 0;
  const fail = currentRun.value?.failCount ?? 0;
  const total = currentRun.value?.totalCount ?? 0;
  if (!total) return '0 / 0';
  return `${success + fail} / ${total}`;
});
const metricsHint = computed(() => {
  if (!metrics.value) return currentRun.value?.status === 'RUNNING' ? '运行中，等待聚合' : '暂无指标快照';
  return `完成率 ${(metrics.value.taskCompletionRate * 100).toFixed(1)}%`;
});

watch(() => props.runId, () => void reload(), { immediate: true });
watch(filteredRecords, (next) => {
  if (!next.length) {
    selectedQaId.value = null;
    return;
  }
  if (!next.some((item) => item.qaId === selectedQaId.value)) {
    selectedQaId.value = next[0].qaId;
  }
});

onMounted(() => void reload());
onBeforeUnmount(() => closeStream());

async function reload(): Promise<void> {
  if (!props.runId || Number.isNaN(props.runId)) return;
  closeStream();
  timeline.value = [];
  sequence = 0;

  try {
    const run = await getRun(props.runId);
    currentRun.value = run;

    try {
      const task = await getTask(run.taskId);
      taskName.value = task.taskName;
    } catch {
      taskName.value = '';
    }

    await refreshRecords();
    await refreshMetrics(run.status);

    appendSyntheticEvent('run_state', {
      runId: props.runId,
      status: run.status,
      totalCount: run.totalCount,
      successCount: run.successCount,
      failCount: run.failCount,
    });

    if (run.status === 'RUNNING') {
      connect(props.runId);
    }
  } catch {
    currentRun.value = null;
    records.value = [];
    metrics.value = null;
  }
}

async function refreshRecords(): Promise<void> {
  const raw = await getRunRecords(props.runId);
  records.value = raw.map((record, idx) => {
    const traceSteps = parseTraceSteps(record.toolTrace);
    const tokenSummary = parseTokenSummary(record.tokenUsage);
    return {
      ...record,
      index: idx + 1,
      passed: !record.errorCode,
      preview: summarizeText(record.input),
      tokenSummary,
      traceSteps,
    };
  });
}

async function refreshMetrics(status?: string | null): Promise<void> {
  if (status === 'RUNNING') {
    metrics.value = null;
    return;
  }
  try {
    metrics.value = await getRunMetrics(props.runId);
  } catch {
    metrics.value = null;
  }
}

function connect(runId: number): void {
  closeStream();
  stream = createRunEventSource(runId);
  const names = [
    'run_state',
    'run_started',
    'sample_start',
    'sample_collected',
    'ragas_started',
    'ragas_done',
    'sample_done',
    'strategy_applied',
    'player_done',
    'bt_run_config',
    'bt_fitting_started',
    'bt_fitting_done',
    'bt_fitting_failed',
    'run_done',
    'run_failed',
    'run_terminated',
  ];

  names.forEach((eventName) => {
    stream?.addEventListener(eventName, async (evt: MessageEvent) => {
      const payload = safeParse(evt.data);
      appendSyntheticEvent(eventName, payload);
      updateRunStateFromEvent(eventName, payload);

      if (eventName === 'sample_done' || eventName === 'player_done' || eventName === 'run_done' || eventName === 'run_failed') {
        await refreshRecords().catch(() => undefined);
      }
      if (eventName === 'run_done' || eventName === 'run_failed' || eventName === 'run_terminated') {
        closeStream();
        await refreshMetrics(eventName === 'run_done' ? 'SUCCEEDED' : 'FAILED');
      }
    });
  });

  stream.onerror = () => {
    closeStream();
  };
}

function closeStream(): void {
  if (!stream) return;
  stream.close();
  stream = null;
}

function updateRunStateFromEvent(eventName: string, payload: Record<string, any>): void {
  const run = currentRun.value;
  if (!run) return;

  if (typeof payload.total === 'number') {
    run.totalCount = payload.total;
  }
  if (typeof payload.successCount === 'number') {
    run.successCount = payload.successCount;
  }
  if (typeof payload.failCount === 'number') {
    run.failCount = payload.failCount;
  }

  if (eventName === 'run_done') run.status = 'SUCCEEDED';
  if (eventName === 'run_failed') run.status = 'FAILED';
  if (eventName === 'run_state' && typeof payload.status === 'string') run.status = payload.status as TaskStatus;
}

function appendSyntheticEvent(eventName: string, payload: Record<string, any>): void {
  sequence += 1;
  const item = describeEvent(eventName, payload);
  timeline.value.unshift({
    key: `${eventName}-${sequence}`,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
    ...item,
  });
  if (timeline.value.length > 80) {
    timeline.value = timeline.value.slice(0, 80);
  }
}

function describeEvent(eventName: string, payload: Record<string, any>): Omit<MonitorEvent, 'key' | 'time'> {
  switch (eventName) {
    case 'run_state':
      return {
        title: '运行状态同步',
        summary: `状态 ${payload.status ?? '-'}，当前进度 ${payload.successCount ?? 0}/${payload.totalCount ?? payload.total ?? 0}`,
        payload: prettyPayload(payload),
        tone: 'info',
      };
    case 'run_started':
      return {
        title: '任务开始',
        summary: `Run #${payload.runId ?? props.runId} 已进入执行阶段。`,
        payload: prettyPayload(payload),
        tone: 'info',
      };
    case 'sample_start':
      return {
        title: '样本开始',
        summary: `正在处理第 ${payload.index ?? '-'} / ${payload.total ?? '-'} 条样本。`,
        payload: prettyPayload(payload),
        tone: 'warning',
      };
    case 'sample_collected':
      return {
        title: '样本已采集',
        summary: `第 ${payload.index ?? '-'} 条样本已拿到模型/Agent 输出，等待评分。`,
        payload: prettyPayload(payload),
        tone: 'info',
      };
    case 'sample_done':
      return {
        title: '样本完成',
        summary: `第 ${payload.index ?? '-'} 条样本${payload.passed ? '通过' : '失败'}，累计成功 ${payload.successCount ?? 0}，失败 ${payload.failCount ?? 0}。`,
        payload: prettyPayload(payload),
        tone: payload.passed ? 'success' : 'danger',
      };
    case 'ragas_started':
      return {
        title: 'Ragas 评分开始',
        summary: `开始对 ${payload.samples ?? '-'} 条样本做 Judge 评分。`,
        payload: prettyPayload(payload),
        tone: 'warning',
      };
    case 'ragas_done':
      return {
        title: 'Ragas 评分完成',
        summary: payload.warning ? `评分结束，但有告警：${payload.warning}` : '评分完成，结果已回流到评测聚合阶段。',
        payload: prettyPayload(payload),
        tone: payload.warning ? 'warning' : 'success',
      };
    case 'strategy_applied':
      return {
        title: '策略聚合完成',
        summary: `总分 ${payload.overallScore ?? '-'}，结论 ${payload.passed ? '通过' : '未通过'}。`,
        payload: prettyPayload(payload),
        tone: payload.passed ? 'success' : 'warning',
      };
    case 'run_done':
      return {
        title: '运行完成',
        summary: '所有样本已执行完成，指标快照和最终状态已落库。',
        payload: prettyPayload(payload),
        tone: 'success',
      };
    case 'run_failed':
      return {
        title: '运行失败',
        summary: payload.message || '运行过程中出现异常。',
        payload: prettyPayload(payload),
        tone: 'danger',
      };
    case 'run_terminated':
      return {
        title: '流已关闭',
        summary: `SSE 连接已结束，最终状态 ${payload.status ?? '-'}`,
        payload: prettyPayload(payload),
        tone: 'idle',
      };
    default:
      return {
        title: eventName,
        summary: summarizeText(prettyPayload(payload), 120),
        payload: prettyPayload(payload),
        tone: 'info',
      };
  }
}

function parseTraceSteps(raw: string | null): TraceStep[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => ({
      tool: String(item?.tool ?? 'tool'),
      input: stringifyValue(item?.input),
      output: stringifyValue(item?.output),
      costLabel: item?.costMs != null ? `${item.costMs} ms` : 'n/a',
    }));
  } catch {
    return [{
      tool: 'tool_trace',
      input: '-',
      output: raw,
      costLabel: 'n/a',
    }];
  }
}

function parseTokenSummary(raw: string | null): string {
  if (!raw) return 'tokens n/a';
  try {
    const parsed = JSON.parse(raw);
    const total = typeof parsed?.totalTokens === 'number' ? parsed.totalTokens : null;
    return total != null ? `${total} tokens` : 'tokens n/a';
  } catch {
    return 'tokens n/a';
  }
}

function summarizeText(text: string | null | undefined, max = 56): string {
  const value = (text || '').replace(/\s+/g, ' ').trim();
  if (!value) return '空内容';
  return value.length <= max ? value : `${value.slice(0, max)}...`;
}

function stringifyValue(value: unknown): string {
  if (value == null) return '-';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function safeParse(raw: string): Record<string, any> {
  try {
    return JSON.parse(raw);
  } catch {
    return { raw };
  }
}

function prettyPayload(payload: Record<string, any>): string {
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return String(payload);
  }
}
</script>

<style scoped>
.monitor-workspace {
  gap: 16px;
}

.monitor-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
}

.monitor-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--brand);
  margin-bottom: 8px;
}

.monitor-hero h2 {
  font-size: 30px;
  letter-spacing: -0.02em;
}

.monitor-hero p {
  margin-top: 8px;
  color: var(--text-secondary);
  max-width: 760px;
  line-height: 1.6;
}

.monitor-hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.live-pill {
  border-radius: 999px;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.live-pill.is-live {
  color: #991b1b;
  background: #fee2e2;
  box-shadow: inset 0 0 0 1px #fecaca;
}

.live-pill.is-static {
  color: #475569;
  background: #f8fafc;
  box-shadow: inset 0 0 0 1px #e2e8f0;
}

.monitor-link {
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.monitor-summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: grid;
  gap: 8px;
  min-height: 126px;
}

.summary-card.emphasis {
  background:
    radial-gradient(circle at top right, rgba(220, 38, 38, 0.12), transparent 42%),
    linear-gradient(180deg, #ffffff, #fff7f7);
}

.summary-label {
  font-size: 12px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.summary-card strong {
  font-size: 26px;
  letter-spacing: -0.03em;
}

.summary-card small {
  color: var(--text-secondary);
  line-height: 1.5;
}

.monitor-layout {
  display: grid;
  grid-template-columns: 1.05fr 0.9fr 1.25fr;
  gap: 14px;
  align-items: start;
}

.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.pane-head h3 {
  font-size: 18px;
}

.pane-sub {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.mini-badge {
  flex-shrink: 0;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--text-secondary);
  background: #fafafa;
}

.event-feed {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
  max-height: 920px;
  overflow: auto;
}

.event-row {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 12px;
}

.event-dot {
  width: 12px;
  height: 12px;
  margin-top: 12px;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(148, 163, 184, 0.12);
}

.event-dot.info {
  background: #2563eb;
}

.event-dot.success {
  background: #15803d;
}

.event-dot.warning {
  background: #d97706;
}

.event-dot.danger {
  background: #dc2626;
}

.event-dot.idle {
  background: #94a3b8;
}

.event-card {
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 12px 13px;
  background: #fffdfd;
}

.event-card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.event-card-head span {
  color: var(--text-secondary);
  white-space: nowrap;
}

.event-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.payload-box {
  margin: 10px 0 0;
  border-radius: 12px;
  background: #111827;
  color: #e5eefc;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.sample-toolbar {
  width: 120px;
}

.sample-list {
  display: grid;
  gap: 10px;
  max-height: 920px;
  overflow: auto;
}

.sample-row {
  width: 100%;
  text-align: left;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 14px;
  padding: 12px 13px;
  display: grid;
  gap: 10px;
}

.sample-row:hover {
  border-color: #fca5a5;
  box-shadow: 0 10px 24px rgba(220, 38, 38, 0.08);
}

.sample-row.active {
  border-color: var(--brand);
  background: linear-gradient(180deg, #fffdfd, #fff5f5);
  box-shadow: 0 10px 24px rgba(220, 38, 38, 0.1);
}

.sample-row.failed {
  border-left: 4px solid #dc2626;
}

.sample-row-head,
.sample-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.sample-row p {
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-primary);
}

.sample-status {
  border-radius: 999px;
  padding: 4px 9px;
  font-size: 11px;
  font-weight: 700;
}

.sample-status.passed {
  color: #166534;
  background: #dcfce7;
}

.sample-status.failed {
  color: #991b1b;
  background: #fee2e2;
}

.sample-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.sample-empty,
.detail-placeholder,
.empty-hint {
  border: 1px dashed var(--line-strong);
  border-radius: 14px;
  padding: 18px;
  color: var(--text-secondary);
  background: #fafafa;
  font-size: 13px;
  line-height: 1.6;
}

.detail-pane {
  display: grid;
  gap: 12px;
}

.detail-topline {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-chip {
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
}

.detail-chip.passed {
  background: #dcfce7;
  color: #166534;
}

.detail-chip.failed {
  background: #fee2e2;
  color: #991b1b;
}

.detail-chip.neutral {
  background: #f8fafc;
  color: #475569;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-block {
  display: grid;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
  background: #fffdfd;
}

.detail-block.error {
  border-color: #fecaca;
  background: #fff7f7;
}

.detail-label,
.trace-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.detail-content {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-content.muted {
  color: var(--text-secondary);
}

.detail-content p {
  margin: 8px 0 0;
}

.trace-headline {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.trace-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.trace-stack {
  display: grid;
  gap: 10px;
}

.trace-step {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  padding: 12px;
}

.trace-step-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}

.trace-step-head span {
  color: var(--text-secondary);
}

.trace-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.trace-columns pre {
  margin: 6px 0 0;
  padding: 10px 11px;
  border-radius: 12px;
  background: #111827;
  color: #e5eefc;
  min-height: 84px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1400px) {
  .monitor-summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .monitor-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .monitor-hero {
    display: grid;
  }

  .monitor-summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .detail-grid,
  .trace-columns {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .monitor-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
