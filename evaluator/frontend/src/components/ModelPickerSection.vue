<template>
  <div class="model-picker">
    <div v-if="modelLoadError" class="notice-text" style="color: #f87171">
      加载模型失败：{{ modelLoadError }}
    </div>
    <div class="picker-block">
      <div class="picker-head">
        <strong>参赛模型（多选，至少 2 个）</strong>
        <button class="ghost" type="button" @click="reload">刷新</button>
      </div>

      <div v-if="!playerModels.length" class="notice-text">
        ⚠️ 暂无可用参赛模型，请先到
        <RouterLink to="/models">「模型管理」</RouterLink> 注册
      </div>

      <div v-else>
        <div v-for="(playerId, idx) in selectedPlayerIds" :key="idx" class="slot-row">
          <span class="slot-index">#{{ idx + 1 }}</span>
          <select :value="playerId" @change="onPlayerSlotChange(idx, ($event.target as HTMLSelectElement).value)">
            <option :value="0">— 请选择参赛模型 —</option>
            <option
              v-for="m in availablePlayerOptions(idx)"
              :key="m.modelProfileId"
              :value="m.modelProfileId"
            >
              {{ m.modelId }}（{{ m.displayName }}）
            </option>
          </select>
          <div class="slot-actions">
            <button
              class="ghost slot-ping"
              type="button"
              :disabled="playerId <= 0 || !!playerPingLoading[idx]"
              @click="pingPlayer(idx, playerId)"
            >
              {{ playerPingLoading[idx] ? '检测中...' : '检测' }}
            </button>
            <button
              class="ghost slot-delete"
              type="button"
              @click="removeSlot(idx)"
              :disabled="selectedPlayerIds.length <= 1"
            >
              删除
            </button>
          </div>
          <small
            v-if="playerPingText[idx]"
            class="slot-feedback"
            :class="playerPingOk[idx] ? 'slot-feedback-ok' : 'slot-feedback-err'"
          >
            {{ playerPingText[idx] }}
          </small>
        </div>
        <button class="ghost slot-add" type="button" @click="addSlot" :disabled="!canAddSlot">
          + 添加参赛模型（已选 {{ countSelectedPlayers }} / 最多 {{ playerModels.length }}）
        </button>
      </div>
    </div>

    <div class="picker-block">
      <div class="picker-head">
        <strong>裁判模型（单选）</strong>
      </div>

      <div v-if="!judgeModels.length" class="notice-text">
        ⚠️ 暂无可用裁判模型，请先到
        <RouterLink to="/models">「模型管理」</RouterLink> 注册
      </div>

      <template v-else>
        <div class="judge-row">
          <select :value="judgeId ?? 0" @change="onJudgeSelect(($event.target as HTMLSelectElement).value)">
            <option :value="0">— 请选择裁判模型 —</option>
            <option
              v-for="m in judgeModels"
              :key="m.modelProfileId"
              :value="m.modelProfileId"
              :disabled="selectedPlayerIds.includes(m.modelProfileId)"
            >
              {{ m.modelId }}（{{ m.displayName }}）{{ selectedPlayerIds.includes(m.modelProfileId) ? '· 已用作参赛模型' : '' }}
            </option>
          </select>
          <button
            class="ghost slot-ping judge-ping"
            type="button"
            :disabled="!judgeId || judgePingLoading"
            @click="pingJudge"
          >
            {{ judgePingLoading ? '检测中...' : '检测' }}
          </button>
        </div>
        <small
          v-if="judgePingText"
          class="slot-feedback"
          :class="judgePingOk ? 'slot-feedback-ok' : 'slot-feedback-err'"
        >
          {{ judgePingText }}
        </small>
      </template>
    </div>

    <p class="notice-text summary-text">
      已选：<strong>{{ countSelectedPlayers }}</strong> 个参赛模型，裁判模型 =
      <strong>{{ judgeId ? judgeLabel : '未选' }}</strong>。
      达到“至少 2 个参赛模型 + 1 个裁判模型”即触发 BT 多模型评测；否则按单模型流程执行。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';

import { listModels, pingModel, type ModelProfile } from '../api/client';

const props = defineProps<{
  selectedPlayerIds: number[];
  judgeId: number | null;
}>();

const emit = defineEmits<{
  'update:selectedPlayerIds': [ids: number[]];
  'update:judgeId': [id: number | null];
}>();

const allModels = ref<ModelProfile[]>([]);
const modelLoadError = ref('');
const playerPingLoading = ref<Record<number, boolean>>({});
const playerPingText = ref<Record<number, string>>({});
const playerPingOk = ref<Record<number, boolean>>({});
const judgePingLoading = ref(false);
const judgePingText = ref('');
const judgePingOk = ref(false);

const playerModels = computed(() =>
  allModels.value.filter((m) => m.enabled && (m.role === 'PLAYER' || m.role === 'BOTH')),
);
const judgeModels = computed(() =>
  allModels.value.filter((m) => m.enabled && (m.role === 'JUDGE' || m.role === 'BOTH')),
);
const judgeLabel = computed(() => {
  const m = allModels.value.find((x) => x.modelProfileId === props.judgeId);
  return m ? m.modelId : `#${props.judgeId}`;
});

