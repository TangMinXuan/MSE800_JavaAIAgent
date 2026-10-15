package pipeline;

/**
 * 职责：把原始文档 → 变成 Vector DB 里的chunks
 * 流程：
 * 原始文件（PDF/MD/TXT）
 *   ↓
 * DocumentLoader         // 读取文件，转成纯文本
 *   ↓
 * ChunkSplitter          // 按语义/段落切割成小块
 *   ↓
 * ParentChildChunker     // 给每个小块绑定Parent上下文
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
}
