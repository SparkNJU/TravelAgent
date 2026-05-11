<template>
  <div v-if="open" class="modal-mask create-mask" @click="close">
    <article class="create-drawer" @click.stop>
      <header class="ct-head">
        <div class="ct-head-titles">
          <span class="ct-kicker">创建任务</span>
          <h3>{{ currentStep === 1 ? '开始 AI 评测' : '创建评测任务' }}</h3>
          <p class="ct-sub">
            {{
              currentStep === 1
                ? '请先选择评测模式。第 2 步会展示对应的配置项。'
                : '填写任务信息后，可一键应用本地评测配置。'
            }}
          </p>
        </div>
        <button type="button" class="ghost icon-close" @click="close">×</button>
      </header>

      <div class="ct-body">
        <section v-if="currentStep === 1" class="mode-step">
          <p class="mode-kicker">第 1 步 / 共 2 步</p>
          <h4>选择评测模式</h4>
          <p class="mode-desc">支持自定义模式，并可在第 2 步一键应用已保存的本地评测配置。</p>

          <div class="mode-grid">
            <button
              v-for="option in modeOptions"
              :key="option.value"
              type="button"
              class="mode-card"
              :class="{ active: selectedMode === option.value }"
              @click="selectMode(option.value)"
            >
              <span class="mode-icon">{{ option.icon }}</span>
              <span class="mode-copy">
                <strong>{{ option.title }}</strong>
                <small>{{ option.desc }}</small>
              </span>
            </button>
          </div>
        </section>

        <template v-else>
          <div class="ct-columns">
            <section class="ct-col ct-col-evaluator">
              <div class="ct-col-head">
                <h4 class="ct-col-title">评测器</h4>
                <span class="mode-badge">{{ currentModeTitle }}</span>
              </div>

              <div class="form-layout">
                <label>
                  本地评测配置
                  <div class="inline-actions">
                    <select v-model.number="selectedStrategyId">
                      <option :value="0">- 不使用本地配置 -</option>
                      <option v-for="item in strategies" :key="item.strategyId" :value="item.strategyId">
                        {{ item.strategyName }}
                      </option>
                    </select>
                    <button type="button" class="ghost" :disabled="!selectedStrategyId" @click="applySelectedStrategyConfig">
                      一键应用
                    </button>
                  </div>
                  <small class="field-hint">应用后将自动带入模式、方法、维度、模型、指标和推理参数。</small>
                </label>

                <label>
                  评测名称
                  <input v-model="form.taskName" type="text" placeholder="例如：TripAgent 回归评测" />
                </label>

                <label>
                  应用版本
                  <input v-model="form.agentVersion" type="text" placeholder="应用版本，例如 1.0.0" />
                </label>

                <div v-if="isDeterministic" class="model-launch-row">
                  <button type="button" class="model-pill" @click="showInference = true">
                    <span class="pill-icon">⚙</span>
                    <span class="pill-text">Model配置</span>
                    <span class="pill-hint">{{ inferenceSummary }}</span>
                    <span class="pill-arrow">→</span>
                  </button>
                </div>
                <p v-if="isDeterministic" class="mode-note">
                  {{
                    inferenceModelProfileId
                      ? 'Deterministic模式会使用这里选择的单模型与推理参数。'
                      : '请先在Model配置中选择单模型；未选择时将使用系统默认参数。'
                  }}
                </p>

                <div v-if="!isDeterministic" class="bt-config-block">
                  <strong>参评模型 / 裁判模型</strong>
                  <ModelPickerSection
                    v-model:selectedPlayerIds="form.selectedPlayerIds"
                    v-model:judgeId="form.judgeModelId"
                  />
                  <label>
                    对比采样策略
                    <select v-model="form.comparisonSamplingStrategy">
                      <option value="ALL_PAIRS">全对比较</option>
                    </select>
                  </label>
                  <label class="inline-check">
                    <input v-model="form.positionSwapEnabled" type="checkbox" />
                    启用位置互换（降低顺序偏置）
                  </label>
                  <p class="notice-text" :class="willTriggerBt ? 'bt-ready' : ''">
                    {{ willTriggerBt ? '将触发 BT 多模型流程。' : 'BT 至少需要 2 个参赛模型 + 1 个裁判模型。' }}
                  </p>
                </div>

                <details class="advanced-block" :open="advancedOpen">
                  <summary @click.prevent="advancedOpen = !advancedOpen">
                    <span>高级 · 评测维度 / 参数</span>
                    <span class="adv-chev">{{ advancedOpen ? '▲' : '▼' }}</span>
                  </summary>

                  <div class="adv-body form-layout">
                    <label>
                      评测模式
                      <select v-if="isCustomMode" v-model="form.evaluationMode">
                        <option value="RESULT">RESULT模式</option>
                        <option value="PROCESS">PROCESS模式</option>
                      </select>
                      <input v-else :value="evaluationModeLabel(form.evaluationMode)" type="text" readonly />
                    </label>

                    <label>
                      评测方法
                      <select v-if="isCustomMode" v-model="form.evaluationMethod">
                        <option value="DETERMINISTIC">DETERMINISTIC模式</option>
                        <option value="JUDGE">JUDGE模式</option>
                        <option value="HYBRID">HYBRID模式</option>
                      </select>
                      <input v-else :value="evaluationMethodLabel(form.evaluationMethod)" type="text" readonly />
                    </label>

                    <div class="field-block">
                      <span>评测维度</span>
                      <details class="dimension-picker" :open="dimensionPickerOpen">
                        <summary @click.prevent="dimensionPickerOpen = !dimensionPickerOpen">
                          <span>{{ selectedDimensionsSummary }}</span>
                          <span class="adv-chev">{{ dimensionPickerOpen ? '▲' : '▼' }}</span>
                        </summary>
                        <div class="dimension-picker-menu">
                          <button
                            v-for="dimension in dimensionOptions"
                            :key="dimension.value"
                            type="button"
                            class="dimension-chip"
                            :class="{ active: selectedDimensions.includes(dimension.value) }"
                            @click="toggleDimension(dimension.value)"
                          >
                            <span class="chip-title">{{ dimension.label }}</span>
                            <small class="chip-desc">{{ dimension.desc }}</small>
                          </button>
                        </div>
                      </details>
                    </div>

                    <label>
                      指标集
                      <div class="inline-actions">
                        <select v-model.number="selectedMetricId" :disabled="!customMetrics.length">
                          <option :value="0">- 请选择指标 -</option>
                          <option
                            v-for="metric in customMetrics"
                            :key="metric.customMetricId"
                            :value="metric.customMetricId"
                          >
                            {{ metric.metricName }} · {{ metric.metricType }} · 阈值={{ metric.thresholdValue ?? '-' }}
                          </option>
                        </select>
                        <button type="button" class="ghost" :disabled="!customMetrics.length" @click="selectAllMetrics">
                          一键全选
                        </button>
                      </div>
                    </label>
                  </div>
                </details>
              </div>
            </section>

            <section class="ct-col ct-col-dataset">
              <div class="ct-col-head">
                <h4 class="ct-col-title">项目与数据集</h4>
                <label class="enabled-toggle">
                  <span>启用</span>
                  <input v-model="enabledFlag" type="checkbox" />
                  <span class="enabled-track" :class="{ on: enabledFlag }"><span class="enabled-thumb"></span></span>
                </label>
              </div>

              <div class="form-layout">
                <label>
                  数据源
                  <div class="inline-actions dataset-select-row">
                    <select v-model="form.datasetId">
                      <option value="">- 请选择数据集 -</option>
                      <option v-for="d in datasets" :key="d.datasetId" :value="d.name">
                        {{ d.name }}{{ d.displayName ? ` (${d.displayName})` : '' }} · {{ d.source }} · {{ d.sampleCount ?? 0 }} 条
                      </option>
                    </select>
                    <button type="button" class="ghost" @click="loadDatasets">刷新</button>
                  </div>
                </label>

                <div class="sample-data-card">
                  <div class="sample-title">样例数据</div>
                  <p class="sample-desc">用于快速预览当前数据集样例，方便做提示词干跑校验。</p>
                </div>

                <p v-if="datasetError" class="notice-text err">数据集加载失败：{{ datasetError }}</p>
                <p v-else-if="!datasets.length" class="notice-text">
                  当前无可用数据集，请先到
                  <RouterLink to="/datasets" @click="close">数据集管理</RouterLink>
                  页面上传。
                </p>
                <p v-else class="hint-muted">已加载 {{ datasets.length }} 个数据集，可直接用于本次评测。</p>
              </div>
            </section>
          </div>

          <p v-if="notice" class="notice-text">{{ notice }}</p>
        </template>
      </div>

      <footer class="ct-foot">
        <section class="ct-foot-note">
          <span v-if="currentStep === 1">先选择模式，再进入详细配置。</span>
          <span v-else>可随时返回第 1 步切换模式。</span>
        </section>
        <section class="ct-foot-actions">
          <button v-if="currentStep === 1" type="button" class="ghost" @click="close">取消</button>
          <button v-if="currentStep === 1" type="button" class="primary" @click="goStep2">下一步</button>

          <button v-if="currentStep === 2" type="button" class="ghost" @click="goStep1">上一步</button>
          <button v-if="currentStep === 2" type="button" class="ghost" @click="createOnly">仅创建</button>
          <button v-if="currentStep === 2" type="button" class="primary" @click="createAndStart">创建并启动</button>
        </section>
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
import {
  createTask,
  listCustomMetrics,
  listDatasets,
  listStrategies,
  startTask,
  type CustomMetric,
  type Dataset,
  type EvalStrategy,
  type EvaluationMethod,
  type EvaluationMode,
} from '../api/client';

