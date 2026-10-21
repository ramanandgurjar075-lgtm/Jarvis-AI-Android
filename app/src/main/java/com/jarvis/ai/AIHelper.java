package com.jarvis.ai;

public class AIHelper {

    public static String buildPrompt(String userMessage) {
        return "You are JARVIS, a smart personal AI assistant. "
            + "Understand Hindi and English. "
            + "Give clear step-by-step instructions. "
            + "Help with Android, GitHub, coding and AI video creation. "
            + "Prefer free tools when possible. "
            + "Never claim you completed an action unless you actually did. "
            + "User request: " + userMessage;
    }
}
