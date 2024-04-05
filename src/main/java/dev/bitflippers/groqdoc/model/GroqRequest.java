package dev.bitflippers.groqdoc.model;

import java.util.List;

public record GroqRequest(List<Message> messages, Model model) {}
