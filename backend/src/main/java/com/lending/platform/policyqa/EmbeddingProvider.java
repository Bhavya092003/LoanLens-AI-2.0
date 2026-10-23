package com.lending.platform.policyqa;

import java.util.List;

public interface EmbeddingProvider {
    List<Double> embed(String text);

    default List<List<Double>> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
