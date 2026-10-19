package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import com.tangfan.aiagent.infra.database.ChromaDbClient;
import com.tangfan.aiagent.model.Chunk;

import java.util.ArrayList;
import java.util.List;

public class VectorStoreService {
    private static final String CHUNK_ID = "chunk_id";
    private static final String SOURCE_FILE = "source_file";
    private static final String PAGE_NUM = "page_num";

    private final ChromaDbClient chromaDbClient;

    public VectorStoreService(ChromaDbClient chromaDbClient) {
        this.chromaDbClient = chromaDbClient;
    }

    // 写入/覆盖
    public void upsert(List<Chunk> chunkList) {
        List<TextSegment> segments = chunkList.stream()
                .map(chunk -> {
                    Metadata metadata = new Metadata();
                    metadata.put(CHUNK_ID, chunk.id);
                    metadata.put(SOURCE_FILE, chunk.sourceFile);
                    metadata.put(PAGE_NUM, chunk.pageNumber);
                    return TextSegment.from(chunk.content, metadata);
                })
                .toList();
        chromaDbClient.addSegments(segments);
    }

    // 召回
    public List<Chunk> retrieve(String query, int topK) {
        List<EmbeddingMatch<TextSegment>> matches = chromaDbClient.search(query, topK, 0.6);
        List<Chunk> chunks = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();
            chunks.add(
                    new Chunk(
                            metadata.getInteger(CHUNK_ID),
                            segment.text(),
                            metadata.getString(SOURCE_FILE),
                            0,
                            -1,
                            null));
        }
        return chunks;
    }

    public List<Chunk> getChunkByParentId(List<Chunk> childChunk) {
        return List.of();
    }

    // 按文档删全部chunks
    public void deleteBySource(String sourceFile) {
        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalArgumentException("Source file must not be blank");
        }
        chromaDbClient.deleteBySource(sourceFile);
    }
}
