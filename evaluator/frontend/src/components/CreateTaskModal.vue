<template>
  <div v-if="open" class="modal-mask create-mask" @click="close">
    <article class="create-modal-wide" @click.stop>
      <header class="ct-head">
        <div class="ct-head-titles">
          <span class="ct-kicker">Configure</span>
          <h3>Configure Evaluator</h3>
          <p class="ct-sub">左侧配置 Evaluator(模型与评测参数),右侧选择数据集来源与样本。</p>
        </div>
        <div class="ct-head-actions">
          <button type="button" class="ghost" @click="close">Cancel</button>
          <button type="button" class="primary" @click="createAndStart">Save</button>
        </div>
      </header>

      <div class="ct-columns">
        <section class="ct-col ct-col-evaluator">
          <h4 class="ct-col-title">Evaluator</h4>
          <div class="form-layout">
            <label>
              Name
              <input v-model="form.taskName" type="text" placeholder="例如:TripAgent-五月回归评测" />
            </label>

            <label>
              Application
              <input v-model="form.agentVersion" type="text" placeholder="Agent 版本,如 1.0.0" />
            </label>

            <div class="model-launch-row">
              <button type="button" class="model-pill" @click="showInference = true">
                <span class="pill-icon">⚙</span>
                <span class="pill-text">Prompt &amp; Model</span>
                <span class="pill-hint">{{ inferenceSummary }}</span>
                <span class="pill-arrow">›</span>
              </button>
            </div>

            <details class="advanced-block" :open="advancedOpen">
              <summary @click.prevent="advancedOpen = !advancedOpen">
                <span>Advanced · 评测模式 / 方法 / 维度</span>
                <span class="adv-chev">{{ advancedOpen ? '▾' : '▸' }}</span>
              </summary>

              <div class="adv-body form-layout">
                <label>
                  评测模式
                  <select v-model="form.evaluationMode">
                    <option value="RESULT">RESULT(结果评测)</option>
                    <option value="PROCESS">PROCESS(过程评测)</option>
                  </select>
                </label>

                <label>
                  评测方法
                  <select v-model="form.evaluationMethod">
                    <option value="DETERMINISTIC">DETERMINISTIC(规则)</option>
                    <option value="JUDGE">JUDGE(LLM 裁判)</option>
                    <option value="HYBRID">HYBRID(混合)</option>
                  </select>
                </label>

                <label>
                  评测维度(逗号分隔)
                  <input
                    v-model="form.evaluationDimensions"
                    type="text"
                    placeholder="effectiveness,safety,performance"
                  />
                </label>

                <div v-if="form.evaluationMethod !== 'DETERMINISTIC'" class="bt-config-block">
                  <strong>多模型对比(BT)</strong>
                  <ModelPickerSection
                    v-model:selectedPlayerIds="form.selectedPlayerIds"
                    v-model:judgeId="form.judgeModelId"
                  />
                  <label>
                    对比采样策略
                    <select v-model="form.comparisonSamplingStrategy">
                      <option value="ALL_PAIRS">ALL_PAIRS(全对比较)</option>
                    </select>
                  </label>
                  <label class="inline-check">
                    <input v-model="form.positionSwapEnabled" type="checkbox" />
                    启用位置交换(降低顺序偏置)
                  </label>
                  <p class="notice-text" :class="willTriggerBt ? 'bt-ready' : ''">
                    {{ willTriggerBt ? '✓ 将走 BT 多模型流程' : '× 未触发 BT(需 ≥2 player + 1 judge)' }}
                  </p>
                </div>

                <label>
                  策略版本(可选)
                  <input v-model.number="form.strategyVersion" type="number" min="1" />
                </label>

                <label>
                  指标集 ID(可选,逗号分隔)
                  <input v-model="form.metricSet" type="text" placeholder="如 1,2" />
                </label>
              </div>
            </details>
          </div>
        </section>

        <section class="ct-col ct-col-dataset">
          <div class="ct-col-head">
            <h4 class="ct-col-title">Projects &amp; Datasets</h4>
            <label class="enabled-toggle">
              <span>Enabled</span>
              <input v-model="enabledFlag" type="checkbox" />
              <span class="enabled-track" :class="{ on: enabledFlag }"><span class="enabled-thumb"></span></span>
            </label>
          </div>
          <div class="form-layout">
            <label>
              Source
              <div class="inline-actions dataset-select-row">
                <select v-model="form.datasetId">
                  <option value="">— 请选择数据集 —</option>
                  <option v-for="d in datasets" :key="d.datasetId" :value="d.name">
                    {{ d.name }}{{ d.displayName ? `(${d.displayName})` : '' }} · {{ d.source }} · {{
                      d.sampleCount ?? 0
                    }}条
                  </option>
                </select>
                <button type="button" class="ghost" @click="loadDatasets">刷新</button>
              </div>
            </label>

            <div class="sample-data-card">
              <div class="sample-title">Sample data</div>
              <p class="sample-desc">从评测数据集中读取的样例,用于 prompt 调试与 dry-run 校验。</p>
            </div>

            <p v-if="datasetError" class="notice-text err">加载数据集失败:{{ datasetError }}</p>
            <p v-else-if="!datasets.length" class="notice-text">
              当前没有可用数据集,请先去
              <RouterLink to="/datasets" @click="close">数据集管理</RouterLink>
              页面上传。
            </p>
            <p v-else class="hint-muted">已加载 {{ datasets.length }} 个数据集,可直接用于本次评测。</p>
          </div>
        </section>
      </div>

      <p v-if="notice" class="notice-text">{{ notice }}</p>

      <footer class="ct-foot">
        <button type="button" class="ghost" @click="createOnly">仅创建(不启动)</button>
        <button type="button" class="primary" @click="createAndStart">创建并启动</button>
      </footer>

      <ModelInferenceModal v-model:open="showInference" v-model="inference" />
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';

