<template>
  <article class="surface model-panel">
    <div class="section-head">
      <h3>模型管理</h3>
      <div class="inline-actions">
        <button class="ghost" @click="loadModels">刷新</button>
        <button class="primary" @click="showCreate = true">注册模型</button>
      </div>
    </div>

    <p v-if="notice" class="notice-text">{{ notice }}</p>

    <div class="filter-bar">
      <select v-model="filterRole">
        <option value="">全部角色</option>
        <option value="PLAYER">参赛模型（PLAYER）</option>
        <option value="JUDGE">裁判模型（JUDGE）</option>
        <option value="BOTH">通用模型（BOTH）</option>
      </select>
      <label class="inline-check">
        <input v-model="onlyEnabled" type="checkbox" />
        仅显示启用
      </label>
    </div>

    <div class="table-wrap">
      <table class="task-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>模型ID</th>
            <th>显示名</th>
            <th>角色</th>
            <th>启用</th>
            <th>API 密钥引用</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in filteredModels" :key="m.modelProfileId" :class="{ 'row-disabled': !m.enabled }">
            <td>#{{ m.modelProfileId }}</td>
            <td class="mono">{{ m.modelId }}</td>
            <td>{{ m.displayName }}</td>
            <td><span class="chip">{{ m.role }}</span></td>
            <td>
              <span class="status" :class="m.enabled ? 'success' : 'failed'">
                {{ m.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ m.apiKeyRef || '-' }}</td>
            <td class="actions-cell">
              <div class="model-actions">
                <button class="link-btn" @click="pingOne(m)">连通性检测</button>
                <button class="link-btn" @click="toggleEnabled(m)">
                  {{ m.enabled ? '禁用' : '启用' }}
                </button>
                <button class="link-btn" @click="removeOne(m)">软删除</button>
                <button class="link-btn danger" @click="hardRemoveOne(m)">彻底删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!filteredModels.length">
            <td colspan="7">暂无模型，先注册一个</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 创建模型模态框 -->
    <div v-if="showCreate" class="modal-mask" @click.self="showCreate = false">
      <article class="create-modal">
        <div class="section-head">
          <h3>注册新模型</h3>
          <button class="link-btn" @click="showCreate = false">关闭</button>
        </div>

        <div class="form-layout">
          <label>
            角色
            <select v-model="createForm.role" @change="onRoleChange">
              <option value="PLAYER">参赛模型（PLAYER）</option>
              <option value="JUDGE">裁判模型（JUDGE）</option>
              <option value="BOTH">通用模型（BOTH）</option>
            </select>
          </label>

          <!-- modelId：下拉 + 自定义 -->
          <label v-if="catalog && availableCatalogItems.length">
            模型ID（从推荐清单选择，或选“自定义”手动输入）
            <select v-model="createForm.modelId" @change="onModelIdSelect">
              <option v-for="item in availableCatalogItems" :key="item.modelId" :value="item.modelId">
                {{ item.modelId }} — {{ item.displayName }}
              </option>
              <option value="__custom__">⚙ 自定义（手动输入）</option>
            </select>
          </label>

          <label v-if="!catalog || createForm.modelId === '__custom__' || !availableCatalogItems.length">
            自定义模型ID
            <input v-model.trim="customModelId" type="text"
              placeholder="例如 Qwen/Qwen3-32B 或 Qwen/Qwen3-32B:DashScope" />
          </label>

          <p v-if="catalog?.providersNote" class="notice-text providers-note">
            ℹ️ {{ catalog.providersNote }}
          </p>

          <label>
            显示名
            <input v-model.trim="createForm.displayName" type="text" placeholder="如 Qwen3-32B 参赛模型" />
          </label>
          <label>
            API 密钥引用（环境变量名，留空走全局 llm.api-key）
            <input v-model.trim="createForm.apiKeyRef" type="text" placeholder="MODELSCOPE_API_KEY" />
          </label>
          <label>
            API 基础地址（留空走全局 base-url）
            <input v-model.trim="createForm.apiBaseUrl" type="text" placeholder="留空即可" />
          </label>
          <label>
            默认参数 JSON
            <textarea v-model="createForm.defaultParams" rows="3"
              placeholder='{"temperature":0,"maxTokens":2048}' />
          </label>
        </div>

        <div class="modal-actions">
          <button class="ghost" @click="showCreate = false">取消</button>
          <button class="primary" @click="submitCreate">保存</button>
        </div>
      </article>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  createModel,
  deleteModel,
  getModelCatalog,
  hardDeleteModel,
  listModels,
  pingModel,
  updateModel,
  type ModelCatalog,
  type ModelCatalogItem,
  type ModelProfile,
  type ModelRole,
} from '../api/client';

