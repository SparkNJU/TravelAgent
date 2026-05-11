<template>
  <div v-if="open" class="modal-mask sub-modal-mask" @click="$emit('update:open', false)">
    <article class="inference-modal" @click.stop>
      <header class="inf-head">
        <div>
          <h3>提示词与模型</h3>
          <p class="inf-sub">单模型或确定性推理时，使用已启用的参评/通用模型。</p>
        </div>
        <button type="button" class="ghost icon-close" aria-label="关闭" @click="$emit('update:open', false)">×</button>
      </header>

      <div class="inf-body">
        <label class="inf-row">
          模型
          <div class="model-row">
            <select v-model.number="selectedModelProfileId">
              <option :value="0">-- 请选择模型 --</option>
              <option v-for="m in modelOptions" :key="m.modelProfileId" :value="m.modelProfileId">
                {{ m.modelId }}（{{ m.displayName }}）[{{ roleLabel(m.role) }}]
              </option>
            </select>
            <button type="button" class="ghost ping-btn" :disabled="!selectedModelProfileId || pingLoading" @click="onPing">
              {{ pingLoading ? '检测中...' : '连通性检测' }}
            </button>
          </div>
          <small v-if="modelLoadError" class="err-text">模型加载失败：{{ modelLoadError }}</small>
          <small v-else-if="!modelOptions.length" class="err-text">暂无可用参评/通用模型，请先在模型管理中启用。</small>
          <small v-else-if="pingText" :class="pingOk ? 'ok-text' : 'err-text'">{{ pingText }}</small>
        </label>

        <div class="inf-row two-col">
          <label>
            温度（Temperature）
            <select v-model.number="local.temperature">
              <option :value="0">0.0（稳定）</option>
              <option :value="0.2">0.2</option>
              <option :value="0.5">0.5</option>
              <option :value="0.7">0.7（均衡）</option>
              <option :value="1.0">1.0（发散）</option>
            </select>
          </label>
          <label>
            最大输出 Token
            <select v-model.number="local.maxTokens">
              <option :value="512">512</option>
              <option :value="1024">1024</option>
              <option :value="2048">2048</option>
              <option :value="4096">4096</option>
            </select>
          </label>
        </div>

        <div class="inf-row two-col">
          <label>
            超时时间
            <select v-model.number="local.timeoutSeconds">
              <option :value="15">15 s</option>
              <option :value="30">30 s</option>
              <option :value="60">60 s</option>
              <option :value="120">120 s</option>
            </select>
          </label>
          <label>
            最大重试次数
            <select v-model.number="local.maxRetries">
              <option :value="0">0</option>
              <option :value="1">1</option>
              <option :value="2">2</option>
              <option :value="3">3</option>
            </select>
          </label>
        </div>

        <div class="inf-row two-col">
          <label>
            推理强度
            <select v-model="reasoningEffort">
              <option value="default">默认</option>
              <option value="low">低</option>
              <option value="medium">中</option>
              <option value="high">高</option>
            </select>
          </label>
          <label>
            接口类型
            <select v-model="providerApi">
              <option value="responses">Responses 接口</option>
              <option value="chat.completions">Chat Completions 接口</option>
            </select>
          </label>
        </div>

        <div class="inf-row two-col">
          <label>
            JSON 模式
            <select v-model="jsonMode">
              <option :value="false">关闭</option>
              <option :value="true">开启</option>
            </select>
          </label>
          <label>
            停止序列
            <select v-model="stopPreset">
              <option value="none">无</option>
              <option value="safe-end">安全结束</option>
              <option value="tool-end">工具结束</option>
            </select>
          </label>
        </div>

        <label class="inf-row">
          额外参数预设
          <select v-model="extraParamsPreset">
            <option value="none">无</option>
            <option value="balanced">均衡</option>
            <option value="strict-judge">严格裁判</option>
            <option value="perf-first">性能优先</option>
          </select>
        </label>
      </div>

      <footer class="inf-foot">
        <button type="button" class="ghost" @click="$emit('update:open', false)">取消</button>
        <button type="button" class="primary" @click="apply">应用</button>
      </footer>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { listModels, pingModel, type ModelProfile } from '../api/client';

export interface InferenceConfig {
  temperature: number;
  maxTokens: number | null;
  timeoutSeconds: number;
  maxRetries: number;
  extraJson: string;
}

const props = defineProps<{
  open: boolean;
  modelValue: InferenceConfig;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: InferenceConfig];
  'update:open': [open: boolean];
}>();

const local = reactive<InferenceConfig>({ ...props.modelValue });
const models = ref<ModelProfile[]>([]);
const modelLoadError = ref('');
const selectedModelProfileId = ref<number>(0);
const reasoningEffort = ref('default');
const providerApi = ref('responses');
const jsonMode = ref(false);
const stopPreset = ref('none');
const extraParamsPreset = ref('none');
const pingLoading = ref(false);
const pingText = ref('');
const pingOk = ref(false);

const modelOptions = computed(() => models.value.filter((m) => m.enabled && m.role !== 'JUDGE'));
const selectedModel = computed(() => modelOptions.value.find((m) => m.modelProfileId === selectedModelProfileId.value) ?? null);

watch(
  () => props.open,
  async (v) => {
    if (!v) return;
    hydrateFromModel();
    await loadModels();
  },
);

