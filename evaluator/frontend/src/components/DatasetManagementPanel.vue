<template>
  <article class="surface dataset-panel">
    <div class="section-head">
      <h3>数据集管理</h3>
      <div class="inline-actions">
        <button class="ghost" @click="loadDatasets">刷新</button>
        <button class="primary" @click="showUpload = true">上传数据集</button>
      </div>
    </div>

    <p v-if="notice" class="notice-text">{{ notice }}</p>

    <div class="filter-bar">
      <select v-model="filterSource">
        <option value="">全部来源</option>
        <option value="BUILTIN">BUILTIN（内置）</option>
        <option value="USER">USER（用户上传）</option>
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
            <th>name</th>
            <th>显示名</th>
            <th>来源</th>
            <th>样本数</th>
            <th>启用</th>
            <th>说明</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in filteredDatasets" :key="d.datasetId">
            <td>#{{ d.datasetId }}</td>
            <td>{{ d.name }}</td>
            <td>{{ d.displayName || '-' }}</td>
            <td><span class="chip">{{ d.source }}</span></td>
            <td>{{ d.sampleCount ?? '-' }}</td>
            <td>
              <span class="status" :class="d.enabled ? 'success' : 'failed'">
                {{ d.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ d.description || '-' }}</td>
            <td>
              <button class="link-btn" @click="preview(d)">预览</button>
              <button class="link-btn" @click="removeOne(d)">删除</button>
            </td>
          </tr>
          <tr v-if="!filteredDatasets.length">
            <td colspan="8">暂无数据集</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 上传数据集模态框 -->
    <div v-if="showUpload" class="modal-mask" @click.self="showUpload = false">
      <article class="create-modal">
        <div class="section-head">
          <h3>上传数据集</h3>
          <button class="link-btn" @click="showUpload = false">关闭</button>
        </div>
        <div class="form-layout">
          <label>
            选择文件（.json 数组 或 .csv，必须含 input 列）
            <input ref="fileInput" type="file" accept=".json,.csv" @change="onFileSelected" />
          </label>
          <label>
            数据集 name（唯一，3-80 位字母/数字/下划线/连字符）
            <input v-model.trim="uploadForm.name" type="text" placeholder="trip-custom-001" />
          </label>
          <label>
            显示名
            <input v-model.trim="uploadForm.displayName" type="text" placeholder="自定义旅游集" />
          </label>
          <label>
            说明（可选）
            <textarea v-model="uploadForm.description" rows="2" placeholder="自由备注" />
          </label>
          <p class="notice-text">限制：文件 ≤1MB · 样本数 ≤200</p>
        </div>
        <div class="modal-actions">
          <button class="ghost" @click="showUpload = false">取消</button>
          <button class="primary" :disabled="!selectedFile" @click="submitUpload">上传</button>
        </div>
      </article>
    </div>

    <!-- 预览模态框 -->
    <div v-if="previewing" class="modal-mask" @click.self="previewing = null">
      <article class="create-modal preview-modal">
        <div class="section-head">
          <h3>{{ previewing.name }} 预览（前 10 条）</h3>
          <button class="link-btn" @click="previewing = null">关闭</button>
        </div>
        <div class="table-wrap">
          <table class="task-table detail-table">
            <thead>
              <tr>
                <th>序号</th>
                <th>sample_key</th>
                <th>input</th>
                <th>expectedOutput</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, idx) in previewSamples" :key="s.sampleId">
                <td>{{ idx + 1 }}</td>
                <td>{{ s.sampleKey || '-' }}</td>
                <td>{{ s.input }}</td>
                <td>{{ s.expectedOutput || '-' }}</td>
              </tr>
              <tr v-if="!previewSamples.length">
                <td colspan="4">暂无样本</td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';

import {
  deleteDataset,
  getDatasetSamples,
  listDatasets,
  uploadDataset,
  type Dataset,
  type DatasetSamplePreview,
  type DatasetSource,
} from '../api/client';

const datasets = ref<Dataset[]>([]);
const notice = ref('');
const filterSource = ref<'' | DatasetSource>('');
const onlyEnabled = ref(false);

const showUpload = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<File | null>(null);

const uploadForm = reactive({
  name: '',
  displayName: '',
  description: '',
});

const previewing = ref<Dataset | null>(null);
const previewSamples = ref<DatasetSamplePreview[]>([]);

const filteredDatasets = computed(() => {
  return datasets.value.filter((d) => {
    if (filterSource.value && d.source !== filterSource.value) return false;
    if (onlyEnabled.value && !d.enabled) return false;
    return true;
  });
});

onMounted(() => {
  void loadDatasets();
});

async function loadDatasets(): Promise<void> {
  try {
    datasets.value = await listDatasets();
    notice.value = `共 ${datasets.value.length} 个数据集（启用 ${datasets.value.filter((d) => d.enabled).length}）`;
  } catch (err: any) {
    notice.value = `加载失败: ${err.message || String(err)}`;
  }
}

function onFileSelected(evt: Event): void {
  const target = evt.target as HTMLInputElement;
  selectedFile.value = target.files && target.files.length > 0 ? target.files[0] : null;
  if (selectedFile.value && !uploadForm.name) {
    const filename = selectedFile.value.name;
    uploadForm.name = filename.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 80);
  }
}

async function submitUpload(): Promise<void> {
  if (!selectedFile.value) {
    notice.value = '请先选择文件';
    return;
  }
  if (!uploadForm.name) {
    notice.value = 'name 必填';
    return;
  }
  try {
    await uploadDataset({
      file: selectedFile.value,
      name: uploadForm.name,
      displayName: uploadForm.displayName || undefined,
      description: uploadForm.description || undefined,
    });
    notice.value = `上传成功：${uploadForm.name}`;
    showUpload.value = false;
    uploadForm.name = '';
    uploadForm.displayName = '';
    uploadForm.description = '';
    selectedFile.value = null;
    if (fileInput.value) fileInput.value.value = '';
    await loadDatasets();
  } catch (err: any) {
    notice.value = `上传失败: ${err.message || String(err)}`;
  }
}

async function preview(d: Dataset): Promise<void> {
  try {
    previewing.value = d;
    previewSamples.value = await getDatasetSamples(d.datasetId, 10);
  } catch (err: any) {
    notice.value = `预览失败: ${err.message || String(err)}`;
  }
}

async function removeOne(d: Dataset): Promise<void> {
  const tag = d.source === 'BUILTIN' ? '内置' : '用户上传';
  if (!window.confirm(`确认软删除「${d.name}」（${tag}）？已使用此数据集的运行历史不受影响。`)) return;
  try {
    await deleteDataset(d.datasetId);
    await loadDatasets();
  } catch (err: any) {
    notice.value = `删除失败: ${err.message || String(err)}`;
  }
}
</script>

<style scoped>
.preview-modal {
  max-width: 80vw;
  max-height: 80vh;
  overflow: auto;
}
</style>
