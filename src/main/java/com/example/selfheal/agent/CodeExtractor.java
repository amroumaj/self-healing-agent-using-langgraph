package com.example.selfheal.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeExtractor {

    private static final Pattern FENCED_CODE_BLOCK = Pattern.compile("```[a-zA-Z0-9_-]*\\R?([\\s\\S]*?)```");

    public static String extractCode(String llmResponse) {
        Matcher matcher = FENCED_CODE_BLOCK.matcher(llmResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return llmResponse.trim();
    }
}