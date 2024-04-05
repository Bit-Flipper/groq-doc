package dev.bitflippers.groqdoc.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record GroqResponse(String id, Model model, List<Choice> choices) {
    public record Choice(int index, Message message, @JsonAlias("finish_reason") String finishReason) {}
}
