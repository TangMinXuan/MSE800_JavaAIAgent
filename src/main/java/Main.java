import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;

public class Main {
    public static final String apiKey = System.getenv("API_KEY");

    public static void main(String[] args) {


        List<TextSegment> segments = splitDoc();
        EmbeddingStore<TextSegment> embeddingStore = localEmbedding(segments);
        retrieveWithRAG(embeddingStore, new AllMiniLmL6V2EmbeddingModel());
    }

    public static void openRouterDemo() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(apiKey)
                .modelName("openai/gpt-4o-mini")
                .build();
        String response = model.chat("你好，介绍一下你自己");
        System.out.println(response);
    }

    public static List<TextSegment> splitDoc() {
        String path = "/Users/tangfan/IdeaProjects/JavaAIAgent/src/main/resources/base.txt";

        // 加载
        Document doc = FileSystemDocumentLoader.loadDocument(path);

        // 切片
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

        // 500 = 每块字符数, 50 = 相邻块重叠字符数
        List<TextSegment> segments = splitter.split(doc);

        // 打印验收
//        for (int i = 0; i < segments.size(); i++) {
//            System.out.println("=== 第" + i + "块 ===");
//            System.out.println(segments.get(i).text());
//        }

        return segments;
    }

    public static InMemoryEmbeddingStore<TextSegment> localEmbedding(List<TextSegment> segments) {
        // 1. 本地Embedding模型
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // 2. 内存向量库
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }
        System.out.println("存入向量数量: " + embeddingStore.size());
        return embeddingStore;
    }

    public static void onlineEmbedding(List<TextSegment> segments) {
        // 1. OpenRouter Embedding模型（兼容OpenAI接口）
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey("你的OpenRouter Key")
                .modelName("text-embedding-3-small")
                .build();

        // 2. 内存向量库
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // 3. 一步完成：切割 + 向量化 + 存储
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }
        System.out.println("存入向量数量: " + embeddingStore.size());
    }

    public static void retrieveWithRAG(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(apiKey)
                .modelName("openai/gpt-4o-mini")
                .build();

        // 1. 构建Retriever
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)        // TopN
                .minScore(0.6)        // 相似度阈值
                .build();

        // 2. 构建RetrievalAugmentor
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .build();

        // 3. 构建AI Service
        interface RedisAssistant {
            @SystemMessage("""
                    You are a TangFan DB assistant.
                    Answer ONLY based on the provided context.
                    If the context does not contain relevant information, 
                    say "I don't have information about this topic" 
                    and do NOT make up any answer.
                    """)
            String chat(String userMessage);
        }

        RedisAssistant assistant = AiServices.builder(RedisAssistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(augmentor)
                .build();

        // 4. 直接问问题
        String answer = assistant.chat("What is the default port of TangFan DB?");
        System.out.println(answer);
    }
}
