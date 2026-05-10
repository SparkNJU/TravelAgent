<template>
  <section class="dashboard-page">
    <article class="surface strategy-card">
      <div class="section-head">
        <h3>AI 评测配置</h3>
        <button class="ghost" @click="loadStrategyData">刷新策略</button>
      </div>

      <p v-if="notice" class="notice-text">{{ notice }}</p>

      <div class="form-layout">
        <h4>策略管理</h4>
        <label>
          新建策略名称
          <input v-model.trim="strategyForm.strategyName" type="text" placeholder="如：默认生产策略" />
        </label>
        <label>
          权重配置 (JSON)
          <textarea v-model="strategyForm.weightConfig" rows="3" placeholder='{"effectiveness":0.5,"safety":0.2,"performance":0.3}' />
        </label>
        <label>
          门限配置 (JSON)
          <textarea v-model="strategyForm.thresholdConfig" rows="3" placeholder='{"overallThreshold":0.75,"safetyMin":0.7}' />
        </label>
        <div class="inline-actions">
          <button class="ghost" @click="createStrategyAction">创建策略</button>
        </div>

        <label>
          选择策略
          <select v-model.number="strategyForm.selectedStrategyId">
            <option :value="0">请选择策略</option>
            <option v-for="item in strategies" :key="item.strategyId" :value="item.strategyId">
              {{ item.strategyName }} (latest={{ item.latestVersion || '-' }})
            </option>
          </select>
        </label>
        <label>
          新版本号 (可选)
          <input v-model.number="strategyForm.newVersion" type="number" min="1" placeholder="留空自动递增" />
        </label>
        <div class="inline-actions">
          <button class="ghost" @click="createStrategyVersionAction">保存版本</button>
        </div>

        <h4>自定义指标</h4>
        <label>
          自定义指标名称
          <input v-model.trim="metricForm.metricName" type="text" placeholder="如：响应稳定性" />
        </label>
        <label>
          指标类型
          <select v-model="metricForm.metricType">
            <option value="DETERMINISTIC">DETERMINISTIC</option>
            <option value="JUDGE">JUDGE</option>
          </select>
        </label>
        <label>
          评分逻辑
          <input v-model.trim="metricForm.scoringLogic" type="text" placeholder="completion / latency / performance" />
        </label>
        <label>
          阈值
          <input v-model.number="metricForm.thresholdValue" type="number" step="0.01" min="0" max="1" />
        </label>
        <div class="inline-actions">
          <button class="ghost" @click="createCustomMetricAction">注册自定义指标</button>
          <span>已启用指标: {{ enabledMetricCount }}</span>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import {
  createCustomMetric,
  createStrategy,
  createStrategyVersion,
  listCustomMetrics,
  listStrategies,
  type CustomMetric,
  type EvalStrategy,
} from '../api/client';

const strategies = ref<EvalStrategy[]>([]);
const customMetrics = ref<CustomMetric[]>([]);
const notice = ref('');

const strategyForm = reactive({
  strategyName: '',
  selectedStrategyId: 0,
  newVersion: null as number | null,
  weightConfig: '{"effectiveness":0.5,"safety":0.2,"performance":0.3}',
  thresholdConfig: '{"overallThreshold":0.75,"safetyMin":0.7}',
});

const metricForm = reactive({
  metricName: '',
  metricType: 'DETERMINISTIC' as 'DETERMINISTIC' | 'JUDGE',
  scoringLogic: 'completion',
  thresholdValue: 0.7,
});

const enabledMetricCount = computed(() => customMetrics.value.filter((x) => x.enabled).length);

onMounted(() => {
  void loadStrategyData();
});

async function loadStrategyData(): Promise<void> {
  try {
    const [strategyList, metricList] = await Promise.all([listStrategies(), listCustomMetrics()]);
    strategies.value = strategyList;
    customMetrics.value = metricList;
    notice.value = `已加载 ${strategyList.length} 个策略，${metricList.length} 个自定义指标`;
  } catch (err: any) {
    notice.value = `加载策略失败: ${err.message || String(err)}`;
  }
}

async function createStrategyAction(): Promise<void> {
  if (!strategyForm.strategyName) {
    notice.value = '请先输入策略名称';
    return;
  }
  try {
    const result = await createStrategy({
      strategyName: strategyForm.strategyName,
      weightConfig: strategyForm.weightConfig,
      thresholdConfig: strategyForm.thresholdConfig,
    });
    strategyForm.selectedStrategyId = result.strategyId;
    notice.value = `策略创建成功: ${result.strategyName}`;
    await loadStrategyData();
  } catch (err: any) {
    notice.value = `创建策略失败: ${err.message || String(err)}`;
  }
}

async function createStrategyVersionAction(): Promise<void> {
  if (!strategyForm.selectedStrategyId) {
    notice.value = '请先选择策略';
    return;
  }
  try {
    const result = await createStrategyVersion(strategyForm.selectedStrategyId, {
      version: strategyForm.newVersion || undefined,
      weightConfig: strategyForm.weightConfig,
      thresholdConfig: strategyForm.thresholdConfig,
    });
    notice.value = `策略版本保存成功: version=${result.version}, id=${result.strategyVersionId}`;
    await loadStrategyData();
  } catch (err: any) {
    notice.value = `保存策略版本失败: ${err.message || String(err)}`;
  }
}

async function createCustomMetricAction(): Promise<void> {
  if (!metricForm.metricName) {
    notice.value = '请先输入自定义指标名称';
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
    notice.value = `自定义指标已注册: ${metric.metricName} (#${metric.customMetricId})`;
    await loadStrategyData();
  } catch (err: any) {
    notice.value = `注册自定义指标失败: ${err.message || String(err)}`;
  }
}
</script>

<style scoped>
.form-layout h4 {
  margin: 12px 0 8px;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
