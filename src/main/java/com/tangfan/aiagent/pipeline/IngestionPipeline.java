package com.tangfan.aiagent.pipeline;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import com.tangfan.aiagent.infra.database.ChromaDbClient;
import com.tangfan.aiagent.model.Chunk;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import com.tangfan.aiagent.pipeline.components.ChunkService;
import com.tangfan.aiagent.pipeline.components.DocumentLoader;
import com.tangfan.aiagent.pipeline.components.VectorStoreService;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;

/**
 * 职责：把原始文档 → 变成 Vector DB 里的chunks
 * 流程：
 * 原始文件（PDF/MD/TXT）
 *   ↓
 * DocumentLoader         // 读取文件，转成纯文本
 *   ↓
 * ChunkSplitter          // 按语义/段落切割成小块
 *   ↓
 * EmbeddingService       // 调用Embedding模型，文本→向量
 *   ↓
 * VectorStoreClient      // 存入Chroma
 *
 * 触发时机：
 *   - 手动触发（新文档上传时）
 *   - 不在对话流程中
 *   - 一次性或定期批量运行
 */
public class IngestionPipeline {
    private static final String FILE = "file";
    private static final String DOCUMENT_TEXT = "documentText";
    private static final String SOURCE_FILE = "sourceFile";
    private static final String CHUNKS = "chunks";
    private static final String INGESTED_COUNT = "ingestedCount";
    private final CompiledGraph<AgentState> graph;

    public IngestionPipeline(DocumentLoader documentLoader, ChunkService chunkService,
                             VectorStoreService vectorStoreService) throws GraphStateException {
        this.graph = new StateGraph<>(AgentState::new)
                .addNode("load_document", AsyncNodeAction.node_async(state -> {
                    File file = state.<File>value(FILE)
                            .orElseThrow(() -> new IllegalArgumentException("A document file is required"));
                    Document document = documentLoader.loadDocument(file);
                    return Map.of(
                            DOCUMENT_TEXT, document.text(),
                            SOURCE_FILE, file.getAbsolutePath());
                }))
                .addNode("split_document", AsyncNodeAction.node_async(state -> {
                    String documentText = state.<String>value(DOCUMENT_TEXT)
                            .orElseThrow(() -> new IllegalStateException("The document was not loaded"));
                    String sourceFile = state.<String>value(SOURCE_FILE)
                            .orElseThrow(() -> new IllegalStateException("The source file was not retained"));
                    List<Chunk> chunks = chunkService.split(documentText, sourceFile);
                    return Map.of(CHUNKS, chunks);
                }))
                .addNode("store_chunks", AsyncNodeAction.node_async(state -> {
                    List<Chunk> chunks = state.<List<Chunk>>value(CHUNKS)
                            .orElseThrow(() -> new IllegalStateException("The document was not split"));
                    String sourceFile = state.<String>value(SOURCE_FILE)
                            .orElseThrow(() -> new IllegalStateException("The source file was not retained"));
                    vectorStoreService.deleteBySource(sourceFile);
                    vectorStoreService.upsert(chunks);
                    return Map.of(INGESTED_COUNT, chunks.size());
                }))
                .addEdge(START, "load_document")
                .addEdge("load_document", "split_document")
                .addEdge("split_document", "store_chunks")
                .addEdge("store_chunks", END)
                .compile();
    }

    public int ingest(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Document file does not exist: " + file);
        }
        AgentState result = graph.invoke(GraphInput.args(Map.of(FILE, file)), RunnableConfig.empty())
                .orElseThrow(() -> new IllegalStateException("Document ingestion produced no state"));
        return result.<Integer>value(INGESTED_COUNT)
                .orElseThrow(() -> new IllegalStateException("Document ingestion produced no chunk count"));
    }

    public static void main(String[] args) throws Exception {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        ChromaDbClient chromaDbClient = ChromaDbClient.fromEnvironment(embeddingModel);
        VectorStoreService vectorStoreService = new VectorStoreService(chromaDbClient);
        ChunkService chunkService = new ChunkService(vectorStoreService);

        IngestionPipeline ingestionPipeline = new IngestionPipeline(
                new DocumentLoader(),
                chunkService,
                vectorStoreService);
        File document = new File("src/main/resources/base.txt");
        int ingestedChunks = ingestionPipeline.ingest(document);
        System.out.println("已处理文档块数: " + ingestedChunks);
    }
}
