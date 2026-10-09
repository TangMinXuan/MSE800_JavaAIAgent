import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;
import java.util.stream.Collectors;

public class TangFanDbTool {
    private final EmbeddingStoreContentRetriever retriever;


    // 构造时注入embeddingStore和embeddingModel
    public TangFanDbTool(EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel) {
        this.retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();
    }

    @Tool("Query TangFan DB documentation. " +
            "Use this tool when user asks about TangFan DB features, " +
            "configuration, commands, or any TangFan DB specific knowledge.")
    public String queryTangFanDb(String question) {
        // 把retriever的召回结果拼成字符串返回给Agent
        List<Content> contents = retriever.retrieve(
                new Query(question)
        );

        return contents.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n---\n"));
    }
}
