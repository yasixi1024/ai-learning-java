package com.superpersonopc.springaimodule.idiom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class IdiomDictionary {

    private final Map<String, Idiom> byWord;
    private final Map<String, List<Idiom>> byFirstPinyin;
    private final List<Idiom> playableStarts;

    IdiomDictionary() throws IOException {
        List<Idiom> idioms;
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("idioms/idioms.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            idioms = reader.lines().map(line -> {
                String[] fields = line.split("\t");
                return new Idiom(fields[0], fields[1], fields[2]);
            }).toList();
        }
        this.byWord = idioms.stream().collect(Collectors.toUnmodifiableMap(Idiom::word, Function.identity()));
        this.byFirstPinyin = idioms.stream().collect(Collectors.groupingBy(Idiom::firstPinyin));
        this.playableStarts = idioms.stream()
                .filter(idiom -> this.byFirstPinyin.containsKey(idiom.lastPinyin()))
                .toList();
    }

    Idiom find(String word) {
        return this.byWord.get(word);
    }

    List<Idiom> startingWith(String pinyin) {
        return this.byFirstPinyin.getOrDefault(pinyin, List.of());
    }

    List<Idiom> playableStarts() {
        return this.playableStarts;
    }

    record Idiom(String word, String firstPinyin, String lastPinyin) {
    }
}
