<template>
  <div v-if="open" class="modal-mask sub-modal-mask" @click="$emit('update:open', false)">
    <article class="inference-modal" @click.stop>
      <header class="inf-head">
        <div>
          <h3>Model Configuration</h3>
          <p class="inf-sub">Create and save model presets to use across prompts in your workspace.</p>
        </div>
        <button type="button" class="ghost icon-close" aria-label="关闭" @click="$emit('update:open', false)">✕</button>
      </header>

      <div class="inf-body">
        <div class="inf-row two-col">
          <label>
            Provider
            <select v-model="provider">
              <option v-for="p in PROVIDERS" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </label>
          <label>
            Model
            <select v-model="model">
              <option v-for="m in modelsForProvider" :key="m" :value="m">{{ m }}</option>
            </select>
          </label>
        </div>

        <label class="inf-row">
          API Key Name
          <input v-model="apiKeyName" type="text" placeholder="OPENAI_API_KEY" />
        </label>

        <div class="inf-row slider-row">
          <div class="slider-head">
            <span class="slider-label">Temperature <span class="info-mark">ⓘ</span></span>
            <span class="slider-value">{{ local.temperature.toFixed(2) }}</span>
          </div>
          <input v-model.number="local.temperature" type="range" min="0" max="2" step="0.05" class="slider-input" />
        </div>

        <label class="inf-row">
          Max Output Tokens
          <input v-model.number="local.maxTokens" type="number" min="1" step="1" placeholder="例如 1024" />
        </label>

        <h5 class="inf-section-title">REASONING</h5>
        <label class="inf-row">
          Reasoning Effort
          <select v-model="reasoningEffort">
            <option value="default">Default</option>
            <option value="low">Low</option>
            <option value="medium">Medium</option>
            <option value="high">High</option>
          </select>
        </label>

        <h5 class="inf-section-title">PROVIDER CONFIG</h5>
        <label class="inf-row">
          Provider API
          <select v-model="providerApi">
            <option value="responses">Responses (Recommended)</option>
            <option value="chat.completions">Chat Completions</option>
          </select>
        </label>

        <h5 class="inf-section-title">OPTIONS</h5>

        <div class="inf-row">
          <div class="slider-head">
            <span class="slider-label">Stop Sequences <span class="info-mark">ⓘ</span></span>
            <span class="slider-value">{{ stopSequences.length }}/4</span>
          </div>
          <div class="chip-input-row">
            <input
              v-model="stopDraft"
              type="text"
              placeholder="Type and press Enter..."
              @keydown.enter.prevent="addStopSequence"
            />
            <button type="button" class="ghost chip-add" @click="addStopSequence" :disabled="!canAddStop">+</button>
          </div>
          <div v-if="stopSequences.length" class="chip-list">
            <span v-for="(s, i) in stopSequences" :key="i" class="chip-tag">
              {{ s }}
              <button type="button" class="chip-del" @click="removeStopSequence(i)">×</button>
            </span>
          </div>
        </div>

        <label class="inf-row">
          Seed <span class="info-mark">ⓘ</span>
          <input v-model.number="seed" type="number" min="0" step="1" placeholder="e.g. 42" />
        </label>

        <div class="inf-row toggle-row">
          <span>JSON Mode <span class="info-mark">ⓘ</span></span>
          <label class="enabled-toggle">
            <input v-model="jsonMode" type="checkbox" />
            <span class="enabled-track" :class="{ on: jsonMode }"><span class="enabled-thumb"></span></span>
          </label>
        </div>

        <div class="inf-row">
          <div class="slider-head">
            <span class="slider-label">Extra Headers <span class="info-mark">ⓘ</span></span>
          </div>
          <div class="header-row">
            <input v-model="extraHeaderKey" type="text" placeholder="Key" />
            <input v-model="extraHeaderValue" type="text" placeholder="Value" />
            <button type="button" class="ghost chip-add" :disabled="!extraHeaderKey.trim()">Add</button>
          </div>
        </div>

        <div class="inf-row two-col">
          <div class="slider-row">
            <div class="slider-head">
              <span class="slider-label">
                Timeout
                <label class="enabled-toggle inline-toggle">
                  <input v-model="timeoutEnabled" type="checkbox" />
                  <span class="enabled-track sm" :class="{ on: timeoutEnabled }"><span class="enabled-thumb"></span></span>
                </label>
              </span>
              <span class="slider-value">{{ local.timeoutSeconds }} s</span>
            </div>
            <input
              v-model.number="local.timeoutSeconds"
              type="range"
              min="5"
              max="300"
              step="5"
              class="slider-input"
              :disabled="!timeoutEnabled"
            />
          </div>
          <div class="slider-row">
            <div class="slider-head">
              <span class="slider-label">
                Max Retries
                <label class="enabled-toggle inline-toggle">
                  <input v-model="retriesEnabled" type="checkbox" />
                  <span class="enabled-track sm" :class="{ on: retriesEnabled }"><span class="enabled-thumb"></span></span>
                </label>
              </span>
              <span class="slider-value">{{ local.maxRetries }}</span>
            </div>
            <input
              v-model.number="local.maxRetries"
              type="range"
              min="0"
              max="10"
              step="1"
              class="slider-input"
              :disabled="!retriesEnabled"
            />
          </div>
        </div>

        <label class="inf-row">
          Extra Parameters (JSON)
          <textarea
            v-model="extraParamsText"
            rows="3"
            placeholder='{ "top_p": 0.9, "frequency_penalty": 0.2 }'
            spellcheck="false"
            class="code-area"
          />
        </label>
      </div>

      <footer class="inf-foot">
        <div class="foot-left">
          <select v-model="presetSelected" class="preset-select">
            <option value="">Select a preset</option>
            <option value="balanced">Balanced</option>
            <option value="creative">Creative</option>
            <option value="strict-judge">Strict Judge</option>
          </select>
          <button type="button" class="ghost" @click="onSaveAsPreset">Save as preset</button>
        </div>
        <div class="foot-right">
          <button type="button" class="ghost" @click="$emit('update:open', false)">Cancel</button>
          <button type="button" class="primary" @click="apply">Apply</button>
        </div>
      </footer>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';

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

