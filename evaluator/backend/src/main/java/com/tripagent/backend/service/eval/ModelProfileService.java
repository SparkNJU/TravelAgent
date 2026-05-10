package com.tripagent.backend.service.eval;

import com.tripagent.backend.dto.eval.CreateModelProfileRequest;
import com.tripagent.backend.dto.eval.ModelProfileResponse;
import com.tripagent.backend.dto.eval.UpdateModelProfileRequest;
import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.enums.ModelRole;
import com.tripagent.backend.repository.EvalComparisonRepository;
import com.tripagent.backend.repository.EvalTaskRepository;
import com.tripagent.backend.repository.ModelProfileRepository;
import com.tripagent.backend.repository.ModelRatingRepository;
import com.tripagent.backend.repository.QaRecordRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProfileService {

  private final ModelProfileRepository repository;
  private final QaRecordRepository qaRecordRepository;
  private final ModelRatingRepository modelRatingRepository;
  private final EvalComparisonRepository evalComparisonRepository;
  private final EvalTaskRepository evalTaskRepository;

  public ModelProfileService(
      ModelProfileRepository repository,
      QaRecordRepository qaRecordRepository,
      ModelRatingRepository modelRatingRepository,
      EvalComparisonRepository evalComparisonRepository,
      EvalTaskRepository evalTaskRepository
  ) {
    this.repository = repository;
    this.qaRecordRepository = qaRecordRepository;
    this.modelRatingRepository = modelRatingRepository;
    this.evalComparisonRepository = evalComparisonRepository;
    this.evalTaskRepository = evalTaskRepository;
  }

  @Transactional
  public ModelProfileResponse create(CreateModelProfileRequest request) {
    String modelId = request.modelId().trim();
    repository.findByModelId(modelId).ifPresent(existing -> {
      throw new IllegalArgumentException("modelId 已存在: " + modelId);
    });

    ModelProfile entity = new ModelProfile();
    entity.setModelId(modelId);
    entity.setDisplayName(request.displayName().trim());
    entity.setProvider(blankToDefault(request.provider(), "openai_compatible"));
    entity.setApiBaseUrl(emptyToNull(request.apiBaseUrl()));
    entity.setApiKeyRef(emptyToNull(request.apiKeyRef()));
    entity.setRole(request.role());
    entity.setDefaultParams(request.defaultParams());
    entity.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());

    ModelProfile saved = repository.save(entity);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<ModelProfileResponse> list(ModelRole role, Boolean enabledOnly) {
    List<ModelProfile> all = repository.findAll();
    return all.stream()
        .filter(p -> role == null || matchesRole(p.getRole(), role))
        .filter(p -> !Boolean.TRUE.equals(enabledOnly) || Boolean.TRUE.equals(p.getEnabled()))
        .sorted(Comparator.comparing(ModelProfile::getModelProfileId))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public ModelProfileResponse get(Long id) {
    return toResponse(getOrThrow(id));
  }

  @Transactional
  public ModelProfileResponse update(Long id, UpdateModelProfileRequest request) {
    ModelProfile entity = getOrThrow(id);
    if (hasText(request.displayName())) {
      entity.setDisplayName(request.displayName().trim());
    }
    if (hasText(request.provider())) {
      entity.setProvider(request.provider().trim());
    }
    if (request.apiBaseUrl() != null) {
      entity.setApiBaseUrl(emptyToNull(request.apiBaseUrl()));
    }
    if (request.apiKeyRef() != null) {
      entity.setApiKeyRef(emptyToNull(request.apiKeyRef()));
    }
    if (request.role() != null) {
      entity.setRole(request.role());
    }
    if (request.defaultParams() != null) {
      entity.setDefaultParams(request.defaultParams());
    }
    if (request.enabled() != null) {
      entity.setEnabled(request.enabled());
    }
    return toResponse(repository.save(entity));
  }

  /** 软删除：仅置 enabled=false，保留历史数据用于已完成 run 的回查。 */
  @Transactional
  public void softDelete(Long id) {
    ModelProfile entity = getOrThrow(id);
    entity.setEnabled(Boolean.FALSE);
    repository.save(entity);
  }

  /**
   * 硬删除：彻底从 DB 移除 ModelProfile。
   * 前置检查：若被 qa_record / model_rating / eval_comparison / eval_task 引用 → 拒绝并返回提示。
   */
  @Transactional
  public void hardDelete(Long id) {
    ModelProfile entity = getOrThrow(id);

    // 1. qa_record 是否引用
    long qaCount = qaRecordRepository.findAll().stream()
        .filter(r -> id.equals(r.getModelProfileId()))
        .count();
    if (qaCount > 0) {
      throw new IllegalStateException(
          "模型已被 " + qaCount + " 条 qa_record 引用，无法硬删除（建议软删除保留历史）：" + entity.getModelId());
    }

    // 2. model_rating 是否引用
    long ratingCount = modelRatingRepository.findAll().stream()
        .filter(r -> id.equals(r.getModelProfileId()))
        .count();
    if (ratingCount > 0) {
      throw new IllegalStateException(
          "模型已被 " + ratingCount + " 条 model_rating 引用，无法硬删除：" + entity.getModelId());
    }

    // 3. eval_comparison 是否引用（作为 modelA/B 或 judge）
    long compCount = evalComparisonRepository.findAll().stream()
        .filter(c -> id.equals(c.getModelAId()) || id.equals(c.getModelBId())
            || id.equals(c.getJudgeModelId()))
        .count();
    if (compCount > 0) {
      throw new IllegalStateException(
          "模型已被 " + compCount + " 条 eval_comparison 引用，无法硬删除：" + entity.getModelId());
    }

    // 4. eval_task.selected_model_ids / judge_model_id 是否引用
    long taskCount = evalTaskRepository.findAll().stream()
        .filter(t -> {
          if (id.equals(t.getJudgeModelId())) return true;
          String raw = t.getSelectedModelIds();
          if (raw == null || raw.isBlank()) return false;
          // 简单字符串匹配（避免引入 Jackson 增加耦合）
          return raw.contains(String.valueOf(id));
        })
        .count();
    if (taskCount > 0) {
      throw new IllegalStateException(
          "模型已被 " + taskCount + " 个 eval_task 引用，无法硬删除：" + entity.getModelId());
    }

    // 全部检查通过，真删
    repository.delete(entity);
  }

  /** 校验：传入的 ID 必须都存在且 enabled 且 role 兼容 player。 */
  @Transactional(readOnly = true)
  public List<ModelProfile> resolvePlayers(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("selectedModelIds 为空");
    }
    List<ModelProfile> profiles = repository.findByModelProfileIdIn(ids);
    if (profiles.size() != ids.size()) {
      throw new IllegalArgumentException("部分 modelProfileId 不存在: 期望 " + ids.size() + " 个，实际 " + profiles.size() + " 个");
    }
    for (ModelProfile p : profiles) {
      if (!Boolean.TRUE.equals(p.getEnabled())) {
        throw new IllegalArgumentException("模型已禁用: " + p.getModelId());
      }
      if (!matchesRole(p.getRole(), ModelRole.PLAYER)) {
        throw new IllegalArgumentException("模型角色不兼容 PLAYER: " + p.getModelId() + " (role=" + p.getRole() + ")");
      }
    }
    return profiles;
  }

  /** 校验：判定模型必须存在、enabled、role 兼容 judge。 */
  @Transactional(readOnly = true)
  public ModelProfile resolveJudge(Long id) {
    if (id == null) {
      throw new IllegalArgumentException("judgeModelId 为空");
    }
    ModelProfile profile = getOrThrow(id);
    if (!Boolean.TRUE.equals(profile.getEnabled())) {
      throw new IllegalArgumentException("Judge 模型已禁用: " + profile.getModelId());
    }
    if (!matchesRole(profile.getRole(), ModelRole.JUDGE)) {
      throw new IllegalArgumentException("Judge 模型角色不兼容 JUDGE: " + profile.getModelId() + " (role=" + profile.getRole() + ")");
    }
    return profile;
  }

  private ModelProfile getOrThrow(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("模型不存在: id=" + id));
  }

  private boolean matchesRole(ModelRole actual, ModelRole expected) {
    if (actual == null) return false;
    return actual == expected || actual == ModelRole.BOTH;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value.trim();
  }

  private String blankToDefault(String value, String def) {
    return (value == null || value.isBlank()) ? def : value.trim();
  }

  private ModelProfileResponse toResponse(ModelProfile entity) {
    return new ModelProfileResponse(
        entity.getModelProfileId(),
        entity.getModelId(),
        entity.getDisplayName(),
        entity.getProvider(),
        entity.getApiBaseUrl(),
        entity.getApiKeyRef(),
        entity.getRole(),
        entity.getDefaultParams(),
        entity.getEnabled(),
        entity.getCreatedAt()
    );
  }
}