const models = ref<ModelProfile[]>([]);
const catalog = ref<ModelCatalog | null>(null);
const customModelId = ref('');
const notice = ref('');
const filterRole = ref<'' | ModelRole>('');
const onlyEnabled = ref(true);   // 默认只显示启用，禁用模型不再占位
const showCreate = ref(false);

const createForm = reactive({
  modelId: '',
  displayName: '',
  role: 'PLAYER' as ModelRole,
  apiKeyRef: 'MODELSCOPE_API_KEY',
  apiBaseUrl: '',
  defaultParams: '{"temperature":0,"maxTokens":2048}',
});

const availableCatalogItems = computed<ModelCatalogItem[]>(() => {
  if (!catalog.value) return [];
  if (createForm.role === 'JUDGE') return catalog.value.judges;
  if (createForm.role === 'PLAYER') return catalog.value.players;
  // BOTH：合并去重
  const merged = new Map<string, ModelCatalogItem>();
  catalog.value.players.forEach((p) => merged.set(p.modelId, p));
  catalog.value.judges.forEach((j) => merged.set(j.modelId, j));
  return Array.from(merged.values());
});

const filteredModels = computed(() => {
  return models.value.filter((m) => {
    if (filterRole.value && !roleMatches(m.role, filterRole.value as ModelRole)) return false;
    if (onlyEnabled.value && !m.enabled) return false;
    return true;
  });
});

function roleMatches(actual: ModelRole, expected: ModelRole): boolean {
  return actual === expected || actual === 'BOTH';
}

watch(showCreate, (open) => {
  if (open) {
    // 打开时若 catalog 有列表，默认选中第一项；否则进入自定义模式
    if (catalog.value && availableCatalogItems.value.length) {
      createForm.modelId = availableCatalogItems.value[0].modelId;
    } else {
      createForm.modelId = '__custom__';
    }
    customModelId.value = '';
    if (!createForm.displayName) {
      createForm.displayName = createForm.modelId === '__custom__' ? '' : suggestDisplayName(createForm.modelId);
    }
  }
});

onMounted(async () => {
  await loadModels();
  try {
    catalog.value = await getModelCatalog();
  } catch {
    catalog.value = null;
  }
});

async function loadModels(): Promise<void> {
  try {
    models.value = await listModels();
    notice.value = `共 ${models.value.length} 个模型`;
  } catch (err: any) {
    notice.value = `加载失败：${err.message || String(err)}`;
  }
}

function onRoleChange(): void {
  // 角色切换时重置 modelId 选择
  if (catalog.value && availableCatalogItems.value.length) {
    createForm.modelId = availableCatalogItems.value[0].modelId;
    if (!createForm.displayName || isAutoDisplayName(createForm.displayName)) {
      createForm.displayName = suggestDisplayName(createForm.modelId);
    }
  } else {
    createForm.modelId = '__custom__';
  }
}

function onModelIdSelect(): void {
  if (createForm.modelId !== '__custom__' && (!createForm.displayName || isAutoDisplayName(createForm.displayName))) {
    createForm.displayName = suggestDisplayName(createForm.modelId);
  }
}

function suggestDisplayName(modelId: string): string {
  if (!modelId || modelId === '__custom__') return '';
  // 取 modelId 路径里的最后一段作为默认显示名
  const cleaned = modelId.split(':')[0];
  const tail = cleaned.split('/').pop() || cleaned;
  return tail;
}

function isAutoDisplayName(name: string): boolean {
  // 简单判断：如果显示名是从某个 catalog modelId 派生的就视为自动名
  return availableCatalogItems.value.some((item) => suggestDisplayName(item.modelId) === name);
}

