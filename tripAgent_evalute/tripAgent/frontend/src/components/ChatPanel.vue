<template>
  <article class="surface monitor-panel">
    <div class="section-head">
      <h3>样本监控详情</h3>
      <button class="ghost" @click="$emit('refresh')">刷新</button>
    </div>

    <ol class="timeline">
      <li v-for="item in timelineItems" :key="item.time + item.title">
        <div class="dot" />
        <div class="event-body">
          <p>
            <strong>{{ item.time }}</strong>
            <span>{{ item.title }}</span>
          </p>
          <small>{{ item.detail }}</small>
        </div>
      </li>
      <li v-if="!timelineItems.length">
        <div class="dot" />
        <div class="event-body">
          <p><strong>-</strong><span>暂无运行事件</span></p>
          <small>启动任务后将实时显示执行时间线</small>
        </div>
      </li>
    </ol>

    <div class="trace-table-wrap">
      <table class="trace-table">
        <thead>
          <tr>
            <th>工具名</th>
            <th>输入参数</th>
            <th>输出摘要</th>
            <th>耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in traceItems" :key="row.tool + row.cost">
            <td>{{ row.tool }}</td>
            <td>{{ row.input }}</td>
            <td>{{ row.output }}</td>
            <td>{{ row.cost }}</td>
          </tr>
          <tr v-if="!traceItems.length">
            <td colspan="4">暂无工具轨迹</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="error-box" v-if="errorSummary">
      <p>失败阶段：运行评测</p>
      <small>{{ errorSummary }}</small>
    </div>

    <div class="quick-ask">
      <input v-model="question" placeholder="快速追问：为什么该样本未通过？" @keyup.enter="appendQuestion" />
      <button class="primary" :disabled="!question.trim()" @click="appendQuestion">记录问题</button>
    </div>

    <div class="memo-list">
      <p v-for="(memo, idx) in memos" :key="idx">{{ memo }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

type TimelineItem = {
  time: string;
  title: string;
  detail: string;
};

type TraceItem = {
  tool: string;
  input: string;
  output: string;
  cost: string;
};

const props = defineProps<{
  timeline?: TimelineItem[];
  traces?: TraceItem[];
  errorSummary?: string;
}>();

defineEmits<{
  refresh: [];
}>();

const question = ref('');
const memos = ref<string[]>([]);

const timelineItems = computed(() => props.timeline ?? []);
const traceItems = computed(() => props.traces ?? []);

function appendQuestion(): void {
  const value = question.value.trim();
  if (!value) {
    return;
  }
  memos.value.unshift(`问题记录：${value}`);
  question.value = '';
}
</script>
