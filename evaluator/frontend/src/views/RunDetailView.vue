<template>
  <section class="dashboard-page">
    <article class="surface intro-card">
      <div>
        <h2>运行详情 · Run #{{ runId }}</h2>
        <p v-if="currentRun">
          所属 task #{{ currentRun.taskId }} ·
          <span class="status" :class="statusClass(currentRun.status)">{{ currentRun.status }}</span>
          · {{ currentRun.successCount ?? 0 }}/{{ currentRun.totalCount ?? 0 }} 成功
          <span v-if="currentRun.failCount"> · {{ currentRun.failCount }} 失败</span>
          <span v-if="isBtRun" class="chip bt-chip">Bradley-Terry 多模型</span>
          <span v-else-if="isSingleModelRun" class="chip">单模型 / Agent</span>
        </p>
        <p v-else class="notice-text">{{ noticeText || '加载中...' }}</p>
      </div>
      <div class="inline-actions">
        <RouterLink to="/tasks" class="link-btn">← 返回任务列表</RouterLink>
        <RouterLink :to="`/runs/${runId}/monitor`" class="link-btn">样本监控 →</RouterLink>
        <button class="ghost" @click="refresh">刷新</button>
      </div>
    </article>

    <article v-if="task" class="surface eval-config-card">
      <h3>评测配置</h3>
      <p class="config-lead">
        与创建任务时一致；BT 模式下每条样本会先由各 player 独立生成回答，再由 judge 做成对比较与拟合排行。
      </p>
      <dl class="config-grid">
        <div><dt>任务名称</dt><dd>{{ task.taskName }}</dd></div>
        <div><dt>数据集</dt><dd class="mono">{{ task.datasetId }}</dd></div>
        <div><dt>Agent 版本</dt><dd>{{ task.agentVersion }}</dd></div>
        <div><dt>评估模式</dt><dd>{{ task.evaluationMode }}</dd></div>
        <div><dt>评估方式</dt><dd>{{ task.evaluationMethod }}</dd></div>
        <div><dt>评估维度</dt><dd class="mono">{{ task.evaluationDimensions || '—' }}</dd></div>
        <div v-if="task.strategyVersion != null"><dt>策略版本</dt><dd>#{{ task.strategyVersion }}</dd></div>
        <div v-if="task.metricSet"><dt>metricSet</dt><dd class="mono">{{ task.metricSet }}</dd></div>
      </dl>
      <template v-if="isBtRun">
        <h4 class="subhead">BT 多模型</h4>
        <dl class="config-grid">
          <div class="span-2">
            <dt>Player（按槽位顺序）</dt>
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
            <dt>Judge</dt>
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
            <dt>位置 swap</dt>
            <dd>{{ task.positionSwapEnabled ? '已启用（每对双跑，成本约翻倍）' : '未启用' }}</dd>
          </div>
        </dl>
      </template>
    </article>

    <section class="metric-grid">
      <article class="surface metric-card" v-for="metric in metricCards" :key="metric.label">
        <p>{{ metric.label }}</p>
        <strong>{{ metric.value }}</strong>
      </article>
    </section>

    <BtRatingsPanel v-if="isBtRun" :run-id="runId" />

    <article class="surface detail-board">
      <div class="section-head">
        <h3>{{ isBtRun ? '样本结果（按数据集条目 · 多 Player 并列）' : '样本结果' }}（共 {{ recordSummaryCount }} 条）</h3>
        <p v-if="isBtRun" class="hint">
          每行对应数据集中的一个输入；列为各 player 的生成结果。下方「原始行数」含每个 player 一条 QA 记录，与后端存储一致。
        </p>
      </div>

      <!-- BT：按样本分组 -->
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
                  <span>Player {{ idx + 1 }}</span>
                  <span class="mono th-model">{{ slot.modelId }}</span>
                  <span v-if="slot.displayName" class="th-dim">{{ slot.displayName }}</span>
                  <span v-else-if="slot.fallbackId != null" class="th-dim">profile #{{ slot.fallbackId }}</span>
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
        <p class="footnote">QA 原始记录行数：{{ runRecords.length }}（≈ 样本数 × {{ btPlayerSlotCount }} 个 player）</p>
      </div>

      <!-- 非 BT：扁平表 -->
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
              <td>{{ record.input }}</td>
              <td>{{ record.expectedOutput }}</td>
              <td class="output-cell">{{ truncate(record.actualOutput, 200) }}</td>
              <td>{{ record.errorCode ? '失败' : '通过' }}</td>
              <td>{{ record.errorMessage || '—' }}</td>
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
  listModels,
  type EvalRun,
  type EvalTask,
  type ModelProfile,
  type QaRecord,
  type RunMetrics,
  type TaskStatus,
} from '../api/client';

const props = defineProps<{ runId: number }>();

const currentRun = ref<EvalRun | null>(null);
const task = ref<EvalTask | null>(null);
const runRecords = ref<QaRecord[]>([]);
const models = ref<ModelProfile[]>([]);
const runMetrics = ref<RunMetrics | null>(null);
const noticeText = ref('');

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

const playerProfiles = computed((): ModelProfile[] => {
  const ids = task.value?.selectedModelIds;
  if (!ids?.length) return [];
  const map = new Map(models.value.map((m) => [m.modelProfileId, m]));
  return ids.map((id) => map.get(id)).filter((m): m is ModelProfile => m != null);
});

/** 分组宽度：优先已解析的 profile 数，否则用任务里选的 player 个数（避免模型列表未加载时表格为空） */
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

/** 表头列：有 ModelProfile 用 modelId，否则用 #id 占位 */
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

const metricCards = computed(() => {
  const m = runMetrics.value;
  return [
    { label: '任务成功率', value: m ? `${(m.taskCompletionRate * 100).toFixed(1)}%` : '-' },
    { label: '首字延迟 P95', value: m ? `${m.firstTokenP95}ms` : '-' },
    { label: '端到端 P95', value: m ? `${m.endToEndP95}ms` : '-' },
    { label: '总 Token', value: m ? m.totalTokens.toLocaleString() : '-' },
  ];
});

watch(() => props.runId, () => void refresh());

onMounted(() => {
  void refresh();
});

onBeforeUnmount(() => {
  closeStream();
});

function modelLabel(id: number | null): string {
  if (id == null) return '—';
  const m = models.value.find((x) => x.modelProfileId === id);
  if (m) return `${m.displayName} (${m.modelId})`;
  return `#${id}`;
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

function truncate(text: string, max: number): string {
  if (!text) return '—';
  return text.length <= max ? text : text.substring(0, max) + '...';
}
</script>

<style scoped>
.bt-chip {
  margin-left: 8px;
  vertical-align: middle;
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
  min-width: 160px;
  max-width: 220px;
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
  max-width: 200px;
  font-size: 13px;
  word-break: break-word;
}

.player-cell {
  vertical-align: top;
  font-size: 12px;
}

.verdict {
  font-weight: 600;
  margin-bottom: 6px;
}

.verdict.ok {
  color: var(--success, #3ecf8e);
}

.verdict.bad {
  color: var(--danger, #f07178);
}

.output-details {
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  padding: 6px 8px;
  background: rgba(0, 0, 0, 0.15);
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
  color: var(--danger, #f07178);
  font-size: 11px;
  word-break: break-word;
}

.meta-line {
  margin-top: 6px;
  opacity: 0.65;
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
</style>
