package com.tripagent.backend;

import com.tripagent.backend.config.TripAgentDotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    TripAgentDotenvLoader.installIntoSystemProperties();
    SpringApplication.run(BackendApplication.class, args);
  }
}
