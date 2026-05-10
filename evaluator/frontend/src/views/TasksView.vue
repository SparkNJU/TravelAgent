<template>
  <section class="dashboard-page">
    <article class="surface intro-card">
      <div>
        <h2>评测任务</h2>
        <p>创建评测任务、启动、查看运行历史。BT 多模型评测请勾选 ≥2 个 player + 1 个 judge。</p>
      </div>
      <div class="chip-row">
        <span class="chip">结果导向</span>
        <span class="chip">过程导向</span>
        <span class="chip">显式指标 + 模糊评估</span>
        <span class="chip">Bradley-Terry 多模型评测</span>
      </div>
    </article>

    <article class="surface task-board">
      <div class="section-head">
        <h3>评测任务列表</h3>
        <div class="inline-actions">
          <button class="ghost" @click="reloadAll">刷新数据</button>
          <button class="primary" @click="openCreateTask()">创建任务</button>
        </div>
      </div>

      <div class="filter-bar">
        <select v-model="filters.status">
          <option value="">任务状态</option>
          <option value="READY">READY</option>
          <option value="RUNNING">RUNNING</option>
          <option value="SUCCEEDED">SUCCEEDED</option>
          <option value="FAILED">FAILED</option>
        </select>
        <input v-model.trim="filters.agentVersion" type="text" placeholder="Agent 版本" />
        <input v-model.trim="filters.keyword" type="text" placeholder="测试任务名称" />
        <button class="ghost" @click="loadTasks">搜索</button>
        <button class="ghost" @click="resetFilters">重置</button>
      </div>

      <p v-if="noticeText" class="notice-text">{{ noticeText }}</p>

      <div class="table-wrap">
        <table class="task-table">
          <thead>
            <tr>
              <th>测试任务名称</th>
              <th>评估方式</th>
              <th>BT 模式</th>
              <th>Agent 版本</th>
              <th>数据集</th>
              <th>状态</th>
              <th>样本数</th>
              <th>Token</th>
              <th>创建时间</th>
              <th>完成时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in filteredTasks" :key="task.taskId">
              <td>{{ task.taskName }}</td>
              <td>{{ task.evaluationMethod }}</td>
              <td>
                <span v-if="task.selectedModelIds && task.selectedModelIds.length >= 2" class="chip">
                  BT × {{ task.selectedModelIds.length }}
                </span>
                <span v-else>-</span>
              </td>
              <td>{{ task.agentVersion }}</td>
              <td class="mono small">{{ task.datasetId }}</td>
              <td><span class="status" :class="statusClass(task.status)">{{ task.status }}</span></td>
              <td>{{ runByTask[task.taskId]?.totalCount ?? '-' }}</td>
              <td>{{ metricsByTask[task.taskId]?.totalTokens ?? '-' }}</td>
              <td>{{ formatTime(task.createdAt) }}</td>
              <td>{{ formatTime(runByTask[task.taskId]?.endTime) }}</td>
              <td>
                <button class="link-btn" @click="goToRunDetail(task.taskId)">详情</button>
                <button class="link-btn" @click="startRun(task.taskId)">启动</button>
                <button class="link-btn danger-link" @click="confirmDelete(task)">删除</button>
              </td>
            </tr>
            <tr v-if="!filteredTasks.length">
              <td colspan="11">暂无任务数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

  </section>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  deleteTask,
  listTaskRuns,
  listTasks,
  startTask,
  type EvalRun,
  type EvalTask,
  type RunMetrics,
  type TaskStatus,
} from '../api/client';

const router = useRouter();

const openCreateTask = inject<(preset?: string) => void>('openCreateTask', () => {});

const tasks = ref<EvalTask[]>([]);
const runByTask = reactive<Record<number, EvalRun>>({});
const metricsByTask = reactive<Record<number, RunMetrics>>({});
const runIdByTask = reactive<Record<number, number>>({});
const noticeText = ref('');

const filters = reactive({
  status: '',
  agentVersion: '',
  keyword: '',
});

const filteredTasks = computed(() => {
  if (!filters.keyword) return tasks.value;
  const key = filters.keyword.toLowerCase();
  return tasks.value.filter((item) => item.taskName.toLowerCase().includes(key));
});

