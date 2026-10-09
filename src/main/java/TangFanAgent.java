import dev.langchain4j.service.SystemMessage;

public interface TangFanAgent {
    @SystemMessage("""
        You are a helpful assistant.
        You have access to TangFan DB documentation tool.
        Use it when user asks TangFan DB related questions.
        For general questions, answer directly.
        """)
    String chat(String userMessage);
}
