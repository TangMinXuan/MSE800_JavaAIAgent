package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import com.tangfan.aiagent.infra.llm.ChatService;
import com.tangfan.aiagent.model.Chunk;

import java.util.ArrayList;
import java.util.List;

// chunks + 历史 + 问题 → 组装Prompt
public class PromptService {
    public final static String REWRITE_PROMPT_TEMPLATE = """
            请将以下问题改写为更适合文档检索的形式，只返回改写后的问题：
            {originalQuery}
        """;

    private final ChatService chatService;

    public PromptService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String buildRetrievedContext(List<Chunk> retrievedChunks) {
        StringBuilder context = new StringBuilder();
        for (Chunk chunk : retrievedChunks) {
            context.append("[来源: ").append(chunk.sourceFile).append("]\n")
                    .append(chunk.content).append("\n\n");
        }
        if (context.isEmpty()) {
            return "没有检索到相关的 TangFan DB 文档资料。";
        }
        return "[检索到的 TangFan DB 参考资料]\n" + context;
    }

    public String rewriteQuery(List<ChatMessage> history, String originalQuery) {
        if (history.isEmpty()) {
            return chatService.chat(REWRITE_PROMPT_TEMPLATE.replace("{originalQuery}", originalQuery));
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
                将对话中的最新问题改写为适合文档检索的独立问题。
                可利用此前对话补全指代信息。只返回改写后的问题，不回答问题。
                """));
        messages.addAll(history);
        messages.add(UserMessage.from(REWRITE_PROMPT_TEMPLATE.replace("{originalQuery}", originalQuery)));
        return chatService.chat(messages);
    }
}
