package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ConversationMemory implements ChatMemoryProvider {
    private static final int MAX_MESSAGES = 20;

    private final Map<Object, ChatMemory> memories = new HashMap<>();

    @Override
    public ChatMemory get(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("Conversation ID must not be null");
        }
        return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(MAX_MESSAGES)
                .build());
    }

    public List<TextHistoryMessage> historyForTool(Object memoryId) {
        List<ChatMessage> messages = new ArrayList<>(get(memoryId).messages());
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage) {
                messages.remove(index);
                break;
            }
        }

        return messages.stream()
                .flatMap(ConversationMemory::toTextHistoryMessage)
                .toList();
    }

    private static Stream<TextHistoryMessage> toTextHistoryMessage(ChatMessage message) {
        if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
            return Stream.of(new TextHistoryMessage("user", userMessage.singleText()));
        }
        if (message instanceof AiMessage aiMessage
                && !aiMessage.hasToolExecutionRequests()
                && aiMessage.text() != null
                && !aiMessage.text().isBlank()) {
            return Stream.of(new TextHistoryMessage("assistant", aiMessage.text()));
        }
        return Stream.empty();
    }

    public record TextHistoryMessage(String role, String text) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public ChatMessage toChatMessage() {
            return switch (role) {
                case "user" -> UserMessage.from(text);
                case "assistant" -> AiMessage.from(text);
                default -> throw new IllegalStateException("Unsupported history role: " + role);
            };
        }
    }
}
