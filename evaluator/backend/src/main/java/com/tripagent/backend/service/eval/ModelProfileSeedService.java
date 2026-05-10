package com.tripagent.backend.service.eval;

import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.enums.ModelRole;
import com.tripagent.backend.repository.ModelProfileRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ModelProfileSeedService {

  private final ModelProfileRepository modelProfileRepository;

  public ModelProfileSeedService(ModelProfileRepository modelProfileRepository) {
    this.modelProfileRepository = modelProfileRepository;
  }

  @PostConstruct
  @Transactional
  public void seed() {
    // Default free models via ModelScope OpenAI-compatible endpoint.
    // Uses MODELSCOPE_API_KEY, or API_KEY mapped to MODELSCOPE_API_KEY by DotenvEnvironmentPostProcessor.
    List<SeedSpec> specs = List.of(
        new SeedSpec("Qwen/Qwen3.5-35B-A3B", "Qwen3.5-35B-A3B", ModelRole.PLAYER),
        new SeedSpec("Qwen/Qwen3-32B", "Qwen3-32B", ModelRole.PLAYER),
        new SeedSpec("Qwen/Qwen3-14B", "Qwen3-14B", ModelRole.PLAYER),
        new SeedSpec("deepseek-ai/DeepSeek-V4-Flash", "DeepSeek-V4-Flash", ModelRole.PLAYER),
        new SeedSpec("ZhipuAI/GLM-5:ZhipuAI", "GLM-5（需魔搭绑定智谱 Key）", ModelRole.PLAYER),
        new SeedSpec("Qwen/Qwen3-30B-A3B", "Qwen3-30B-A3B (Judge)", ModelRole.JUDGE)
    );
    for (SeedSpec spec : specs) {
      seedOne(spec);
    }
  }

  private void seedOne(SeedSpec spec) {
    if (modelProfileRepository.findByModelId(spec.modelId()).isPresent()) {
      return;
    }
    ModelProfile model = new ModelProfile();
    model.setModelId(spec.modelId());
    model.setDisplayName(spec.displayName());
    model.setProvider("openai_compatible");
    model.setApiBaseUrl("https://api-inference.modelscope.cn/v1");
    model.setApiKeyRef("MODELSCOPE_API_KEY");
    model.setRole(spec.role());
    model.setEnabled(Boolean.TRUE);
    modelProfileRepository.save(model);
  }

  private record SeedSpec(String modelId, String displayName, ModelRole role) {
  }
}
