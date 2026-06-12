package org.example.backend.dto.arena;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class PairwiseMatrixResponse {
    /** Ordered list of model names (rows = cols) */
    private List<String> models;
    /** matrix[rowModel][colModel] = { wins, losses, ties, total, winRate } */
    private Map<String, Map<String, PairwiseCell>> matrix;
    /** Total votes counted in this matrix */
    private int totalVotes;

    @Data
    @AllArgsConstructor
    public static class PairwiseCell {
        private int wins;
        private int losses;
        private int ties;
        private int total;
        private double winRate;
    }
}
