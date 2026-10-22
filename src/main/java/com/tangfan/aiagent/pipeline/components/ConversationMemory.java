package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import com.tangfan.aiagent.infra.database.PostgresDbClient;
import com.tangfan.aiagent.model.ConversationHistory;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ConversationMemory implements ChatMemoryProvider {
    private static final int MAX_MESSAGES = 20;
    private static final String CONVERSATION_HISTORY_TABLE = "conversation_history";

    private final Map<Object, ChatMemory> memories = new HashMap<>();


    public ChatMemory getFromMemory(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("Conversation ID must not be null");
        }
        return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                .id(id)
                .maxMessages(MAX_MESSAGES)
                .build());
    }

    @Override
    public ChatMemory get(Object memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("Conversation ID must not be null");
        }

        String conversationId = memoryId.toString();
        PostgresDbClient dbClient = PostgresDbClient.fromEnvironment();
        ChatMemoryStore store = new ChatMemoryStore() {
            @Override
            public List<ChatMessage> getMessages(Object id) {
                try {
                    List<ConversationHistory> rows = dbClient.select(
                            CONVERSATION_HISTORY_TABLE,
                            ConversationHistory.class,
                            new ConversationHistory(null, null, id.toString(), null, null));
                    if (rows.isEmpty() || rows.get(0).getHistory() == null) {
                        return List.of();
                    }
                    return ChatMessageDeserializer.messagesFromJson(rows.get(0).getHistory());
                } catch (Exception exception) {
                    System.out.println(exception);
                    throw new IllegalStateException("Failed to load conversation history from PostgreSQL", exception);
                }
            }

            @Override
            public void updateMessages(Object id, List<ChatMessage> messages) {
                String serializedMessages = ChatMessageSerializer.messagesToJson(messages);
                ConversationHistory values = new ConversationHistory(
                        null, null, id.toString(), null, serializedMessages);
                ConversationHistory conditions = new ConversationHistory(
                        null, null, id.toString(), null, null);
                try {
                    List<ConversationHistory> rows = dbClient.select(CONVERSATION_HISTORY_TABLE,
                            ConversationHistory.class,
                            conditions);
                    if (rows.isEmpty()) {
                        dbClient.insert(CONVERSATION_HISTORY_TABLE, values);
                    } else {
                        dbClient.update(CONVERSATION_HISTORY_TABLE, values, conditions);
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to save conversation history to PostgreSQL", exception);
                }
            }

            @Override
            public void deleteMessages(Object id) {
                try {
                    dbClient.delete(
                            CONVERSATION_HISTORY_TABLE,
                            new ConversationHistory(null, null, id.toString(), null, null));
                } catch (java.sql.SQLException exception) {
                    throw new IllegalStateException("Failed to delete conversation history from PostgreSQL", exception);
                }
            }
        };

        return MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(store)
                .build();
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

    public void updateConversationTitle(String conversationId, String title) {
        try {
            PostgresDbClient.fromEnvironment().update(
                    CONVERSATION_HISTORY_TABLE,
                    new ConversationHistory(null, null, null, title, null),
                    new ConversationHistory(null, null, conversationId, null, null));
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Failed to save conversation title to PostgreSQL", exception);
        }
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
