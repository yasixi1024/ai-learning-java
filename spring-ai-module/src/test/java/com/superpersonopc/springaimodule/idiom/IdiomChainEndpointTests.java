package com.superpersonopc.springaimodule.idiom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superpersonopc.springaimodule.chat.AiChatService;
import com.superpersonopc.springaimodule.chat.OpenAiResponsesChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdiomChainEndpointTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mvc;
    private MockRestServiceServer openAi;

    @BeforeEach
    void setUp() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        this.openAi = MockRestServiceServer.bindTo(builder).build();
        var model = new OpenAiResponsesChatModel(
                builder, "test-key", "https://api.openai.com", "/v1/responses", "gpt-4o-mini", 0.7);
        var service = new IdiomChainService(new AiChatService(model), new IdiomDictionary());
        this.mvc = MockMvcBuilders.standaloneSetup(new IdiomChainController(service)).build();
    }

    @Test
    void initSamplesIdiomsAndIgnoresTheMessage() throws Exception {
        String[] chosen = new String[1];
        this.openAi.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    var body = this.objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    String prompt = body.at("/input/0/content").textValue();
                    assertFalse(prompt.contains("ignore-this"));
                    String[] candidates = prompt.substring(prompt.indexOf('：') + 1, prompt.indexOf('。')).split("、");
                    assertEquals(16, candidates.length);
                    chosen[0] = candidates[0];
                    return withSuccess(responseWith(chosen[0]), MediaType.APPLICATION_JSON).createResponse(request);
                });

        byte[] body = send("{\"action\":\"init\",\"message\":{\"value\":\"ignore-this\"}}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(this.objectMapper.readTree("{\"message\":\"" + chosen[0] + "\"}"),
                this.objectMapper.readTree(body));
        this.openAi.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "春华秋实,石沉大海,shi",
        "春华秋实,事半功倍,shi",
        "一语中的,滴水不漏,di"
    })
    void relayAcceptsMatchingPinyinIgnoringTones(String input, String answer, String pinyin) throws Exception {
        this.openAi.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.input[0].content", containsString(input)))
                .andExpect(jsonPath("$.input[0].content", containsString(pinyin + "（忽略声调）")))
                .andRespond(withSuccess(responseWith(answer), MediaType.APPLICATION_JSON));

        send("{\"action\":\"relay\",\"message\":\"" + input + "\"}")
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"" + answer + "\"}", true));
        this.openAi.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"action\":null}",
        "{\"action\":\"INIT\"}",
        "{\"action\":\"other\",\"message\":\"春华秋实\"}",
        "{\"action\":\"relay\"}",
        "{\"action\":\"relay\",\"message\":\" \"}",
        "{\"action\":\"relay\",\"message\":\"你好世界\"}",
        "{\"action\":\"relay\",\"message\":\"画蛇\"}",
        "{\"action\":\"relay\",\"message\":[\"春华秋实\"]}"
    })
    void rejectsInvalidRequestsBeforeCallingOpenAi(String body) throws Exception {
        send(body).andExpect(status().isBadRequest());
        this.openAi.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"海阔天空", "你好世界", "石沉大海。"})
    void rejectsInvalidModelReplies(String answer) throws Exception {
        this.openAi.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(responseWith(answer), MediaType.APPLICATION_JSON));

        send("{\"action\":\"relay\",\"message\":\"春华秋实\"}")
                .andExpect(status().isBadGateway());
        this.openAi.verify();
    }

    @Test
    void returnsUnprocessableEntityWhenThereIsNoMatchingIdiom() throws Exception {
        send("{\"action\":\"relay\",\"message\":\"七拼八凑\"}")
                .andExpect(status().isUnprocessableEntity());
        this.openAi.verify();
    }

    @Test
    void returnsBadGatewayWhenOpenAiFails() throws Exception {
        this.openAi.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        send("{\"action\":\"init\"}").andExpect(status().isBadGateway());
        this.openAi.verify();
    }

    private ResultActions send(String body) throws Exception {
        return this.mvc.perform(post("/api/v1/idiomChain").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String responseWith(String answer) {
        return """
                {"status":"completed","output":[{"type":"message","role":"assistant",
                  "content":[{"type":"output_text","text":"%s"}]}]}
                """.formatted(answer);
    }
}
