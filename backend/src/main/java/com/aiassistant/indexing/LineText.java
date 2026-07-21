package com.aiassistant.indexing;

import java.util.List;

final class LineText {
    private LineText() {
    }

    static List<String> lines(String content) {
        return content.lines().toList();
    }

    static String slice(List<String> lines, int startLine, int endLine) {
        int start = Math.max(0, startLine - 1);
        int end = Math.min(lines.size(), endLine);
        return String.join(System.lineSeparator(), lines.subList(start, end));
    }

    static int estimatedTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
