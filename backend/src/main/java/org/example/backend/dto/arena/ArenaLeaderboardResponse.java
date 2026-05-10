package org.example.backend.dto.arena;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArenaLeaderboardResponse {
    private List<ArenaLeaderboardEntry> entries;
}
