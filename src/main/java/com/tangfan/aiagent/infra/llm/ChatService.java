package com.tangfan.aiagent.infra.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.ArrayList;
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

    public String generateTitle(List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
                Generate a concise title for this conversation based on its history.
                Use the same language as the conversation. Return only the title, without quotes or explanation.
                """));
        messages.addAll(history);
        messages.add(UserMessage.from("Generate a concise title for the conversation above."));
        return chat(messages).strip();
    }
}
