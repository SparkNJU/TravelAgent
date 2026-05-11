<template>
  <section class="dashboard-page strategy-studio">
    <article class="surface surface-red studio-hero">
      <div>
        <p class="hero-kicker">评测配置中心</p>
        <h2>AI 评测配置</h2>
        <p class="hero-copy">
          评测配置已彻底去版本化。每一条配置都可在创建任务时直接一键复用。
        </p>
      </div>
      <div class="hero-actions">
        <button class="ghost" @click="loadStrategyData">刷新</button>
        <button class="ghost" @click="resetDraftForNewStrategy">新建配置</button>
      </div>
    </article>

    <p v-if="notice" class="notice-text studio-notice">{{ notice }}</p>

    <section class="studio-kpis">
      <article class="surface mini-kpi">
        <span>配置总数</span>
        <strong>{{ strategies.length }}</strong>
        <small>已保存本地评测配置</small>
      </article>
      <article class="surface mini-kpi">
        <span>启用指标</span>
        <strong>{{ enabledMetricCount }}</strong>
        <small>可选自定义指标</small>
      </article>
      <article class="surface mini-kpi">
        <span>当前配置</span>
        <strong>{{ strategyForm.selectedStrategyId ? `#${strategyForm.selectedStrategyId}` : '新建' }}</strong>
        <small>{{ strategyForm.strategyName || '未命名' }}</small>
      </article>
      <article class="surface mini-kpi">
        <span>评测模式</span>
        <strong>{{ `${evaluationModeLabel(strategyForm.evaluationMode)} / ${evaluationMethodLabel(strategyForm.evaluationMethod)}` }}</strong>
        <small>可直接复用到任务创建</small>
      </article>
    </section>

    <section class="studio-grid">
      <div class="studio-main">
        <article class="surface studio-panel">
          <div class="panel-topline">
            <div>
              <p class="panel-kicker">配置草稿</p>
              <h3>编辑评测配置</h3>
            </div>
            <span class="soft-badge">无版本模式</span>
          </div>

          <div class="config-stack">
            <label class="field-block">
              <span class="field-label">评测名称</span>
              <input v-model.trim="strategyForm.strategyName" type="text" placeholder="例如：生产回归评测配置" />
            </label>

            <div class="json-grid">
              <label class="field-block">
                <span class="field-label">评测模式</span>
                <select v-model="strategyForm.evaluationMode">
                  <option value="RESULT">结果模式</option>
                  <option value="PROCESS">过程模式</option>
                </select>
              </label>

              <label class="field-block">
                <span class="field-label">评测方法</span>
                <select v-model="strategyForm.evaluationMethod">
                  <option value="DETERMINISTIC">确定性评测</option>
                  <option value="JUDGE">裁判评测</option>
                  <option value="HYBRID">混合评测</option>
                </select>
              </label>
            </div>

            <div class="field-block">
              <span class="field-label">评测维度</span>
              <div class="dimension-row">
                <button
                  v-for="item in dimensionOptions"
                  :key="item.value"
                  type="button"
                  class="dimension-chip"
                  :class="{ active: strategyForm.evaluationDimensions.includes(item.value) }"
                  @click="toggleDimension(item.value)"
                >
                  <span class="dimension-chip-title">{{ item.label }}</span>
                  <small class="dimension-chip-help">{{ item.hint }}</small>
                </button>
              </div>
            </div>

            <div class="field-block">
              <span class="field-label">评测模型（与创建任务一致）</span>
              <div v-if="isDeterministic" class="model-launch-row">
                <button type="button" class="model-pill" @click="showInference = true">
                  <span class="pill-icon">⚙</span>
                  <span class="pill-text">提示词与模型</span>
                  <span class="pill-hint">{{ inferenceSummary }}</span>
                  <span class="pill-arrow">→</span>
                </button>
              </div>
              <div v-else class="bt-config-block">
                <ModelPickerSection
                  v-model:selectedPlayerIds="strategyForm.selectedPlayerIds"
                  v-model:judgeId="strategyForm.judgeModelId"
                />
                <label>
                  对比采样策略
                  <select v-model="strategyForm.comparisonSamplingStrategy">
                    <option value="ALL_PAIRS">全对比较</option>
                  </select>
                </label>
                <label class="inline-check">
                  <input v-model="strategyForm.positionSwapEnabled" type="checkbox" />
                  启用位置互换
                </label>
              </div>
            </div>

            <div class="field-block">
              <span class="field-label">指标集</span>
              <div class="action-row">
                <button class="ghost small-btn" @click="selectAllMetrics">一键全选</button>
                <button class="ghost small-btn" @click="clearMetrics">清空</button>
                <span class="inline-hint">已选 {{ selectedMetricCount }} 项指标</span>
              </div>
              <div class="metric-pick-grid">
                <label v-for="metric in customMetrics" :key="metric.customMetricId" class="metric-pick-item">
                  <input
                    type="checkbox"
                    :checked="strategyForm.metricSetIds.includes(metric.customMetricId)"
                    @change="toggleMetric(metric.customMetricId)"
                  />
                  <div class="metric-copy">
                    <span class="metric-title">{{ metric.metricName }} | {{ metricTypeLabel(metric.metricType) }}</span>
                    <small class="metric-help">{{ metricHelpText(metric) }}</small>
                  </div>
                </label>
              </div>
            </div>

            <div class="param-grid">
              <article class="param-card">
                <h4 class="param-title">权重参数</h4>
                <div class="param-row">
                  <span class="param-label">效果权重</span>
                  <div class="param-control">
                    <input v-model.number="strategyForm.weightEffectiveness" type="range" min="0" max="1" step="0.01" />
                    <input v-model.number="strategyForm.weightEffectiveness" class="param-number" type="number" min="0" max="1" step="0.01" />
                  </div>
                </div>
                <div class="param-row">
                  <span class="param-label">安全权重</span>
                  <div class="param-control">
                    <input v-model.number="strategyForm.weightSafety" type="range" min="0" max="1" step="0.01" />
                    <input v-model.number="strategyForm.weightSafety" class="param-number" type="number" min="0" max="1" step="0.01" />
                  </div>
                </div>
                <div class="param-row">
                  <span class="param-label">性能权重</span>
                  <div class="param-control">
                    <input v-model.number="strategyForm.weightPerformance" type="range" min="0" max="1" step="0.01" />
                    <input v-model.number="strategyForm.weightPerformance" class="param-number" type="number" min="0" max="1" step="0.01" />
                  </div>
                </div>
                <p class="param-sum" :class="{ warn: Math.abs(weightSum - 1) > 0.02 }">权重和：{{ weightSum.toFixed(2) }}（建议接近 1）</p>
              </article>

              <article class="param-card">
                <h4 class="param-title">阈值参数</h4>
                <div class="param-row">
                  <span class="param-label">总分阈值</span>
                  <div class="param-control">
                    <input v-model.number="strategyForm.thresholdOverall" type="range" min="0" max="1" step="0.01" />
                    <input v-model.number="strategyForm.thresholdOverall" class="param-number" type="number" min="0" max="1" step="0.01" />
                  </div>
                </div>
                <div class="param-row">
                  <span class="param-label">安全下限</span>
                  <div class="param-control">
                    <input v-model.number="strategyForm.thresholdSafetyMin" type="range" min="0" max="1" step="0.01" />
                    <input v-model.number="strategyForm.thresholdSafetyMin" class="param-number" type="number" min="0" max="1" step="0.01" />
                  </div>
                </div>
              </article>
            </div>

            <div class="action-row">
              <button class="primary" @click="saveStrategyConfig">
                {{ strategyForm.selectedStrategyId ? '更新配置' : '保存新配置' }}
              </button>
              <span class="inline-hint">保存后可在创建任务时一键应用全部参数。</span>
            </div>
          </div>
        </article>

        <article class="surface studio-panel">
          <div class="panel-topline">
            <div>
              <p class="panel-kicker">自定义指标</p>
              <h3>注册新指标</h3>
            </div>
            <span class="soft-badge">{{ customMetrics.length }} 项</span>
          </div>

          <div class="metric-form-grid">
            <label class="field-block span-2">
              <span class="field-label">指标名称</span>
              <input v-model.trim="metricForm.metricName" type="text" placeholder="例如：响应稳定性" />
            </label>

            <label class="field-block">
              <span class="field-label">指标类型</span>
              <select v-model="metricForm.metricType">
                <option value="DETERMINISTIC">确定性评测</option>
                <option value="JUDGE">裁判评测</option>
              </select>
            </label>

            <label class="field-block">
              <span class="field-label">阈值</span>
              <input v-model.number="metricForm.thresholdValue" type="number" min="0" max="1" step="0.01" />
            </label>

            <label class="field-block span-2">
              <span class="field-label">评分逻辑</span>
              <input v-model.trim="metricForm.scoringLogic" type="text" placeholder="例如：完成率 / 延迟 / 稳定性" />
            </label>
          </div>

          <div class="action-row">
            <button class="primary" @click="createCustomMetricAction">注册指标</button>
          </div>
        </article>
      </div>

      <aside class="studio-side">
        <article class="surface side-panel">
          <div class="panel-topline compact">
            <div>
              <p class="panel-kicker">配置库</p>
              <h3>已保存配置</h3>
            </div>
          </div>

          <div v-if="strategies.length" class="library-list">
            <button
              v-for="item in strategies"
              :key="item.strategyId"
              class="library-item"
              :class="{ active: item.strategyId === strategyForm.selectedStrategyId }"
              @click="pickStrategy(item.strategyId)"
            >
              <div>
                <strong>{{ item.strategyName }}</strong>
                <p>#{{ item.strategyId }}</p>
              </div>
              <span class="chip">应用</span>
            </button>
          </div>
          <p v-else class="empty-state">暂无评测配置</p>
        </article>

        <article class="surface side-panel">
          <div class="panel-topline compact">
            <div>
              <p class="panel-kicker">指标库</p>
              <h3>全部指标</h3>
            </div>
          </div>
          <div v-if="customMetrics.length" class="registry-table-wrap">
            <table class="registry-table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>类型</th>
                  <th>阈值</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="metric in customMetrics" :key="metric.customMetricId">
                  <td><strong>{{ metric.metricName }}</strong></td>
                  <td>{{ metricTypeLabel(metric.metricType) }}</td>
                  <td>{{ metric.thresholdValue ?? '-' }}</td>
                  <td>{{ metric.enabled ? '启用' : '停用' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="empty-state">暂无自定义指标</p>
        </article>
      </aside>
    </section>

    <ModelInferenceModal v-model:open="showInference" v-model="inference" />
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import ModelInferenceModal, { type InferenceConfig } from '../components/ModelInferenceModal.vue';
import ModelPickerSection from '../components/ModelPickerSection.vue';
import {
  createCustomMetric,
  createStrategy,
  listCustomMetrics,
  listStrategies,
  updateStrategy,
  type CustomMetric,
  type EvalStrategy,
  type EvaluationMethod,
  type EvaluationMode,
} from '../api/client';

type StrategyPresetDefinition = {
  evaluationMode: EvaluationMode;
  evaluationMethod: EvaluationMethod;
  evaluationDimensions: string[] | string;
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

const defaultWeightConfig = { effectiveness: 0.5, safety: 0.2, performance: 0.3 };
const defaultThresholdConfig = { overallThreshold: 0.75, safetyMin: 0.7 };

const dimensionOptions = [
  { value: 'effectiveness', label: '效果', hint: '任务完成质量与正确性。' },
  { value: 'safety', label: '安全', hint: '安全风险与违规内容控制。' },
  { value: 'performance', label: '性能', hint: '延迟、稳定性与资源开销。' },
];

const strategies = ref<EvalStrategy[]>([]);
const customMetrics = ref<CustomMetric[]>([]);
const notice = ref('');
const showInference = ref(false);

const strategyForm = reactive({
  selectedStrategyId: 0,
  strategyName: '',
  evaluationMode: 'RESULT' as EvaluationMode,
  evaluationMethod: 'JUDGE' as EvaluationMethod,
  evaluationDimensions: ['effectiveness', 'safety', 'performance'] as string[],
  metricSetIds: [] as number[],
  selectedPlayerIds: [] as number[],
  judgeModelId: null as number | null,
  comparisonSamplingStrategy: 'ALL_PAIRS' as 'ALL_PAIRS',
  positionSwapEnabled: true,
  weightEffectiveness: defaultWeightConfig.effectiveness,
  weightSafety: defaultWeightConfig.safety,
  weightPerformance: defaultWeightConfig.performance,
  thresholdOverall: defaultThresholdConfig.overallThreshold,
  thresholdSafetyMin: defaultThresholdConfig.safetyMin,
});

const inference = ref<InferenceConfig>({
  temperature: 0.7,
  maxTokens: 1024,
  timeoutSeconds: 60,
  maxRetries: 2,
  extraJson: '',
});

const metricForm = reactive({
  metricName: '',
  metricType: 'DETERMINISTIC' as 'DETERMINISTIC' | 'JUDGE',
  scoringLogic: 'completion',
  thresholdValue: 0.7,
});

const enabledMetricCount = computed(() => customMetrics.value.filter((x) => x.enabled).length);
const isDeterministic = computed(() => strategyForm.evaluationMethod === 'DETERMINISTIC');
const selectedMetricCount = computed(() => strategyForm.metricSetIds.length);
const weightSum = computed(
  () => strategyForm.weightEffectiveness + strategyForm.weightSafety + strategyForm.weightPerformance,
);

const inferenceSummary = computed(() => {
  const { temperature, maxTokens, extraJson } = inference.value;
  let modelHint = '';
  if (extraJson.trim()) {
    try {
      const parsed = JSON.parse(extraJson) as Record<string, unknown>;
      const model = typeof parsed.model === 'string' ? parsed.model : '';
      if (model) modelHint = `${model} · `;
    } catch {
      // ignore
    }
  }
  return `${modelHint}温度=${temperature}${maxTokens != null ? ` · 最大Token ${maxTokens}` : ''}`;
});

onMounted(() => {
  void loadStrategyData();
});

async function loadStrategyData(): Promise<void> {
  try {
    const [strategyList, metricList] = await Promise.all([listStrategies(), listCustomMetrics()]);
    strategies.value = strategyList;
    customMetrics.value = metricList;
    if (strategyForm.selectedStrategyId) {
      const found = strategies.value.find((x) => x.strategyId === strategyForm.selectedStrategyId);
      if (!found) {
        strategyForm.selectedStrategyId = 0;
      }
    }
    notice.value = `已加载 ${strategyList.length} 个配置，${metricList.length} 个自定义指标。`;
  } catch (err: any) {
    notice.value = `加载失败：${err.message || String(err)}`;
  }
}

function resetDraftForNewStrategy(): void {
  strategyForm.selectedStrategyId = 0;
  strategyForm.strategyName = '';
  strategyForm.evaluationMode = 'RESULT';
  strategyForm.evaluationMethod = 'JUDGE';
  strategyForm.evaluationDimensions = ['effectiveness', 'safety', 'performance'];
  strategyForm.metricSetIds = [];
  strategyForm.selectedPlayerIds = [];
  strategyForm.judgeModelId = null;
  strategyForm.comparisonSamplingStrategy = 'ALL_PAIRS';
  strategyForm.positionSwapEnabled = true;
  strategyForm.weightEffectiveness = defaultWeightConfig.effectiveness;
  strategyForm.weightSafety = defaultWeightConfig.safety;
  strategyForm.weightPerformance = defaultWeightConfig.performance;
  strategyForm.thresholdOverall = defaultThresholdConfig.overallThreshold;
  strategyForm.thresholdSafetyMin = defaultThresholdConfig.safetyMin;
  inference.value = {
    temperature: 0.7,
    maxTokens: 1024,
    timeoutSeconds: 60,
    maxRetries: 2,
    extraJson: '',
  };
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

function metricHelpText(metric: CustomMetric): string {
  const logic = metric.scoringLogic?.trim() || '自定义逻辑';
  const threshold = metric.thresholdValue == null ? '-' : metric.thresholdValue;
  const enabled = metric.enabled ? '启用' : '停用';
  return `${logic} | 阈值 ${threshold} | ${enabled}`;
}

function evaluationModeLabel(mode: EvaluationMode | string): string {
  if (mode === 'RESULT') return 'RESULT';
  if (mode === 'PROCESS') return 'PROCESS';
  return mode;
}

function evaluationMethodLabel(method: EvaluationMethod | string): string {
  if (method === 'DETERMINISTIC') return 'DETERMINISTIC';
  if (method === 'JUDGE') return 'JUDGE';
  if (method === 'HYBRID') return 'HYBRID';
  return method;
}

function metricTypeLabel(metricType: string): string {
  if (metricType === 'DETERMINISTIC') return 'DETERMINISTIC';
  if (metricType === 'JUDGE') return 'JUDGE';
  return metricType;
}

function clamp01(value: unknown, fallback: number): number {
  if (typeof value !== 'number' || Number.isNaN(value)) return fallback;
  return Math.max(0, Math.min(1, value));
}

function parseJsonMap(raw?: string | null): Record<string, unknown> {
  if (!raw || !raw.trim()) return {};
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function applyNumericParams(weightRaw?: string | null, thresholdRaw?: string | null): void {
  const weight = parseJsonMap(weightRaw);
  const threshold = parseJsonMap(thresholdRaw);
  strategyForm.weightEffectiveness = clamp01(weight.effectiveness, defaultWeightConfig.effectiveness);
  strategyForm.weightSafety = clamp01(weight.safety, defaultWeightConfig.safety);
  strategyForm.weightPerformance = clamp01(weight.performance, defaultWeightConfig.performance);
  strategyForm.thresholdOverall = clamp01(threshold.overallThreshold, defaultThresholdConfig.overallThreshold);
  strategyForm.thresholdSafetyMin = clamp01(threshold.safetyMin, defaultThresholdConfig.safetyMin);
}

function buildWeightConfigJson(): string {
  return JSON.stringify({
    effectiveness: Number(strategyForm.weightEffectiveness.toFixed(4)),
    safety: Number(strategyForm.weightSafety.toFixed(4)),
    performance: Number(strategyForm.weightPerformance.toFixed(4)),
  });
}

function buildThresholdConfigJson(): string {
  return JSON.stringify({
    overallThreshold: Number(strategyForm.thresholdOverall.toFixed(4)),
    safetyMin: Number(strategyForm.thresholdSafetyMin.toFixed(4)),
  });
}

function pickStrategy(strategyId: number): void {
  const strategy = strategies.value.find((item) => item.strategyId === strategyId);
  if (!strategy) return;
  strategyForm.selectedStrategyId = strategyId;
  strategyForm.strategyName = strategy.strategyName;
  applyNumericParams(strategy.weightConfig, strategy.thresholdConfig);
  hydratePreset(strategy.metricDefinitions);
}

function hydratePreset(metricDefinitions: string | null): void {
  if (!metricDefinitions || !metricDefinitions.trim()) {
    return;
  }
  try {
    const preset = JSON.parse(metricDefinitions) as StrategyPresetDefinition;
    strategyForm.evaluationMode = preset.evaluationMode || 'RESULT';
    strategyForm.evaluationMethod = preset.evaluationMethod || 'JUDGE';
    if (Array.isArray(preset.evaluationDimensions)) {
      strategyForm.evaluationDimensions = normalizeDimensionList(
        preset.evaluationDimensions.map((x) => String(x)),
      );
    } else if (typeof preset.evaluationDimensions === 'string') {
      strategyForm.evaluationDimensions = normalizeDimensionList(
        preset.evaluationDimensions.split(',').map((x) => x.trim()).filter(Boolean),
      );
    }
    if (!strategyForm.evaluationDimensions.length) {
      strategyForm.evaluationDimensions = ['effectiveness', 'safety', 'performance'];
    }
    strategyForm.metricSetIds = parseMetricSetIds(preset.metricSet);
    const selectedModelIds = Array.isArray(preset.selectedModelIds)
      ? preset.selectedModelIds.filter((x) => typeof x === 'number' && x > 0)
      : [];
    if (strategyForm.evaluationMethod === 'DETERMINISTIC') {
      if (selectedModelIds.length) {
        inference.value.extraJson = JSON.stringify({ modelProfileId: selectedModelIds[0] });
      }
    } else {
      strategyForm.selectedPlayerIds = selectedModelIds;
      strategyForm.judgeModelId = preset.judgeModelId ?? null;
      strategyForm.comparisonSamplingStrategy = preset.comparisonSamplingStrategy || 'ALL_PAIRS';
      strategyForm.positionSwapEnabled = preset.positionSwapEnabled ?? true;
    }
    if (preset.inference) {
      if (typeof preset.inference.temperature === 'number') inference.value.temperature = preset.inference.temperature;
      if (typeof preset.inference.maxTokens === 'number' || preset.inference.maxTokens === null) {
        inference.value.maxTokens = preset.inference.maxTokens ?? null;
      }
      if (typeof preset.inference.timeoutSeconds === 'number') inference.value.timeoutSeconds = preset.inference.timeoutSeconds;
      if (typeof preset.inference.maxRetries === 'number') inference.value.maxRetries = preset.inference.maxRetries;
      if (typeof preset.inference.extraRaw === 'string') {
        inference.value.extraJson = preset.inference.extraRaw;
      } else if (preset.inference.extra != null) {
        inference.value.extraJson = JSON.stringify(preset.inference.extra);
      }
    }
  } catch {
    notice.value = '评测模板 JSON 解析失败，已保留基础字段。';
  }
}

function toggleDimension(value: string): void {
  const normalized = normalizeDimensionToken(value);
  if (!normalized) {
    return;
  }
  if (strategyForm.evaluationDimensions.includes(normalized)) {
    if (strategyForm.evaluationDimensions.length <= 1) {
      notice.value = '评测维度至少保留 1 项。';
      return;
    }
    strategyForm.evaluationDimensions = strategyForm.evaluationDimensions.filter((item) => item !== normalized);
    return;
  }
  strategyForm.evaluationDimensions = [...strategyForm.evaluationDimensions, normalized];
}

function toggleMetric(metricId: number): void {
  if (strategyForm.metricSetIds.includes(metricId)) {
    strategyForm.metricSetIds = strategyForm.metricSetIds.filter((id) => id !== metricId);
    return;
  }
  strategyForm.metricSetIds = [...strategyForm.metricSetIds, metricId];
}

function selectAllMetrics(): void {
  strategyForm.metricSetIds = customMetrics.value.map((item) => item.customMetricId);
}

function clearMetrics(): void {
  strategyForm.metricSetIds = [];
}

function parseMetricSetIds(raw?: string): number[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(',')
    .map((x) => Number(x.trim()))
    .filter((x) => Number.isFinite(x) && x > 0);
}

function getInferenceModelProfileId(): number | null {
  const raw = inference.value.extraJson.trim();
  if (!raw) return null;
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

function buildPresetDefinition(): StrategyPresetDefinition {
  const preset: StrategyPresetDefinition = {
    evaluationMode: strategyForm.evaluationMode,
    evaluationMethod: strategyForm.evaluationMethod,
    evaluationDimensions: strategyForm.evaluationDimensions,
    metricSet: strategyForm.metricSetIds.join(','),
    comparisonSamplingStrategy: strategyForm.comparisonSamplingStrategy,
    positionSwapEnabled: strategyForm.positionSwapEnabled,
    inference: {
      temperature: inference.value.temperature,
      maxTokens: inference.value.maxTokens,
      timeoutSeconds: inference.value.timeoutSeconds,
      maxRetries: inference.value.maxRetries,
    },
  };

  if (inference.value.extraJson.trim()) {
    try {
      preset.inference!.extra = JSON.parse(inference.value.extraJson);
    } catch {
      preset.inference!.extraRaw = inference.value.extraJson;
    }
  }

  if (strategyForm.evaluationMethod === 'DETERMINISTIC') {
    const deterministicModel = getInferenceModelProfileId();
    preset.selectedModelIds = deterministicModel ? [deterministicModel] : [];
    preset.judgeModelId = null;
    return preset;
  }

  const selectedPlayers = strategyForm.selectedPlayerIds.filter((id) => id > 0);
  preset.selectedModelIds = selectedPlayers;
  preset.judgeModelId = strategyForm.judgeModelId;
  return preset;
}

async function saveStrategyConfig(): Promise<void> {
  if (!strategyForm.strategyName.trim()) {
    notice.value = '请先填写评测名称。';
    return;
  }

  const payload = {
    strategyName: strategyForm.strategyName.trim(),
    metricDefinitions: JSON.stringify(buildPresetDefinition()),
    weightConfig: buildWeightConfigJson(),
    thresholdConfig: buildThresholdConfigJson(),
  };

  try {
    if (strategyForm.selectedStrategyId) {
      const updated = await updateStrategy(strategyForm.selectedStrategyId, payload);
      notice.value = `配置更新成功：${updated.strategyName}`;
      await loadStrategyData();
      pickStrategy(updated.strategyId);
      return;
    }

    const created = await createStrategy(payload);
    strategyForm.selectedStrategyId = created.strategyId;
    notice.value = `配置创建成功：${created.strategyName}`;
    await loadStrategyData();
    pickStrategy(created.strategyId);
  } catch (err: any) {
    notice.value = `配置保存失败：${err.message || String(err)}`;
  }
}

async function createCustomMetricAction(): Promise<void> {
  if (!metricForm.metricName) {
    notice.value = '请先填写自定义指标名称。';
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
    notice.value = `自定义指标创建成功：${metric.metricName} (#${metric.customMetricId})`;
    metricForm.metricName = '';
    metricForm.scoringLogic = 'completion';
    metricForm.thresholdValue = 0.7;
    metricForm.metricType = 'DETERMINISTIC';
    await loadStrategyData();
  } catch (err: any) {
    notice.value = `自定义指标创建失败：${err.message || String(err)}`;
  }
}
</script>

<style scoped>
.strategy-studio {
  gap: 16px;
}

.studio-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 22px 20px;
}

.hero-kicker {
  color: var(--brand);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.studio-hero h2 {
  margin-top: 6px;
  font-size: 24px;
}

.hero-copy {
  margin-top: 8px;
  max-width: 760px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.studio-notice {
  margin: 0;
}

.studio-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.mini-kpi {
  display: grid;
  gap: 6px;
  min-height: 108px;
  align-content: start;
}

.mini-kpi span {
  font-size: 12px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 700;
}

.mini-kpi strong {
  font-size: 30px;
  line-height: 1;
}

.mini-kpi small {
  color: var(--text-secondary);
  font-size: 12px;
}

.studio-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(340px, 0.95fr);
  gap: 14px;
  align-items: start;
}

.studio-main,
.studio-side {
  display: grid;
  gap: 14px;
}

.studio-panel,
.side-panel {
  padding: 22px;
}

.panel-topline {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-topline.compact {
  margin-bottom: 14px;
}

.panel-kicker {
  color: var(--brand);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-topline h3 {
  margin-top: 6px;
  font-size: 22px;
}

.soft-badge {
  border: 1px solid #f2c6cb;
  background: #fff4f5;
  color: #a43f49;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
}

.config-stack,
.metric-form-grid {
  display: grid;
  gap: 18px;
}

.json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.metric-form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.param-card {
  border: 1px solid #f0d8db;
  border-radius: 12px;
  background: #fffafa;
  padding: 14px;
  display: grid;
  gap: 12px;
}

.param-title {
  margin: 0;
  font-size: 14px;
  color: #7f1d1d;
}

.param-row {
  display: grid;
  gap: 8px;
}

.param-label {
  font-size: 12px;
  font-weight: 700;
  color: #555;
}

.param-control {
  display: grid;
  grid-template-columns: 1fr 86px;
  gap: 10px;
  align-items: center;
}

.param-control input[type='range'] {
  width: 100%;
}

.param-number {
  width: 86px;
  text-align: center;
}

.param-sum {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.param-sum.warn {
  color: #b91c1c;
}

.span-2 {
  grid-column: 1 / -1;
}

.field-block {
  display: grid;
  gap: 10px;
}

.field-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #945760;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.inline-hint {
  color: var(--text-secondary);
  font-size: 13px;
}

.small-btn {
  padding: 7px 10px;
  font-size: 12px;
}

.dimension-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.dimension-chip {
  border: 1px solid #f0d8db;
  border-radius: 12px;
  background: #fffafa;
  padding: 11px 12px;
  text-align: left;
  display: grid;
  gap: 4px;
  cursor: pointer;
}

.dimension-chip:hover {
  border-color: #ef9ba4;
  background: #fff3f5;
}

.dimension-chip.active {
  border-color: #ef4c5a;
  background: #fff1f2;
  box-shadow: 0 8px 18px rgba(239, 76, 90, 0.14);
}

.dimension-chip-title {
  font-size: 13px;
  font-weight: 700;
  color: #2f3643;
}

.dimension-chip-help {
  font-size: 11px;
  line-height: 1.45;
  color: #6b7280;
}

.metric-pick-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.metric-pick-item {
  border: 1px solid #f0d8db;
  border-radius: 10px;
  background: #fffdfd;
  padding: 12px 12px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 12px;
}

.metric-pick-item input {
  width: auto;
  flex: 0 0 auto;
  margin-top: 2px;
}

.metric-copy {
  display: grid;
  gap: 2px;
}

.metric-title {
  font-size: 13px;
  font-weight: 700;
  color: #2f3643;
}

.metric-help {
  font-size: 11px;
  line-height: 1.45;
  color: #6b7280;
}

.library-list {
  display: grid;
  gap: 10px;
}

.library-item {
  width: 100%;
  border: 1px solid #f0d8db;
  border-radius: 14px;
  background: #fffdfd;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
}

.library-item strong {
  font-size: 15px;
}

.library-item p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.library-item.active {
  border-color: #f39aa4;
  background: linear-gradient(180deg, #fff7f8, #fff2f3);
  box-shadow: 0 12px 26px rgba(185, 28, 28, 0.08);
}

.registry-table-wrap {
  border: 1px solid #f0d8db;
  border-radius: 14px;
  overflow: auto;
  background: #fffdfd;
}

.registry-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 520px;
}

.registry-table th,
.registry-table td {
  padding: 12px 14px;
  border-bottom: 1px solid #f2e0e2;
  text-align: left;
  font-size: 13px;
}

.registry-table th {
  background: #fff5f6;
  color: #8b5d66;
  font-size: 12px;
  text-transform: uppercase;
}

.empty-state {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
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
}

.bt-config-block {
  border: 1px dashed #fecaca;
  border-radius: 10px;
  padding: 10px;
  display: grid;
  gap: 10px;
  background: #fffafa;
}

@media (max-width: 1200px) {
  .studio-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .studio-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .studio-hero,
  .panel-topline {
    display: grid;
  }

  .json-grid,
  .param-grid,
  .metric-form-grid,
  .studio-kpis,
  .metric-pick-grid,
  .dimension-row {
    grid-template-columns: 1fr;
  }
}
</style>