const PROVIDERS = [
  { value: 'openai', label: 'OpenAI', models: ['gpt-5.5', 'gpt-4o', 'gpt-4o-mini', 'gpt-3.5-turbo'] },
  { value: 'deepseek', label: 'DeepSeek', models: ['deepseek-chat', 'deepseek-reasoner'] },
  { value: 'anthropic', label: 'Anthropic', models: ['claude-3-5-sonnet', 'claude-3-haiku'] },
  { value: 'qwen', label: '通义千问', models: ['qwen-max', 'qwen-plus', 'qwen-turbo'] },
  { value: 'other', label: 'Other / Custom', models: ['custom-model'] },
];

const local = reactive<InferenceConfig>({ ...props.modelValue });
const provider = ref('openai');
const model = ref('gpt-5.5');
const apiKeyName = ref('OPENAI_API_KEY');
const reasoningEffort = ref('default');
const providerApi = ref('responses');
const stopSequences = ref<string[]>([]);
const stopDraft = ref('');
const seed = ref<number | null>(null);
const jsonMode = ref(false);
const extraHeaderKey = ref('');
const extraHeaderValue = ref('');
const extraParamsText = ref('');
const timeoutEnabled = ref(true);
const retriesEnabled = ref(true);
const presetSelected = ref('');

const modelsForProvider = computed(() => {
  const p = PROVIDERS.find((x) => x.value === provider.value);
  return p ? p.models : [];
});

const canAddStop = computed(() => stopSequences.value.length < 4 && stopDraft.value.trim().length > 0);

watch(provider, (p) => {
  const found = PROVIDERS.find((x) => x.value === p);
  if (found && !found.models.includes(model.value)) {
    model.value = found.models[0] ?? '';
  }
});

watch(
  () => props.open,
  (v) => {
    if (v) hydrateFromModel();
  },
);

watch(
  () => props.modelValue,
  () => {
    if (props.open) hydrateFromModel();
  },
  { deep: true },
);

function hydrateFromModel(): void {
  Object.assign(local, props.modelValue);
  provider.value = 'openai';
  model.value = 'gpt-5.5';
  apiKeyName.value = 'OPENAI_API_KEY';
  reasoningEffort.value = 'default';
  providerApi.value = 'responses';
  stopSequences.value = [];
  stopDraft.value = '';
  seed.value = null;
  jsonMode.value = false;
  extraHeaderKey.value = '';
  extraHeaderValue.value = '';
  extraParamsText.value = '';
  timeoutEnabled.value = true;
  retriesEnabled.value = true;
  presetSelected.value = '';

  if (!props.modelValue.extraJson.trim()) return;
  try {
    const parsed = JSON.parse(props.modelValue.extraJson) as Record<string, unknown>;
    if (typeof parsed.provider === 'string') provider.value = parsed.provider;
    if (typeof parsed.model === 'string') model.value = parsed.model;
    if (typeof parsed.apiKeyName === 'string') apiKeyName.value = parsed.apiKeyName;
    if (typeof parsed.reasoningEffort === 'string') reasoningEffort.value = parsed.reasoningEffort;
    if (typeof parsed.providerApi === 'string') providerApi.value = parsed.providerApi;
    if (Array.isArray(parsed.stopSequences)) stopSequences.value = parsed.stopSequences.map(String);
    if (typeof parsed.seed === 'number') seed.value = parsed.seed;
    if (typeof parsed.jsonMode === 'boolean') jsonMode.value = parsed.jsonMode;
    if (typeof parsed.timeoutEnabled === 'boolean') timeoutEnabled.value = parsed.timeoutEnabled;
    if (typeof parsed.retriesEnabled === 'boolean') retriesEnabled.value = parsed.retriesEnabled;
    if (parsed.headers && typeof parsed.headers === 'object') {
      const entries = Object.entries(parsed.headers as Record<string, unknown>);
      if (entries.length > 0) {
        extraHeaderKey.value = entries[0][0];
        extraHeaderValue.value = String(entries[0][1] ?? '');
      }
    }
    if (parsed.extraParams && typeof parsed.extraParams === 'object') {
      extraParamsText.value = JSON.stringify(parsed.extraParams, null, 2);
    } else if (typeof parsed.extraParamsRaw === 'string') {
      extraParamsText.value = parsed.extraParamsRaw;
    }
  } catch {
    extraParamsText.value = props.modelValue.extraJson;
  }
}

