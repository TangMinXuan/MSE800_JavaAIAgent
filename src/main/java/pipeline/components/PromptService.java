package pipeline.components;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import infra.llm.ChatService;
import model.Chunk;

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


    /**
     * 核心方法：组装最终发给LLM的Prompt
     *
     * 格式:
     *  - system messages [包含此次提问的chunks]
     *  - history messages
     *  - current query
     *
     *  之所以, 这么排列, 一个很重要的原因是: 根据 Lost in the Middle (Stanford, 2023), 即注意力机制,
     *  LLM底层天然对 开头 和 结尾 的信息更加敏感.
     */
    public List<ChatMessage> buildMessages(String latestQuery, List<Chunk> retrievedChunks, JsonArray history) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. 组装system messages
        StringBuilder context = new StringBuilder();
        for (Chunk chunk : retrievedChunks) {
            context.append(chunk.content).append("\n\n");
        }
        String systemContent = """
                    你是一个知识库助手，请根据以下资料回答问题。
                    如果资料中没有相关信息，请直接说不知道，不要编造。
                
                    [参考资料]
                    {context}
                """.replace("{context}", context);
        messages.add(SystemMessage.from(systemContent));

        // 2. history messages
        for (JsonElement element : history) {
            var json = element.getAsJsonObject();
            String role = json.get("role").getAsString();
            String content = json.get("content").getAsString();

            switch (role) {
                case "user" -> messages.add(UserMessage.from(content));
                case "assistant" -> messages.add(AiMessage.from(content));
                default -> throw new IllegalArgumentException(
                        "Unsupported chat history role: " + role
                );
            }
        }

        // 3. current query
        messages.add(UserMessage.from(latestQuery));

        return messages;
    }

    public String rewriteQuery(JsonArray history, String originalQuery) {
        // 调用LLM，让它把口语化的query改写成更适合检索的形式
        // 例如："最近公司有啥规定" -> "公司规章制度最新版本"
        String rewritePrompt = REWRITE_PROMPT_TEMPLATE.replace("{originalQuery}", originalQuery);
        return chatService.chat(rewritePrompt);
    }
}
