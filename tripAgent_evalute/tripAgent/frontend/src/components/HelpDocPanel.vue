<template>
  <div class="help-overlay" @click.self="$emit('close')">
    <article class="help-panel">
      <header class="help-header">
        <div>
          <p class="help-kicker">TripAgent 使用说明</p>
          <h2>评测系统帮助文档</h2>
          <p>了解任务创建、运行监控、评测模式、指标含义和常见问题处理。</p>
        </div>
         <div class="help-header-actions">
           <button class="help-return-button" type="button" @click="$emit('close')">返回</button>
           <button class="ghost" @click="$emit('close')">关闭</button>
         </div>
      </header>

      <section class="help-grid">
        <article class="help-card">
          <h3>1. 快速启动</h3>
          <ol>
            <li>启动 Agent 服务，端口默认 8000。</li>
            <li>启动后端服务，端口默认 8080。</li>
            <li>启动前端服务，端口默认 5173。</li>
            <li>打开控制台后先创建任务，再点击“启动”。</li>
          </ol>
        </article>

        <article class="help-card">
          <h3>2. 评测流程</h3>
          <ol>
            <li>创建任务时填写任务名称、Agent 版本、数据集 ID 和评测方式。</li>
            <li>点击“启动”后，系统会拉起 run 并异步执行数据集样本。</li>
            <li>运行详情页会持续展示样本记录、工具轨迹和指标结果。</li>
            <li>运行结束后可对比不同 run 并导出结果。</li>
          </ol>
        </article>

        <article class="help-card">
          <h3>3. 评测模式</h3>
          <ul>
            <li><strong>RESULT</strong>：只关注最终结果是否符合预期。</li>
            <li><strong>PROCESS</strong>：关注过程是否产生有效工具轨迹。</li>
            <li><strong>DETERMINISTIC</strong>：根据预期关键词或结构做规则匹配。</li>
            <li><strong>JUDGE</strong>：按输出质量、完整性与安全性做启发式判断。</li>
            <li><strong>HYBRID</strong>：综合 DETERMINISTIC 和 JUDGE 两类得分。</li>
          </ul>
        </article>

        <article class="help-card">
          <h3>4. 指标说明</h3>
          <ul>
            <li><strong>任务成功率</strong>：本次 run 中通过样本占比。</li>
            <li><strong>工具正确性</strong>：工具轨迹是否存在且有效。</li>
            <li><strong>工具效率</strong>：过程型执行是否存在过多无效步骤。</li>
            <li><strong>P95 延迟</strong>：首字和端到端时延的 95 分位值。</li>
            <li><strong>Token 消耗</strong>：模型输出与输入估算的 token 用量。</li>
          </ul>
        </article>

        <article class="help-card help-wide">
          <h3>5. 页面导航</h3>
          <ul>
            <li><strong>概况</strong>：查看控制台总体说明。</li>
            <li><strong>评测任务</strong>：创建、筛选、启动任务。</li>
            <li><strong>运行详情</strong>：查看运行历史、样本记录、对比和导出。</li>
            <li><strong>AI评测配置</strong>：管理策略版本和自定义指标。</li>
            <li><strong>样本监控</strong>：查看时间线、工具轨迹和错误阶段。</li>
          </ul>
        </article>

        <article class="help-card help-wide">
          <h3>6. 常见问题</h3>
          <ul>
            <li>如果任务一直显示 RUNNING，请先刷新任务列表，再查看 run 是否已经结束。</li>
            <li>如果 Agent 无法启动，请确认 8000 端口未被占用并且 .venv 已激活。</li>
            <li>如果前端没数据，确认后端和 Agent 服务都已启动。</li>
            <li>如果帮助文档内容不够，请直接查看此面板中的评测模式和指标说明。</li>
          </ul>
        </article>
      </section>
    </article>
  </div>
</template>

<script setup lang="ts">
defineEmits<{
  close: [];
}>();
</script>