package com.jarvis.ai;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView status;
    private EditText chatInput;
    private TextToSpeech tts;

    private static final int VOICE_REQUEST = 100;
    private static final String MODEL = "gemini-3.1-flash-lite";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);
        layout.setBackgroundColor(Color.BLACK);
        layout.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S");
        title.setTextSize(36);
        title.setTextColor(Color.CYAN);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("Hello Sir! JARVIS V2 is ready.");
        status.setTextSize(19);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("Gemini API Key (private setup)");
        keyLabel.setTextColor(Color.CYAN);
        layout.addView(keyLabel);

        EditText keyInput = new EditText(this);
        keyInput.setHint("Enter Gemini API Key");
        keyInput.setHintTextColor(Color.GRAY);
        keyInput.setTextColor(Color.WHITE);
        keyInput.setSingleLine(true);
        keyInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        layout.addView(keyInput);

        Button saveKey = new Button(this);
        saveKey.setText("SAVE API KEY");
        layout.addView(saveKey);

        SharedPreferences prefs =
            getSharedPreferences("jarvis_private", MODE_PRIVATE);

        saveKey.setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();

            if (key.isEmpty()) {
                status.setText("Please enter your API key.");
                return;
            }

            prefs.edit().putString("gemini_key", key).apply();
            keyInput.setText("");
            status.setText("API key saved. Ask me anything!");
        });

        chatInput = new EditText(this);
        chatInput.setHint("Ask JARVIS anything...");
        chatInput.setHintTextColor(Color.GRAY);
        chatInput.setTextColor(Color.WHITE);
        layout.addView(chatInput);

        Button sendButton = new Button(this);
        sendButton.setText("SEND MESSAGE");
        layout.addView(sendButton);

        Button voiceButton = new Button(this);
        voiceButton.setText("🎙 TALK TO JARVIS");
        layout.addView(voiceButton);

        setContentView(layout);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = chatInput.getText().toString().trim();

            if (!message.isEmpty()) {
                chatInput.setText("");
                askGemini(message);
            }
        });

        voiceButton.setOnClickListener(v -> startListening());
    }

    private void startListening() {
        Intent intent = new Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "hi-IN"
        );

        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception e) {
            status.setText("Voice recognition unavailable.");
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_REQUEST &&
            resultCode == RESULT_OK &&
            data != null) {

            ArrayList<String> results =
                data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                );

            if (results != null && !results.isEmpty()) {
                askGemini(results.get(0));
            }
        }
    }

    private void askGemini(String message) {
        SharedPreferences prefs =
            getSharedPreferences("jarvis_private", MODE_PRIVATE);

        String apiKey = prefs.getString("gemini_key", "");

        if (apiKey.isEmpty()) {
            status.setText("First save your Gemini API key.");
            return;
        }

        status.setText("You: " + message + "\n\nJARVIS is thinking...");

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent"
                );

                connection =
                    (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                );
                connection.setRequestProperty(
                    "x-goog-api-key",
                    apiKey
                );

                connection.setConnectTimeout(20000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);

                JSONObject textPart = new JSONObject();
                textPart.put(
                    "text",
                    "You are JARVIS, a helpful personal AI assistant. "
                    + "Reply in the user's language, Hindi or English. "
                    + "Give practical step-by-step help. "
                    + "Prefer free tools when possible. "
                    + "Never claim an action was completed unless it was. "
                    + "User: " + message
                );

                JSONArray parts = new JSONArray();
                parts.put(textPart);

                JSONObject content = new JSONObject();
                content.put("parts", parts);

                JSONArray contents = new JSONArray();
                contents.put(content);

                JSONObject body = new JSONObject();
                body.put("contents", contents);

                byte[] requestData =
                    body.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream out =
                         connection.getOutputStream()) {
                    out.write(requestData);
                }

                int responseCode = connection.getResponseCode();

                InputStream stream =
                    responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

                if (stream == null) {
                    throw new Exception("No server response");
                }

                ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

                try (InputStream input = stream) {
                    byte[] bytes = new byte[4096];
                    int count;

                    while ((count = input.read(bytes)) != -1) {
                        buffer.write(bytes, 0, count);
                    }
                }

                String response = new String(
                    buffer.toByteArray(),
                    StandardCharsets.UTF_8
                );

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                        "Gemini HTTP " + responseCode + ": "
                        + response.substring(
                            0, Math.min(response.length(), 250)
                        )
                    );
                }

                JSONObject json = new JSONObject(response);

                String reply = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

                runOnUiThread(() -> {
                    status.setText("JARVIS:\n" + reply);

                    if (tts != null) {
                        tts.speak(
                            reply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "jarvis_reply"
                        );
                    }
                });

            } catch (Exception e) {
                String error = e.getMessage();

                runOnUiThread(() ->
                    status.setText("Connection error: " + error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
    }
