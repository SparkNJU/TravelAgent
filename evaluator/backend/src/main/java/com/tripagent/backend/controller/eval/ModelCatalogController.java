package com.tripagent.backend.controller.eval;

import com.tripagent.backend.dto.eval.EvalApiResponse;
import com.tripagent.backend.dto.eval.ModelCatalogResponse;
import com.tripagent.backend.dto.eval.ModelCatalogResponse.Item;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 推荐 modelId 清单（静态硬编码，列出 ModelScope 上已知可调通的模型）。
 * 前端注册 ModelProfile 时下拉这些选项；用户也可以选"自定义"手填。
 * 清单跟 ModelScope 实际状态可能漂移——以前端 ping 测试结果为准。
 */
@RestController
@RequestMapping("/api/eval/models")
public class ModelCatalogController {

  /**
   * 推荐清单与魔搭 API-Inference 对齐：base_url 固定为 application.yml 中的 api-inference.modelscope.cn；
   * {@code model} 字段须与模型详情页「API-Inference」展示的 Model Id 一致（会随上下线变化，以下仅供参考）。
   * Token：账号 Access Token（https://modelscope.cn/my/myaccesstoken ），需完成实名等要求后方可调用。
   */
  private static final ModelCatalogResponse CATALOG = new ModelCatalogResponse(
      List.of(
          // --- Qwen3.5（官方 OpenAI 兼容示例常见 Model Id；以模型页为准）---
          new Item("Qwen/Qwen3.5-72B-Instruct", "Qwen3.5-72B-Instruct", List.of("qwen3.5", "instruct")),
          new Item("Qwen/Qwen3.5-35B-A3B", "Qwen3.5-35B-A3B（MoE，流式示例）", List.of("qwen3.5", "moe", "thinking")),
          new Item("Qwen/Qwen3.5-32B-Instruct", "Qwen3.5-32B-Instruct", List.of("qwen3.5", "instruct")),
          new Item("Qwen/Qwen3.5-27B", "Qwen3.5-27B（Responses API 文档示例）", List.of("qwen3.5")),
          new Item("Qwen/Qwen3.5-14B-Instruct", "Qwen3.5-14B-Instruct", List.of("qwen3.5", "instruct", "fast")),
          new Item("Qwen/Qwen3.5-7B-Instruct", "Qwen3.5-7B-Instruct（轻量）", List.of("qwen3.5", "instruct", "light")),
          // --- Qwen3 ---
          new Item("Qwen/Qwen3-32B", "Qwen3-32B（开源 thinking）", List.of("qwen3", "thinking", "free")),
          new Item("Qwen/Qwen3-30B-A3B", "Qwen3-30B-A3B（MoE，速度快）", List.of("qwen3", "thinking", "free")),
          new Item("Qwen/Qwen3-14B", "Qwen3-14B", List.of("qwen3", "thinking", "free")),
          new Item("Qwen/Qwen3-8B", "Qwen3-8B（轻量）", List.of("qwen3", "thinking", "free")),
          new Item("Qwen/Qwen3-4B", "Qwen3-4B（最轻量）", List.of("qwen3", "free")),
          new Item("Qwen/Qwen3-1.7B", "Qwen3-1.7B（最小）", List.of("qwen3", "free")),
          new Item("Qwen/QwQ-32B-Preview", "QwQ-32B-Preview（推理特化）", List.of("qwen3", "thinking", "free")),
          // --- DeepSeek V4 ---
          new Item("deepseek-ai/DeepSeek-V4-Flash", "DeepSeek-V4-Flash（魔搭侧常见免费推理）", List.of("deepseek-v4", "free")),
          new Item("deepseek-ai/DeepSeek-V4-Pro:DashScope", "DeepSeek-V4-Pro（:DashScope 托管，需绑定阿里云）",
              List.of("deepseek-v4", "dashscope", "premium")),
          // --- GLM（5 / 5.1 多为智谱托管路由，需在魔搭绑定 Zhipu Key）---
          new Item("ZhipuAI/GLM-4.7-Flash", "GLM-4.7-Flash（智谱 free）", List.of("glm", "zhipu", "free")),
          new Item("ZhipuAI/GLM-5:ZhipuAI", "GLM-5（:ZhipuAI）", List.of("glm-5", "zhipu", "premium")),
          new Item("ZhipuAI/GLM-5.1:ZhipuAI", "GLM-5.1（:ZhipuAI）", List.of("glm-5.1", "zhipu", "premium"))
      ),
      List.of(
          new Item("Qwen/Qwen3.5-14B-Instruct", "Qwen3.5-14B judge（快）", List.of("qwen3.5", "fast")),
          new Item("Qwen/Qwen3.5-32B-Instruct", "Qwen3.5-32B judge（更稳）", List.of("qwen3.5")),
          new Item("Qwen/Qwen3-30B-A3B", "Qwen3-30B-A3B（推荐 judge，速度快）", List.of("qwen3", "fast", "free")),
          new Item("Qwen/Qwen3-32B", "Qwen3-32B（更准确但慢）", List.of("qwen3", "accurate", "free")),
          new Item("Qwen/Qwen3-14B", "Qwen3-14B（折中）", List.of("qwen3", "free")),
          new Item("deepseek-ai/DeepSeek-V4-Flash", "DeepSeek-V4-Flash judge（快）", List.of("deepseek-v4", "fast", "free"))
      ),
      "使用魔搭 API-Inference：api_key 为 Access Token；model 必须与模型页 API-Inference 中的 Model Id 一致（会变更，请以前端 ping 与页面为准）。"
          + "Responses API 目前仅部分 Qwen3.5 支持；本平台评测走 Chat Completions（OpenAI 兼容 stream），与文档流式示例一致。"
          + " modelId 末尾可附 ':Provider'（如 :DashScope / :ZhipuAI）走托管，需先在魔搭控制台绑定对应厂商 Key；无后缀则走魔搭平台推理。"
  );

  @GetMapping("/catalog")
  public ResponseEntity<EvalApiResponse<ModelCatalogResponse>> getCatalog() {
    return ResponseEntity.ok(EvalApiResponse.success(CATALOG));
  }
}
