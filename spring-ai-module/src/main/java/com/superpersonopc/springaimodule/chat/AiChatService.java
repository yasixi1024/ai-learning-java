package com.superpersonopc.springaimodule.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    public String chat(String prompt) {
        return chatClient.prompt(prompt)
                .call()
                .content();
    }
}
