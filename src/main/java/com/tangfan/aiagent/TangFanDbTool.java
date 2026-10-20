package com.tangfan.aiagent;

import dev.langchain4j.agent.tool.Tool;
import com.tangfan.aiagent.pipeline.RagPipeline;
import dev.langchain4j.agent.tool.ToolMemoryId;
import com.tangfan.aiagent.pipeline.components.ConversationMemory;

public class TangFanDbTool {
    private final RagPipeline ragPipeline;
    private final ConversationMemory conversationMemory;

    public TangFanDbTool(RagPipeline ragPipeline, ConversationMemory conversationMemory) {
        this.ragPipeline = ragPipeline;
        this.conversationMemory = conversationMemory;
    }

    @Tool("Query TangFan DB documentation. " +
            "Use this tool when user asks about TangFan DB features, " +
            "configuration, commands, or any TangFan DB specific knowledge.")
    public String queryTangFanDb(String question, @ToolMemoryId String conversationId) {
        return ragPipeline.toolCalling(question, conversationMemory.historyForTool(conversationId));
    }
}
