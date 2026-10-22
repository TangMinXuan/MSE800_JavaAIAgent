package com.tangfan.aiagent.infra.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tangfan.aiagent.TangFanAgent;
import com.tangfan.aiagent.TangFanDbTool;
import com.tangfan.aiagent.infra.database.ChromaDbClient;
import com.tangfan.aiagent.infra.llm.ChatService;
import com.tangfan.aiagent.pipeline.RagPipeline;
import com.tangfan.aiagent.pipeline.components.ChunkService;
import com.tangfan.aiagent.pipeline.components.ConversationMemory;
import com.tangfan.aiagent.pipeline.components.PromptService;
import com.tangfan.aiagent.pipeline.components.VectorStoreService;
import com.tangfan.aiagent.util.StringUtil;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.bsc.langgraph4j.GraphStateException;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

public class ApiServer {
    private static final int DEFAULT_PORT = 7070;
    private final Gson gson = new Gson();
    public static final String apiKey = System.getenv("API_KEY");

    private static TangFanAgent agent;
    private static ConversationMemory conversationMemory;
    private static ChatService chatService;

    private Javalin app;

    public static void main(String[] args) throws GraphStateException {
        new ApiServer().start(DEFAULT_PORT);
    }

    private void initializeAgent() throws GraphStateException {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        ChromaDbClient chromaDbClient = ChromaDbClient.fromEnvironment(embeddingModel);
        VectorStoreService vectorStoreService = new VectorStoreService(chromaDbClient);
        ChunkService chunkService = new ChunkService(vectorStoreService);

        chatService = new ChatService();
        RagPipeline ragPipeline = new RagPipeline(chunkService, new PromptService(chatService));
        conversationMemory = new ConversationMemory();
        TangFanDbTool tool = new TangFanDbTool(ragPipeline, conversationMemory);

        OpenAiStreamingChatModel agentModel = OpenAiStreamingChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(apiKey)
                .modelName("openai/gpt-4o-mini")
                .build();
        agent = AiServices.builder(TangFanAgent.class)
                .streamingChatModel(agentModel)
                .chatMemoryProvider(conversationMemory)
                .tools(tool)
                .build();
    }

    public ApiServer() {

    }

    public void start(int port) throws GraphStateException {
        initializeAgent();
        app = Javalin.create(config -> {
            config.routes.get("/health", this::health);
            config.routes.post("/api/chat", this::chat);
            config.routes.post("/api/chat/stream", this::streamChat);
        }).start(port);
    }

    private void streamChat(Context ctx) {
        LLM_ChatReq req = ctx.bodyAsClass(LLM_ChatReq.class);
        System.out.println(req);

        boolean newConversation = StringUtil.isBlank(req.conversationId());
        if (newConversation) {
            req.setConversationId(UUID.randomUUID().toString());
        }
        final String conversationId = req.conversationId();

        ctx.status(200)
                .contentType("text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no");

        ctx.async(() -> {
            var writer = ctx.res().getWriter();
            CompletableFuture<Void> streamFinished = new CompletableFuture<>();

            if (newConversation) {
                writeSse(writer, "conversationId", Map.of("conversationId", conversationId));
            }

            agent.chatStream(conversationId, req.rawQuery())
                    .onPartialResponse(token -> {
                        try {
                            writeSse(writer, "delta", Map.of("content", token));
                        } catch (IOException exception) {
                            streamFinished.completeExceptionally(exception);
                        }
                    })
                    .onCompleteResponse(response -> {
                        if (newConversation) {
                            try {
                                String title = chatService.generateTitle(
                                        conversationMemory.get(conversationId).messages());
                                conversationMemory.updateConversationTitle(conversationId, title);
                                writeSse(writer, "conversationTitle", Map.of(
                                        "conversationId", conversationId,
                                        "title", title));
                            } catch (RuntimeException | IOException exception) {
                                try {
                                    writeSse(writer, "error", Map.of("message", "Conversation title generation failed"));
                                } catch (IOException writeException) {
                                    exception.addSuppressed(writeException);
                                }
                                streamFinished.completeExceptionally(exception);
                                return;
                            }
                        }
                        try {
                            writeSse(writer, "done", Map.of());
                            streamFinished.complete(null);
                        } catch (IOException exception) {
                            streamFinished.completeExceptionally(exception);
                        }
                    })
                    .onError(error -> {
                        error.printStackTrace();
                        try {
                            writeSse(writer, "error", Map.of("message", "Chat generation failed"));
                        } catch (IOException writeException) {
                            error.addSuppressed(writeException);
                        }
                        streamFinished.completeExceptionally(error);
                    })
                    .start();

            streamFinished.get();
        });
    }

    private void chat(Context ctx) {
        LLM_ChatReq req = ctx.bodyAsClass(LLM_ChatReq.class);
        System.out.println(req);
//        String llmResp = agent.chat(req.conversationId, req.rawQuery());
        json(ctx, 200, new LLM_ChatResp(200, "success",
                "blanket for test",
                "test",
                "1+1=2"));
    }

    private void health(Context ctx) {
        json(ctx, 200, Map.of("status", "ok"));
    }

    private void json(Context ctx, int status, Object body) {
        ctx.status(status)
                .contentType("application/json")
                .result(gson.toJson(body));
    }

    private void writeSse(Writer writer, String event, Object data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + gson.toJson(data) + "\n\n");
        writer.flush();
    }

    private static class LLM_ChatResp {
        private int code;
        private String msg;
        private String conversationId;
        private String conversationTitle;
        private String llmResp;

        private LLM_ChatResp(int code, String msg, String conversationId, String conversationTitle, String llmResp) {
            this.code = code;
            this.msg = msg;
            this.conversationId = conversationId;
            this.conversationTitle = conversationTitle;
            this.llmResp = llmResp;
        }

        @Override
        public String toString() {
            return "LLM_ChatResp{" +
                    "code=" + code +
                    ", msg='" + msg + '\'' +
                    ", conversationId='" + conversationId + '\'' +
                    ", conversationTitle='" + conversationTitle + '\'' +
                    ", llmResp='" + llmResp + '\'' +
                    '}';
        }
    }

    private static class LLM_ChatReq {
        private String conversationId;
        private String rawQuery;

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public void setRawQuery(String rawQuery) {
            this.rawQuery = rawQuery;
        }

        public String conversationId() {
            return conversationId;
        }

        public String rawQuery() {
            return rawQuery;
        }

        @Override
        public String toString() {
            return "LLM_ChatReq{" +
                    "conversationId='" + conversationId + '\'' +
                    ", rawQuery='" + rawQuery + '\'' +
                    '}';
        }
    }

}
