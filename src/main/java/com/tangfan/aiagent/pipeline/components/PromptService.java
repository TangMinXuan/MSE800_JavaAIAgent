package com.tangfan.aiagent.pipeline.components;

import com.tangfan.aiagent.util.StringUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import com.tangfan.aiagent.infra.llm.ChatService;
import com.tangfan.aiagent.model.Chunk;
import org.apache.commons.codec.binary.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// chunks + 历史 + 问题 → 组装Prompt
public class PromptService {
    public final static String REWRITE_PROMPT_TEMPLATE = """
            Rewrite the latest user question as a concise, standalone search query for document retrieval.
                Rules:
                1. If the subject of the question is uncertain, it is presumed to be TangFan DB
                2. If the question is already clear and specific, return it unchanged
                3. Return only the rewritten query, no explanation
            Return only the rewritten question:
            {originalQuery}
        """;

    private final ChatService chatService;

    public PromptService(ChatService chatService) {
        this.chatService = chatService;
    }

    public String buildRetrievedContext(List<Chunk> retrievedChunks) {
        StringBuilder context = new StringBuilder();
        for (Chunk chunk : retrievedChunks) {
            if (!StringUtil.isBlank(chunk.content)) {
                context.append("[Source: ").append(chunk.sourceFile).append("]\n")
                        .append(chunk.content).append("\n\n");
            }
        }
        if (context.isEmpty()) {
            return "No relevant TangFan DB documentation was found.";
        }
        return "[Retrieved TangFan DB reference material]\n" + context;
    }

    public String rewriteQuery(List<ChatMessage> history, String originalQuery) {
        if (history.isEmpty()) {
            return chatService.chat(REWRITE_PROMPT_TEMPLATE.replace("{originalQuery}", originalQuery));
        }
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("""
                Rewrite the latest question in the conversation as a standalone question
                suitable for document retrieval. Use the previous conversation to resolve
                references when helpful. Return only the rewritten question; do not answer it.
                """));
        messages.addAll(history);
        messages.add(UserMessage.from(REWRITE_PROMPT_TEMPLATE.replace("{originalQuery}", originalQuery)));
        return chatService.chat(messages);
    }
}
