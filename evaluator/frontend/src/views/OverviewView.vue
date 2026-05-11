<template>
  <div class="eval-home">
    <nav class="eval-breadcrumb" aria-label="当前位置">
      <span>个人空间</span>
      <span>/</span>
      <strong>评测器</strong>
    </nav>

    <section class="eval-home-body" aria-labelledby="eval-home-title">
      <header class="eval-home-head">
        <h1 id="eval-home-title">开始使用Evaluator</h1>
        <p>选择评测模式或指标快捷模板，即可一键预填配置；也可以从右侧创建完整评测任务。</p>
      </header>

      <div class="eval-home-grid">
        <main class="eval-template-area">
          <section aria-labelledby="mode-title">
            <h2 id="mode-title">从模板开始 · 评测模式</h2>
            <div class="eval-card-grid">
              <button
                v-for="card in modeCards"
                :key="card.preset"
                type="button"
                class="eval-preset-card"
                @click="openCreate(card.preset)"
              >
                <span class="eval-card-icon" :class="card.tone">{{ card.icon }}</span>
                <span class="eval-card-copy">
                  <span class="eval-card-title">{{ card.title }}</span>
                  <span class="eval-card-desc">{{ card.desc }}</span>
                </span>
              </button>
            </div>
          </section>

          <section class="eval-section-gap" aria-labelledby="metric-title">
            <h2 id="metric-title">指标维度</h2>
            <div class="eval-card-grid">
              <button
                v-for="card in metricCards"
                :key="card.preset"
                type="button"
                class="eval-preset-card"
                @click="openCreate(card.preset)"
              >
                <span class="eval-card-icon eval-tone-soft">{{ card.icon }}</span>
                <span class="eval-card-copy">
                  <span class="eval-card-title">{{ card.title }}</span>
                  <span class="eval-card-desc">{{ card.desc }}</span>
                </span>
              </button>
            </div>
          </section>
        </main>

        <aside class="eval-create-area" aria-label="创建入口">
          <h2>从零创建</h2>
          <button type="button" class="eval-create-card" @click="openCreate()">
            <span class="eval-create-icon">01</span>
            <span class="eval-card-copy">
              <span class="eval-card-title">LLM-Judge Evaluator</span>
              <span class="eval-card-desc">让LLM作为裁判进行评测。</span>
            </span>
          </button>

          <button type="button" class="eval-create-card" @click="openCreate('deterministic')">
            <span class="eval-create-icon">02</span>
            <span class="eval-card-copy">
              <span class="eval-card-title">Deterministic Evaluator</span>
              <span class="eval-card-desc">依据规则评判，无需LLM-as-a-Judge。</span>
            </span>
          </button>

          <div class="eval-side-divider"></div>

          <button type="button" class="eval-help-card" @click="openHelp">
            <span aria-hidden="true">▣</span>
            <span>帮助文档</span>
          </button>

          <nav class="eval-quick-panel" aria-label="快捷入口">
            <span class="eval-quick-title">快捷入口</span>
            <RouterLink to="/tasks">评测任务列表</RouterLink>
            <RouterLink to="/history">评测历史</RouterLink>
            <RouterLink to="/models">模型管理</RouterLink>
            <RouterLink to="/datasets">数据集</RouterLink>
          </nav>
        </aside>
      </div>
    </section>
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
    desc: '至少 2 个参赛模型 + 1 个裁判模型，全对比较并输出 Elo 排行。',
    tone: 'eval-tone-red',
  },
  {
    preset: 'result-judge',
    icon: '✓',
    title: '结果 + LLM 裁判',
    desc: '结果模式（RESULT）：只看输入与最终输出。',
    tone: 'eval-tone-red',
  },
  {
    preset: 'process-judge',
    icon: '◇',
    title: '过程 + 裁判',
    desc: '过程模式（PROCESS）：参考工具轨迹等过程信息。',
    tone: 'eval-tone-soft',
  },
  {
    preset: 'deterministic',
    icon: '#',
    title: '显式 / 关键词',
    desc: '不调用裁判模型，适合规则型验收。',
    tone: 'eval-tone-soft',
  },
  {
    preset: 'hybrid',
    icon: '◆',
    title: '混合评测',
    desc: '兼顾规则与语义判断。',
    tone: 'eval-tone-soft',
  },
  {
    preset: 'judge-single',
    icon: '☑',
    title: '单模型质量验收',
    desc: '适合版本回归检查。',
    tone: 'eval-tone-soft',
  },
];

