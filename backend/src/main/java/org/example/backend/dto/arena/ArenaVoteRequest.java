package org.example.backend.dto.arena;

import lombok.Data;

@Data
public class ArenaVoteRequest {
    private String modelA;
    private String modelB;
    private String result; // A, B, BOTH_GOOD, BOTH_BAD
}
