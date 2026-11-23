package com.superpersonopc.springaimodule.idiom;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.superpersonopc.springaimodule.chat.AiChatService;
import com.superpersonopc.springaimodule.idiom.IdiomDictionary.Idiom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdiomChainService {

    private static final int MAX_SUGGESTIONS = 16;

    private final AiChatService aiChatService;
    private final IdiomDictionary dictionary;

    public IdiomChainService(AiChatService aiChatService, IdiomDictionary dictionary) {
        this.aiChatService = aiChatService;
        this.dictionary = dictionary;
    }

    public String init() {
        List<String> candidates = sample(this.dictionary.playableStarts());
        String answer = askOpenAi("""
                从这些四字成语中选一个作为成语接龙的开场：%s。
                只输出选中的四字成语，不加标点或解释。
                """.formatted(String.join("、", candidates)));
        if (!candidates.contains(answer)) {
            throw invalidModelAnswer();
        }
        return answer;
    }

    public String relay(String message) {
        Idiom input = message == null || message.isBlank() ? null : this.dictionary.find(message);
        if (input == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must be a four-character idiom");
        }

        List<Idiom> matches = this.dictionary.startingWith(input.lastPinyin());
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No matching idiom was found");
        }

        String answer = askOpenAi("""
                为四字成语“%s”接龙。新成语的首字在该成语中的汉语拼音须为 %s（忽略声调）。
                可从这些候选中选择：%s。优先选常见、和输入不同的成语。
                只输出一个四字成语，不加标点或解释。
                """.formatted(input.word(), input.lastPinyin(), String.join("、", sample(matches))));
        Idiom result = this.dictionary.find(answer);
        if (result == null || !input.lastPinyin().equals(result.firstPinyin())) {
            throw invalidModelAnswer();
        }
        return answer;
    }

    private static List<String> sample(List<Idiom> idioms) {
        return ThreadLocalRandom.current().ints(0, idioms.size())
                .distinct()
                .limit(Math.min(MAX_SUGGESTIONS, idioms.size()))
                .mapToObj(index -> idioms.get(index).word())
                .toList();
    }

    private String askOpenAi(String prompt) {
        try {
            String answer = this.aiChatService.chat(prompt);
            if (answer == null || answer.isBlank()) {
                throw invalidModelAnswer();
            }
            return answer.strip();
        }
        catch (RestClientException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI Responses API failed", ex);
        }
    }

    private static ResponseStatusException invalidModelAnswer() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI returned an invalid idiom");
    }
}
