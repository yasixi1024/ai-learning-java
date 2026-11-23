package com.superpersonopc.springaimodule.idiom;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/idiomChain")
public class IdiomChainController {

    private final IdiomChainService idiomChainService;

    public IdiomChainController(IdiomChainService idiomChainService) {
        this.idiomChainService = idiomChainService;
    }

    @PostMapping
    public IdiomChainResponse play(@RequestBody IdiomChainRequest request) {
        String message = switch (request.action()) {
            case "init" -> this.idiomChainService.init();
            case "relay" -> this.idiomChainService.relay(
                    request.message() == null ? null : request.message().textValue());
            case null, default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "action must be init or relay");
        };
        return new IdiomChainResponse(message);
    }

    public record IdiomChainRequest(String action, JsonNode message) {
    }

    public record IdiomChainResponse(String message) {
    }
}
