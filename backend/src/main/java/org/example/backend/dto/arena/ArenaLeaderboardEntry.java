package org.example.backend.dto.arena;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ArenaLeaderboardEntry {
    private String model;
    private double score;
    private int wins;
    private int losses;
    private int ties;
    private int matches;
}
