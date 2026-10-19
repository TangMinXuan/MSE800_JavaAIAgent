package com.tangfan.aiagent.pipeline;

import com.tangfan.aiagent.TangFanAgent;
import com.tangfan.aiagent.TangFanDbTool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import com.tangfan.aiagent.infra.database.ChromaDbClient;
import com.tangfan.aiagent.infra.llm.ChatService;
import com.tangfan.aiagent.model.Chunk;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import com.tangfan.aiagent.pipeline.components.ChunkService;
import com.tangfan.aiagent.pipeline.components.ConversationMemory;
import com.tangfan.aiagent.pipeline.components.ConversationMemory.TextHistoryMessage;
import com.tangfan.aiagent.pipeline.components.PromptService;
import com.tangfan.aiagent.pipeline.components.VectorStoreService;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * 职责：根据用户问题检索并返回相关知识库资料，由外层Agent结合会话生成答案。
 *
 * 流程：
 *
 * 用户输入
 *   ↓
 * ConversationMemory     // 读取历史对话，构建上下文
 *   ↓
 * PromptService.rewriteQuery          // query改写, 让召回更精准
 *   ↓
 * ChunkService.retrieve        // 召回 top K
 *   ↓
 * ChunkService.buildParentChildChunks
 *   ↓
 * 返回检索上下文给外层Agent
 *
 * 触发时机：
 *   - 每次用户发消息时触发
 *   - 实时运行
 */
public class RagPipeline {
    private static final String QUESTION = "question";
    private static final String HISTORY = "history";
    private static final String REWRITTEN_QUERY = "rewrittenQuery";
    private static final String RETRIEVED_CHUNKS = "retrievedChunks";
    private static final String CONTEXT = "context";

    private final CompiledGraph<AgentState> graph;

    public static final String apiKey = System.getenv("API_KEY");

    public RagPipeline(ChunkService chunkService, PromptService promptService) throws GraphStateException {
        this.graph = new StateGraph<>(AgentState::new)
                .addNode("rewrite_query", AsyncNodeAction.node_async(state -> {
                    String question = state.<String>value(QUESTION)
                            .orElseThrow(() -> new IllegalStateException("Missing graph state value: " + QUESTION));
                    List<ChatMessage> history = state.<List<TextHistoryMessage>>value(HISTORY)
                            .orElseGet(List::of)
                            .stream()
                            .map(TextHistoryMessage::toChatMessage)
                            .toList();
                    return Map.of(REWRITTEN_QUERY, promptService.rewriteQuery(history, question));
                }))
                .addNode("retrieve_chunks", AsyncNodeAction.node_async(state -> {
                    String rewrittenQuery = state.<String>value(REWRITTEN_QUERY)
                            .orElseThrow(() -> new IllegalStateException("Missing graph state value: " + REWRITTEN_QUERY));
                    return Map.of(RETRIEVED_CHUNKS, chunkService.retrieve(rewrittenQuery));
                }))
                .addNode("build_context", AsyncNodeAction.node_async(state -> {
                    List<Chunk> chunks = state.<List<Chunk>>value(RETRIEVED_CHUNKS)
                            .orElseThrow(() -> new IllegalStateException("Chunks were not retrieved"));
                    return Map.of(CONTEXT, promptService.buildRetrievedContext(chunks));
                }))
                .addEdge(START, "rewrite_query")
                .addEdge("rewrite_query", "retrieve_chunks")
                .addEdge("retrieve_chunks", "build_context")
                .addEdge("build_context", END)
                .compile();
    }
    public String retrieveContext(String question, List<TextHistoryMessage> history) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        List<TextHistoryMessage> conversationHistory = history == null ? List.of() : List.copyOf(history);
        AgentState result = graph.invoke(
                        GraphInput.args(Map.of(QUESTION, question, HISTORY, conversationHistory)),
                        RunnableConfig.empty())
                .orElseThrow(() -> new IllegalStateException("RAG graph produced no context"));
        return result.<String>value(CONTEXT)
                .orElseThrow(() -> new IllegalStateException("RAG graph produced no context"));
    }

    public static void main(String[] args) throws GraphStateException {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        ChromaDbClient chromaDbClient = ChromaDbClient.fromEnvironment(embeddingModel);
        VectorStoreService vectorStoreService = new VectorStoreService(chromaDbClient);
        ChunkService chunkService = new ChunkService(vectorStoreService);

        ChatService chatService = new ChatService();
        RagPipeline ragPipeline = new RagPipeline(chunkService, new PromptService(chatService));
        ConversationMemory conversationMemory = new ConversationMemory();
        TangFanDbTool tool = new TangFanDbTool(ragPipeline, conversationMemory);

        OpenAiChatModel agentModel = OpenAiChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(apiKey)
                .modelName("openai/gpt-4o-mini")
                .build();
        TangFanAgent agent = AiServices.builder(TangFanAgent.class)
                .chatModel(agentModel)
                .chatMemoryProvider(conversationMemory)
                .tools(tool)
                .build();

        String conversationId = "local-console";
        System.out.println(agent.chat(conversationId, "What is the default port of TangFan DB?"));
        System.out.println(agent.chat(conversationId, "What is its eviction policy?"));
    }
}