import ModelInferenceModal, { type InferenceConfig } from './ModelInferenceModal.vue';
import ModelPickerSection from './ModelPickerSection.vue';
import { createTask, listDatasets, startTask, type Dataset, type EvaluationMethod, type EvaluationMode } from '../api/client';

const props = defineProps<{
  open: boolean;
  initialPreset?: string | null;
}>();

const emit = defineEmits<{
  'update:open': [open: boolean];
  created: [task: { taskId: number; taskName: string }];
}>();

const router = useRouter();
const datasets = ref<Dataset[]>([]);
const datasetError = ref('');
const notice = ref('');
const showInference = ref(false);
const advancedOpen = ref(true);
const enabledFlag = ref(true);

const inference = ref<InferenceConfig>({
  temperature: 0.7,
  maxTokens: 1024,
  timeoutSeconds: 60,
  maxRetries: 2,
  extraJson: '',
});

const form = reactive({
  taskName: '',
  agentVersion: '1.0.0',
  datasetId: '',
  metricSet: '',
  evaluationMode: 'RESULT' as EvaluationMode,
  evaluationMethod: 'JUDGE' as EvaluationMethod,
  evaluationDimensions: 'effectiveness,safety,performance',
  strategyVersion: null as number | null,
  selectedPlayerIds: [] as number[],
  judgeModelId: null as number | null,
  comparisonSamplingStrategy: 'ALL_PAIRS' as 'ALL_PAIRS',
  positionSwapEnabled: true,
});

const willTriggerBt = computed(() => {
  const selectedPlayers = form.selectedPlayerIds.filter((id) => id > 0);
  return (
    (form.evaluationMethod === 'JUDGE' || form.evaluationMethod === 'HYBRID') &&
    selectedPlayers.length >= 2 &&
    form.judgeModelId != null
  );
});

const inferenceSummary = computed(() => {
  const { temperature, maxTokens, extraJson } = inference.value;
  let providerLabel = '';
  if (extraJson && extraJson.trim()) {
    try {
      const parsed = JSON.parse(extraJson) as Record<string, unknown>;
      const provider = typeof parsed.provider === 'string' ? parsed.provider : '';
      const model = typeof parsed.model === 'string' ? parsed.model : '';
      if (provider || model) {
        providerLabel = [provider, model].filter(Boolean).join(' · ') + ' · ';
      }
    } catch {
      // ignore
    }
  }
  return `${providerLabel}T=${temperature}${maxTokens != null ? ` · max ${maxTokens}` : ''}`;
});

