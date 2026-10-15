package pipeline;

/**
 * 职责：用户问一个问题 → 返回一个答案
 *
 * 流程：
 *
 * 用户输入
 *   ↓
 * ConversationMemory     // 读取历史对话，构建上下文
 *   ↓
 * PromptService.rewriteQuery          // query改写, 让召回更精准
 *   ↓
 * ChunkService.retrieve        // 召回 top K
 *   ↓
 * ChunkService.buildParentChildChunks
 *   ↓
 * PromptService.buildMessages         // chunks + 历史 + 问题 → 组装Prompt
 *   ↓
 * ChatService            // 调用LLM，生成答案
 *   ↓
 * 返回给用户
 *
 * 触发时机：
 *   - 每次用户发消息时触发
 *   - 实时运行
 */
public class RagPipeline {
}
