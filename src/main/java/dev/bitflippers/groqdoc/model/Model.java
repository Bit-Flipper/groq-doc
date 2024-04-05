package dev.bitflippers.groqdoc.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Model {
    LLAMA2("llama2-70b-4096"),
    MIXTRAL("mixtral-8x7b-32768"),
    GEMMA("Gemma-7b-it");

    private final String model;

    Model(String model) {
        this.model = model;
    }

    @JsonValue
    public String getModel() {
        return model;
    }
}