type ModeChoice = 'deterministic' | 'result-judge' | 'process-judge' | 'hybrid' | 'bt' | 'custom';

const modeOptions: Array<{ value: ModeChoice; icon: string; title: string; desc: string }> = [
  { value: 'deterministic', icon: 'D', title: 'DETERMINISITC:确定性评测', desc: '规则评测，单模型推理。' },
  { value: 'result-judge', icon: 'R', title: 'RESULT-JUDGE:结果 + 裁判', desc: '结果评测，LLM 裁判。' },
  { value: 'process-judge', icon: 'P', title: 'PROCESS-JUDGE:过程 + 裁判', desc: '过程评测，关注工具轨迹。' },
  { value: 'hybrid', icon: 'H', title: 'HYBRID:混合评测', desc: '规则与语义联合评测。' },
  { value: 'bt', icon: 'B', title: 'BT:多模型对比', desc: '参赛模型/裁判模型对比评测。' },
  { value: 'custom', icon: 'C', title: 'CUSTOM:自定义', desc: '手动配置并支持一键应用本地评测配置。' },
];

const dimensionOptions = [
  { value: 'effectiveness', label: 'efficiency', desc: '任务完成质量。' },
  { value: 'safety', label: 'safety', desc: '安全与风险控制。' },
  { value: 'performance', label: 'performance', desc: '延迟与资源效率。' },
];

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
const strategies = ref<EvalStrategy[]>([]);
const customMetrics = ref<CustomMetric[]>([]);
const selectedStrategyId = ref(0);
const selectedMetricId = ref(0);
const selectedDimensions = ref<string[]>(['effectiveness', 'safety', 'performance']);
const dimensionPickerOpen = ref(false);
const datasetError = ref('');
const notice = ref('');
const showInference = ref(false);
const advancedOpen = ref(true);
const enabledFlag = ref(true);
const currentStep = ref<1 | 2>(1);
const selectedMode = ref<ModeChoice>('result-judge');

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
  selectedPlayerIds: [] as number[],
  judgeModelId: null as number | null,
  comparisonSamplingStrategy: 'ALL_PAIRS' as 'ALL_PAIRS',
  positionSwapEnabled: true,
});

