<template>
  <div class="model-picker">
    <div v-if="modelLoadError" class="notice-text" style="color: #f87171">
      加载模型失败：{{ modelLoadError }}
    </div>
    <div class="picker-block">
      <div class="picker-head">
        <strong>Player（参赛模型，多选 ≥2）</strong>
        <button class="ghost" type="button" @click="reload">刷新</button>
      </div>

      <div v-if="!playerModels.length" class="notice-text">
        ⚠️ 暂无可用 PLAYER 模型 — 先去
        <RouterLink to="/models">「模型管理」</RouterLink> 注册
      </div>

      <div v-else>
        <div v-for="(playerId, idx) in selectedPlayerIds" :key="idx" class="slot-row">
          <span class="slot-index">#{{ idx + 1 }}</span>
          <select :value="playerId" @change="onPlayerSlotChange(idx, ($event.target as HTMLSelectElement).value)">
            <option :value="0">— 请选择 player —</option>
            <option
              v-for="m in availablePlayerOptions(idx)"
              :key="m.modelProfileId"
              :value="m.modelProfileId"
            >
              {{ m.modelId }}（{{ m.displayName }}）
            </option>
          </select>
          <button class="link-btn" type="button" @click="removeSlot(idx)" :disabled="selectedPlayerIds.length <= 1">删除</button>
        </div>
        <button class="ghost slot-add" type="button" @click="addSlot" :disabled="!canAddSlot">
          + 添加 player（已选 {{ countSelectedPlayers }} / 最多 {{ playerModels.length }}）
        </button>
      </div>
    </div>

    <div class="picker-block">
      <div class="picker-head">
        <strong>Judge（评判模型，单选）</strong>
      </div>

      <div v-if="!judgeModels.length" class="notice-text">
        ⚠️ 暂无可用 JUDGE 模型 — 先去
        <RouterLink to="/models">「模型管理」</RouterLink> 注册
      </div>

      <select v-else :value="judgeId ?? 0" @change="onJudgeSelect(($event.target as HTMLSelectElement).value)">
        <option :value="0">— 请选择 judge —</option>
        <option
          v-for="m in judgeModels"
          :key="m.modelProfileId"
          :value="m.modelProfileId"
          :disabled="selectedPlayerIds.includes(m.modelProfileId)"
        >
          {{ m.modelId }}（{{ m.displayName }}）{{ selectedPlayerIds.includes(m.modelProfileId) ? '· 已用作 player' : '' }}
        </option>
      </select>
    </div>

    <p class="notice-text summary-text">
      已选：<strong>{{ countSelectedPlayers }}</strong> 个 player，judge =
      <strong>{{ judgeId ? judgeLabel : '未选' }}</strong>。
      达到 ≥2 player + 1 judge 即触发 BT 多模型评测；否则按单模型流程跑。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';

import { listModels, type ModelProfile } from '../api/client';

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
  emit('update:selectedPlayerIds', next);
}

function addSlot(): void {
  if (!canAddSlot.value) return;
  emit('update:selectedPlayerIds', [...props.selectedPlayerIds, 0]);
}

function removeSlot(idx: number): void {
  if (props.selectedPlayerIds.length <= 1) return;
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
  emit('update:judgeId', id);
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
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
}
.slot-row select {
  flex: 1;
}
.slot-index {
  font-family: monospace;
  color: var(--text-secondary);
  width: 26px;
}
.slot-add {
  margin-top: 4px;
  width: 100%;
}
.summary-text {
  margin-top: 4px;
}
</style>
