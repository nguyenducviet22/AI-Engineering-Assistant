package com.aiassistant.embedding;

import java.util.List;

public record EmbeddingVector(String model, int dimensions, List<Double> values) {
}
