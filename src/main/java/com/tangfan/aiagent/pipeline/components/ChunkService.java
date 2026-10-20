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
            chunks.add(new Chunk(i, segments.get(i).text(), sourceFile, -1, null));
        }
        return chunks;
    }

    public List<Chunk> split2(String markdownText, String sourceFile) {
        if (markdownText == null || markdownText.isBlank()) {
            throw new IllegalArgumentException("Markdown text must not be blank");
        }
        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalArgumentException("Source file must not be blank");
        }
        record MarkdownSection(String content) {}

        Node document = Parser.builder().build().parse(markdownText);
        TextContentRenderer textRenderer = TextContentRenderer.builder().build();
        List<MarkdownSection> sections = new ArrayList<>();
        StringBuilder sectionContent = new StringBuilder();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading) {
                if (!sectionContent.toString().isBlank()) {
                    sections.add(new MarkdownSection(sectionContent.toString().trim()));
                    sectionContent.setLength(0);
                }
            } else {
                sectionContent.append(textRenderer.render(node)).append('\n');
            }
        }
        if (!sectionContent.toString().isBlank()) {
            sections.add(new MarkdownSection(sectionContent.toString().trim()));
        }

        List<Chunk> chunks = new ArrayList<>();
        int childId = 0;
        int parentId = 0;
        for (MarkdownSection section : sections) {
            String parentContent = section.content();
            List<TextSegment> childSegments = DocumentSplitters.recursive(100, 20)
                    .split(Document.from(section.content()));
            for (TextSegment childSegment : childSegments) {
                chunks.add(new Chunk(
                        childId++,
                        childSegment.text(),
                        sourceFile,
                        parentId,
                        parentContent));
            }
            parentId++;
        }
        chunks.forEach(System.out::println); // TODO for debug purpose
        return chunks;
    }

    public List<Chunk> retrieve(String rewrittenQuery) {
        return vectorStoreService.retrieve(rewrittenQuery, 3);
    }

    public List<Chunk> buildParentChildChunks(List<Chunk> children) {
        if (children == null) {
            throw new IllegalArgumentException("Children must not be null");
        }

        List<Chunk> parentChildChunks = new ArrayList<>(children.size());
        for (int start = 0; start < children.size(); start += 3) {
            int end = Math.min(start + 3, children.size());
            List<Chunk> parentChildren = children.subList(start, end);
            StringBuilder parentContent = new StringBuilder();
            for (Chunk child : parentChildren) {
                if (!parentContent.isEmpty()) {
                    parentContent.append('\n');
                }
                parentContent.append(child.content);
            }

            int parentId = parentChildren.get(0).id;
            String content = parentContent.toString();
            for (Chunk child : parentChildren) {
                parentChildChunks.add(new Chunk(
                        child.id,
                        child.content,
                        child.sourceFile,
                        parentId,
                        content));
            }
        }
        return parentChildChunks;
    }
}