const isDeterministic = computed(() => form.evaluationMethod === 'DETERMINISTIC');
const isCustomMode = computed(() => selectedMode.value === 'custom');

const currentModeTitle = computed(() => {
  const found = modeOptions.find((x) => x.value === selectedMode.value);
  return found ? found.title : '未知';
});

const willTriggerBt = computed(() => {
  const selectedPlayers = form.selectedPlayerIds.filter((id) => id > 0);
  return (
    (form.evaluationMethod === 'JUDGE' || form.evaluationMethod === 'HYBRID') &&
    selectedPlayers.length >= 2 &&
    form.judgeModelId != null
  );
});

const inferenceModelProfileId = computed(() => getInferenceModelProfileId());

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
  return `${providerLabel}温度=${temperature}${maxTokens != null ? ` · 最大Token ${maxTokens}` : ''}`;
});

const selectedDimensionsSummary = computed(() => {
  if (!selectedDimensions.value.length) {
    return '点击选择评测维度';
  }
  return selectedDimensions.value
    .map((item) => dimensionOptions.find((option) => option.value === item)?.label ?? item)
    .join(', ');
});

function evaluationModeLabel(mode: EvaluationMode | string): string {
  if (mode === 'RESULT') return 'RESULT:结果模式';
  if (mode === 'PROCESS') return 'PROCESS:过程模式';
  return mode;
}