watch(
  () => props.open,
  async (v) => {
    if (!v) return;
    notice.value = '';
    resetFormDefaults();
    await loadDatasets();
    applyPreset(props.initialPreset ?? undefined);
  },
);

function resetFormDefaults(): void {
  form.taskName = '';
  form.agentVersion = '1.0.0';
  form.datasetId = '';
  form.metricSet = '';
  form.evaluationMode = 'RESULT';
  form.evaluationMethod = 'JUDGE';
  form.evaluationDimensions = 'effectiveness,safety,performance';
  form.strategyVersion = null;
  form.selectedPlayerIds = [];
  form.judgeModelId = null;
  form.comparisonSamplingStrategy = 'ALL_PAIRS';
  form.positionSwapEnabled = true;
  inference.value = {
    temperature: 0.7,
    maxTokens: 1024,
    timeoutSeconds: 60,
    maxRetries: 2,
    extraJson: '',
  };
  advancedOpen.value = true;
  enabledFlag.value = true;
}

function applyPreset(preset?: string): void {
  if (!preset) return;
  switch (preset) {
    case 'bt':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      form.evaluationDimensions = 'effectiveness,safety,performance';
      form.positionSwapEnabled = true;
      break;
    case 'result-judge':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      break;
    case 'process-judge':
      form.evaluationMode = 'PROCESS';
      form.evaluationMethod = 'JUDGE';
      break;
    case 'deterministic':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'DETERMINISTIC';
      form.evaluationDimensions = 'effectiveness';
      break;
    case 'hybrid':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'HYBRID';
      break;
    case 'safety':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      form.evaluationDimensions = 'safety';
      break;
    case 'effectiveness':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      form.evaluationDimensions = 'effectiveness';
      break;
    case 'performance':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      form.evaluationDimensions = 'performance';
      break;
    case 'full-dims':
      form.evaluationDimensions = 'effectiveness,safety,performance';
      break;
    case 'judge-single':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      break;
    default:
      break;
  }
}

async function loadDatasets(): Promise<void> {
  try {
    datasetError.value = '';
    datasets.value = await listDatasets({ enabledOnly: true });
    if (!form.datasetId && datasets.value.length) {
      const trip = datasets.value.find((d) => d.name.includes('trip')) || datasets.value[0];
      form.datasetId = trip.name;
    }
  } catch (err: any) {
    datasets.value = [];
    datasetError.value = err.message || String(err);
  }
}

function buildStrategyConfigJson(): string {
  const inferencePayload: Record<string, unknown> = {
    temperature: inference.value.temperature,
    timeoutSeconds: inference.value.timeoutSeconds,
    maxRetries: inference.value.maxRetries,
  };
  if (inference.value.maxTokens != null && !Number.isNaN(inference.value.maxTokens)) {
    inferencePayload.maxTokens = inference.value.maxTokens;
  }
  if (inference.value.extraJson.trim()) {
    try {
      inferencePayload.extra = JSON.parse(inference.value.extraJson);
    } catch {
      inferencePayload.extraRaw = inference.value.extraJson;
    }
  }
  return JSON.stringify({ inference: inferencePayload });
}

function close(): void {
  emit('update:open', false);
}

async function createOnly(): Promise<{ taskId: number; taskName: string } | null> {
  try {
    const selectedPlayers = form.selectedPlayerIds.filter((id) => id > 0);
    const isBt =
      (form.evaluationMethod === 'JUDGE' || form.evaluationMethod === 'HYBRID') &&
      selectedPlayers.length >= 2 &&
      form.judgeModelId != null;

    const created = await createTask({
      taskName: form.taskName,
      agentVersion: form.agentVersion,
      datasetId: form.datasetId,
      metricSet: form.metricSet || undefined,
      evaluationMode: form.evaluationMode,
      evaluationMethod: form.evaluationMethod,
      evaluationDimensions: form.evaluationDimensions,
      strategyVersion: form.strategyVersion || undefined,
      strategyConfig: buildStrategyConfigJson(),
      selectedModelIds: selectedPlayers.length > 0 ? selectedPlayers : undefined,
      judgeModelId: isBt ? form.judgeModelId || undefined : undefined,
      comparisonSamplingStrategy: isBt ? form.comparisonSamplingStrategy : undefined,
      positionSwapEnabled: isBt ? form.positionSwapEnabled : undefined,
    });
    notice.value = `创建成功：${created.taskName}`;
    emit('created', { taskId: created.taskId, taskName: created.taskName });
    close();
    return created;
  } catch (err: any) {
    notice.value = `创建失败：${err.message || String(err)}`;
    return null;
  }
}

