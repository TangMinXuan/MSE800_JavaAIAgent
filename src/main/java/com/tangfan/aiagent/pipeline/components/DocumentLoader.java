package com.tangfan.aiagent.pipeline.components;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import java.io.File;
public class DocumentLoader {
    public Document loadDocument(File file) {
        return FileSystemDocumentLoader.loadDocument(file.toPath());
    }
}
