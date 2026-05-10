package com.tripagent.backend.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
public class AgentGatewayService {

  private final WebClient agentWebClient;

  public AgentGatewayService(WebClient agentWebClient) {
    this.agentWebClient = agentWebClient;
  }

  public Flux<String> streamAnswer(String sessionId, String question) {
    return agentWebClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/agent/chat/stream")
            .queryParam("sessionId", sessionId)
            .queryParam("question", question)
            .build())
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(String.class);
  }
}
