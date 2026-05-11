<template>
  <div class="hub-layout">
    <section class="hub-main">
      <header class="hub-header">
        <h1 class="hub-title">Get started with Evaluators</h1>
        <p class="hub-sub">
          选择评测模式或指标快捷模板,即可一键预填配置;也可从右侧 "Create from scratch" 进入完整双栏配置。
        </p>
      </header>

      <h2 class="hub-section-title">Start from a template · 评测模式</h2>
      <div class="template-grid">
        <button
          v-for="card in modeCards"
          :key="card.preset"
          type="button"
          class="template-card"
          @click="openCreate(card.preset)"
        >
          <span class="tc-icon">{{ card.icon }}</span>
          <span class="tc-body">
            <span class="tc-title">{{ card.title }}</span>
            <span class="tc-desc">{{ card.desc }}</span>
          </span>
        </button>
      </div>

      <h2 class="hub-section-title second-title">指标维度</h2>
      <div class="template-grid">
        <button
          v-for="card in metricCards"
          :key="card.preset"
          type="button"
          class="template-card"
          @click="openCreate(card.preset)"
        >
          <span class="tc-icon">{{ card.icon }}</span>
          <span class="tc-body">
            <span class="tc-title">{{ card.title }}</span>
            <span class="tc-desc">{{ card.desc }}</span>
          </span>
        </button>
      </div>
    </section>

    <aside class="hub-rail">
      <h2 class="hub-section-title rail-section-title">Create from scratch</h2>
      <button type="button" class="scratch-card" @click="openCreate()">
        <span class="sc-icon">⚖</span>
        <span class="sc-body">
          <span class="sc-title">LLM-as-a-Judge Evaluator</span>
          <span class="sc-desc">完整配置评测任务:模型、数据集、维度。</span>
        </span>
      </button>
      <button type="button" class="scratch-card" @click="openCreate('deterministic')">
        <span class="sc-icon">⌨</span>
        <span class="sc-body">
          <span class="sc-title">Code / Rule Evaluator</span>
          <span class="sc-desc">规则验收,无需 Judge 模型。</span>
        </span>
      </button>

      <div class="rail-divider"></div>

      <button type="button" class="rail-help" @click="openHelp">
        <span>📖</span>
        <span>帮助文档</span>
      </button>

      <div class="rail-quick">
        <span class="rail-quick-title">快捷入口</span>
        <RouterLink to="/tasks" class="rail-quick-link">评测任务列表</RouterLink>
        <RouterLink to="/history" class="rail-quick-link">评测历史</RouterLink>
        <RouterLink to="/models" class="rail-quick-link">模型管理</RouterLink>
        <RouterLink to="/datasets" class="rail-quick-link">数据集</RouterLink>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue';
import { RouterLink } from 'vue-router';

type OpenCreate = (preset?: string) => void;
type OpenHelp = () => void;

const openCreateTask = inject<OpenCreate>('openCreateTask', () => {});
const openHelpDoc = inject<OpenHelp>('openHelpDoc', () => {});

const modeCards = [
  {
    preset: 'bt',
    icon: '⚖',
    title: 'BT 多模型对比',
    desc: '≥2 Player + Judge，全对比较与 Elo 排行；适合横向对比不同模型。',
    tint: 'linear-gradient(135deg,#b91c1c,#dc2626)',
  },
  {
    preset: 'result-judge',
    icon: '✓',
    title: '结果 + LLM 裁判',
    desc: 'RESULT 模式，只看输入与最终输出，由 Judge 打分。',
    tint: 'linear-gradient(135deg,#991b1b,#ef4444)',
  },
  {
    preset: 'process-judge',
    icon: '◇',
    title: '过程 + 裁判',
    desc: 'PROCESS 模式，裁判可参考工具轨迹等过程信息。',
    tint: 'linear-gradient(135deg,#7f1d1d,#f87171)',
  },
  {
    preset: 'deterministic',
    icon: '#',
    title: '显式 / 关键词',
    desc: 'DETERMINISTIC，不调用裁判模型，适合规则型验收。',
    tint: 'linear-gradient(135deg,#450a0a,#b91c1c)',
  },
  {
    preset: 'hybrid',
    icon: '◆',
    title: '混合 HYBRID',
    desc: '向 Judge 注入 expectedOutput，兼顾规则与语义判断。',
    tint: 'linear-gradient(135deg,#9f1239,#fb7185)',
  },
  {
    preset: 'judge-single',
    icon: '☑',
    title: '单模型质量验收',
    desc: '1 个模型 + 1 套维度，适合版本回归检查。',
    tint: 'linear-gradient(135deg,#be123c,#fb7185)',
  },
];

