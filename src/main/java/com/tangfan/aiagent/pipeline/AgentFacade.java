package com.tangfan.aiagent.pipeline;

import com.tangfan.aiagent.TangFanAgent;
import com.tangfan.aiagent.model.Chunk;
import com.tangfan.aiagent.model.FaithfulnessResult;
import com.tangfan.aiagent.pipeline.components.FaithfulnessChecker;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AgentFacade {
    private final TangFanAgent agent;
    private final FaithfulnessChecker faithfulnessChecker;
    private final TurnEvidenceProvider evidenceProvider;

    public AgentFacade(TangFanAgent agent, FaithfulnessChecker faithfulnessChecker, TurnEvidenceProvider evidenceProvider) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.faithfulnessChecker = Objects.requireNonNull(faithfulnessChecker, "faithfulnessChecker");
        this.evidenceProvider = Objects.requireNonNull(evidenceProvider, "evidenceProvider");
    }

    public AgentResponse chat(String conversationId, String userMessage) {
        String answer = agent.chat(conversationId, userMessage);
        Optional<List<Chunk>> evidence = Objects.requireNonNull(
                evidenceProvider.evidenceFor(conversationId),
                "Evidence provider must return an Optional");

        FaithfulnessResult result = evidence
                .map(chunks -> faithfulnessChecker.check(answer, chunks))
                .orElseGet(FaithfulnessResult::notApplicable);
        return new AgentResponse(answer, result);
    }

    @FunctionalInterface
    public interface TurnEvidenceProvider {
        /**
         * Returns this completed chat turn's retrieved chunks, if the agent used the RAG tool.
         * An empty list means the tool ran but retrieved no evidence; Optional.empty()
         * means this turn did not use the RAG tool.
         *
         * Implementations must associate evidence with the matching invocation and
         * support concurrent calls safely; a conversation-wide "last chunks" value
         * can associate evidence with the wrong answer.
         */
        Optional<List<Chunk>> evidenceFor(String conversationId);
    }

    public record AgentResponse(String answer, FaithfulnessResult faithfulnessResult) {
        public AgentResponse {
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(faithfulnessResult, "faithfulnessResult");
        }
    }
}
