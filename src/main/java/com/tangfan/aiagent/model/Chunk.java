package com.tangfan.aiagent.model;

import java.io.Serial;
import java.io.Serializable;

public class Chunk implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final int id;
    public final String content;
    public final String sourceFile;
    public final int parentId;
    public final String parentContent;

    public Chunk(int id, String content, String sourceFile, int parentId, String parentContent) {
        this.id = id;
        this.content = content;
        this.parentId = parentId;
        this.sourceFile = sourceFile;
        this.parentContent = parentContent;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "id=" + id +
                ", parentId=" + parentId +
//                ", sourceFile='" + sourceFile + '\'' +
                ", content='" + content + '\'' +
                ", parentContent='" + parentContent + '\'' +
                '}';
    }
}
