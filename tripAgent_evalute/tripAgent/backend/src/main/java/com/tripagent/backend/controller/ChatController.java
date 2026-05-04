package com.tripagent.backend.controller;

import com.tripagent.backend.service.AgentGatewayService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final AgentGatewayService agentGatewayService;

  public ChatController(AgentGatewayService agentGatewayService) {
    this.agentGatewayService = agentGatewayService;
  }

  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> streamChat(
      @RequestParam String sessionId,
      @RequestParam String question
  ) {
    return agentGatewayService.streamAnswer(sessionId, question);
  }
}
