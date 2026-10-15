package pipeline.components;

import model.Chunk;

import java.util.List;

public class VectorStoreService {

    // 写入/覆盖
    public void upsert(List<Chunk> chunkList) {

    }

    // 召回
    public List<Chunk> retrieve(String query, int topK) {
        return null;
    }

    public List<Chunk> getChunkByParentId(List<Chunk> parentsChunk) {
        return null;
    }

    // 按文档删全部chunks
    public void deleteBySource(String sourceFile) {

    }
}
