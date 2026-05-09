package org.example.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");

    private final String value;

    MessageRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageRole fromValue(String value) {
        for (MessageRole role : MessageRole.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return USER; // Default or could throw exception handling
    }
}
