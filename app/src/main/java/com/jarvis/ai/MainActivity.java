package com.jarvis.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String MODEL = "gemini-3.1-flash-lite";
    private static final int VOICE_REQUEST = 101;

    private final int BG = Color.rgb(5, 10, 20);
    private final int CARD = Color.rgb(18, 28, 45);
    private final int CYAN = Color.rgb(0, 220, 255);
    private final int WHITE = Color.WHITE;
    private final int GRAY = Color.rgb(155, 170, 190);

    private SharedPreferences prefs;

    private LinearLayout root;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView sendButton;
    private TextView modeButton;

    private JSONArray history = new JSONArray();
    private JSONArray workspaces = new JSONArray();

    private String workspaceId = "default";
    private String workspaceName = "My Chat";
    private String mode = "FAST";

    private boolean busy = false;
    private boolean voiceReply = false;

    private Thread requestThread;
    private HttpURLConnection activeConnection;
    private TextToSpeech tts;

    private int requestToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(
            "jarvis_private",
            MODE_PRIVATE
        );

        workspaceId = prefs.getString(
            "active_workspace",
            "default"
        );

        mode = prefs.getString("ai_mode", "FAST");

        loadWorkspaces();
        loadHistory();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("hi", "IN"));
            }
        });

        showHome();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String value) {
        TextView b = label(value, 16, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(20, 14, 20, 14);
        b.setBackground(shape(CARD, 25));
        return b;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void setupRoot() {
        root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void showHome() {
        setupRoot();

        LinearLayout header = horizontal();
        header.setPadding(12, 15, 12, 10);

        TextView menu = button("☰");
        header.addView(menu);

        menu.setOnClickListener(v -> showMainMenu());

        TextView title = label("  J.A.R.V.I.S", 23, CYAN);
        title.setTypeface(null, Typeface.BOLD);

        header.addView(
            title,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView newChat = button("＋");
        header.addView(newChat);

        newChat.setOnClickListener(v -> newWorkspace());

        root.addView(header);

        LinearLayout info = horizontal();
        info.setPadding(16, 0, 16, 12);

        TextView project = label(
            "📁 " + workspaceName,
            14,
            WHITE
        );

        project.setSingleLine(true);
        project.setEllipsize(
            android.text.TextUtils.TruncateAt.END
        );

        info.addView(
            project,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        project.setOnClickListener(v -> showWorkspaces());

        modeButton = button("⚡ " + mode);
        modeButton.setTextColor(CYAN);

        info.addView(modeButton);

        modeButton.setOnClickListener(v -> selectMode());

        root.addView(info);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        messages = vertical();
        messages.setPadding(16, 15, 16, 30);

        scroll.addView(messages);

        root.addView(
            scroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        if (history.length() == 0) {
            showWelcome();
        } else {
            renderHistory();
        }

        LinearLayout composer = vertical();
        composer.setPadding(12, 10, 12, 15);
        composer.setBackground(shape(CARD, 25));

        input = new EditText(this);
        input.setHint("Message JARVIS...");
        input.setHintTextColor(GRAY);
        input.setTextColor(WHITE);
        input.setTextSize(16);
        input.setMinLines(1);
        input.setMaxLines(4);

        input.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        composer.addView(input);

        LinearLayout controls = horizontal();

        TextView attach = button("＋");
        controls.addView(attach);
        attach.setOnClickListener(v -> showAttachments());

        TextView spacer = label("", 12, WHITE);

        controls.addView(
            spacer,
            new LinearLayout.LayoutParams(0, 1, 1)
        );

        TextView mic = button("🎙");
        controls.addView(mic);

        mic.setOnClickListener(v -> startVoice());

        sendButton = button("➤");
        sendButton.setTextColor(CYAN);

        controls.addView(sendButton);

        sendButton.setOnClickListener(v -> {
            if (busy) {
                stopRequest();
            } else {
                String msg = input.getText().toString().trim();

                if (!msg.isEmpty()) {
                    input.setText("");
                    voiceReply = false;
                    sendMessage(msg);
                }
            }
        });

        composer.addView(controls);
        root.addView(composer);
    }

    private void showWelcome() {
        LinearLayout welcome = vertical();
        welcome.setGravity(Gravity.CENTER);
        welcome.setPadding(10, 65, 10, 35);

        TextView core = label("⬡", 110, CYAN);
        core.setGravity(Gravity.CENTER);
        welcome.addView(core);

        TextView title = label(
            "J.A.R.V.I.S  V3",
            28,
            CYAN
        );

        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        welcome.addView(title);

        TextView subtitle = label(
            "Your Personal AI Assistant\nWhat shall we create today?",
            17,
            WHITE
        );

        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 20, 0, 25);

        welcome.addView(subtitle);
        messages.addView(welcome);

        suggestion("🎬 Create AI Video");
        suggestion("🎨 Create Images");
        suggestion("📱 Create Android App");
        suggestion("🎮 Create Game");
    }

    private void suggestion(String text) {
        TextView b = button(text);
        b.setGravity(Gravity.LEFT);

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, -2);

        p.setMargins(0, 5, 0, 5);
        messages.addView(b, p);

        b.setOnClickListener(v -> {
            String prompt = text.substring(
                text.indexOf(" ") + 1
            );

            voiceReply = false;
            sendMessage(prompt);
        });
    }

    private void addBubble(
        String value,
        boolean isUser,
        int historyIndex
    ) {
        LinearLayout holder = horizontal();

        holder.setGravity(
            isUser ? Gravity.RIGHT : Gravity.LEFT
        );

        TextView bubble = label(value, 16, WHITE);

        bubble.setPadding(17, 14, 17, 14);
        bubble.setTextIsSelectable(true);

        bubble.setBackground(
            shape(
                isUser
                    ? Color.rgb(0, 80, 115)
                    : CARD,
                22
            )
        );

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-2, -2);

        p.setMargins(0, 7, 0, 7);

        holder.addView(bubble, p);
        messages.addView(holder);

        if (isUser && historyIndex >= 0) {
            bubble.setOnLongClickListener(v -> {
                showEditMessage(historyIndex);
                return true;
            });
        }

        scroll.post(() ->
            scroll.fullScroll(View.FOCUS_DOWN)
        );
    }

    private void renderHistory() {
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);

            if (item == null) continue;

            String role = item.optString("role");
            String value = getMessageText(item);

            addBubble(
                value,
                role.equals("user"),
                role.equals("user") ? i : -1
            );
        }
    }

    private String getMessageText(JSONObject item) {
        JSONArray parts = item.optJSONArray("parts");

        if (parts == null || parts.length() == 0) {
            return "";
        }

        JSONObject part = parts.optJSONObject(0);

        return part == null
            ? ""
            : part.optString("text");
    }

    private JSONObject createMessage(
        String role,
        String value
    ) {
        JSONObject item = new JSONObject();

        try {
            JSONObject part = new JSONObject();
            part.put("text", value);

            JSONArray parts = new JSONArray();
            parts.put(part);

            item.put("role", role);
            item.put("parts", parts);

        } catch (Exception ignored) {}

        return item;
    }

    private void addHistory(String role, String value) {
        history.put(createMessage(role, value));
        saveHistory();
    }

    private void saveHistory() {
        prefs.edit()
            .putString(
                "history_" + workspaceId,
                history.toString()
            )
            .apply();
    }

    private void loadHistory() {
        try {
            history = new JSONArray(
                prefs.getString(
                    "history_" + workspaceId,
                    "[]"
                )
            );
        } catch (Exception e) {
            history = new JSONArray();
        }
    }

    private void showEditMessage(int index) {
        if (busy) {
            Toast.makeText(
                this,
                "Stop the current response first",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        JSONObject item = history.optJSONObject(index);

        if (item == null) return;

        EditText editor = new EditText(this);
        editor.setText(getMessageText(item));
        editor.setMinLines(2);

        new AlertDialog.Builder(this)
            .setTitle("✏ Edit & Resend")
            .setView(editor)
            .setPositiveButton("Resend", (d, w) -> {
                String edited =
                    editor.getText().toString().trim();

                if (edited.isEmpty()) return;

                JSONArray updated = new JSONArray();

                for (int i = 0; i < index; i++) {
                    updated.put(
                        history.optJSONObject(i)
                    );
                }

                history = updated;
                saveHistory();

                showHome();
                sendMessage(edited);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sendMessage(String message) {
        if (busy) return;

        String key = prefs.getString(
            "gemini_key",
            ""
        );

        if (key.isEmpty()) {
            showSettings();

            Toast.makeText(
                this,
                "Save your Gemini API key first",
                Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (history.length() == 0) {
            messages.removeAllViews();
        }

        busy = true;
        sendButton.setText("■ STOP");

        final boolean shouldSpeak = voiceReply;
        voiceReply = false;

        int index = history.length();

        addHistory("user", message);
        addBubble(message, true, index);

        addBubble("◌ JARVIS is thinking...", false, -1);

        final int token = ++requestToken;
        final String selectedMode = mode;

        final String requestHistory = history.toString();

        requestThread = new Thread(() -> {
            String answer;

            try {
                answer = callGemini(
                    key,
                    selectedMode,
                    requestHistory
                );

            } catch (Exception e) {
                answer = "Connection problem: "
                    + e.getMessage();
            }

            final String result = answer;

            runOnUiThread(() -> {
                if (token != requestToken) return;

                busy = false;
                sendButton.setText("➤");

                if (messages.getChildCount() > 0) {
                    messages.removeViewAt(
                        messages.getChildCount() - 1
                    );
                }

                boolean success =
                    !result.startsWith("Connection problem:") &&
                    !result.startsWith("Gemini error:");

                addBubble(result, false, -1);

                if (success) {
                    addHistory("model", result);

                    if (shouldSpeak && tts != null) {
                        String spoken = result
                            .replace("**", "")
                            .replace("###", "")
                            .replace("*", "");

                        if (spoken.length() > 350) {
                            spoken = spoken.substring(0, 350);
                        }

                        tts.speak(
                            spoken,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "jarvis"
                        );
                    }
                }
            });
        });

        requestThread.start();
    }

    private void stopRequest() {
        if (!busy) return;

        ++requestToken;
        busy = false;

        if (activeConnection != null) {
            activeConnection.disconnect();
            activeConnection = null;
        }

        if (requestThread != null) {
            requestThread.interrupt();
        }

        if (tts != null) {
            tts.stop();
        }

        sendButton.setText("➤");

        if (messages.getChildCount() > 0) {
            messages.removeViewAt(
                messages.getChildCount() - 1
            );
        }

        addBubble("■ Response stopped.", false, -1);
    }

    private String callGemini(
        String key,
        String selectedMode,
        String requestHistory
    ) throws Exception {

        URL url = new URL(
            "https://generativelanguage.googleapis.com/"
            + "v1beta/models/"
            + MODEL
            + ":generateContent"
        );

        HttpURLConnection conn =
            (HttpURLConnection) url.openConnection();

        activeConnection = conn;

        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            conn.setDoOutput(true);

            conn.setRequestProperty(
                "Content-Type",
                "application/json"
            );

            conn.setRequestProperty(
                "x-goog-api-key",
                key
            );

            String instruction =
                "You are JARVIS, an advanced personal AI assistant. "
                + "Understand Hindi, Hinglish and English. "
                + "Reply naturally in the user's language. "
                + "Be accurate, practical and conversational. "
                + "Do not flatter the user or blindly agree. "
                + "Politely correct mistakes and explain why. "
                + "Keep normal replies concise. "
                + "Give detailed steps when requested. "
                + "Remember the conversation context provided. "
                + "Help with coding, Android apps, games, "
                + "image prompts, video scripts and planning. "
                + "Never pretend that you created files, "
                + "images, videos or apps when you did not. "
                + "Prefer free tools where practical. ";

            if (selectedMode.equals("DEEP")) {
                instruction +=
                    "Think carefully about complex problems. "
                    + "Compare alternatives and explain tradeoffs. ";
            }

            if (selectedMode.equals("TRUTH")) {
                instruction +=
                    "Prioritize factual accuracy. "
                    + "Check reasoning for contradictions. "
                    + "Clearly distinguish verified facts "
                    + "from assumptions and uncertainty. "
                    + "Do not invent sources or claim "
                    + "to have verified facts externally. ";
            }

            JSONObject body = new JSONObject();

            JSONObject system = new JSONObject();
            JSONObject systemPart = new JSONObject();

            systemPart.put("text", instruction);

            JSONArray systemParts = new JSONArray();
            systemParts.put(systemPart);

            system.put("parts", systemParts);

            body.put("systemInstruction", system);

            JSONArray allHistory =
                new JSONArray(requestHistory);

            JSONArray recent = new JSONArray();

            int start = Math.max(
                0,
                allHistory.length() - 30
            );

            for (int i = start;
                 i < allHistory.length();
                 i++) {

                JSONObject item =
                    allHistory.optJSONObject(i);

                if (item != null) {
                    recent.put(item);
                }
            }

            body.put("contents", recent);

            if (selectedMode.equals("FAST")) {
                JSONObject config = new JSONObject();
                JSONObject thinking = new JSONObject();

                thinking.put(
                    "thinkingLevel",
                    "minimal"
                );

                config.put("thinkingConfig", thinking);
                body.put("generationConfig", config);
            }

            byte[] data = body.toString()
                .getBytes(StandardCharsets.UTF_8);

            try (OutputStream out =
                     conn.getOutputStream()) {
                out.write(data);
            }

            int status = conn.getResponseCode();

            InputStream stream =
                status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (stream == null) {
                return "Gemini error: HTTP " + status;
            }

            ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int count;

            try (InputStream in = stream) {
                while ((count = in.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                }
            }

            String response = new String(
                bytes.toByteArray(),
                StandardCharsets.UTF_8
            );

            JSONObject json = new JSONObject(response);

            if (status < 200 || status >= 300) {
                JSONObject error =
                    json.optJSONObject("error");

                String errorText = error == null
                    ? response
                    : error.optString("message");

                return "Gemini error: HTTP "
                    + status
                    + "\n"
                    + errorText;
            }

            JSONArray candidates =
                json.optJSONArray("candidates");

            if (candidates == null ||
                candidates.length() == 0) {

                return "Gemini error: No response.";
            }

            JSONObject candidate =
                candidates.optJSONObject(0);

            JSONObject content =
                candidate.optJSONObject("content");

            if (content == null) {
                return "Gemini error: Empty content.";
            }

            JSONArray parts =
                content.optJSONArray("parts");

            if (parts == null) {
                return "Gemini error: Empty answer.";
            }

            StringBuilder answer = new StringBuilder();

            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);

                if (part == null) continue;

                String text = part.optString("text");

                if (!text.isEmpty()) {
                    if (answer.length() > 0) {
                        answer.append("\n");
                    }

                    answer.append(text);
                }
            }

            return answer.length() > 0
                ? answer.toString()
                : "Gemini error: No text received.";

        } finally {
            conn.disconnect();

            if (activeConnection == conn) {
                activeConnection = null;
            }
        }
    }

    private void loadWorkspaces() {
        try {
            workspaces = new JSONArray(
                prefs.getString(
                    "workspaces",
                    "[]"
                )
            );
        } catch (Exception e) {
            workspaces = new JSONArray();
        }

        if (workspaces.length() == 0) {
            JSONObject first = new JSONObject();

            try {
                first.put("id", "default");
                first.put("name", "My Chat");
                workspaces.put(first);

            } catch (Exception ignored) {}

            saveWorkspaces();
        }

        boolean found = false;

        for (int i = 0; i < workspaces.length(); i++) {
            JSONObject item =
                workspaces.optJSONObject(i);

            if (item == null) continue;

            if (workspaceId.equals(
                item.optString("id")
            )) {
                workspaceName =
                    item.optString("name", "My Chat");

                found = true;
                break;
            }
        }

        if (!found) {
            workspaceId = "default";
            workspaceName = "My Chat";
        }
    }

    private void saveWorkspaces() {
        prefs.edit()
            .putString(
                "workspaces",
                workspaces.toString()
            )
            .apply();
    }

    private void newWorkspace() {
        if (busy) {
            Toast.makeText(
                this,
                "Stop the current response first",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setHint("Workspace name");

        new AlertDialog.Builder(this)
            .setTitle("＋ New Workspace")
            .setView(nameInput)
            .setPositiveButton("Create", (d, w) -> {
                String name =
                    nameInput.getText().toString().trim();

                if (name.isEmpty()) {
                    name = "New Project";
                }

                workspaceId =
                    "ws_" + System.currentTimeMillis();

                workspaceName = name;

                JSONObject item = new JSONObject();

                try {
                    item.put("id", workspaceId);
                    item.put("name", workspaceName);
                    workspaces.put(item);

                } catch (Exception ignored) {}

                saveWorkspaces();

                prefs.edit()
                    .putString(
                        "active_workspace",
                        workspaceId
                    )
                    .apply();

                history = new JSONArray();
                saveHistory();

                showHome();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showWorkspaces() {
        if (busy) {
            Toast.makeText(
                this,
                "Stop the current response first",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String[] names =
            new String[workspaces.length()];

        for (int i = 0; i < names.length; i++) {
            JSONObject item =
                workspaces.optJSONObject(i);

            names[i] = item == null
                ? "Workspace"
                : item.optString("name");
        }

        new AlertDialog.Builder(this)
            .setTitle("📁 My Workspaces")
            .setItems(names, (d, which) -> {
                JSONObject item =
                    workspaces.optJSONObject(which);

                if (item == null) return;

                workspaceId = item.optString("id");
                workspaceName = item.optString("name");

                prefs.edit()
                    .putString(
                        "active_workspace",
                        workspaceId
                    )
                    .apply();

                loadHistory();
                showHome();
            })
            .setPositiveButton(
                "＋ New Workspace",
                (d, w) -> newWorkspace()
            )
            .setNegativeButton("Close", null)
            .show();
    }

    private void selectMode() {
        String[] options = {
            "⚡ FAST — Quick answers",
            "🧠 DEEP — Detailed reasoning",
            "🔍 TRUTH — Accuracy focused"
        };

        new AlertDialog.Builder(this)
            .setTitle("Choose AI Mode")
            .setItems(options, (d, which) -> {
                if (which == 0) mode = "FAST";
                if (which == 1) mode = "DEEP";
                if (which == 2) mode = "TRUTH";

                prefs.edit()
                    .putString("ai_mode", mode)
                    .apply();

                modeButton.setText("◉ " + mode);
            })
            .show();
    }

    private void showMainMenu() {
        String[] options = {
            "💬 Chat",
            "📁 My Workspaces",
            "🎨 AI Image Studio",
            "🎬 AI Video Studio",
            "📱 Create App",
            "🎮 Create Game",
            "👁 Live Screen",
            "🔗 Connected Apps",
            "⚙ Settings"
        };

        new AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S CONTROL")
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0:
                        showHome();
                        break;

                    case 1:
                        showWorkspaces();
                        break;

                    case 2:
                        showFeature(
                            "AI Image Studio",
                            "Image generation and Character Lock"
                        );
                        break;

                    case 3:
                        showFeature(
                            "AI Video Studio",
                            "Video creation, dubbing and editing"
                        );
                        break;

                    case 4:
                        showFeature(
                            "Create App",
                            "GitHub AI App Builder"
                        );
                        break;

                    case 5:
                        showFeature(
                            "Create Game",
                            "AI Game Development Studio"
                        );
                        break;

                    case 6:
                        showFeature(
                            "Live Screen",
                            "Screen sharing and AI guidance"
                        );
                        break;

                    case 7:
                        showFeature(
                            "Connected Apps",
                            "Connect supported AI services"
                        );
                        break;

                    case 8:
                        showSettings();
                        break;
                }
            })
            .show();
    }

    private void showFeature(
        String title,
        String description
    ) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                description
                + "\n\nThis feature is planned "
                + "for a future JARVIS update. "
                + "It is not active yet."
            )
            .setPositiveButton("OK", null)
            .show();
    }

    private void showAttachments() {
        String[] options = {
            "📷 Camera",
            "🖼 Photos",
            "📄 Files / PDF",
            "🎬 Video",
            "🎵 Audio"
        };

        new AlertDialog.Builder(this)
            .setTitle("Attach File")
            .setItems(options, (d, which) -> {
                Toast.makeText(
                    this,
                    "File understanding is coming "
                    + "in a future update.",
                    Toast.LENGTH_LONG
                ).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showSettings() {
        LinearLayout panel = vertical();
        panel.setPadding(25, 10, 25, 10);

        TextView note = label(
            "Enter your private Gemini API key. "
            + "Do not share it in screenshots or GitHub.",
            14,
            Color.DKGRAY
        );

        panel.addView(note);

        EditText keyInput = new EditText(this);
        keyInput.setHint("Gemini API Key");
        keyInput.setSingleLine(true);

        keyInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        panel.addView(keyInput);

        new AlertDialog.Builder(this)
            .setTitle("⚙ JARVIS Settings")
            .setView(panel)
            .setPositiveButton("Save", (d, w) -> {
                String key =
                    keyInput.getText().toString().trim();

                if (!key.isEmpty()) {
                    prefs.edit()
                        .putString("gemini_key", key)
                        .apply();

                    Toast.makeText(
                        this,
                        "API key saved",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startVoice() {
        if (busy) {
            Toast.makeText(
                this,
                "Stop current response first",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
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

            intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to JARVIS"
            );

            startActivityForResult(
                intent,
                VOICE_REQUEST
            );

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Voice input is unavailable",
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if (requestCode == VOICE_REQUEST &&
            resultCode == RESULT_OK &&
            data != null) {

            ArrayList<String> results =
                data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                );

            if (results != null &&
                !results.isEmpty()) {

                voiceReply = true;
                sendMessage(results.get(0));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (activeConnection != null) {
            activeConnection.disconnect();
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
