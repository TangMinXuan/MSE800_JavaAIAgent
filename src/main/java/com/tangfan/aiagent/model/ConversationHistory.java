package com.tangfan.aiagent.model;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
@Entity(name = "ConversationHistory")
@Table(name = "conversation_history", schema = "public")
@Access(AccessType.FIELD)
public class ConversationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "conversation_history_id_generator")
    @SequenceGenerator(
            name = "conversation_history_id_generator",
            sequenceName = "conversation_history_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "uid")
    private String uid;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "conversation_title")
    private String conversationTitle;

    @Column(name = "history", columnDefinition = "varchar")
    private String history;

    protected ConversationHistory() {
    }

    public ConversationHistory(Integer id, String uid, String conversationId, String conversationTitle,
                               String history) {
        this.id = id;
        this.uid = uid;
        this.conversationId = conversationId;
        this.conversationTitle = conversationTitle;
        this.history = history;
    }

    public Integer getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getConversationTitle() {
        return conversationTitle;
    }

    public String getHistory() {
        return history;
    }
}