const metricCards = [
  {
    preset: 'safety',
    icon: '♢',
    title: '安全优先',
    desc: '聚焦安全维度，适合内容风险与合规能力评测。',
  },
  {
    preset: 'effectiveness',
    icon: '◎',
    title: '效果优先',
    desc: '聚焦效果维度，验证任务完成质量。',
  },
  {
    preset: 'performance',
    icon: '⚡',
    title: '性能优先',
    desc: '聚焦性能维度，关注延迟与资源消耗。',
  },
  {
    preset: 'full-dims',
    icon: '▦',
    title: '全维度',
    desc: '效果 + 安全 + 性能一次评估。',
  },
];

function openCreate(preset?: string): void {
  openCreateTask(preset);
}

function openHelp(): void {
  openHelpDoc();
}
</script>

<style>
.eval-home {
  min-height: 100%;
}

.eval-breadcrumb {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #8b8f9a;
  font-size: 13px;
  font-weight: 600;
}

.eval-breadcrumb strong {
  color: #26282f;
}

.eval-home-body {
  width: min(1500px, 100%);
  margin: 118px auto 0;
}

.eval-home-head {
  margin-bottom: 28px;
}

.eval-home-head h1 {
  color: #090a0f;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.15;
}

.eval-home-head p {
  margin-top: 8px;
  color: #555b66;
  font-size: 14px;
  line-height: 1.55;
}

.eval-home-grid {
  display: grid;
  grid-template-columns: minmax(620px, 1fr) 420px;
  gap: 28px;
  align-items: start;
}

.eval-template-area h2,
.eval-create-area h2 {
  margin: 0 0 14px;
  color: #23252c;
  font-size: 16px;
  font-weight: 800;
}

.eval-section-gap {
  margin-top: 24px;
}

.eval-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.eval-preset-card,
.eval-create-card,
.eval-help-card {
  width: 100%;
  border: 1px solid #e7dfdd !important;
  background: #ffffff !important;
  color: #151515 !important;
  cursor: pointer;
  font: inherit;
  text-align: left;
  box-shadow: none !important;
  transition: border-color 0.14s ease, background-color 0.14s ease, box-shadow 0.14s ease;
}

.eval-preset-card {
  min-height: 84px;
  border-radius: 8px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.eval-preset-card:hover,
.eval-create-card:hover,
.eval-help-card:hover {
  border-color: #ff6b73 !important;
  background: #fffafa !important;
  box-shadow: 0 10px 28px rgba(220, 38, 38, 0.08) !important;
}

.eval-card-icon,
.eval-create-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 16px;
  font-weight: 800;
}

.eval-tone-red,
.eval-create-icon {
  background: #ef232d;
  color: #ffffff;
}

.eval-tone-soft {
  background: #ffe1e4;
  color: #ef232d;
}

.eval-card-copy {
  min-width: 0;
  width: 100%;
  display: grid;
  gap: 5px;
}

.eval-card-title {
  color: #090a0f;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.25;
}

.eval-card-desc {
  color: #555b66;
  font-size: 14px;
  line-height: 1.35;
}

.eval-create-area {
  display: grid;
  align-content: start;
  gap: 10px;
}

.eval-create-card {
  min-height: 84px;
  border-radius: 8px;
  padding: 15px 16px;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.eval-side-divider {
  height: 1px;
  margin: 14px 0;
  background: #e7dfdd;
}

.eval-help-card {
  min-height: 54px;
  border-radius: 8px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  font-weight: 800;
}

.eval-quick-panel {
  border: 1px solid #e7dfdd;
  border-radius: 8px;
  background: #fff8f8;
  padding: 16px;
  display: grid;
  gap: 14px;
}

.eval-quick-title {
  color: #23252c;
  font-size: 15px;
  font-weight: 800;
}

.eval-quick-panel a {
  color: #4c515c;
  font-size: 14px;
  text-decoration: none;
}

.eval-quick-panel a:hover {
  color: var(--brand);
}

@media (max-width: 980px) {
  .eval-home-body {
    margin-top: 48px;
  }

  .eval-home-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .eval-home-body {
    margin-top: 28px;
  }

  .eval-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
