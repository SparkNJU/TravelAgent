package org.example.backend.dto.arena;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArenaAutoResponse {
    private String modelA;
    private String modelB;
    private String answerA;
    private String answerB;
}
