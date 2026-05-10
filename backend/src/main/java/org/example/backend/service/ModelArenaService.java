package org.example.backend.service;

import org.example.backend.dto.AgentChatRequest;
import org.example.backend.dto.arena.ArenaAutoResponse;
import org.example.backend.dto.arena.ArenaLeaderboardEntry;
import org.example.backend.dto.arena.ArenaLeaderboardResponse;
import org.example.backend.dto.arena.ArenaVoteRequest;
import org.example.backend.entity.ModelArenaVote;
import org.example.backend.repository.ModelArenaVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ModelArenaService {

    private static final List<String> DEFAULT_MODELS = List.of(
            "deepseek-v4-flash",
            "kimi-k2.6",
            "MiniMax-M2.5",
            "qwen3.6-plus",
            "glm-5.1"
    );

    private final TripAssistantService tripAssistantService;
    private final ModelArenaVoteRepository voteRepository;
    private final Random random = new Random();
    private final ExecutorService arenaExecutor = Executors.newFixedThreadPool(4);

    public ModelArenaService(TripAssistantService tripAssistantService, ModelArenaVoteRepository voteRepository) {
        this.tripAssistantService = tripAssistantService;
        this.voteRepository = voteRepository;
    }

    public List<String> listModels() {
        return DEFAULT_MODELS;
    }

    public ArenaAutoResponse runAutoComparison(String query, Long userId, String chatHistoryJson, MultipartFile file) {
        List<String> models = new ArrayList<>(DEFAULT_MODELS);
        if (models.size() < 2) {
            return new ArenaAutoResponse("", "", "模型配置不足", "");
        }
        Collections.shuffle(models, random);
        String modelA = models.get(0);
        String modelB = models.get(1);

        AgentChatRequest reqA = createRequest(query, userId, modelA, chatHistoryJson);
        AgentChatRequest reqB = createRequest(query, userId, modelB, chatHistoryJson);

        CompletableFuture<String> futureA = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("开始请求模型 A: " + modelA + " (mode: agent, generatePlanFirst: true)");
                String result = tripAssistantService.fetchAgentAnswer(reqA, file);
                System.out.println("模型 A 响应完成: " + modelA);
                return result;
            } catch (Exception e) {
                System.out.println("模型 A 请求失败: " + modelA + ", 错误: " + e.getMessage());
                return "模型A请求失败: " + e.getMessage();
            }
        }, arenaExecutor);
        
        CompletableFuture<String> futureB = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("开始请求模型 B: " + modelB + " (mode: agent, generatePlanFirst: true)");
                String result = tripAssistantService.fetchAgentAnswer(reqB, file);
                System.out.println("模型 B 响应完成: " + modelB);
                return result;
            } catch (Exception e) {
                System.out.println("模型 B 请求失败: " + modelB + ", 错误: " + e.getMessage());
                return "模型B请求失败: " + e.getMessage();
            }
        }, arenaExecutor);

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futureA, futureB);
        
        try {
            combinedFuture.get(600, TimeUnit.SECONDS); 
            String answerA = futureA.get();
            String answerB = futureB.get();
            
            return new ArenaAutoResponse(modelA, modelB, answerA, answerB);
        } catch (TimeoutException e) {
            throw new RuntimeException("模型响应超时", e);
        } catch (Exception e) {
            throw new RuntimeException("获取模型响应时出错: " + e.getMessage(), e);
        }
    }

    public SseEmitter streamAutoComparison(String query, Long userId, String chatHistoryJson, MultipartFile file) {
        SseEmitter emitter = new SseEmitter(600_000L);

        List<String> models = new ArrayList<>(DEFAULT_MODELS);
        if (models.size() < 2) {
            safeSend(emitter, Map.of(
                    "type", "arena_error",
                    "content", "模型配置不足"
            ));
            completeStream(emitter);
            return emitter;
        }

        Collections.shuffle(models, random);
        String modelA = models.get(0);
        String modelB = models.get(1);

        AgentChatRequest reqA = createRequest(query, userId, modelA, chatHistoryJson);
        AgentChatRequest reqB = createRequest(query, userId, modelB, chatHistoryJson);

        StringBuilder answerA = new StringBuilder();
        StringBuilder answerB = new StringBuilder();
        AtomicInteger doneCount = new AtomicInteger(0);
        AtomicBoolean finished = new AtomicBoolean(false);

        safeSend(emitter, Map.of(
                "type", "arena_init",
                "content", "已创建双模型流式对比任务",
                "metadata", Map.of("sourceA", "A", "sourceB", "B")
        ));

        CompletableFuture<Void> streamA = CompletableFuture.runAsync(() ->
                streamSingleModel(emitter, reqA, file, "A", answerA, doneCount, finished), arenaExecutor);
        CompletableFuture<Void> streamB = CompletableFuture.runAsync(() ->
                streamSingleModel(emitter, reqB, file, "B", answerB, doneCount, finished), arenaExecutor);

        CompletableFuture.allOf(streamA, streamB)
                .whenComplete((unused, throwable) -> {
                    if (finished.compareAndSet(false, true)) {
                        safeSend(emitter, Map.of(
                                "type", "arena_complete",
                                "content", "双模型对比完成",
                                "metadata", Map.of(
                                        "modelA", modelA,
                                        "modelB", modelB,
                                        "answerA", answerA.toString(),
                                        "answerB", answerB.toString()
                                )
                        ));
                        completeStream(emitter);
                    }
                });

        emitter.onTimeout(() -> {
            if (finished.compareAndSet(false, true)) {
                safeSend(emitter, Map.of("type", "arena_error", "content", "对比超时"));
                completeStream(emitter);
            }
        });
        emitter.onError(t -> {
            if (finished.compareAndSet(false, true)) {
                completeStream(emitter);
            }
        });

        return emitter;
    }

    private void streamSingleModel(
            SseEmitter emitter,
            AgentChatRequest req,
            MultipartFile file,
            String source,
            StringBuilder answerBuffer,
            AtomicInteger doneCount,
            AtomicBoolean finished
    ) {
        try {
            tripAssistantService.streamAgentEvents(
                    req,
                    file,
                    event -> {
                        if (finished.get()) {
                            return;
                        }
                        String eventType = String.valueOf(event.getOrDefault("type", "unknown"));
                        Object content = event.getOrDefault("content", "");

                        if ("answer".equals(eventType)) {
                            String chunk = content == null ? "" : content.toString();
                            answerBuffer.append(chunk);
                            safeSend(emitter, Map.of(
                                    "type", "arena_answer_chunk",
                                    "content", chunk,
                                    "metadata", Map.of("source", source)
                            ));
                            return;
                        }

                        if ("error".equals(eventType)) {
                            safeSend(emitter, Map.of(
                                    "type", "arena_model_error",
                                    "content", content == null ? "模型调用失败" : content.toString(),
                                    "metadata", Map.of("source", source)
                            ));
                            return;
                        }

                        if (List.of("thought", "action", "observation", "reflection", "plan").contains(eventType)) {
                            Object metadata = event.get("metadata");
                            safeSend(emitter, Map.of(
                                    "type", "arena_model_event",
                                    "content", content == null ? "" : content.toString(),
                                    "metadata", Map.of(
                                            "source", source,
                                            "eventType", eventType,
                                            "eventMetadata", metadata == null ? Map.of() : metadata
                                    )
                            ));
                        }
                    },
                    () -> {
                        if (finished.get()) {
                            return;
                        }
                        int count = doneCount.incrementAndGet();
                        safeSend(emitter, Map.of(
                                "type", "arena_model_done",
                                "content", "模型输出完成",
                                "metadata", Map.of("source", source, "doneCount", count)
                        ));
                    },
                    err -> {
                        if (finished.get()) {
                            return;
                        }
                        safeSend(emitter, Map.of(
                                "type", "arena_model_error",
                                "content", err,
                                "metadata", Map.of("source", source)
                        ));
                    }
            );
        } catch (Exception ex) {
            if (finished.get()) {
                return;
            }
            safeSend(emitter, Map.of(
                    "type", "arena_model_error",
                    "content", "模型流式调用异常: " + ex.getMessage(),
                    "metadata", Map.of("source", source)
            ));
        }
    }

    private void safeSend(SseEmitter emitter, Map<String, Object> payload) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name("arena").data(payload));
            } catch (Exception ignored) {
            }
        }
    }

    private void completeStream(SseEmitter emitter) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            } catch (Exception ignored) {
            }
            emitter.complete();
        }
    }

    private AgentChatRequest createRequest(String query, Long userId, String model, String chatHistoryJson) {
        AgentChatRequest req = new AgentChatRequest();
        req.setQuery(query);
        req.setUserId(userId);
        req.setMode("agent");
        req.setGeneratePlanFirst(true);
        req.setModel(model);
        req.setChatHistoryJson(chatHistoryJson);
        return req;
    }

    @PreDestroy
    public void shutdownArenaExecutor() {
        arenaExecutor.shutdown();
    }

    public void recordVote(ArenaVoteRequest request) {
        if (request == null || request.getModelA() == null || request.getModelB() == null) {
            return;
        }
        ModelArenaVote vote = new ModelArenaVote();
        vote.setModelA(request.getModelA());
        vote.setModelB(request.getModelB());
        vote.setResult(request.getResult());
        voteRepository.save(vote);
    }

    public ArenaLeaderboardResponse getLeaderboard() {
        List<ModelArenaVote> votes = voteRepository.findAll();
        List<String> models = new ArrayList<>(DEFAULT_MODELS);

        Map<String, Integer> wins = initCountMap(models);
        Map<String, Integer> losses = initCountMap(models);
        Map<String, Integer> ties = initCountMap(models);
        Map<String, Integer> matches = initCountMap(models);

        Map<String, Map<String, Integer>> n = initPairMap(models);
        Map<String, Map<String, Double>> w = initPairWinMap(models);

        for (ModelArenaVote vote : votes) {
            String modelA = vote.getModelA();
            String modelB = vote.getModelB();
            ensureModel(models, modelA, wins, losses, ties, matches, n, w);
            ensureModel(models, modelB, wins, losses, ties, matches, n, w);

            String result = vote.getResult();
            if ("A".equalsIgnoreCase(result)) {
                wins.put(modelA, wins.get(modelA) + 1);
                losses.put(modelB, losses.get(modelB) + 1);
                matches.put(modelA, matches.get(modelA) + 1);
                matches.put(modelB, matches.get(modelB) + 1);
                incPair(n, modelA, modelB, 1);
                incPair(n, modelB, modelA, 1);
                incPair(w, modelA, modelB, 1.0);
            } else if ("B".equalsIgnoreCase(result)) {
                wins.put(modelB, wins.get(modelB) + 1);
                losses.put(modelA, losses.get(modelA) + 1);
                matches.put(modelA, matches.get(modelA) + 1);
                matches.put(modelB, matches.get(modelB) + 1);
                incPair(n, modelA, modelB, 1);
                incPair(n, modelB, modelA, 1);
                incPair(w, modelB, modelA, 1.0);
            } else {
                ties.put(modelA, ties.get(modelA) + 1);
                ties.put(modelB, ties.get(modelB) + 1);
                matches.put(modelA, matches.get(modelA) + 1);
                matches.put(modelB, matches.get(modelB) + 1);
                incPair(n, modelA, modelB, 1);
                incPair(n, modelB, modelA, 1);
                incPair(w, modelA, modelB, 0.5);
                incPair(w, modelB, modelA, 0.5);
            }
        }

        Map<String, Double> scores = computeBradleyTerryScores(models, n, w);

        List<ArenaLeaderboardEntry> entries = new ArrayList<>();
        for (String model : models) {
            double score = scores.getOrDefault(model, 1000.0);
            entries.add(new ArenaLeaderboardEntry(
                    model,
                    score,
                    wins.get(model),
                    losses.get(model),
                    ties.get(model),
                    matches.get(model)
            ));
        }
        entries.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return new ArenaLeaderboardResponse(entries);
    }

    private Map<String, Integer> initCountMap(List<String> models) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String model : models) {
            map.put(model, 0);
        }
        return map;
    }

    private Map<String, Map<String, Integer>> initPairMap(List<String> models) {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        for (String model : models) {
            Map<String, Integer> inner = new HashMap<>();
            for (String other : models) {
                if (!model.equals(other)) {
                    inner.put(other, 0);
                }
            }
            map.put(model, inner);
        }
        return map;
    }

    private Map<String, Map<String, Double>> initPairWinMap(List<String> models) {
        Map<String, Map<String, Double>> map = new HashMap<>();
        for (String model : models) {
            Map<String, Double> inner = new HashMap<>();
            for (String other : models) {
                if (!model.equals(other)) {
                    inner.put(other, 0.0);
                }
            }
            map.put(model, inner);
        }
        return map;
    }

    private void ensureModel(
            List<String> models,
            String model,
            Map<String, Integer> wins,
            Map<String, Integer> losses,
            Map<String, Integer> ties,
            Map<String, Integer> matches,
            Map<String, Map<String, Integer>> n,
            Map<String, Map<String, Double>> w
    ) {
        if (model == null || model.isBlank() || models.contains(model)) {
            return;
        }
        models.add(model);
        wins.put(model, 0);
        losses.put(model, 0);
        ties.put(model, 0);
        matches.put(model, 0);
        n.put(model, new HashMap<>());
        w.put(model, new HashMap<>());
        for (String other : models) {
            if (other.equals(model)) {
                continue;
            }
            n.get(model).put(other, 0);
            n.get(other).put(model, n.get(other).getOrDefault(model, 0));
            w.get(model).put(other, 0.0);
            w.get(other).put(model, w.get(other).getOrDefault(model, 0.0));
        }
    }

    private void incPair(Map<String, Map<String, Integer>> map, String a, String b, int delta) {
        map.get(a).put(b, map.get(a).getOrDefault(b, 0) + delta);
    }

    private void incPair(Map<String, Map<String, Double>> map, String a, String b, double delta) {
        map.get(a).put(b, map.get(a).getOrDefault(b, 0.0) + delta);
    }

    private Map<String, Double> computeBradleyTerryScores(
            List<String> models,
            Map<String, Map<String, Integer>> n,
            Map<String, Map<String, Double>> w
    ) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String model : models) {
            scores.put(model, 1.0);
        }

        boolean hasMatches = models.stream().anyMatch(m ->
                n.getOrDefault(m, Map.of()).values().stream().mapToInt(Integer::intValue).sum() > 0
        );
        if (!hasMatches) {
            Map<String, Double> base = new LinkedHashMap<>();
            for (String model : models) {
                base.put(model, 1000.0);
            }
            return base;
        }

        for (int iter = 0; iter < 100; iter++) {
            Map<String, Double> next = new LinkedHashMap<>();
            double maxDiff = 0.0;
            for (String i : models) {
                double wi = w.getOrDefault(i, Map.of()).values().stream().mapToDouble(Double::doubleValue).sum();
                double denom = 0.0;
                for (String j : models) {
                    if (i.equals(j)) {
                        continue;
                    }
                    int nij = n.getOrDefault(i, Map.of()).getOrDefault(j, 0);
                    if (nij == 0) {
                        continue;
                    }
                    double si = scores.get(i);
                    double sj = scores.get(j);
                    denom += nij / (si + sj);
                }
                double newScore = denom > 0 ? wi / denom : scores.get(i);
                next.put(i, Math.max(newScore, 1e-6));
            }
            double avg = next.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
            for (String i : models) {
                double normalized = next.get(i) / avg;
                maxDiff = Math.max(maxDiff, Math.abs(normalized - scores.get(i)));
                scores.put(i, normalized);
            }
            if (maxDiff < 1e-6) {
                break;
            }
        }

        Map<String, Double> scaled = new LinkedHashMap<>();
        for (String model : models) {
            scaled.put(model, scores.get(model) * 1000.0);
        }
        return scaled;
    }
}