function evaluationMethodLabel(method: EvaluationMethod | string): string {
  if (method === 'DETERMINISTIC') return 'Deterministic:确定性评测';
  if (method === 'JUDGE') return 'JUDGE:裁判评测';
  if (method === 'HYBRID') return 'HYBRID:混合评测';
  return method;
}

type StrategyPresetDefinition = {
  evaluationMode?: EvaluationMode;
  evaluationMethod?: EvaluationMethod;
  evaluationDimensions?: string[] | string;
  metricSet?: string;
  selectedModelIds?: number[];
  judgeModelId?: number | null;
  comparisonSamplingStrategy?: 'ALL_PAIRS' | null;
  positionSwapEnabled?: boolean | null;
  inference?: {
    temperature?: number;
    maxTokens?: number | null;
    timeoutSeconds?: number;
    maxRetries?: number;
    extra?: unknown;
    extraRaw?: string;
  };
};

const selectedStrategy = computed(() =>
  strategies.value.find((item) => item.strategyId === selectedStrategyId.value) ?? null,
);

watch(
  () => props.open,
  async (v) => {
    if (!v) return;
    notice.value = '';
    resetFormDefaults();
    await Promise.all([loadDatasets(), loadStrategyResources()]);
    selectMode(mapPresetToMode(props.initialPreset ?? undefined));
    currentStep.value = 1;
  },
);

watch(selectedMetricId, (id) => {
  form.metricSet = id > 0 ? String(id) : '';
});

watch(selectedDimensions, (items) => {
  form.evaluationDimensions = items.join(',');
});