async function submitCreate(): Promise<void> {
  const finalModelId =
    createForm.modelId === '__custom__' || !createForm.modelId
      ? customModelId.value.trim()
      : createForm.modelId;

  if (!finalModelId) {
    notice.value = '模型ID 必填（请从下拉选择，或手动输入）';
    return;
  }
  if (!createForm.displayName) {
    notice.value = '显示名必填';
    return;
  }

  // 同 modelId 在 DB 唯一，重复时提示用户改用 BOTH
  const existing = models.value.find((m) => m.modelId === finalModelId);
  if (existing) {
    if (existing.role === createForm.role || existing.role === 'BOTH') {
      notice.value = `已存在同模型ID的 ${existing.role} 角色（#${existing.modelProfileId}），无需重复注册`;
      return;
    }
    // 已注册为另一角色 → 询问用户是否升级为 BOTH
    const yes = window.confirm(
      `「${finalModelId}」已注册为 ${existing.role} 角色（#${existing.modelProfileId}）。\n\n` +
        `同一模型ID不能注册两次（数据库唯一约束）。\n` +
        `是否把它的角色升级为 BOTH（同时支持参赛模型和裁判模型）？`,
    );
    if (!yes) return;
    try {
      await updateModel(existing.modelProfileId, { role: 'BOTH', enabled: true });
      notice.value = `已把「${finalModelId}」升级为 BOTH 角色`;
      showCreate.value = false;
      await loadModels();
    } catch (err: any) {
      notice.value = `升级角色失败：${err.message || String(err)}`;
    }
    return;
  }

  try {
    await createModel({
      modelId: finalModelId,
      displayName: createForm.displayName,
      role: createForm.role,
      apiKeyRef: createForm.apiKeyRef || undefined,
      apiBaseUrl: createForm.apiBaseUrl || undefined,
      defaultParams: createForm.defaultParams || undefined,
      enabled: true,
    });
    showCreate.value = false;
    notice.value = `模型已注册：${finalModelId}`;
    createForm.displayName = '';
    customModelId.value = '';
    await loadModels();
  } catch (err: any) {
    notice.value = `注册失败：${err.message || String(err)}`;
  }
}

async function pingOne(m: ModelProfile): Promise<void> {
  notice.value = `正在检测 ${m.modelId} 连通性...`;
  try {
    const r = await pingModel(m.modelProfileId);
    const preview = r.text.length > 60 ? r.text.slice(0, 60) + '...' : r.text;
    notice.value = `[${m.modelId}] ${r.text.length} 字符 · Token=${r.promptTokens}/${r.completionTokens} · ${r.latencyMs}ms · ${preview || '(空)'}`;
  } catch (err: any) {
    notice.value = `检测失败 [${m.modelId}]：${err.message || String(err)}`;
  }
}

async function toggleEnabled(m: ModelProfile): Promise<void> {
  try {
    await updateModel(m.modelProfileId, { enabled: !m.enabled });
    await loadModels();
  } catch (err: any) {
    notice.value = `切换失败：${err.message || String(err)}`;
  }
}

async function removeOne(m: ModelProfile): Promise<void> {
  if (!window.confirm(`确认软删除 ${m.modelId}？\n\n软删除后模型不可用但保留在数据库（被历史 run 引用时建议用此方式）。`)) return;
  try {
    await deleteModel(m.modelProfileId);
    notice.value = `已软删除：${m.modelId}（如需彻底移除，点击「彻底删除」）`;
    await loadModels();
  } catch (err: any) {
    notice.value = `删除失败：${err.message || String(err)}`;
  }
}

async function hardRemoveOne(m: ModelProfile): Promise<void> {
  if (!window.confirm(
    `⚠️ 确认彻底删除 ${m.modelId}？\n\n` +
    `此操作不可恢复！\n` +
    `若该模型被任意历史 run / qa_record / model_rating / eval_comparison / eval_task 引用，后端会拒绝删除并提示用软删除。`
  )) return;
  try {
    await hardDeleteModel(m.modelProfileId);
    notice.value = `已彻底删除：${m.modelId}`;
    await loadModels();
  } catch (err: any) {
    const msg = err.message || String(err);
    notice.value = `彻底删除失败：${msg}`;
  }
}
</script>

<style scoped>
.filter-bar {
  grid-template-columns: minmax(240px, 360px) auto;
  align-items: center;
}

.inline-check {
  min-height: 42px;
}

.providers-note {
  background: #1e293b;
  padding: 8px 10px;
  border-radius: 6px;
  border-left: 3px solid var(--brand);
  font-size: 12px;
  margin: 4px 0;
}
.mono {
  font-family: monospace;
}
.actions-cell {
  min-width: 220px;
}
.model-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  row-gap: 8px;
}
.model-actions .link-btn {
  line-height: 1;
  padding: 2px 0;
}
.row-disabled {
  opacity: 0.45;
}
.row-disabled:hover {
  opacity: 0.7;
}
.link-btn.danger {
  color: #f87171;
}
.link-btn.danger:hover {
  color: #ef4444;
  text-decoration: underline;
}

@media (max-width: 640px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }
}
</style>
