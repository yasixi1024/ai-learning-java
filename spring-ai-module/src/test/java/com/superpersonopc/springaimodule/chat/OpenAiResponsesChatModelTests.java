package com.superpersonopc.springaimodule.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenAiResponsesChatModelTests {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        this.builder = RestClient.builder();
        this.server = MockRestServiceServer.bindTo(this.builder).build();
    }

    @Test
    void chatEndpointUsesResponsesProtocolAndPreservesReplyContract() throws Exception {
        OpenAiResponsesChatModel model = model("https://proxy.example.com/openai/", "/v1/responses");
        this.server.expect(requestTo("https://proxy.example.com/openai/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"model":"gpt-4o-mini","input":[{"role":"user","content":"Hello"}],
                         "temperature":0.7}
                        """, true))
                .andRespond(withSuccess("""
                        {"id":"resp_test","status":"completed","usage":{"output_tokens":2},"output":[
                          {"type":"reasoning","summary":[]},
                          {"type":"message","role":"assistant","content":[
                            {"type":"output_text","text":"Hello ","annotations":[]},
                            {"type":"output_text","text":"world"}
                          ]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        MockMvcBuilders.standaloneSetup(new ChatController(new AiChatService(model))).build()
                .perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hello world"));
        this.server.verify();
    }

    @Test
    void supportsOfficialBaseUrlAndReturnsRefusalText() {
        OpenAiResponsesChatModel model = model("https://api.openai.com", "/v1/responses");
        this.server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","role":"assistant",
                         "content":[{"type":"refusal","refusal":"Cannot assist with that request."}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("Cannot assist with that request.", model.call("Hello"));
        this.server.verify();
    }

    @Test
    void supportsCustomResponsesPath() {
        OpenAiResponsesChatModel model = model("https://proxy.example.com", "/openai/v1/responses");
        this.server.expect(requestTo("https://proxy.example.com/openai/v1/responses"))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","role":"assistant",
                         "content":[{"type":"output_text","text":"OK"}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertEquals("OK", model.call("Hello"));
        this.server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "incomplete", "in_progress"})
    void rejectsUnfinishedResponsesInsteadOfReturningPartialText(String responseStatus) {
        OpenAiResponsesChatModel model = model("https://api.openai.com", "/v1/responses");
        this.server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"" + responseStatus + "\",\"output\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> model.call("Hello"));
        this.server.verify();
    }

    @Test
    void rejectsCompletedResponsesWithoutAssistantText() {
        OpenAiResponsesChatModel model = model("https://api.openai.com", "/v1/responses");
        this.server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> model.call("Hello"));
        this.server.verify();
    }

    @Test
    void propagatesHttpErrors() {
        OpenAiResponsesChatModel model = model("https://api.openai.com", "/v1/responses");
        this.server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> model.call("Hello"));
        this.server.verify();
    }

    private OpenAiResponsesChatModel model(String baseUrl, String path) {
        return new OpenAiResponsesChatModel(this.builder, "test-key", baseUrl, path, "gpt-4o-mini", 0.7);
    }
}
