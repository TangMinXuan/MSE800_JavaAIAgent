package infra.vectordb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaApiVersion;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Chroma的Collection理解：
 *   Collection -> 一张独立的表 -> 有自己独立的向量空间 -> 查询时只能查一个Collection
 */
public class ChromaDbClient {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    public ChromaDbClient(String baseUrl, String collectionName, EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)
                .apiVersion(ChromaApiVersion.V2)
                .collectionName(collectionName)
                .build();
    }
    public static ChromaDbClient fromEnvironment(EmbeddingModel embeddingModel) {
        String baseUrl = environmentValue("CHROMA_URL", "http://localhost:8000");
        String collectionName = environmentValue("CHROMA_COLLECTION", "java-agent-docs");
        return new ChromaDbClient(baseUrl, collectionName, embeddingModel);
    }
    public void addSegments(List<TextSegment> segments) {
        List<Embedding> embeddings = segments.stream()
                .map(segment -> embeddingModel.embed(segment).content())
                .toList();
        List<String> ids = java.util.stream.IntStream.range(0, segments.size())
                .mapToObj(index -> UUID.nameUUIDFromBytes(
                        (index + "\0" + segments.get(index).text()).getBytes(StandardCharsets.UTF_8)).toString())
                .toList();
        embeddingStore.addAll(ids, embeddings, segments);
    }
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore)
                        .build())
                .matches();
    }
    public EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }
    private static String environmentValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}