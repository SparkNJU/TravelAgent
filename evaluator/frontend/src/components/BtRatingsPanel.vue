<template>
  <article class="surface bt-panel">
    <div class="section-head">
      <h3>BT 排行榜</h3>
      <div class="inline-actions">
        <button class="ghost" @click="reload">刷新</button>
      </div>
    </div>

    <div class="chip-row sort-chip-row">
      <button
        v-for="opt in sortOptions"
        :key="opt.key"
        class="chip"
        :class="{ active: currentSort === opt.key }"
        @click="switchSort(opt.key)"
      >{{ opt.label }}</button>
    </div>

    <p v-if="notice" class="notice-text">{{ notice }}</p>

    <div v-if="ranked.length" class="table-wrap">
      <table class="task-table detail-table">
        <thead>
          <tr>
            <th>排名</th>
            <th>modelId</th>
            <th>显示名</th>
            <th>{{ primaryColLabel }}</th>
            <th>Elo</th>
            <th>CI95%</th>
            <th>胜率</th>
            <th>对战场次</th>
            <th>平均延迟</th>
            <th>平均 token</th>
            <th>完成率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, idx) in ranked" :key="r.ratingId">
            <td>{{ idx + 1 }}</td>
            <td class="mono">{{ r.modelId || '#' + r.modelProfileId }}</td>
            <td>{{ r.displayName || '-' }}</td>
            <td class="primary-col">{{ formatPrimary(r) }}</td>
            <td>{{ r.elo != null ? r.elo.toFixed(1) : '-' }}</td>
            <td>{{ formatCi(r.lowerCi95, r.upperCi95) }}</td>
            <td>{{ formatPercent(r.winRate) }}</td>
            <td>{{ r.nComparisons ?? '-' }}</td>
            <td>{{ r.avgLatencyMs != null ? r.avgLatencyMs + 'ms' : '-' }}</td>
            <td>{{ r.avgTokens ?? '-' }}</td>
            <td>{{ formatPercent(r.completionRate) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="notice-text">
      暂无 BT 排行数据 — 任务可能还没跑完，或当前 run 不是 BT 模式
    </div>
  </article>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';

import { getRunRanked, type ModelRating, type SortBy } from '../api/client';

const props = defineProps<{
  runId: number;
}>();

type SortKey = {
  key: SortBy;
  label: string;
  dimension?: 'OVERALL' | 'EFFECTIVENESS' | 'SAFETY';
};

const sortOptions: SortKey[] = [
  { key: 'elo', label: '总分 Elo', dimension: 'OVERALL' },
  { key: 'winRate', label: '效果胜率', dimension: 'EFFECTIVENESS' },
  { key: 'safetyElo', label: '安全 Elo' },
  { key: 'latency', label: '响应速度' },
  { key: 'tokens', label: 'Token 消耗' },
  { key: 'completionRate', label: '完成率' },
];

const currentSort = ref<SortBy>('elo');
const ranked = ref<ModelRating[]>([]);
const notice = ref('');
const primaryColLabel = ref('Elo (OVERALL)');

watch(() => props.runId, () => {
  void reload();
});

onMounted(() => {
  void reload();
});

async function switchSort(key: SortBy): Promise<void> {
  currentSort.value = key;
  await reload();
}

async function reload(): Promise<void> {
  if (!props.runId) {
    ranked.value = [];
    return;
  }
  try {
    const opt = sortOptions.find((o) => o.key === currentSort.value)!;
    const res = await getRunRanked(props.runId, currentSort.value, {
      dimension: opt.dimension as any,
    });
    ranked.value = res.ranked;
    primaryColLabel.value = labelOf(currentSort.value, opt.dimension);
    notice.value = ranked.value.length
      ? `共 ${ranked.value.length} 个模型 · 排序：${opt.label} · order=${res.order}`
      : '该 run 没有 BT 评测数据（确认任务以 BT 模式跑完）';
  } catch (err: any) {
    notice.value = `加载失败: ${err.message || String(err)}`;
    ranked.value = [];
  }
}

function labelOf(sortBy: SortBy, dimension?: string): string {
  switch (sortBy) {
    case 'elo': return `Elo (${dimension || 'OVERALL'})`;
    case 'winRate': return `胜率 (${dimension || 'OVERALL'})`;
    case 'latency': return '平均延迟 (ms)';
    case 'tokens': return '平均 Token';
    case 'completionRate': return '完成率';
    case 'safetyElo': return 'Elo (SAFETY)';
    default: return sortBy;
  }
}

function formatPrimary(r: ModelRating): string {
  switch (currentSort.value) {
    case 'elo':
    case 'safetyElo':
      return r.elo != null ? r.elo.toFixed(1) : '-';
    case 'winRate':
      return formatPercent(r.winRate);
    case 'latency':
      return r.avgLatencyMs != null ? r.avgLatencyMs + 'ms' : '-';
    case 'tokens':
      return r.avgTokens != null ? String(r.avgTokens) : '-';
    case 'completionRate':
      return formatPercent(r.completionRate);
    default:
      return '-';
  }
}

function formatPercent(v: number | null | undefined): string {
  if (v == null) return '-';
  return (v * 100).toFixed(1) + '%';
}

function formatCi(lo: number | null, hi: number | null): string {
  if (lo == null || hi == null) return '-';
  return `[${lo.toFixed(1)}, ${hi.toFixed(1)}]`;
}
</script>

<style scoped>
.bt-panel {
  display: grid;
  gap: 12px;
}
.sort-chip-row {
  margin-bottom: 4px;
}
.sort-chip-row .chip {
  cursor: pointer;
  user-select: none;
}
.sort-chip-row .chip.active {
  background: var(--brand);
  color: #ffffff;
  border-color: var(--brand);
}
.mono {
  font-family: monospace;
}
.primary-col {
  font-weight: 600;
  color: var(--brand-press);
}
</style>
