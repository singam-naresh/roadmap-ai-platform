package com.assistant.dto;

public class TaskRequest {

    private String userInput;
    private String mode;

    /**
     * Optional. If provided, this request continues an existing conversation thread.
     * The ConversationService will inject the last N messages as context.
     */
    private Long conversationId;

    public TaskRequest() {}

    public TaskRequest(String userInput, String mode) {
        this.userInput = userInput;
        this.mode = mode;
    }

    public String getUserInput()               { return userInput; }
    public void setUserInput(String v)         { this.userInput = v; }

    public String getMode()                    { return mode; }
    public void setMode(String v)              { this.mode = v; }

    public Long getConversationId()            { return conversationId; }
    public void setConversationId(Long v)      { this.conversationId = v; }
}
