package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.Document;
import com.tangfan.aiagent.model.Chunk;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import java.util.ArrayList;
import java.util.List;

public class ChunkService {
    private final VectorStoreService vectorStoreService;

    public ChunkService(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    public List<Chunk> split(String documentText, String sourceFile) {
        if (sourceFile == null) {
            sourceFile = "unknown";
        }

        Document document = Document.from(documentText);
        List<TextSegment> segments = DocumentSplitters.recursive(300, 50).split(document);
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            chunks.add(new Chunk(i, segments.get(i).text(), sourceFile));
        }
        return chunks;
    }

    public List<Chunk> retrieve(String rewrittenQuery) {
        return vectorStoreService.retrieve(rewrittenQuery, 3);
    }
}