async function createAndStart(): Promise<void> {
  const created = await createOnly();
  if (!created) return;
  try {
    const run = await startTask(created.taskId);
    await router.push({ name: 'RunDetail', params: { runId: run.runId } });
  } catch (err: any) {
    notice.value = `启动失败：${err.message || String(err)}`;
  }
}
</script>

<style scoped>
.create-mask {
  z-index: 80;
}

.create-modal-wide {
  width: min(1180px, calc(100vw - 32px));
  max-height: calc(100vh - 40px);
  overflow: auto;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--line);
  box-shadow: 0 26px 60px rgba(127, 29, 29, 0.18);
  padding: 18px 20px 20px;
}

.ct-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.ct-head-titles {
  min-width: 0;
}

.ct-kicker {
  display: inline-block;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--brand);
}

.ct-head h3 {
  margin: 4px 0 0;
  font-size: 20px;
}

.ct-sub {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.ct-head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.ct-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 14px;
}

.ct-col {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.ct-col-evaluator {
  background: #fff;
}

.ct-col-dataset {
  background: var(--bg-elevated);
}

.ct-col-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.ct-col-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
}

.ct-col-head .ct-col-title {
  margin-bottom: 0;
}

.enabled-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary);
  user-select: none;
}

.enabled-toggle input {
  display: none;
}

.enabled-track {
  width: 32px;
  height: 18px;
  border-radius: 999px;
  background: var(--line-strong);
  position: relative;
  transition: background 0.15s;
}

.enabled-track.on {
  background: var(--brand);
}

.enabled-thumb {
  position: absolute;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  top: 2px;
  left: 2px;
  transition: left 0.15s;
}

.enabled-track.on .enabled-thumb {
  left: 16px;
}

.model-launch-row {
  display: flex;
}

.model-pill {
  width: 100%;
  border: 1px solid #fecaca;
  background: #fff5f5;
  color: #991b1b;
  border-radius: 11px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
}

.model-pill:hover {
  border-color: #ef4444;
  box-shadow: 0 6px 16px rgba(239, 68, 68, 0.18);
}

.pill-icon {
  font-size: 16px;
}

.pill-text {
  font-weight: 700;
}

.pill-hint {
  margin-left: auto;
  color: #7f1d1d;
  font-size: 12px;
}

.pill-arrow {
  color: #b91c1c;
  font-weight: 700;
  margin-left: 4px;
}

.advanced-block {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 0;
  background: #fff;
}

.advanced-block > summary {
  list-style: none;
  cursor: pointer;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  border-radius: 10px;
}

.advanced-block > summary::-webkit-details-marker {
  display: none;
}

.advanced-block > summary:hover {
  background: var(--bg-elevated);
}

.adv-chev {
  color: var(--brand);
  font-size: 12px;
}

.adv-body {
  padding: 0 12px 12px;
}

.bt-config-block {
  border: 1px dashed #fecaca;
  border-radius: 10px;
  padding: 10px;
  display: grid;
  gap: 10px;
  background: #fffafa;
}

.bt-config-block strong {
  font-size: 13px;
}

.dataset-select-row {
  align-items: center;
}

.dataset-select-row select {
  flex: 1;
}

.sample-data-card {
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  padding: 12px;
  background: #fff;
}

.sample-title {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 4px;
}

.sample-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.hint-muted {
  font-size: 12px;
  color: var(--text-secondary);
}

.notice-text.err {
  color: #b91c1c;
}

.ct-foot {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 960px) {
  .ct-columns {
    grid-template-columns: 1fr;
  }
}
</style>
