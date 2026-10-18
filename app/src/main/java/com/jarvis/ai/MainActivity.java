package com.jarvis.ai;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.graphics.Color;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private TextView status;
    private static final int VOICE_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(30, 30, 30, 30);
        layout.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S");
        title.setTextSize(35);
        title.setTextColor(Color.CYAN);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        status = new TextView(this);
        status.setText("Hello Sir! I am JARVIS.");
        status.setTextSize(20);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        layout.addView(status);

        Button button = new Button(this);
        button.setText("🎙️ TALK TO JARVIS");
        layout.addView(button);

        setContentView(layout);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });

        button.setOnClickListener(v -> startListening());
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
            speak("Speech recognition is not available.");
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
                processCommand(results.get(0).toLowerCase());
            }
        }
    }

    private void processCommand(String command) {

        status.setText("You: " + command);

        if (command.contains("youtube") ||
            command.contains("यूट्यूब")) {

            speak("Opening YouTube Sir");

            openWebsite("https://youtube.com");

        } else if (command.contains("google") ||
                   command.contains("गूगल")) {

            speak("Opening Google Sir");

            openWebsite("https://google.com");

        } else if (command.contains("hello") ||
                   command.contains("हेलो") ||
                   command.contains("नमस्ते")) {

            speak("Hello Sir! How can I help you?");

        } else if (command.contains("video") ||
                   command.contains("वीडियो")) {

            speak("Sir, opening AI video generator.");

            openWebsite("https://huggingface.co/spaces");

        } else {

            speak("Sir, I am learning. Please try another command.");
        }
    }

    private void openWebsite(String url) {
        Intent intent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        );
        startActivity(intent);
    }

    private void speak(String text) {
        status.setText("JARVIS: " + text);

        if (tts != null) {
            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis"
            );
        }
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
