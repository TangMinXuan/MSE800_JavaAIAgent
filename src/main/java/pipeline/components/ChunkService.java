package pipeline.components;

import dev.langchain4j.data.document.Document;
import model.Chunk;

import java.util.List;

public class ChunkService {

    List<Chunk> split(Document doc) {
        return null;
    }

    List<Chunk> retrieve(String rewrittenQuery) {
        return null;
    }

    List<Chunk> buildParentChildChunks(List<Chunk> children) {
        return null;
    }
}
