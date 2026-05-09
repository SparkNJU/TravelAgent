package org.example.backend.dto;

import org.example.backend.enums.MessageRole;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private MessageRole role;
    private String content;
    // You can add more fields if necessary, like 'name' for tool responses
}