let filterDebounceTimer: ReturnType<typeof setTimeout> | null = null;
watch(() => filters.status, () => void loadTasks());
watch(() => filters.agentVersion, () => {
  if (filterDebounceTimer) clearTimeout(filterDebounceTimer);
  filterDebounceTimer = setTimeout(() => void loadTasks(), 300);
});

function onTasksReload(): void {
  void reloadAll();
}

onMounted(async () => {
  window.addEventListener('tripagent-tasks-reload', onTasksReload);
  await reloadAll();
});

onBeforeUnmount(() => {
  window.removeEventListener('tripagent-tasks-reload', onTasksReload);
});

async function reloadAll(): Promise<void> {
  void loadTasks();
}

async function loadTasks(): Promise<void> {
  try {
    tasks.value = await listTasks({
      status: filters.status || undefined,
      agentVersion: filters.agentVersion || undefined,
    });
    await syncTaskRunSnapshots();
    noticeText.value = `已加载 ${tasks.value.length} 个任务`;
  } catch (err: any) {
    noticeText.value = `加载任务失败: ${err.message || String(err)}`;
  }
}

async function syncTaskRunSnapshots(): Promise<void> {
  const taskIds = new Set(tasks.value.map((t) => t.taskId));
  Object.keys(runByTask).forEach((key) => {
    const id = Number(key);
    if (!taskIds.has(id)) {
      delete runByTask[id];
      delete runIdByTask[id];
      delete metricsByTask[id];
    }
  });

  const results = await Promise.all(
    tasks.value.map(async (task) => {
      try {
        const page = await listTaskRuns(task.taskId, { page: 0, size: 1 });
        return { taskId: task.taskId, latestRun: page.items[0] || null };
      } catch {
        return { taskId: task.taskId, latestRun: null };
      }
    }),
  );

  results.forEach(({ taskId, latestRun }) => {
    if (latestRun) {
      runByTask[taskId] = latestRun;
      runIdByTask[taskId] = latestRun.runId;
    } else {
      delete runByTask[taskId];
      delete runIdByTask[taskId];
    }
  });
}

function resetFilters(): void {
  filters.status = '';
  filters.agentVersion = '';
  filters.keyword = '';
  void loadTasks();
}

async function startRun(taskId: number): Promise<void> {
  try {
    const run = await startTask(taskId);
    runByTask[taskId] = run;
    runIdByTask[taskId] = run.runId;
    noticeText.value = `任务已启动，runId=${run.runId}，跳转到运行详情...`;
    router.push({ name: 'RunDetail', params: { runId: run.runId } });
  } catch (err: any) {
    noticeText.value = `启动任务失败: ${err.message || String(err)}`;
  }
}

async function confirmDelete(task: EvalTask): Promise<void> {
  if (task.status === 'RUNNING') {
    noticeText.value = `任务 #${task.taskId} 正在运行,无法删除`;
    return;
  }
  const ok = window.confirm(
    `确定要删除任务「${task.taskName}」吗?\n会同时清空该任务的所有运行记录、对比、评分与样本数据,且不可恢复。`,
  );
  if (!ok) return;
  try {
    await deleteTask(task.taskId);
    noticeText.value = `已删除任务 #${task.taskId}`;
    delete runByTask[task.taskId];
    delete runIdByTask[task.taskId];
    delete metricsByTask[task.taskId];
    await loadTasks();
  } catch (err: any) {
    noticeText.value = `删除失败: ${err.message || String(err)}`;
  }
}

async function goToRunDetail(taskId: number): Promise<void> {
  let runId = runIdByTask[taskId];
  if (!runId) {
    try {
      const page = await listTaskRuns(taskId, { page: 0, size: 1 });
      runId = page.items[0]?.runId;
    } catch {
      // ignore
    }
  }
  if (!runId) {
    noticeText.value = '该任务暂无运行记录，请先点击启动';
    return;
  }
  router.push({ name: 'RunDetail', params: { runId } });
}

function statusClass(status: TaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'failed';
  if (status === 'RUNNING') return 'running';
  return '';
}

function formatTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', { hour12: false });
}
</script>

<style scoped>
.mono {
  font-family: monospace;
}
.small {
  font-size: 12px;
}
.danger-link {
  color: #b91c1c;
  margin-left: 6px;
}
.danger-link:hover {
  color: #7f1d1d;
}
</style>