const countSelectedPlayers = computed(() => props.selectedPlayerIds.filter((id) => id > 0).length);
const canAddSlot = computed(() => props.selectedPlayerIds.length < playerModels.value.length);

onMounted(() => {
  void reload();
});

async function reload(): Promise<void> {
  try {
    modelLoadError.value = '';
    allModels.value = await listModels({ enabledOnly: true });
    // 初始化时若 selectedPlayerIds 是空数组，给两个空槽位（用户至少要选 2 个）
    if (props.selectedPlayerIds.length === 0) {
      emit('update:selectedPlayerIds', [0, 0]);
    }
  } catch (err: any) {
    allModels.value = [];
    modelLoadError.value = err.message || String(err);
  }
}

function availablePlayerOptions(currentSlotIdx: number): ModelProfile[] {
  // 排除掉其他槽位已选的，但允许当前槽位的当前选中值（这样 select 能显示）
  const selectedInOtherSlots = props.selectedPlayerIds
    .filter((_, i) => i !== currentSlotIdx)
    .filter((id) => id > 0);
  return playerModels.value.filter(
    (m) =>
      !selectedInOtherSlots.includes(m.modelProfileId) &&
      m.modelProfileId !== props.judgeId,
  );
}

function onPlayerSlotChange(slotIdx: number, raw: string): void {
  const next = [...props.selectedPlayerIds];
  next[slotIdx] = Number(raw);
  playerPingText.value[slotIdx] = '';
  playerPingOk.value[slotIdx] = false;
  emit('update:selectedPlayerIds', next);
}

function addSlot(): void {
  if (!canAddSlot.value) return;
  playerPingLoading.value = {};
  playerPingText.value = {};
  playerPingOk.value = {};
  emit('update:selectedPlayerIds', [...props.selectedPlayerIds, 0]);
}

function removeSlot(idx: number): void {
  if (props.selectedPlayerIds.length <= 1) return;
  playerPingLoading.value = {};
  playerPingText.value = {};
  playerPingOk.value = {};
  const next = props.selectedPlayerIds.filter((_, i) => i !== idx);
  emit('update:selectedPlayerIds', next);
}

function onJudgeSelect(raw: string): void {
  const id = Number(raw);
  if (id === 0) {
    emit('update:judgeId', null);
    return;
  }
  if (props.selectedPlayerIds.includes(id)) return;
  judgePingText.value = '';
  judgePingOk.value = false;
  emit('update:judgeId', id);
}

async function pingPlayer(slotIdx: number, modelProfileId: number): Promise<void> {
  if (!modelProfileId || modelProfileId <= 0) return;
  playerPingLoading.value[slotIdx] = true;
  playerPingText.value[slotIdx] = '';
  playerPingOk.value[slotIdx] = false;
  try {
    const res = await pingModel(modelProfileId, 'Ping from player slot');
    playerPingOk.value[slotIdx] = true;
    playerPingText.value[slotIdx] = `可用 · ${res.latencyMs} ms`;
  } catch (err: any) {
    playerPingOk.value[slotIdx] = false;
    playerPingText.value[slotIdx] = `不可用 · ${err.message || String(err)}`;
  } finally {
    playerPingLoading.value[slotIdx] = false;
  }
}

async function pingJudge(): Promise<void> {
  if (!props.judgeId) return;
  judgePingLoading.value = true;
  judgePingText.value = '';
  judgePingOk.value = false;
  try {
    const res = await pingModel(props.judgeId, 'Ping from judge slot');
    judgePingOk.value = true;
    judgePingText.value = `可用 · ${res.latencyMs} ms`;
  } catch (err: any) {
    judgePingOk.value = false;
    judgePingText.value = `不可用 · ${err.message || String(err)}`;
  } finally {
    judgePingLoading.value = false;
  }
}
</script>

<style scoped>
.model-picker {
  display: grid;
  gap: 14px;
}
.picker-block {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px;
}
.picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.slot-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  margin-bottom: 10px;
}
.slot-row select {
  min-width: 0;
}
.slot-index {
  font-family: monospace;
  color: var(--text-secondary);
  width: 26px;
  margin-top: 8px;
}
.slot-actions {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  margin-top: 1px;
}
.slot-ping,
.slot-delete {
  min-width: 74px;
  min-height: 36px;
  font-size: 13px;
}
.judge-ping {
  min-width: 82px;
}
.slot-feedback {
  grid-column: 2 / -1;
  font-size: 12px;
  line-height: 1.35;
  margin-top: -4px;
  margin-bottom: 2px;
}
.slot-feedback-ok {
  color: #15803d;
}
.slot-feedback-err {
  color: #b91c1c;
}
.judge-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}
.slot-add {
  margin-top: 8px;
  width: 100%;
  min-height: 38px;
}
.summary-text {
  margin-top: 6px;
}
</style>
