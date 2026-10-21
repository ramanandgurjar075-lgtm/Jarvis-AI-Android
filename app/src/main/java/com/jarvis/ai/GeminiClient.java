package com.jarvis.ai;

public class GeminiClient {

    public static String getPrompt(String message) {
        return AIHelper.buildPrompt(message);
    }

}
