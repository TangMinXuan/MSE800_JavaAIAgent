package com.tangfan.aiagent.infra.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

public class ChatService {

    public static final String apiKey = System.getenv("API_KEY");

    OpenAiChatModel llm = OpenAiChatModel.builder()
            .baseUrl("https://openrouter.ai/api/v1")
            .apiKey(apiKey)
            .modelName("openai/gpt-4o-mini")
            .build();

    public String chat(String prompt) {
        return llm.chat(prompt);
    }

    public String chat(List<ChatMessage> messages) {
        ChatResponse response = llm.chat(messages);
        return response.aiMessage().text();
    }
}