const metricCards = [
  {
    preset: 'safety',
    icon: '🛡',
    title: '安全优先',
    desc: '聚焦 safety，适合内容风险、合规能力评测。',
    tint: 'linear-gradient(135deg,#881337,#e11d48)',
  },
  {
    preset: 'effectiveness',
    icon: '🎯',
    title: '效果优先',
    desc: '聚焦 effectiveness，验证任务完成质量。',
    tint: 'linear-gradient(135deg,#9f1239,#f43f5e)',
  },
  {
    preset: 'performance',
    icon: '⚡',
    title: '性能优先',
    desc: '聚焦 performance，关注延迟与资源消耗。',
    tint: 'linear-gradient(135deg,#7f1d1d,#ef4444)',
  },
  {
    preset: 'full-dims',
    icon: '▦',
    title: '全维度',
    desc: 'effectiveness + safety + performance 一次评估。',
    tint: 'linear-gradient(135deg,#831843,#f43f5e)',
  },
];

function openCreate(preset?: string): void {
  openCreateTask(preset);
}

function openHelp(): void {
  openHelpDoc();
}
</script>

<style scoped>
.hub-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: start;
}

@media (max-width: 960px) {
  .hub-layout {
    grid-template-columns: 1fr;
  }
}

.hub-main {
  min-width: 0;
}

.hub-header {
  margin-bottom: 22px;
}

.hub-title {
  font-size: 26px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.hub-sub {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  max-width: 720px;
}

.hub-section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0 0 12px;
}

.second-title {
  margin-top: 22px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 700px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}

.template-card {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  background: var(--bg-surface);
  cursor: pointer;
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  align-items: start;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
  color: inherit;
  font: inherit;
  min-height: 78px;
}

.template-card:hover {
  border-color: var(--brand);
  box-shadow: 0 8px 22px var(--shadow-red);
  transform: translateY(-1px);
}

.tc-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--brand-faint);
  color: var(--brand);
  font-size: 16px;
  font-weight: 700;
}

.tc-body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.tc-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
}

.tc-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.hub-rail {
  display: grid;
  gap: 10px;
  position: sticky;
  top: 18px;
  align-content: start;
}

.rail-section-title {
  margin-bottom: 4px;
}

.scratch-card {
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 14px;
  background: var(--bg-surface);
  cursor: pointer;
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  align-items: start;
  color: inherit;
  font: inherit;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.12s;
}

.scratch-card:hover {
  border-color: var(--brand);
  box-shadow: 0 8px 22px var(--shadow-red);
  transform: translateY(-1px);
}

.sc-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--brand);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}

.sc-body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.sc-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
}

.sc-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.rail-divider {
  height: 1px;
  background: var(--line);
  margin: 6px 0;
}

.rail-help {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--bg-elevated);
  color: var(--text-primary);
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
}

.rail-help:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.rail-quick {
  display: grid;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px;
  background: var(--bg-elevated);
}

.rail-quick-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.rail-quick-link {
  font-size: 13px;
  color: var(--text-secondary);
  text-decoration: none;
  padding: 4px 0;
}

.rail-quick-link:hover {
  color: var(--brand);
}
</style>