function resetFormDefaults(): void {
  form.taskName = '';
  form.agentVersion = '1.0.0';
  form.datasetId = '';
  form.metricSet = '';
  form.evaluationMode = 'RESULT';
  form.evaluationMethod = 'JUDGE';
  form.evaluationDimensions = 'effectiveness,safety,performance';
  form.selectedPlayerIds = [];
  form.judgeModelId = null;
  form.comparisonSamplingStrategy = 'ALL_PAIRS';
  form.positionSwapEnabled = true;
  selectedStrategyId.value = 0;
  selectedMetricId.value = 0;
  selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
  dimensionPickerOpen.value = false;
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

function mapPresetToMode(preset?: string): ModeChoice {
  if (!preset) return 'result-judge';
  if (preset === 'deterministic') return 'deterministic';
  if (preset === 'process-judge') return 'process-judge';
  if (preset === 'hybrid') return 'hybrid';
  if (preset === 'bt') return 'bt';
  if (preset === 'custom') return 'custom';
  return 'result-judge';
}

function selectMode(mode: ModeChoice): void {
  selectedMode.value = mode;
  form.selectedPlayerIds = [];
  form.judgeModelId = null;

  switch (mode) {
    case 'deterministic':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'DETERMINISTIC';
      selectedDimensions.value = ['effectiveness'];
      break;
    case 'process-judge':
      form.evaluationMode = 'PROCESS';
      form.evaluationMethod = 'JUDGE';
      selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
      break;
    case 'hybrid':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'HYBRID';
      selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
      break;
    case 'bt':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
      form.positionSwapEnabled = true;
      break;
    case 'custom':
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
      break;
    case 'result-judge':
    default:
      form.evaluationMode = 'RESULT';
      form.evaluationMethod = 'JUDGE';
      selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
      break;
  }
}

function normalizeDimensionToken(raw: string): string | null {
  const normalized = raw.trim().toLowerCase();
  if (!normalized) return null;
  if (normalized === 'effectiveness' || normalized === 'efficiency') return 'effectiveness';
  if (normalized === 'safety') return 'safety';
  if (normalized === 'performance') return 'performance';
  return null;
}

function normalizeDimensionList(raw: string[]): string[] {
  const out: string[] = [];
  for (const item of raw) {
    const normalized = normalizeDimensionToken(item);
    if (!normalized || out.includes(normalized)) continue;
    out.push(normalized);
  }
  return out;
}

function toggleDimension(value: string): void {
  const normalized = normalizeDimensionToken(value);
  if (!normalized) {
    return;
  }
  if (selectedDimensions.value.includes(normalized)) {
    if (selectedDimensions.value.length <= 1) {
      notice.value = '评测维度至少保留 1 项。';
      return;
    }
    selectedDimensions.value = selectedDimensions.value.filter((item) => item !== normalized);
    return;
  }
  selectedDimensions.value = [...selectedDimensions.value, normalized];
}

function goStep2(): void {
  currentStep.value = 2;
}

function goStep1(): void {
  currentStep.value = 1;
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

async function loadStrategyResources(): Promise<void> {
  try {
    const [strategyList, metricList] = await Promise.all([
      listStrategies(),
      listCustomMetrics(true),
    ]);
    strategies.value = strategyList;
    customMetrics.value = metricList;
  } catch (err: any) {
    notice.value = `评测配置/指标加载失败：${err.message || String(err)}`;
  }
}

function selectAllMetrics(): void {
  if (!customMetrics.value.length) {
    form.metricSet = '';
    selectedMetricId.value = 0;
    return;
  }
  const ids = customMetrics.value.map((item) => item.customMetricId);
  form.metricSet = ids.join(',');
  selectedMetricId.value = 0;
  notice.value = `已一键全选 ${ids.length} 项自定义指标。`;
}

function parseStrategyDefinition(raw: string | null): StrategyPresetDefinition | null {
  if (!raw || !raw.trim()) return null;
  try {
    const parsed = JSON.parse(raw) as StrategyPresetDefinition;
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function mapDefinitionToMode(definition: StrategyPresetDefinition): ModeChoice {
  const method = definition.evaluationMethod;
  const mode = definition.evaluationMode;
  const players = definition.selectedModelIds ?? [];
  if (method === 'DETERMINISTIC') return 'deterministic';
  if ((method === 'JUDGE' || method === 'HYBRID') && players.length >= 2 && definition.judgeModelId != null) {
    return 'bt';
  }
  if (method === 'HYBRID') return 'hybrid';
  if (mode === 'PROCESS' && method === 'JUDGE') return 'process-judge';
  if (mode === 'RESULT' && method === 'JUDGE') return 'result-judge';
  return 'custom';
}

function applySelectedStrategyConfig(): void {
  const strategy = selectedStrategy.value;
  if (!strategy) {
    notice.value = '请先选择本地评测配置。';
    return;
  }

  const def = parseStrategyDefinition(strategy.metricDefinitions);
  if (!def) {
    notice.value = '当前配置未保存可复用的模板定义。';
    return;
  }

  if (def.evaluationMode) {
    form.evaluationMode = def.evaluationMode;
  }
  if (def.evaluationMethod) {
    form.evaluationMethod = def.evaluationMethod;
  }
  if (Array.isArray(def.evaluationDimensions)) {
    selectedDimensions.value = normalizeDimensionList(
      def.evaluationDimensions.filter((x): x is string => !!x && typeof x === 'string'),
    );
  } else if (typeof def.evaluationDimensions === 'string' && def.evaluationDimensions.trim()) {
    selectedDimensions.value = normalizeDimensionList(
      def.evaluationDimensions.split(',').map((x) => x.trim()).filter(Boolean),
    );
  }
  if (!selectedDimensions.value.length) {
    selectedDimensions.value = ['effectiveness', 'safety', 'performance'];
  }

  form.metricSet = def.metricSet?.trim() || '';
  const metricSingleId = Number(form.metricSet);
  selectedMetricId.value = Number.isFinite(metricSingleId) && metricSingleId > 0 ? metricSingleId : 0;

  const selectedIds = Array.isArray(def.selectedModelIds) ? def.selectedModelIds.filter((x) => x > 0) : [];
  const isDet = form.evaluationMethod === 'DETERMINISTIC';
  if (isDet) {
    const single = selectedIds[0] ?? null;
    if (single) {
      const extra = { modelProfileId: single };
      inference.value.extraJson = JSON.stringify(extra);
    }
  } else {
    form.selectedPlayerIds = selectedIds;
    form.judgeModelId = def.judgeModelId ?? null;
    form.comparisonSamplingStrategy = def.comparisonSamplingStrategy || 'ALL_PAIRS';
    form.positionSwapEnabled = def.positionSwapEnabled ?? true;
  }

  if (def.inference) {
    if (typeof def.inference.temperature === 'number') inference.value.temperature = def.inference.temperature;
    if (typeof def.inference.maxTokens === 'number' || def.inference.maxTokens === null) {
      inference.value.maxTokens = def.inference.maxTokens ?? null;
    }
    if (typeof def.inference.timeoutSeconds === 'number') inference.value.timeoutSeconds = def.inference.timeoutSeconds;
    if (typeof def.inference.maxRetries === 'number') inference.value.maxRetries = def.inference.maxRetries;
    if (def.inference.extraRaw && typeof def.inference.extraRaw === 'string') {
      inference.value.extraJson = def.inference.extraRaw;
    } else if (def.inference.extra != null) {
      inference.value.extraJson = JSON.stringify(def.inference.extra);
    }
  }

  selectedMode.value = mapDefinitionToMode(def);
  notice.value = `已应用本地配置：${strategy.strategyName}`;
}

function buildStrategyConfigJson(): string {
  const strategy = selectedStrategy.value;
  const root: Record<string, unknown> = {};
  if (strategy?.weightConfig) {
    try {
      root.weightConfig = JSON.parse(strategy.weightConfig);
    } catch {
      // ignore invalid historical config
    }
  }
  if (strategy?.thresholdConfig) {
    try {
      root.thresholdConfig = JSON.parse(strategy.thresholdConfig);
    } catch {
      // ignore invalid historical config
    }
  }

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
  root.inference = inferencePayload;
  return JSON.stringify(root);
}

function getInferenceModelProfileId(): number | null {
  const raw = inference.value.extraJson.trim();
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (typeof parsed.modelProfileId === 'number' && parsed.modelProfileId > 0) {
      return parsed.modelProfileId;
    }
    return null;
  } catch {
    return null;
  }
}

function close(): void {
  emit('update:open', false);
}

async function createOnly(): Promise<{ taskId: number; taskName: string } | null> {
  try {
    const selectedPlayers = form.selectedPlayerIds.filter((id) => id > 0);
    const promptModelProfileId = getInferenceModelProfileId();
    const selectedModelIds = isDeterministic.value
      ? promptModelProfileId != null
        ? [promptModelProfileId]
        : []
      : selectedPlayers;
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
      strategyConfig: buildStrategyConfigJson(),
      selectedModelIds: selectedModelIds.length > 0 ? selectedModelIds : undefined,
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
  display: flex;
  justify-content: flex-end;
  align-items: stretch;
  background: rgba(34, 16, 16, 0.42);
  backdrop-filter: blur(3px);
}

.create-drawer {
  width: min(1140px, calc(100vw - 8px));
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #f5cdcf;
  box-shadow: -20px 0 46px rgba(127, 29, 29, 0.2);
  animation: drawer-in 0.2s ease-out;
}

.ct-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 20px 24px 14px;
  border-bottom: 1px solid var(--line);
}

.ct-head-titles {
  min-width: 0;
}

.icon-close {
  width: 40px;
  height: 40px;
  font-size: 28px;
  line-height: 1;
  padding: 0;
  flex-shrink: 0;
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
  margin: 6px 0 0;
  font-size: 34px;
  line-height: 1.15;
}

.ct-sub {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.ct-body {
  flex: 1;
  overflow: auto;
  padding: 18px 24px 14px;
}

.mode-step {
  width: min(940px, 100%);
  margin: 34px auto 0;
}

.mode-kicker {
  margin: 0;
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.mode-step h4 {
  margin: 10px 0 0;
  font-size: 36px;
  line-height: 1.15;
}

.mode-desc {
  margin-top: 10px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.55;
}

.mode-grid {
  margin-top: 22px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.mode-card {
  border: 1px solid #f3d5d7;
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  text-align: left;
}

.mode-card:hover {
  border-color: #ef4444;
  box-shadow: 0 8px 18px rgba(220, 38, 38, 0.12);
}

.mode-card.active {
  border-color: #ef232d;
  background: #fff3f4;
}

.mode-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #ef232d;
  color: #fff;
  font-weight: 700;
}

.mode-copy {
  display: grid;
  gap: 4px;
}

.mode-copy strong {
  font-size: 16px;
}

.mode-copy small {
  color: var(--text-secondary);
  font-size: 12px;
}

.ct-columns {
  display: grid;
  gap: 14px;
}

.ct-col {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.ct-col-dataset {
  background: #fff9f9;
}

.ct-col-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 8px;
}

.ct-col-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
}

.mode-badge {
  font-size: 12px;
  color: #991b1b;
  background: #fff5f5;
  border: 1px solid #fecaca;
  border-radius: 999px;
  padding: 4px 10px;
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

.mode-note {
  margin: 4px 2px 0;
  font-size: 12px;
  color: #b45309;
  line-height: 1.45;
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

.dimension-picker {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: #fffdfd;
}

.dimension-picker > summary {
  list-style: none;
  cursor: pointer;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.dimension-picker > summary::-webkit-details-marker {
  display: none;
}

.dimension-picker-menu {
  padding: 0 12px 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.dimension-chip {
  border: 1px solid #f0d8db;
  border-radius: 10px;
  background: #fff8f8;
  padding: 10px;
  text-align: left;
  display: grid;
  gap: 4px;
  min-height: 62px;
}

.dimension-chip:hover {
  border-color: #ef9ba4;
  background: #fff4f5;
}

.dimension-chip.active {
  border-color: #ef4c5a;
  background: #fff1f2;
  box-shadow: 0 6px 14px rgba(239, 35, 45, 0.12);
}

.chip-title {
  font-size: 14px;
  font-weight: 700;
  color: #2c3442;
}

.chip-desc {
  font-size: 12px;
  color: #6b7280;
}

.field-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-secondary);
}

.dimension-chip.active .chip-title {
  color: #a02735;
}

.dimension-chip.active .chip-desc {
  color: #9a4d57;
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
  border-top: 1px solid var(--line);
  padding: 14px 24px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  background: #fffdfd;
}

.ct-foot-note {
  font-size: 12px;
  color: var(--text-secondary);
}

.ct-foot-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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

@media (min-width: 980px) {
  .ct-columns {
    grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .mode-step h4 {
    font-size: 30px;
  }

  .dimension-picker-menu {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .ct-head {
    padding: 14px 14px 12px;
  }

  .ct-head h3 {
    font-size: 28px;
  }

  .ct-body {
    padding: 12px 14px;
  }

  .ct-foot {
    padding: 10px 14px 14px;
    flex-direction: column;
    align-items: stretch;
  }

  .ct-foot-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .ct-foot-actions button {
    flex: 1;
  }
}
</style>