function addStopSequence(): void {
  if (!canAddStop.value) return;
  stopSequences.value.push(stopDraft.value.trim());
  stopDraft.value = '';
}

function removeStopSequence(i: number): void {
  stopSequences.value.splice(i, 1);
}

function onSaveAsPreset(): void {
  // UI-only stub; would persist to backend in a real preset store
  presetSelected.value = 'custom';
}

function apply(): void {
  const extraPayload: Record<string, unknown> = {
    provider: provider.value,
    model: model.value,
    apiKeyName: apiKeyName.value,
    reasoningEffort: reasoningEffort.value,
    providerApi: providerApi.value,
    jsonMode: jsonMode.value,
    timeoutEnabled: timeoutEnabled.value,
    retriesEnabled: retriesEnabled.value,
  };
  if (stopSequences.value.length > 0) {
    extraPayload.stopSequences = [...stopSequences.value];
  }
  if (seed.value != null && !Number.isNaN(seed.value)) {
    extraPayload.seed = seed.value;
  }
  if (extraHeaderKey.value.trim()) {
    extraPayload.headers = { [extraHeaderKey.value.trim()]: extraHeaderValue.value };
  }
  if (extraParamsText.value.trim()) {
    try {
      extraPayload.extraParams = JSON.parse(extraParamsText.value);
    } catch {
      extraPayload.extraParamsRaw = extraParamsText.value;
    }
  }

  emit('update:modelValue', {
    ...local,
    extraJson: JSON.stringify(extraPayload),
  });
  emit('update:open', false);
}
</script>

<style scoped>
.sub-modal-mask {
  z-index: 90;
  background: rgba(17, 24, 39, 0.55);
}

.inference-modal {
  width: min(720px, calc(100vw - 36px));
  max-height: calc(100vh - 50px);
  overflow: auto;
  border-radius: 16px;
  border: 1px solid #fecaca;
  background: #fff;
  padding: 18px 20px;
  box-shadow: 0 24px 70px rgba(127, 29, 29, 0.25);
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

.inf-section-title {
  margin: 8px 0 -4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--brand);
  text-transform: uppercase;
}

.info-mark {
  color: #a8a29e;
  font-size: 11px;
  margin-left: 2px;
}

.slider-row {
  display: grid;
  gap: 8px;
}

.slider-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 600;
}

.slider-value {
  color: var(--brand);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.slider-input {
  width: 100%;
  -webkit-appearance: none;
  appearance: none;
  height: 4px;
  background: var(--line-strong);
  border-radius: 999px;
  outline: none;
  padding: 0;
  border: none;
}

.slider-input:disabled {
  opacity: 0.5;
}

.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--brand);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px var(--brand);
}

.slider-input::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--brand);
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px var(--brand);
}

.chip-input-row {
  display: flex;
  gap: 6px;
}

.chip-input-row input {
  flex: 1;
}

.chip-add {
  width: 38px;
  flex-shrink: 0;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.chip-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--brand-faint);
  color: var(--brand);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}

.chip-del {
  background: transparent;
  border: none;
  color: var(--brand);
  cursor: pointer;
  padding: 0;
  font-size: 14px;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 600;
}

.enabled-toggle {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
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
  display: inline-block;
}

.enabled-track.sm {
  width: 28px;
  height: 16px;
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

.enabled-track.sm .enabled-thumb {
  width: 12px;
  height: 12px;
  top: 2px;
}

.enabled-track.on .enabled-thumb {
  left: 16px;
}

.enabled-track.sm.on .enabled-thumb {
  left: 14px;
}

.inline-toggle {
  margin-left: 8px;
}

.header-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 6px;
}

.code-area {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  background: #fafafa;
}

.inf-foot {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.foot-left,
.foot-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.preset-select {
  width: 160px;
}

@media (max-width: 600px) {
  .inf-row.two-col {
    grid-template-columns: 1fr;
  }

  .inf-foot {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
