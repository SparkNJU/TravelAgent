<template>
  <section class="dashboard-page history-page">
    <article class="surface intro-card">
      <div>
        <h2>评测历史</h2>
        <p>集中查看所有任务的运行记录，支持按状态回看最近执行并跳转到详情与监控。</p>
      </div>
      <button type="button" class="ghost" @click="load">刷新</button>
    </article>

    <article class="surface task-board">
      <p v-if="notice" class="notice-text">{{ notice }}</p>

      <div class="table-wrap">
        <table class="task-table">
          <thead>
            <tr>
              <th>Run ID</th>
              <th>任务名称</th>
              <th>Task ID</th>
              <th>状态</th>
              <th>成功 / 总数</th>
              <th>开始时间</th>
              <th>结束时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.runId">
              <td class="mono">#{{ row.runId }}</td>
              <td>{{ row.taskName }}</td>
              <td class="mono">#{{ row.taskId }}</td>
              <td><span class="status" :class="statusClass(row.status)">{{ row.status }}</span></td>
              <td>{{ row.successCount ?? '-' }} / {{ row.totalCount ?? '-' }}</td>
              <td>{{ formatTime(row.startTime) }}</td>
              <td>{{ formatTime(row.endTime) }}</td>
              <td class="history-actions">
                <button type="button" class="link-btn" @click="goRun(row.runId)">详情</button>
                <button type="button" class="link-btn" @click="goMonitor(row.runId)">监控</button>
              </td>
            </tr>
            <tr v-if="!rows.length && !loading">
              <td colspan="8">暂无运行记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { listTaskRuns, listTasks, type EvalRun } from '../api/client';

type HistoryRow = EvalRun & {
  taskName: string;
};

const router = useRouter();
const rows = ref<HistoryRow[]>([]);
const notice = ref('');
const loading = ref(false);

onMounted(() => void load());

async function load(): Promise<void> {
  loading.value = true;
  notice.value = '';
  try {
    const tasks = await listTasks();
    const runsPerTask = await Promise.all(
      tasks.map(async (task) => {
        try {
          const page = await listTaskRuns(task.taskId, { page: 0, size: 15 });
          return page.items.map((run) => ({
            ...run,
            taskName: task.taskName,
          }));
        } catch {
          return [] as HistoryRow[];
        }
      }),
    );

    const flat = runsPerTask.flat();
    flat.sort((a, b) => {
      const ta = a.endTime || a.startTime || '';
      const tb = b.endTime || b.startTime || '';
      return tb.localeCompare(ta);
    });
    rows.value = flat.slice(0, 200);
    notice.value = `共加载 ${rows.value.length} 条运行记录（最多显示 200 条）`;
  } catch (err: any) {
    rows.value = [];
    notice.value = `加载失败: ${err.message || String(err)}`;
  } finally {
    loading.value = false;
  }
}

function goRun(runId: number): void {
  router.push({ name: 'RunDetail', params: { runId } });
}

function goMonitor(runId: number): void {
  router.push({ name: 'Monitor', params: { runId } });
}

function statusClass(status: string): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'failed';
  if (status === 'RUNNING') return 'running';
  return '';
}

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString('zh-CN', { hour12: false });
}
</script>

<style scoped>
.mono {
  font-family: monospace;
}

.history-actions {
  display: flex;
  gap: 10px;
}
</style>
