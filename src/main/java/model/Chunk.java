package model;

public class Chunk {
    int id;          // 唯一标识
    public String content;     // 文本内容
    String embedding;   // 向量（Embedding后才有值，初始为null）
    int parentId;    // 如果是Child chunk，指向Parent的id; 如果是Parent chunk，为-1


    // 溯源信息
    String sourceFile;  // 来自哪个文件
    int pageNumber;  // 第几页
    int chunkIndex;  // 第几个chunk


    // 人工干预
    int weight;      // 权重，默认1.0
    boolean disabled;    // 是否禁用
}