watch(
  () => props.modelValue,
  () => {
    if (props.open) {
      hydrateFromModel();
    }
  },
  { deep: true },
);

function hydrateFromModel(): void {
  Object.assign(local, props.modelValue);
  reasoningEffort.value = 'default';
  providerApi.value = 'responses';
  jsonMode.value = false;
  stopPreset.value = 'none';
  extraParamsPreset.value = 'none';
  selectedModelProfileId.value = 0;
  pingText.value = '';
  pingOk.value = false;

  if (!props.modelValue.extraJson.trim()) return;
  try {
    const parsed = JSON.parse(props.modelValue.extraJson) as Record<string, unknown>;
    if (typeof parsed.modelProfileId === 'number') selectedModelProfileId.value = parsed.modelProfileId;
    if (typeof parsed.reasoningEffort === 'string') reasoningEffort.value = parsed.reasoningEffort;
    if (typeof parsed.providerApi === 'string') providerApi.value = parsed.providerApi;
    if (typeof parsed.jsonMode === 'boolean') jsonMode.value = parsed.jsonMode;
    if (typeof parsed.stopPreset === 'string') stopPreset.value = parsed.stopPreset;
    if (typeof parsed.extraParamsPreset === 'string') extraParamsPreset.value = parsed.extraParamsPreset;
  } catch {
    // ignore invalid legacy json
  }
}

async function loadModels(): Promise<void> {
  try {
    modelLoadError.value = '';
    models.value = await listModels({ enabledOnly: true });
    const available = models.value.filter((m) => m.enabled && m.role !== 'JUDGE');
    if (!available.length) {
      selectedModelProfileId.value = 0;
      return;
    }
    if (!available.some((m) => m.modelProfileId === selectedModelProfileId.value)) {
      selectedModelProfileId.value = available[0].modelProfileId;
    }
  } catch (err: any) {
    models.value = [];
    modelLoadError.value = err.message || String(err);
  }
}

async function onPing(): Promise<void> {
  if (!selectedModelProfileId.value) return;
  pingLoading.value = true;
  pingText.value = '';
  pingOk.value = false;
  try {
    const res = await pingModel(selectedModelProfileId.value, 'Ping from evaluator config');
    pingOk.value = true;
    pingText.value = `可用 · ${res.latencyMs} ms · Token=${res.totalTokens}`;
  } catch (err: any) {
    pingOk.value = false;
    pingText.value = `不可用 · ${err.message || String(err)}`;
  } finally {
    pingLoading.value = false;
  }
}

function buildExtraParamsPreset(name: string): Record<string, unknown> | null {
  if (name === 'balanced') return { top_p: 0.9 };
  if (name === 'strict-judge') return { temperature: 0, top_p: 0.1 };
  if (name === 'perf-first') return { stream: false };
  return null;
}

function buildStopSequences(name: string): string[] {
  if (name === 'safe-end') return ['[END]'];
  if (name === 'tool-end') return ['[TOOL_DONE]'];
  return [];
}

function apply(): void {
  const modelProfile = selectedModel.value;
  const extraPayload: Record<string, unknown> = {
    modelProfileId: selectedModelProfileId.value || null,
    provider: modelProfile?.provider || '',
    model: modelProfile?.modelId || '',
    reasoningEffort: reasoningEffort.value,
    providerApi: providerApi.value,
    jsonMode: jsonMode.value,
    stopPreset: stopPreset.value,
    extraParamsPreset: extraParamsPreset.value,
  };

  const stopSequences = buildStopSequences(stopPreset.value);
  if (stopSequences.length) {
    extraPayload.stopSequences = stopSequences;
  }

  const extraParams = buildExtraParamsPreset(extraParamsPreset.value);
  if (extraParams) {
    extraPayload.extraParams = extraParams;
  }

  emit('update:modelValue', {
    ...local,
    extraJson: JSON.stringify(extraPayload),
  });
  emit('update:open', false);
}

function roleLabel(role: string): string {
  if (role === 'PLAYER') return '参评模型（PLAYER）';
  if (role === 'JUDGE') return '裁判模型（JUDGE）';
  if (role === 'BOTH') return '通用模型（BOTH）';
  return role;
}
</script>

<style scoped>
.sub-modal-mask {
  z-index: 90;
  background: rgba(17, 24, 39, 0.4);
}

.inference-modal {
  width: min(720px, calc(100vw - 36px));
  max-height: calc(100vh - 50px);
  overflow: auto;
  border-radius: 14px;
  border: 1px solid #fecaca;
  background: #fff;
  padding: 18px 20px;
  box-shadow: 0 24px 70px rgba(127, 29, 29, 0.22);
}

.inf-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 14px;
}

.inf-head h3 {
  margin: 0;
  font-size: 18px;
}

.inf-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.icon-close {
  width: 32px;
  height: 32px;
  padding: 0;
}

.inf-body {
  display: grid;
  gap: 12px;
}

.inf-row {
  display: grid;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.inf-row.two-col {
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.model-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.ping-btn {
  min-width: 84px;
  min-height: 36px;
}

.ok-text {
  color: #15803d;
}

.err-text {
  color: #b91c1c;
}

.inf-foot {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .inf-row.two-col {
    grid-template-columns: 1fr;
  }
}
</style>
