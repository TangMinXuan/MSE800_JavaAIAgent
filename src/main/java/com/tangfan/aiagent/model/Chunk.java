package com.tangfan.aiagent.model;

import java.io.Serial;
import java.io.Serializable;

public class Chunk implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public final int id;
    public final String content;
    public final String sourceFile;

    public Chunk(int id, String content, String sourceFile) {
        this.id = id;
        this.content = content;
        this.sourceFile = sourceFile;
    }

    @Override
    public String toString() {
        return "Chunk{" +
                "id=" + id +
//                ", sourceFile='" + sourceFile + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
