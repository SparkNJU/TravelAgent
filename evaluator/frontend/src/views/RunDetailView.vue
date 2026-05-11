<template>
  <section class="dashboard-page run-visual-page">
    <article class="surface run-hero surface-red">
      <div class="hero-main">
        <span class="hero-kicker">评测结果</span>
        <h2>运行 #{{ runId }}</h2>
        <p v-if="currentRun" class="hero-sub">
          任务 #{{ currentRun.taskId }} ·
          <span class="status" :class="statusClass(currentRun.status)">{{ statusLabel(currentRun.status) }}</span>
          · 成功 {{ successCount }} / {{ totalCount }}
          <span v-if="failedCount > 0"> · 失败 {{ failedCount }}</span>
          <span v-if="isBtRun" class="chip bt-chip">BT 多模型</span>
          <span v-else-if="isSingleModelRun" class="chip">单模型 / 应用</span>
        </p>
        <p v-else class="notice-text">{{ noticeText || '加载中...' }}</p>
        <div class="hero-mini-stats">
          <span>工具覆盖 {{ traceCoverageCount }}/{{ runRecords.length || 0 }} ({{ toPercent(traceCoverageRate) }})</span>
          <span>失败主因 {{ failureStats.length }} 类</span>
        </div>
      </div>
      <div class="hero-actions">
        <button
          v-if="isDeterministicSingleRun"
          class="primary"
          @click="toggleDeterministicCompareBuilder"
        >
          {{ compareBuilderVisible ? '收起对比' : '创建对比' }}
        </button>
        <RouterLink to="/tasks" class="link-btn">← 返回任务列表</RouterLink>
        <RouterLink :to="`/runs/${runId}/monitor`" class="link-btn">样本监控 →</RouterLink>
        <button class="ghost" @click="refresh">刷新</button>
      </div>
    </article>

    <section class="kpi-strip">
      <article class="surface kpi-card focus">
        <p>完成率</p>
        <strong>{{ toPercent(completionRate) }}</strong>
        <small>样本通过占比</small>
      </article>
      <article class="surface kpi-card">
        <p>通过 / 失败</p>
        <strong>{{ successCount }} / {{ failedCount }}</strong>
        <small>总样本 {{ totalCount || 0 }}</small>
      </article>
      <article class="surface kpi-card">
        <p>P95 端到端</p>
        <strong>{{ runMetrics?.endToEndP95 != null ? `${runMetrics.endToEndP95} ms` : '-' }}</strong>
        <small>P95 首字 {{ runMetrics?.firstTokenP95 != null ? `${runMetrics.firstTokenP95} ms` : '-' }}</small>
      </article>
      <article class="surface kpi-card">
        <p>Token消耗</p>
        <strong>{{ runMetrics?.totalTokens?.toLocaleString?.() ?? '-' }}</strong>
        <small>样本记录 {{ runRecords.length }}</small>
      </article>
    </section>

    <section class="analytics-grid">
      <article class="surface analytics-panel">
        <div class="panel-head">
          <h3>质量维度评分</h3>
          <span class="panel-badge">质量汇总</span>
        </div>
        <div class="score-rows">
          <div v-for="row in qualityRows" :key="row.key" class="score-row">
            <div class="score-top">
              <span>{{ row.label }}</span>
              <strong>{{ row.score == null ? '-' : toPercent(row.score) }}</strong>
            </div>
            <div class="score-bar">
              <span :style="{ width: `${row.score == null ? 0 : clampPercent(row.score * 100)}%` }"></span>
            </div>
          </div>
        </div>
      </article>

      <article class="surface analytics-panel">
        <div class="panel-head">
          <h3>延迟分布</h3>
          <span class="panel-badge">{{ latencySampleCount }} 条样本</span>
        </div>
        <div class="latency-rows">
          <div v-for="bucket in latencyBuckets" :key="bucket.key" class="latency-row">
            <div class="latency-label">{{ bucket.label }}</div>
            <div class="latency-track">
              <span :style="{ width: `${bucket.bar}%` }"></span>
            </div>
            <div class="latency-value">{{ bucket.count }} · {{ toPercent(bucket.share) }}</div>
          </div>
        </div>
      </article>

      <article class="surface analytics-panel">
        <div class="panel-head">
          <h3>失败归因</h3>
          <span class="panel-badge">前 {{ failureStats.length }} 项</span>
        </div>
        <div v-if="failureStats.length" class="failure-list">
          <div v-for="item in failureStats" :key="item.reason" class="failure-item">
            <div class="failure-top">
              <strong>{{ item.reason }}</strong>
              <span>{{ item.count }} 次</span>
            </div>
            <div class="score-bar danger">
              <span :style="{ width: `${item.share * 100}%` }"></span>
            </div>
          </div>
        </div>
        <p v-else class="empty-note">本次运行没有失败样本。</p>
      </article>
    </section>

    <article v-if="task" class="surface eval-config-card">
      <h3>评测配置</h3>
      <p class="config-lead">
        与创建任务时一致；BT 模式下每条样本会先由各参赛模型独立生成回答，再由裁判模型做成对比较与拟合排行。
      </p>
      <dl class="config-grid">
        <div><dt>任务名称</dt><dd>{{ task.taskName }}</dd></div>
        <div><dt>数据集</dt><dd class="mono">{{ task.datasetId }}</dd></div>
        <div><dt>应用版本</dt><dd>{{ task.agentVersion }}</dd></div>
        <div><dt>评估模式</dt><dd>{{ evaluationModeLabel(task.evaluationMode) }}</dd></div>
        <div><dt>评估方式</dt><dd>{{ evaluationMethodLabel(task.evaluationMethod) }}</dd></div>
        <div><dt>评估维度</dt><dd class="mono">{{ task.evaluationDimensions || '—' }}</dd></div>
        <div v-if="task.metricSet"><dt>指标集</dt><dd class="mono">{{ task.metricSet }}</dd></div>
      </dl>
      <template v-if="isBtRun">
        <h4 class="subhead">BT 多模型</h4>
        <dl class="config-grid">
          <div class="span-2">
            <dt>参赛模型（按槽位顺序）</dt>
            <dd>
              <ol class="player-list">
                <li v-for="p in playerProfiles" :key="p.modelProfileId">
                  <span class="mono">{{ p.modelId }}</span>
                  <span v-if="p.displayName" class="dim"> — {{ p.displayName }}</span>
                  <span class="id-tag">#{{ p.modelProfileId }}</span>
                </li>
              </ol>
            </dd>
          </div>
          <div>
            <dt>裁判模型</dt>
            <dd>
              <template v-if="judgeProfile">
                <span class="mono">{{ judgeProfile.modelId }}</span>
                <span v-if="judgeProfile.displayName" class="dim"> — {{ judgeProfile.displayName }}</span>
                <span class="id-tag">#{{ judgeProfile.modelProfileId }}</span>
              </template>
              <template v-else>#{{ task.judgeModelId }}</template>
            </dd>
          </div>
          <div>
            <dt>抽样策略</dt>
            <dd>{{ samplingLabel }}</dd>
          </div>
          <div>
            <dt>位置互换</dt>
            <dd>{{ task.positionSwapEnabled ? '已启用（每对双跑，成本约翻倍）' : '未启用' }}</dd>
          </div>
        </dl>
      </template>
    </article>

    <BtRatingsPanel v-if="isBtRun" :run-id="runId" />

    <article v-if="isDeterministicSingleRun && compareBuilderVisible" class="surface det-compare-panel">
      <div class="panel-head">
        <h3>确定性单模型对比</h3>
        <span class="panel-badge">从任务列表中已有确定性结果选择</span>
      </div>
      <p class="config-lead">选择两个不同模型的确定性运行结果，按维度折叠查看差异。</p>

      <div v-if="deterministicCandidates.length" class="compare-builder">
        <label>
          左侧结果
          <select v-model.number="compareLeftRunId">
            <option :value="0">请选择</option>
            <option v-for="item in deterministicCandidates" :key="`left-${item.runId}`" :value="item.runId">
              {{ formatCandidateLabel(item) }}
            </option>
          </select>
        </label>
        <label>
          右侧结果
          <select v-model.number="compareRightRunId">
            <option :value="0">请选择</option>
            <option v-for="item in deterministicCandidates" :key="`right-${item.runId}`" :value="item.runId">
              {{ formatCandidateLabel(item) }}
            </option>
          </select>
        </label>
        <div class="inline-actions">
          <button class="ghost" @click="swapCompareSides">交换</button>
          <button class="primary" :disabled="compareLoading || compareCandidateLoading" @click="runDeterministicCompare">
            {{ compareLoading ? '对比中...' : '生成对比结果' }}
          </button>
        </div>
      </div>
      <p v-else-if="!compareCandidateLoading" class="notice-text">暂无可用于对比的确定性结果</p>

      <p v-if="compareNotice" class="notice-text">{{ compareNotice }}</p>

      <section v-if="detCompare.left && detCompare.right" class="det-compare-result">
        <article class="compare-headline">
          <h4>{{ detCompare.left.modelTitle }} 对比 {{ detCompare.right.modelTitle }}</h4>
          <div class="compare-headline-grid">
            <p><strong>{{ detCompare.left.shortTitle }}</strong> · 数据集：{{ detCompare.left.datasetId }}</p>
            <p><strong>{{ detCompare.right.shortTitle }}</strong> · 数据集：{{ detCompare.right.datasetId }}</p>
          </div>
          <p class="compare-meta">
            左侧运行#{{ detCompare.left.runId }} · 右侧运行#{{ detCompare.right.runId }}
          </p>
        </article>

        <details class="compare-block" open>
          <summary>质量维度（效果 / 安全 / 性能）</summary>
          <table class="task-table compare-table">
            <thead>
              <tr>
                <th>指标</th>
                <th>{{ detCompare.left.shortTitle }}</th>
                <th>{{ detCompare.right.shortTitle }}</th>
                <th>差值（右-左）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in detCompare.qualityRows" :key="row.key">
                <td>{{ row.label }}</td>
                <td>{{ row.left }}</td>
                <td>{{ row.right }}</td>
                <td>{{ row.delta }}</td>
              </tr>
            </tbody>
          </table>
        </details>

        <details class="compare-block" open>
          <summary>性能维度（P95 延迟 / 令牌）</summary>
          <table class="task-table compare-table">
            <thead>
              <tr>
                <th>指标</th>
                <th>{{ detCompare.left.shortTitle }}</th>
                <th>{{ detCompare.right.shortTitle }}</th>
                <th>差值（右-左）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in detCompare.performanceRows" :key="row.key">
                <td>{{ row.label }}</td>
                <td>{{ row.left }}</td>
                <td>{{ row.right }}</td>
                <td>{{ row.delta }}</td>
              </tr>
            </tbody>
          </table>
        </details>

        <details class="compare-block" open>
          <summary>结果维度（通过率 / 失败数）</summary>
          <table class="task-table compare-table">
            <thead>
              <tr>
                <th>指标</th>
                <th>{{ detCompare.left.shortTitle }}</th>
                <th>{{ detCompare.right.shortTitle }}</th>
                <th>差值（右-左）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in detCompare.outcomeRows" :key="row.key">
                <td>{{ row.label }}</td>
                <td>{{ row.left }}</td>
                <td>{{ row.right }}</td>
                <td>{{ row.delta }}</td>
              </tr>
            </tbody>
          </table>
        </details>

        <details class="compare-block" open>
          <summary>样本差异概览</summary>
          <p class="compare-summary">
            总样本 {{ detCompare.sampleSummary.total }}，输出或错误信息有差异 {{ detCompare.sampleSummary.changed }}
            （{{ detCompare.sampleSummary.changedRate }}）
          </p>
          <div class="table-wrap">
            <table class="task-table compare-table">
              <thead>
                <tr>
                  <th>样本</th>
                  <th>输入</th>
                  <th>{{ detCompare.left.shortTitle }}</th>
                  <th>{{ detCompare.right.shortTitle }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detCompare.sampleSummary.preview" :key="item.index">
                  <td>{{ item.index }}</td>
                  <td>{{ item.input }}</td>
                  <td>{{ item.left }}</td>
                  <td>{{ item.right }}</td>
                </tr>
                <tr v-if="!detCompare.sampleSummary.preview.length">
                  <td colspan="4">无差异样本（或两侧样本为空）</td>
                </tr>
              </tbody>
            </table>
          </div>
        </details>
      </section>
    </article>

    <article class="surface detail-board">
      <div class="section-head">
        <h3>{{ isBtRun ? '样本结果（按数据集条目 · 多模型并列）' : '样本结果' }}（共 {{ recordSummaryCount }} 条）</h3>
        <p v-if="isBtRun" class="hint">
          每行对应数据集中的一个输入；列为各 player 的生成结果。下方「原始行数」含每个 player 一条 QA 记录，与后端存储一致。
        </p>
      </div>

      <div v-if="isBtRun" class="table-wrap">
        <table class="task-table detail-table bt-table">
          <thead>
            <tr>
              <th>样本</th>
              <th>输入</th>
              <th>期望输出</th>
              <th
                v-for="(slot, idx) in btTableColumns"
                :key="slot.key"
                class="player-col"
              >
                <div class="th-stack">
                  <span>模型 {{ idx + 1 }}</span>
                  <span class="mono th-model">{{ slot.modelId }}</span>
                  <span v-if="slot.displayName" class="th-dim">{{ slot.displayName }}</span>
                  <span v-else-if="slot.fallbackId != null" class="th-dim">模型档案 #{{ slot.fallbackId }}</span>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(g, idx) in groupedBtSamples" :key="idx">
              <td>{{ idx + 1 }}</td>
              <td class="input-cell">{{ g.input }}</td>
              <td class="input-cell">{{ g.expectedOutput }}</td>
              <td v-for="(cell, j) in g.cells" :key="j" class="player-cell">
                <div class="verdict" :class="cell.ok ? 'ok' : 'bad'">{{ cell.ok ? '通过' : '失败' }}</div>
                <details class="output-details">
                  <summary>输出与说明</summary>
                  <div class="output-body">{{ cell.output || '（空）' }}</div>
                  <div v-if="cell.error" class="err-line">{{ cell.error }}</div>
                </details>
                <div class="meta-line" v-if="cell.latency != null">{{ cell.latency }} ms</div>
              </td>
            </tr>
            <tr v-if="!groupedBtSamples.length">
              <td :colspan="3 + Math.max(btTableColumns.length, 1)">暂无样本记录</td>
            </tr>
          </tbody>
        </table>
        <p class="footnote">问答原始记录行数：{{ runRecords.length }}（≈ 样本数 × {{ btPlayerSlotCount }} 个模型）</p>
      </div>

      <div v-else class="table-wrap">
        <table class="task-table detail-table">
          <thead>
            <tr>
              <th>序号</th>
              <th>模型</th>
              <th>输入</th>
              <th>期望输出</th>
              <th>实际输出</th>
              <th>结果</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(record, idx) in runRecords" :key="record.qaId">
              <td>{{ idx + 1 }}</td>
              <td class="model-cell">{{ modelLabel(record.modelProfileId) }}</td>
              <td>{{ truncate(record.input, 54) }}</td>
              <td>{{ truncate(record.expectedOutput, 54) }}</td>
              <td class="output-cell">
                <details class="output-details">
                  <summary>{{ truncate(record.actualOutput, 84) }}</summary>
                  <div class="output-body">{{ record.actualOutput || '（空）' }}</div>
                </details>
              </td>
              <td>{{ record.errorCode ? '失败' : '通过' }}</td>
              <td>{{ truncate(record.errorMessage || '—', 82) }}</td>
            </tr>
            <tr v-if="!runRecords.length">
              <td colspan="7">暂无样本记录（任务可能还在跑或失败了）</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';

import BtRatingsPanel from '../components/BtRatingsPanel.vue';
import {
  createRunEventSource,
  getRun,
  getRunMetrics,
  getRunRecords,
  getTask,
  listTaskRuns,
  listTasks,
  listModels,
  type EvalRun,
  type EvalTask,
  type ModelProfile,
  type QaRecord,
  type RunMetrics,
  type TaskStatus,
} from '../api/client';

type FailureStat = {
  reason: string;
  count: number;
  share: number;
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
};

type CompareSamplePreview = {
  index: number;
  input: string;
  left: string;
  right: string;
};

type DeterministicRunData = {
  metrics: RunMetrics | null;
  records: QaRecord[];
  successCount: number;
  failCount: number;
};

const props = defineProps<{ runId: number }>();

const currentRun = ref<EvalRun | null>(null);
const task = ref<EvalTask | null>(null);
const runRecords = ref<QaRecord[]>([]);
const models = ref<ModelProfile[]>([]);
const runMetrics = ref<RunMetrics | null>(null);
const noticeText = ref('');
const deterministicCandidates = ref<DeterministicCandidate[]>([]);
const compareLeftRunId = ref(0);
const compareRightRunId = ref(0);
const compareLoading = ref(false);
const compareCandidateLoading = ref(false);
const compareBuilderVisible = ref(false);
const compareNotice = ref('');

let stream: EventSource | null = null;

const isBtRun = computed(() => {
  const t = task.value;
  if (!t?.selectedModelIds || t.selectedModelIds.length < 2) return false;
  return t.judgeModelId != null;
});

const isSingleModelRun = computed(() => {
  const t = task.value;
  if (!t?.selectedModelIds?.length) return true;
  return t.selectedModelIds.length === 1 && t.judgeModelId == null;
});

const isDeterministicSingleRun = computed(() => {
  const t = task.value;
  const modelCount = t?.selectedModelIds?.length ?? 0;
  return t?.evaluationMethod === 'DETERMINISTIC' && modelCount === 1 && t.judgeModelId == null;
});

const totalCount = computed(() => currentRun.value?.totalCount ?? runRecords.value.length);
const successCount = computed(() => currentRun.value?.successCount ?? runRecords.value.filter((r) => !r.errorCode).length);
const failedCount = computed(() => currentRun.value?.failCount ?? runRecords.value.filter((r) => !!r.errorCode).length);
const completionRate = computed(() => {
  if (runMetrics.value) return runMetrics.value.taskCompletionRate;
  return totalCount.value > 0 ? successCount.value / totalCount.value : 0;
});

const traceCoverageCount = computed(() =>
  runRecords.value.filter((r) => hasToolTrace(r.toolTrace)).length,
);
const traceCoverageRate = computed(() =>
  runRecords.value.length > 0 ? traceCoverageCount.value / runRecords.value.length : 0,
);

const qualityRows = computed(() => {
  const m = runMetrics.value;
  return [
    { key: 'effectiveness', label: '效果', score: m?.effectivenessScore ?? null },
    { key: 'safety', label: '安全', score: m?.safetyScore ?? null },
    { key: 'performance', label: '性能', score: m?.performanceScore ?? null },
  ];
});

const latencyBuckets = computed(() => {
  const latencies = runRecords.value
    .map((r) => r.endToEndLatencyMs)
    .filter((v): v is number => typeof v === 'number' && v >= 0);
  const defs = [
    { key: 'lt1', label: '< 1s', min: 0, max: 1000 },
    { key: 's1_2', label: '1s - 2s', min: 1000, max: 2000 },
    { key: 's2_5', label: '2s - 5s', min: 2000, max: 5000 },
    { key: 'gt5', label: '> 5s', min: 5000, max: Infinity },
  ];
  const counts = defs.map((d) => latencies.filter((ms) => ms >= d.min && ms < d.max).length);
  const maxCount = Math.max(1, ...counts);
  const base = Math.max(latencies.length, 1);
  return defs.map((d, i) => ({
    key: d.key,
    label: d.label,
    count: counts[i],
    share: counts[i] / base,
    bar: clampPercent((counts[i] / maxCount) * 100),
  }));
});
const latencySampleCount = computed(() =>
  runRecords.value.filter((r) => typeof r.endToEndLatencyMs === 'number').length,
);

const failureBaseCount = computed(() => runRecords.value.filter((r) => !!r.errorCode || !!r.errorMessage).length);
const failureStats = computed<FailureStat[]>(() => {
  const failed = runRecords.value.filter((r) => !!r.errorCode || !!r.errorMessage);
  if (!failed.length) return [];
  const counter = new Map<string, number>();
  for (const row of failed) {
    const reason = (row.errorCode || row.errorMessage || '未知原因').trim().slice(0, 72);
    counter.set(reason, (counter.get(reason) ?? 0) + 1);
  }
  return [...counter.entries()]
    .map(([reason, count]) => ({ reason, count, share: count / failed.length }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6);
});

const playerProfiles = computed((): ModelProfile[] => {
  const ids = task.value?.selectedModelIds;
  if (!ids?.length) return [];
  const map = new Map(models.value.map((m) => [m.modelProfileId, m]));
  return ids.map((id) => map.get(id)).filter((m): m is ModelProfile => m != null);
});

const btPlayerSlotCount = computed(() => {
  if (!isBtRun.value) return 0;
  if (playerProfiles.value.length > 0) return playerProfiles.value.length;
  return task.value?.selectedModelIds?.length ?? 0;
});

const judgeProfile = computed((): ModelProfile | null => {
  const jid = task.value?.judgeModelId;
  if (jid == null) return null;
  return models.value.find((m) => m.modelProfileId === jid) ?? null;
});

const samplingLabel = computed(() => {
  const s = task.value?.comparisonSamplingStrategy;
  if (s === 'ALL_PAIRS') return 'ALL_PAIRS（全对比较）';
  return s ?? '—';
});

const btTableColumns = computed(() => {
  const ids = task.value?.selectedModelIds ?? [];
  const map = new Map(models.value.map((m) => [m.modelProfileId, m]));
  return ids.map((id, i) => {
    const m = map.get(id);
    return {
      key: `p-${id}-${i}`,
      modelId: m?.modelId ?? '—',
      displayName: m?.displayName ?? '',
      fallbackId: m ? null : id,
    };
  });
});

const groupedBtSamples = computed(() => {
  if (!isBtRun.value) return [];
  const n = btPlayerSlotCount.value;
  if (n === 0) return [];
  const recs = runRecords.value;
  const groups: Array<{
    input: string;
    expectedOutput: string;
    cells: Array<{ ok: boolean; output: string; error: string; latency: number | null }>;
  }> = [];
  for (let i = 0; i < recs.length; i += n) {
    const slice = recs.slice(i, i + n);
    if (!slice.length) break;
    const cells = slice.map((r) => ({
      ok: !r.errorCode,
      output: r.actualOutput || '',
      error: r.errorMessage || '',
      latency: r.endToEndLatencyMs,
    }));
    groups.push({
      input: slice[0]?.input ?? '',
      expectedOutput: slice[0]?.expectedOutput ?? '',
      cells,
    });
  }
  return groups;
});

const recordSummaryCount = computed(() => {
  if (isBtRun.value) return groupedBtSamples.value.length || 0;
  return runRecords.value.length;
});

const candidateMap = computed(() => {
  const map = new Map<number, DeterministicCandidate>();
  for (const item of deterministicCandidates.value) {
    map.set(item.runId, item);
  }
  return map;
});

const detCompare = computed(() => {
  const left = candidateMap.value.get(compareLeftRunId.value) ?? null;
  const right = candidateMap.value.get(compareRightRunId.value) ?? null;
  if (!left || !right) {
    return {
      left: null as any,
      right: null as any,
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

  const leftRun = left.compareData;
  const rightRun = right.compareData;
  if (!leftRun || !rightRun) {
    return {
      left: null as any,
      right: null as any,
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
    formatCompareRow('effectiveness', '效果', leftRun.metrics?.effectivenessScore, rightRun.metrics?.effectivenessScore, true, true),
    formatCompareRow('safety', '安全', leftRun.metrics?.safetyScore, rightRun.metrics?.safetyScore, true, true),
    formatCompareRow('performance', '性能', leftRun.metrics?.performanceScore, rightRun.metrics?.performanceScore, true, true),
  ];
  const performanceRows: CompareRow[] = [
    formatCompareRow('first_token', 'P95 首字延迟', leftRun.metrics?.firstTokenP95, rightRun.metrics?.firstTokenP95, false, false, 'ms'),
    formatCompareRow('end_to_end', 'P95 端到端延迟', leftRun.metrics?.endToEndP95, rightRun.metrics?.endToEndP95, false, false, 'ms'),
    formatCompareRow('tokens', 'Token消耗', leftRun.metrics?.totalTokens, rightRun.metrics?.totalTokens, false, false),
  ];
  const outcomeRows: CompareRow[] = [
    formatCompareRow('completion', '完成率', leftRun.metrics?.taskCompletionRate, rightRun.metrics?.taskCompletionRate, true, true),
    formatCompareRow('success', '成功样本', leftRun.successCount, rightRun.successCount, false, true),
    formatCompareRow('failed', '失败样本', leftRun.failCount, rightRun.failCount, false, false),
  ];

  const sampleSummary = summarizeSampleDiff(leftRun.records, rightRun.records);
  return {
    left,
    right,
    qualityRows,
    performanceRows,
    outcomeRows,
    sampleSummary,
  };
});

watch(() => props.runId, () => {
  resetDeterministicCompareState();
  void refresh();
});

onMounted(() => {
  void refresh();
});

onBeforeUnmount(() => {
  closeStream();
});

function hasToolTrace(raw: string | null): boolean {
  if (!raw) return false;
  const value = raw.trim();
  return value.length > 0 && value !== '[]' && value !== 'null';
}

function resetDeterministicCompareState(): void {
  deterministicCandidates.value = [];
  compareLeftRunId.value = 0;
  compareRightRunId.value = 0;
  compareLoading.value = false;
  compareCandidateLoading.value = false;
  compareBuilderVisible.value = false;
  compareNotice.value = '';
}

function toPercent(v: number): string {
  return `${(clampPercent(v * 100)).toFixed(1)}%`;
}

function clampPercent(v: number): number {
  if (!Number.isFinite(v)) return 0;
  if (v < 0) return 0;
  if (v > 100) return 100;
  return v;
}

function modelLabel(id: number | null): string {
  if (id == null) return '—';
  const m = models.value.find((x) => x.modelProfileId === id);
  if (m) return `${m.displayName} (${m.modelId})`;
  return `#${id}`;
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

function toggleDeterministicCompareBuilder(): void {
  compareBuilderVisible.value = !compareBuilderVisible.value;
  if (!compareBuilderVisible.value) return;
  void loadDeterministicCandidates();
}

function swapCompareSides(): void {
  const left = compareLeftRunId.value;
  compareLeftRunId.value = compareRightRunId.value;
  compareRightRunId.value = left;
}

async function loadDeterministicCandidates(): Promise<void> {
  compareCandidateLoading.value = true;
  compareNotice.value = '正在加载确定性运行结果...';
  try {
    const [taskList, modelList] = await Promise.all([
      listTasks(),
      models.value.length ? Promise.resolve(models.value) : listModels().catch(() => [] as ModelProfile[]),
    ]);
    if (!models.value.length && modelList.length) {
      models.value = modelList;
    }
    const modelMap = new Map(modelList.map((m) => [m.modelProfileId, m]));
    const deterministicTasks = taskList.filter((t) =>
      t.evaluationMethod === 'DETERMINISTIC' && (t.selectedModelIds?.length ?? 0) === 1 && t.judgeModelId == null,
    );
    const runGroups = await Promise.all(
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

    deterministicCandidates.value = runGroups
      .flat()
      .sort((a, b) => {
        const bTime = b.finishedAt ? Date.parse(b.finishedAt) : 0;
        const aTime = a.finishedAt ? Date.parse(a.finishedAt) : 0;
        return (Number.isFinite(bTime) ? bTime : 0) - (Number.isFinite(aTime) ? aTime : 0) || b.runId - a.runId;
      });
    if (!deterministicCandidates.value.length) {
      compareNotice.value = '没有可用于对比的确定性成功结果';
      return;
    }

    if (!candidateMap.value.has(compareLeftRunId.value)) {
      const current = deterministicCandidates.value.find((c) => c.runId === props.runId);
      compareLeftRunId.value = current?.runId || deterministicCandidates.value[0].runId;
    }
    if (!candidateMap.value.has(compareRightRunId.value) || compareRightRunId.value === compareLeftRunId.value) {
      const base = candidateMap.value.get(compareLeftRunId.value);
      const another = deterministicCandidates.value.find((c) =>
        c.runId !== compareLeftRunId.value && (!base || !isSameModelCandidate(base, c)),
      );
      compareRightRunId.value = another?.runId || 0;
    }
    compareNotice.value = `已加载 ${deterministicCandidates.value.length} 条确定性结果`;
  } catch (err: any) {
    compareNotice.value = `加载对比候选失败: ${err.message || String(err)}`;
  } finally {
    compareCandidateLoading.value = false;
  }
}

async function runDeterministicCompare(): Promise<void> {
  if (!compareLeftRunId.value || !compareRightRunId.value) {
    compareNotice.value = '请先选择左右两条确定性结果';
    return;
  }
  if (compareLeftRunId.value === compareRightRunId.value) {
    compareNotice.value = '左右两侧不能选择同一个 run';
    return;
  }
  const left = candidateMap.value.get(compareLeftRunId.value);
  const right = candidateMap.value.get(compareRightRunId.value);
  if (!left || !right) {
    compareNotice.value = '选择的 run 无效，请重新选择';
    return;
  }
  if (isSameModelCandidate(left, right)) {
    compareNotice.value = '请选两个不同模型的确定性结果';
    return;
  }

  compareLoading.value = true;
  compareNotice.value = '正在加载两侧运行详情并生成对比...';
  try {
    const [leftData, rightData] = await Promise.all([
      loadDeterministicRunData(left.runId),
      loadDeterministicRunData(right.runId),
    ]);
    deterministicCandidates.value = deterministicCandidates.value.map((item) => {
      if (item.runId === left.runId) return { ...item, compareData: leftData };
      if (item.runId === right.runId) return { ...item, compareData: rightData };
      return item;
    });
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
  const successCount = run.successCount ?? records.filter((r) => !r.errorCode).length;
  const failCount = run.failCount ?? records.filter((r) => !!r.errorCode).length;
  return { metrics, records, successCount, failCount };
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
  const l = typeof leftValue === 'number' && Number.isFinite(leftValue) ? leftValue : null;
  const r = typeof rightValue === 'number' && Number.isFinite(rightValue) ? rightValue : null;
  const leftText = l == null ? '-' : asPercent ? `${(l * 100).toFixed(1)}%` : `${Math.round(l * 10) / 10}${unit}`;
  const rightText = r == null ? '-' : asPercent ? `${(r * 100).toFixed(1)}%` : `${Math.round(r * 10) / 10}${unit}`;
  if (l == null || r == null) {
    return { key, label, left: leftText, right: rightText, delta: '-' };
  }
  const delta = r - l;
  const better = higherBetter ? (delta >= 0 ? '↑' : '↓') : (delta <= 0 ? '↑' : '↓');
  const deltaText = asPercent ? `${better} ${(delta * 100).toFixed(1)}%` : `${better} ${(Math.round(delta * 10) / 10)}${unit}`;
  return { key, label, left: leftText, right: rightText, delta: deltaText };
}

function isSameModelCandidate(a: DeterministicCandidate, b: DeterministicCandidate): boolean {
  if (a.modelProfileId != null && b.modelProfileId != null) {
    return a.modelProfileId === b.modelProfileId;
  }
  return a.modelTitle.trim().toLowerCase() === b.modelTitle.trim().toLowerCase();
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
      if (preview.length < 10) {
        preview.push({
          index: i + 1,
          input: truncate(l?.input || r?.input || '-', 42),
          left: l ? truncate((l.errorMessage || l.actualOutput || '-').replace(/\s+/g, ' '), 64) : '（缺失）',
          right: r ? truncate((r.errorMessage || r.actualOutput || '-').replace(/\s+/g, ' '), 64) : '（缺失）',
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

async function refresh(): Promise<void> {
  if (!props.runId || Number.isNaN(props.runId)) return;
  try {
    const [run, records] = await Promise.all([getRun(props.runId), getRunRecords(props.runId)]);
    currentRun.value = run;
    runRecords.value = records;
    const [t, ml] = await Promise.all([
      getTask(run.taskId),
      listModels().catch(() => [] as ModelProfile[]),
    ]);
    task.value = t;
    models.value = ml;
    if (run.status === 'SUCCEEDED') {
      try {
        runMetrics.value = await getRunMetrics(props.runId);
      } catch {
        runMetrics.value = null;
      }
    } else {
      runMetrics.value = null;
    }
    if (run.status === 'RUNNING') {
      connectStream();
    } else {
      closeStream();
    }
  } catch (err: any) {
    noticeText.value = `加载运行失败: ${err.message || String(err)}`;
  }
}

function connectStream(): void {
  closeStream();
  if (!props.runId) return;
  stream = createRunEventSource(props.runId);
  const autoCloseOn = (eventName: string) => {
    stream?.addEventListener(eventName, () => {
      closeStream();
      void refresh();
    });
  };
  autoCloseOn('run_done');
  autoCloseOn('run_failed');
  autoCloseOn('run_terminated');
  stream.onerror = () => {
    if (currentRun.value?.status !== 'RUNNING') {
      closeStream();
    }
  };
}

function closeStream(): void {
  if (stream) {
    stream.close();
    stream = null;
  }
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

function evaluationModeLabel(mode?: string | null): string {
  if (!mode) return '-';
  if (mode === 'RESULT') return 'RESULT:结果模式';
  if (mode === 'PROCESS') return 'PROCESS:过程模式';
  return mode;
}

function evaluationMethodLabel(method?: string | null): string {
  if (!method) return '-';
  if (method === 'DETERMINISTIC') return 'DETERMINISTIC:确定性评测';
  if (method === 'JUDGE') return 'JUDGE:裁判评测';
  if (method === 'HYBRID') return 'HYBRID:混合评测';
  return method;
}

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}

function truncate(text: string, max: number): string {
  if (!text) return '—';
  return text.length <= max ? text : text.substring(0, max) + '...';
}
</script>

<style scoped>
.run-visual-page {
  gap: 14px;
}

.run-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.hero-kicker {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--brand);
}

.run-hero h2 {
  margin-top: 6px;
  font-size: 30px;
}

.hero-sub {
  margin-top: 6px;
  color: var(--text-secondary);
}

.hero-mini-stats {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-mini-stats span {
  font-size: 12px;
  color: #7f1d1d;
  background: #fff5f5;
  border: 1px solid #fecaca;
  border-radius: 999px;
  padding: 4px 10px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.bt-chip {
  margin-left: 8px;
  vertical-align: middle;
}

.kpi-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.kpi-card {
  min-height: 86px;
  display: grid;
  align-content: start;
  gap: 6px;
}

.kpi-card.focus {
  background:
    radial-gradient(circle at top right, rgba(220, 38, 38, 0.12), transparent 42%),
    linear-gradient(180deg, #ffffff, #fff7f7);
}

.kpi-card p {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
}

.kpi-card strong {
  font-size: 29px;
  letter-spacing: -0.03em;
  line-height: 1;
}

.kpi-card small {
  color: var(--text-secondary);
  font-size: 12px;
}

.analytics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.analytics-panel {
  display: grid;
  gap: 12px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 16px;
}

.panel-badge {
  font-size: 11px;
  color: #7f1d1d;
  border: 1px solid #fecaca;
  background: #fff5f5;
  border-radius: 999px;
  padding: 4px 9px;
}

.score-rows,
.latency-rows,
.failure-list {
  display: grid;
  gap: 10px;
}

.score-row,
.failure-item {
  display: grid;
  gap: 6px;
}

.score-top,
.failure-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.score-bar {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #f8d7da;
  overflow: hidden;
}

.score-bar > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.score-bar.danger > span {
  background: linear-gradient(90deg, #f97316, #dc2626);
}

.latency-row {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr) 88px;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.latency-label,
.latency-value {
  color: var(--text-secondary);
}

.latency-track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #f8d7da;
  overflow: hidden;
}

.latency-track > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #fb7185, #ef4444);
}

.empty-note {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.eval-config-card {
  display: grid;
  gap: 12px;
}

.eval-config-card h3 {
  margin: 0;
}

.config-lead {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px 20px;
  margin: 0;
}

.config-grid > div {
  margin: 0;
}

.config-grid .span-2 {
  grid-column: 1 / -1;
}

.config-grid dt {
  font-size: 12px;
  opacity: 0.7;
  margin-bottom: 4px;
}

.config-grid dd {
  margin: 0;
  font-size: 14px;
}

.subhead {
  margin: 8px 0 0;
  font-size: 15px;
}

.player-list {
  margin: 0;
  padding-left: 1.2rem;
}

.id-tag,
.th-dim,
.dim {
  opacity: 0.75;
  font-size: 12px;
}

.id-tag::before {
  content: ' ';
}

.hint {
  margin: 0;
  font-size: 12px;
  opacity: 0.8;
}

.detail-board .section-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bt-table .player-col {
  min-width: 180px;
  max-width: 260px;
  vertical-align: top;
}

.th-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.th-model {
  font-size: 12px;
  word-break: break-all;
}

.input-cell {
  max-width: 220px;
  font-size: 13px;
  word-break: break-word;
}

.player-cell {
  vertical-align: top;
  font-size: 12px;
}

.verdict {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 999px;
  font-weight: 700;
  margin-bottom: 6px;
}

.verdict.ok {
  color: #166534;
  background: #dcfce7;
}

.verdict.bad {
  color: #991b1b;
  background: #fee2e2;
}

.output-details {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 6px 8px;
  background: #fff;
}

.output-details summary {
  cursor: pointer;
  font-size: 12px;
}

.output-body {
  margin-top: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px;
  overflow: auto;
  font-size: 12px;
}

.err-line {
  margin-top: 6px;
  color: #dc2626;
  font-size: 11px;
  word-break: break-word;
}

.meta-line {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 11px;
}

.footnote {
  margin: 10px 0 0;
  font-size: 12px;
  opacity: 0.7;
}

.model-cell {
  font-size: 13px;
  max-width: 200px;
  word-break: break-word;
}

.output-cell {
  max-width: 360px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
}

.det-compare-panel {
  display: grid;
  gap: 12px;
}

.compare-builder {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 10px;
  align-items: end;
}

.compare-builder label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.det-compare-result {
  display: grid;
  gap: 10px;
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

.compare-headline p {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.compare-headline-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px 12px;
}

.compare-meta {
  font-size: 12px !important;
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
  margin-bottom: 8px;
}

.compare-table {
  min-width: 620px;
}

.compare-summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

@media (max-width: 1400px) {
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .run-hero {
    display: grid;
  }
}

@media (max-width: 640px) {
  .kpi-strip {
    grid-template-columns: 1fr;
  }

  .compare-builder {
    grid-template-columns: 1fr;
  }
}
</style>
