package com.jarvis.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

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

    private static final String MODEL =
        "gemini-3.1-flash-lite";

    private static final int VOICE_REQUEST = 101;
    private static final int IMAGE_REQUEST = 102;

    private static final int MAX_IMAGE_BYTES =
        8 * 1024 * 1024;

    private final int BG = Color.rgb(5, 9, 18);
    private final int CARD = Color.rgb(20, 29, 45);
    private final int CYAN = Color.rgb(0, 215, 255);
    private final int WHITE = Color.WHITE;
    private final int GRAY = Color.rgb(150, 165, 185);

    private SharedPreferences prefs;

    private FrameLayout screen;
    private LinearLayout root;
    private LinearLayout messages;
    private ScrollView chatScroll;

    private EditText input;
    private TextView sendButton;
    private TextView imageIndicator;

    private LinearLayout sidebar;
    private View sidebarShade;

    private JSONArray history = new JSONArray();
    private JSONArray workspaces = new JSONArray();

    private String workspaceId = "default";
    private String workspaceName = "My Chat";
    private String mode = "FAST";

    private String pendingImageBase64 = null;
    private String pendingImageMime = null;
    private String pendingImageName = null;

    private boolean busy = false;
    private boolean voiceReply = false;
    private boolean sidebarOpen = false;

    private int requestToken = 0;

    private Thread requestThread;
    private HttpURLConnection activeConnection;
    private TextToSpeech tts;

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

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

    private GradientDrawable shape(
        int color,
        int radius
    ) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(
        String value,
        int size,
        int color
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView icon(String value) {
        TextView b = text(value, 20, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(CARD, 24));
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

    private void toast(String value) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_SHORT
        ).show();
    }

    private void setupScreen() {
    screen = new FrameLayout(this);
    screen.setBackgroundColor(BG);

    root = vertical();

    screen.addView(
        root,
        new FrameLayout.LayoutParams(-1, -1)
    );

    setContentView(screen);

    // Android 16: detect keyboard and adjust chat layout
    screen.setOnApplyWindowInsetsListener((view, insets) -> {
        android.graphics.Insets ime = insets.getInsets(
            android.view.WindowInsets.Type.ime()
        );

        android.graphics.Insets bars = insets.getInsets(
            android.view.WindowInsets.Type.systemBars()
        );

        boolean keyboardOpen = insets.isVisible(
            android.view.WindowInsets.Type.ime()
        );

        int bottomPadding = keyboardOpen
            ? Math.max(0, ime.bottom - bars.bottom)
            : 0;

        root.setPadding(0, 0, 0, bottomPadding);

        return insets;
    });

    screen.requestApplyInsets();
    }

    private void showHome() {
        sidebarOpen = false;
        setupScreen();

        LinearLayout header = horizontal();

        header.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(8)
        );

        TextView menu = icon("☰");

        header.addView(
            menu,
            new LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        );

        menu.setOnClickListener(v -> openSidebar());

        TextView title = text(
            "J.A.R.V.I.S",
            19,
            CYAN
        );

        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams tp =
            new LinearLayout.LayoutParams(
                0,
                dp(44),
                1
            );

        tp.leftMargin = dp(14);
        header.addView(title, tp);

        TextView newChat = icon("＋");

        header.addView(
            newChat,
            new LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        );

        newChat.setOnClickListener(v -> newWorkspace());

        root.addView(header);

        LinearLayout info = horizontal();

        info.setPadding(
            dp(18),
            0,
            dp(18),
            dp(8)
        );

        TextView project = text(
            "📁 " + workspaceName,
            13,
            GRAY
        );

        project.setSingleLine(true);

        info.addView(
            project,
            new LinearLayout.LayoutParams(
                0,
                -2,
                1
            )
        );

        project.setOnClickListener(v -> openSidebar());

        TextView modeButton = text(
            "◉ " + mode + " ⌄",
            13,
            CYAN
        );

        modeButton.setPadding(
            dp(12),
            dp(8),
            dp(12),
            dp(8)
        );

        modeButton.setBackground(shape(CARD, 18));

        info.addView(modeButton);

        modeButton.setOnClickListener(v -> selectMode());

        root.addView(info);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);

        messages = vertical();

        messages.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(18)
        );

        chatScroll.addView(messages);

        root.addView(
            chatScroll,
            new LinearLayout.LayoutParams(
                -1,
                0,
                1
            )
        );

        if (history.length() == 0) {
            showWelcome();
        } else {
            renderHistory();
        }

        createComposer();
    }

    private void showWelcome() {
        LinearLayout welcome = vertical();

        welcome.setGravity(Gravity.CENTER);

        welcome.setPadding(
            dp(10),
            dp(100),
            dp(10),
            dp(30)
        );

        FrameLayout coreBox = new FrameLayout(this);

        LinearLayout.LayoutParams cp =
            new LinearLayout.LayoutParams(
                dp(112),
                dp(112)
            );

        cp.gravity = Gravity.CENTER;
        welcome.addView(coreBox, cp);

        View glow = new View(this);

        GradientDrawable glowShape =
            new GradientDrawable();

        glowShape.setShape(GradientDrawable.OVAL);

        glowShape.setColor(
            Color.rgb(3, 30, 48)
        );

        glowShape.setStroke(dp(3), CYAN);

        glow.setBackground(glowShape);

        coreBox.addView(
            glow,
            new FrameLayout.LayoutParams(
                dp(95),
                dp(95),
                Gravity.CENTER
            )
        );

        TextView core = text(
            "⬡",
            73,
            CYAN
        );

        core.setGravity(Gravity.CENTER);

        coreBox.addView(
            core,
            new FrameLayout.LayoutParams(-1, -1)
        );

        ObjectAnimator pulse =
            ObjectAnimator.ofFloat(
                glow,
                "alpha",
                0.35f,
                1f
            );

        pulse.setDuration(1700);

        pulse.setRepeatCount(
            ValueAnimator.INFINITE
        );

        pulse.setRepeatMode(
            ValueAnimator.REVERSE
        );

        pulse.start();

        coreBox.setOnClickListener(v -> startVoice());

        TextView heading = text(
            "How can I help you?",
            22,
            WHITE
        );

        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);

        heading.setPadding(
            0,
            dp(20),
            0,
            dp(8)
        );

        welcome.addView(heading);

        TextView subtitle = text(
            "Your Personal AI Assistant",
            14,
            GRAY
        );

        subtitle.setGravity(Gravity.CENTER);

        welcome.addView(subtitle);

        messages.addView(welcome);
    }

      private void createComposer() {
        LinearLayout outer = vertical();
        outer.setPadding(dp(8), dp(4), dp(8), dp(8));

        LinearLayout box = vertical();
        box.setBackground(shape(CARD, 28));
        box.setPadding(dp(8), dp(4), dp(8), dp(4));

        imageIndicator = text("", 12, CYAN);
        imageIndicator.setVisibility(
            pendingImageBase64 == null
                ? View.GONE
                : View.VISIBLE
        );

        if (pendingImageBase64 != null) {
            imageIndicator.setText(
                "🖼 " + pendingImageName + "  ✕"
            );
        }

        imageIndicator.setPadding(dp(8), dp(3), dp(8), dp(3));
        box.addView(imageIndicator);

        imageIndicator.setOnClickListener(v -> {
            clearPendingImage();
            updateImageIndicator();
        });

        LinearLayout row = horizontal();

        TextView attach = text("+", 28, WHITE);
        attach.setGravity(Gravity.CENTER);

        row.addView(
            attach,
            new LinearLayout.LayoutParams(dp(38), dp(44))
        );

        attach.setOnClickListener(v -> showAttachments());

        input = new EditText(this);
        input.setHint("Message JARVIS...");
        input.setHintTextColor(GRAY);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setBackgroundColor(Color.TRANSPARENT);

        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);

        input.setPadding(dp(5), 0, dp(5), 0);

        input.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        LinearLayout.LayoutParams inputParams =
            new LinearLayout.LayoutParams(0, -2, 1);

        row.addView(input, inputParams);

        TextView mic = text("🎙", 22, WHITE);
        mic.setGravity(Gravity.CENTER);

        row.addView(
            mic,
            new LinearLayout.LayoutParams(dp(42), dp(44))
        );

        mic.setOnClickListener(v -> startVoice());

        sendButton = text("➤", 25, CYAN);
        sendButton.setGravity(Gravity.CENTER);

        row.addView(
            sendButton,
            new LinearLayout.LayoutParams(dp(42), dp(44))
        );

        sendButton.setOnClickListener(v -> {
            if (busy) {
                stopRequest();
                return;
            }

            String message =
                input.getText().toString().trim();

            if (message.isEmpty() &&
                pendingImageBase64 != null) {
                message = "Describe and analyze this image.";
            }

            if (!message.isEmpty()) {
                voiceReply = false;
                input.setText("");
                sendMessage(message);
            }
        });

        box.addView(row);
        outer.addView(box);
        root.addView(outer);
      }  

    private void updateImageIndicator() {
        if (imageIndicator == null) return;

        if (pendingImageBase64 == null) {
            imageIndicator.setVisibility(View.GONE);
        } else {
            imageIndicator.setVisibility(View.VISIBLE);

            imageIndicator.setText(
                "🖼 " + pendingImageName + "  ✕"
            );
        }
    }

    private void clearPendingImage() {
        pendingImageBase64 = null;
        pendingImageMime = null;
        pendingImageName = null;
    }

    private JSONObject makeMessage(
        String role,
        String value
    ) {
        JSONObject item = new JSONObject();

        try {
            JSONArray parts = new JSONArray();

            JSONObject part = new JSONObject();
            part.put("text", value);

            parts.put(part);

            item.put("role", role);
            item.put("parts", parts);

        } catch (Exception ignored) {}

        return item;
    }

    private String messageText(JSONObject item) {
        if (item == null) return "";

        JSONArray parts = item.optJSONArray("parts");

        if (parts == null ||
            parts.length() == 0) {

            return "";
        }

        JSONObject part = parts.optJSONObject(0);

        return part == null
            ? ""
            : part.optString("text");
    }

    private void addBubble(
        String value,
        boolean user,
        int index
    ) {
        LinearLayout line = horizontal();

        line.setGravity(
            user ? Gravity.RIGHT : Gravity.LEFT
        );

        TextView bubble = text(
            value,
            16,
            WHITE
        );

        bubble.setTextIsSelectable(true);

        bubble.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(12)
        );

        bubble.setBackground(
            shape(
                user
                    ? Color.rgb(0, 75, 110)
                    : CARD,
                20
            )
        );

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(
                -2,
                -2
            );

        p.setMargins(
            0,
            dp(5),
            0,
            dp(5)
        );

        line.addView(bubble, p);
        messages.addView(line);

        if (user && index >= 0) {
            bubble.setOnLongClickListener(v -> {
                editMessage(index);
                return true;
            });
        }

        chatScroll.post(() ->
            chatScroll.fullScroll(View.FOCUS_DOWN)
        );
    }

    private void renderHistory() {
        for (int i = 0; i < history.length(); i++) {
            JSONObject item =
                history.optJSONObject(i);

            if (item == null) continue;

            boolean user =
                "user".equals(item.optString("role"));

            addBubble(
                messageText(item),
                user,
                user ? i : -1
            );
        }
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

    private void addHistory(
        String role,
        String value
    ) {
        history.put(makeMessage(role, value));
        saveHistory();
    }

    private void editMessage(int index) {
        if (busy) {
            toast("Stop the current response first");
            return;
        }

        JSONObject old =
            history.optJSONObject(index);

        if (old == null) return;

        EditText editor = new EditText(this);
        editor.setText(messageText(old));
        editor.setMinLines(2);

        new AlertDialog.Builder(this)
            .setTitle("Edit & Resend")
            .setView(editor)
            .setPositiveButton("Resend", (d, w) -> {
                String changed =
                    editor.getText()
                        .toString()
                        .trim();

                if (changed.isEmpty()) return;

                JSONArray revised = new JSONArray();

                for (int i = 0; i < index; i++) {
                    JSONObject item =
                        history.optJSONObject(i);

                    if (item != null) {
                        revised.put(item);
                    }
                }

                history = revised;
                saveHistory();

                showHome();
                sendMessage(changed);
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
            toast("Save Gemini API key first");
            showSettings();
            return;
        }

        final String imageData = pendingImageBase64;
        final String imageMime = pendingImageMime;

        String displayedMessage = message;

        if (imageData != null) {
            displayedMessage += "\n🖼 [Image attached]";
        }

        if (history.length() == 0) {
            messages.removeAllViews();
        }

        busy = true;

        sendButton.setText("■");

        final boolean shouldSpeak = voiceReply;
        voiceReply = false;

        final int index = history.length();

        addHistory("user", displayedMessage);

        addBubble(
            displayedMessage,
            true,
            index
        );

        clearPendingImage();
        updateImageIndicator();

        addBubble(
            "JARVIS is thinking...",
            false,
            -1
        );

        final int token = ++requestToken;

        final String selectedMode = mode;

        final String requestHistory = history.toString();

        requestThread = new Thread(() -> {
            String answer;

            try {
                answer = callGemini(
                    key,
                    selectedMode,
                    requestHistory,
                    imageData,
                    imageMime
                );
            } catch (Exception e) {
                answer =
                    "Connection problem: " + e.getMessage();
            }

            final String result = answer;

            runOnUiThread(() -> {
                if (token != requestToken) {
                    return;
                }

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
                            "jarvis_voice"
                        );
                    }
                }
            });
        });

        requestThread.start();
    }

    private void stopRequest() {
        if (!busy) return;

        requestToken++;
        busy = false;

        if (activeConnection != null) {
            activeConnection.disconnect();
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

        addBubble(
            "■ Response stopped.",
            false,
            -1
        );
    }

    private String callGemini(
        String key,
        String selectedMode,
        String requestHistory,
        String imageData,
        String imageMime
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
                "You are JARVIS, an advanced personal "
                + "AI assistant. "
                + "Understand Hindi, Hinglish and English. "
                + "Reply naturally in the user's language. "
                + "Be accurate, practical and helpful. "
                + "Never blindly agree or flatter. "
                + "Politely correct incorrect statements. "
                + "Keep normal replies concise. "
                + "Give detailed steps when needed. "
                + "Use earlier conversation as context. "
                + "Help with coding, games, Android apps, "
                + "stories, video creation and image prompts. "
                + "When an image is attached, analyze its "
                + "visible contents carefully. "
                + "Do not invent details that are not visible. "
                + "Never claim you generated an image, video "
                + "or app unless you actually did. "
                + "Prefer free tools where possible. ";

            if (selectedMode.equals("DEEP")) {
                instruction +=
                    "Consider complex problems carefully. "
                    + "Explain relevant limitations. ";
            }

            if (selectedMode.equals("TRUTH")) {
                instruction +=
                    "Prioritize accuracy. "
                    + "Check reasoning for contradictions. "
                    + "State uncertainty clearly. "
                    + "Never invent citations or claim "
                    + "external verification without tools. ";
            }

            JSONObject body = new JSONObject();

            JSONObject system = new JSONObject();
            JSONArray systemParts = new JSONArray();

            JSONObject systemPart = new JSONObject();
            systemPart.put("text", instruction);

            systemParts.put(systemPart);
            system.put("parts", systemParts);

            body.put("systemInstruction", system);

            JSONArray all =
                new JSONArray(requestHistory);

            JSONArray recent = new JSONArray();

            int start = Math.max(
                0,
                all.length() - 20
            );

            for (int i = start; i < all.length(); i++) {
                JSONObject item =
                    all.optJSONObject(i);

                if (item != null) {
                    recent.put(item);
                }
            }

            if (imageData != null &&
                recent.length() > 0) {

                JSONObject last =
                    recent.optJSONObject(
                        recent.length() - 1
                    );

                if (last != null) {
                    JSONArray parts =
                        last.optJSONArray("parts");

                    if (parts != null) {
                        JSONObject imagePart =
                            new JSONObject();

                        JSONObject inline =
                            new JSONObject();

                        inline.put(
                            "mime_type",
                            imageMime
                        );

                        inline.put(
                            "data",
                            imageData
                        );

                        imagePart.put(
                            "inline_data",
                            inline
                        );

                        parts.put(imagePart);
                    }
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

                config.put(
                    "thinkingConfig",
                    thinking
                );

                body.put(
                    "generationConfig",
                    config
                );
            }

            byte[] data =
                body.toString().getBytes(
                    StandardCharsets.UTF_8
                );

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

                String detail = error == null
                    ? response
                    : error.optString("message");

                return "Gemini error: HTTP "
                    + status
                    + "\n"
                    + detail;
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
                candidate == null
                    ? null
                    : candidate.optJSONObject("content");

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
                JSONObject p = parts.optJSONObject(i);

                if (p == null) continue;

                String value = p.optString("text");

                if (!value.isEmpty()) {
                    if (answer.length() > 0) {
                        answer.append("\n");
                    }

                    answer.append(value);
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

    private void showAttachments() {
        String[] options = {
            "🖼 Choose Photo / Screenshot",
            "📷 Camera — Later Update",
            "📄 PDF / Documents — Later Update",
            "🎬 Video — Later Update",
            "🎵 Audio File — Later Update"
        };

        new AlertDialog.Builder(this)
            .setTitle("Attach to JARVIS")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    chooseImage();
                } else {
                    toast(
                        "This file type is not supported yet"
                    );
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void chooseImage() {
        if (busy) {
            toast("Stop current response first");
            return;
        }

        Intent intent = new Intent(
            Intent.ACTION_OPEN_DOCUMENT
        );

        intent.setType("image/*");

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(
            intent,
            IMAGE_REQUEST
        );
    }

    private void handleSelectedImage(Uri uri) {
        if (uri == null) return;

        try {
            ContentResolver resolver =
                getContentResolver();

            String mime = resolver.getType(uri);

            if (mime == null ||
                !mime.startsWith("image/")) {

                toast("Please select an image");
                return;
            }

            String name = "Selected Image";

            Cursor cursor = resolver.query(
                uri,
                null,
                null,
                null,
                null
            );

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        );

                        if (index >= 0) {
                            name = cursor.getString(index);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }

            ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();

            try (InputStream in =
                     resolver.openInputStream(uri)) {

                if (in == null) {
                    toast("Cannot open image");
                    return;
                }

                byte[] buffer = new byte[8192];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count >
                        MAX_IMAGE_BYTES) {

                        toast(
                            "Image too large. Maximum 8 MB."
                        );

                        return;
                    }

                    bytes.write(buffer, 0, count);
                }
            }

            if (bytes.size() == 0) {
                toast("Image is empty");
                return;
            }

            pendingImageBase64 =
                Base64.encodeToString(
                    bytes.toByteArray(),
                    Base64.NO_WRAP
                );

            pendingImageMime = mime;
            pendingImageName = name;

            updateImageIndicator();

            toast(
                "Image attached. Ask JARVIS about it."
            );

        } catch (Exception e) {
            toast(
                "Image error: " + e.getMessage()
            );
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
                first.put("pinned", false);
                first.put("archived", false);

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
            JSONObject first =
                workspaces.optJSONObject(0);

            if (first != null) {
                workspaceId =
                    first.optString("id", "default");

                workspaceName =
                    first.optString("name", "My Chat");
            }
        }

        prefs.edit()
            .putString(
                "active_workspace",
                workspaceId
            )
            .apply();
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
            toast("Stop the response first");
            return;
        }

        EditText editor = new EditText(this);
        editor.setHint("Workspace name");

        new AlertDialog.Builder(this)
            .setTitle("New Workspace")
            .setView(editor)
            .setPositiveButton("Create", (d, w) -> {
                String name =
                    editor.getText().toString().trim();

                if (name.isEmpty()) {
                    name = "New Project";
                }

                String id =
                    "ws_" + System.currentTimeMillis();

                JSONObject item = new JSONObject();

                try {
                    item.put("id", id);
                    item.put("name", name);
                    item.put("pinned", false);
                    item.put("archived", false);

                    workspaces.put(item);

                } catch (Exception ignored) {}

                saveWorkspaces();

                workspaceId = id;
                workspaceName = name;

                prefs.edit()
                    .putString(
                        "active_workspace",
                        workspaceId
                    )
                    .apply();

                history = new JSONArray();

                clearPendingImage();
                saveHistory();
                showHome();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void switchWorkspace(JSONObject item) {
        if (busy) {
            toast("Stop the response first");
            return;
        }

        if (item == null) return;

        workspaceId = item.optString("id");
        workspaceName = item.optString("name");

        prefs.edit()
            .putString(
                "active_workspace",
                workspaceId
            )
            .apply();

        clearPendingImage();

        loadHistory();
        showHome();
    }

    private void updateWorkspace(
        String id,
        String key,
        Object value
    ) {
        for (int i = 0; i < workspaces.length(); i++) {
            JSONObject item =
                workspaces.optJSONObject(i);

            if (item == null) continue;

            if (id.equals(item.optString("id"))) {
                try {
                    item.put(key, value);
                } catch (Exception ignored) {}

                saveWorkspaces();
                return;
            }
        }
    }

    private void workspaceOptions(JSONObject item) {
        if (item == null) return;

        String id = item.optString("id");
        String name = item.optString("name");

        boolean pinned =
            item.optBoolean("pinned", false);

        boolean archived =
            item.optBoolean("archived", false);

        String[] options = {
            pinned ? "Unpin" : "Pin",
            "Rename",
            archived ? "Unarchive" : "Archive",
            "Delete"
        };

        new AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    updateWorkspace(
                        id,
                        "pinned",
                        !pinned
                    );

                    refreshSidebar();

                } else if (which == 1) {
                    renameWorkspace(id, name);

                } else if (which == 2) {
                    updateWorkspace(
                        id,
                        "archived",
                        !archived
                    );

                    refreshSidebar();

                } else {
                    deleteWorkspace(id, name);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void renameWorkspace(
        String id,
        String oldName
    ) {
        EditText editor = new EditText(this);
        editor.setText(oldName);

        new AlertDialog.Builder(this)
            .setTitle("Rename Workspace")
            .setView(editor)
            .setPositiveButton("Save", (d, w) -> {
                String value =
                    editor.getText().toString().trim();

                if (value.isEmpty()) return;

                updateWorkspace(id, "name", value);

                if (workspaceId.equals(id)) {
                    workspaceName = value;
                }

                showHome();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteWorkspace(
        String id,
        String name
    ) {
        if (busy) {
            toast("Stop the response first");
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Delete Workspace?")
            .setMessage(
                "Delete \"" + name
                + "\" and its saved chat history?\n\n"
                + "This cannot be undone."
            )
            .setPositiveButton("Delete", (d, w) -> {
                JSONArray updated = new JSONArray();

                for (int i = 0;
                     i < workspaces.length();
                     i++) {

                    JSONObject item =
                        workspaces.optJSONObject(i);

                    if (item == null) continue;

                    if (!id.equals(
                        item.optString("id")
                    )) {
                        updated.put(item);
                    }
                }

                workspaces = updated;

                prefs.edit()
                    .remove("history_" + id)
                    .apply();

                if (workspaces.length() == 0) {
                    JSONObject fresh = new JSONObject();

                    try {
                        fresh.put("id", "default");
                        fresh.put("name", "My Chat");
                        fresh.put("pinned", false);
                        fresh.put("archived", false);

                        workspaces.put(fresh);

                    } catch (Exception ignored) {}
                }

                saveWorkspaces();

                if (workspaceId.equals(id)) {
                    JSONObject first =
                        workspaces.optJSONObject(0);

                    if (first != null) {
                        workspaceId =
                            first.optString("id");

                        workspaceName =
                            first.optString("name");

                        prefs.edit()
                            .putString(
                                "active_workspace",
                                workspaceId
                            )
                            .apply();

                        loadHistory();
                    }
                }

                clearPendingImage();
                showHome();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void addSidebarItem(
        LinearLayout list,
        String title,
        Runnable action
    ) {
        TextView item = text(title, 16, WHITE);

        item.setPadding(
            dp(18),
            dp(13),
            dp(12),
            dp(13)
        );

        list.addView(item);

        item.setOnClickListener(v -> {
            closeSidebar();
            action.run();
        });
    }

    private void openSidebar() {
        if (sidebarOpen) {
            closeSidebar();
            return;
        }

        sidebarOpen = true;

        sidebarShade = new View(this);

        sidebarShade.setBackgroundColor(
            Color.argb(165, 0, 0, 0)
        );

        screen.addView(
            sidebarShade,
            new FrameLayout.LayoutParams(-1, -1)
        );

        sidebarShade.setOnClickListener(
            v -> closeSidebar()
        );

        sidebar = vertical();
        sidebar.setBackgroundColor(CARD);

        int width = Math.min(
            dp(310),
            getResources()
                .getDisplayMetrics()
                .widthPixels * 85 / 100
        );

        screen.addView(
            sidebar,
            new FrameLayout.LayoutParams(
                width,
                -1,
                Gravity.LEFT
            )
        );

        sidebar.setTranslationX(-width);

        sidebar.animate()
            .translationX(0)
            .setDuration(220)
            .start();

        TextView heading = text(
            "J.A.R.V.I.S",
            23,
            CYAN
        );

        heading.setTypeface(null, Typeface.BOLD);

        heading.setPadding(
            dp(18),
            dp(25),
            dp(12),
            dp(20)
        );

        sidebar.addView(heading);

        ScrollView scroll = new ScrollView(this);

        LinearLayout list = vertical();

        scroll.addView(list);

        sidebar.addView(
            scroll,
            new LinearLayout.LayoutParams(
                -1,
                0,
                1
            )
        );

        addSidebarItem(
            list,
            "💬 Chat",
            () -> showHome()
        );

        addSidebarItem(
            list,
            "＋ New Workspace",
            () -> newWorkspace()
        );

        TextView wsHeading = text(
            "WORKSPACES",
            12,
            GRAY
        );

        wsHeading.setPadding(
            dp(18),
            dp(18),
            dp(10),
            dp(8)
        );

        list.addView(wsHeading);

        addWorkspaceRows(list, true);
        addWorkspaceRows(list, false);

        TextView tools = text(
            "AI TOOLS",
            12,
            GRAY
        );

        tools.setPadding(
            dp(18),
            dp(22),
            dp(10),
            dp(8)
        );

        list.addView(tools);

        addSidebarItem(
            list,
            "🎨 AI Image Studio",
            () -> openTool("IMAGE")
        );

        addSidebarItem(
            list,
            "🎬 AI Video Studio",
            () -> openTool("VIDEO")
        );

        addSidebarItem(
            list,
            "📱 Create App",
            () -> openTool("APP")
        );

        addSidebarItem(
            list,
            "🎮 Create Game",
            () -> openTool("GAME")
        );

        addSidebarItem(
            list,
            "👁 Live Screen",
            () -> showLiveScreen()
        );

        addSidebarItem(
            list,
            "🔗 Connected Apps",
            () -> showConnectedApps()
        );

        addSidebarItem(
            list,
            "⚙ Settings",
            () -> showSettings()
        );

        TextView help = text(
            "Long press a workspace to Pin, Rename, Archive or Delete.",
            12,
            GRAY
        );

        help.setPadding(
            dp(18),
            dp(12),
            dp(18),
            dp(24)
        );

        sidebar.addView(help);
    }

    private void addWorkspaceRows(
        LinearLayout list,
        boolean pinnedOnly
    ) {
        for (int i = 0; i < workspaces.length(); i++) {
            JSONObject item =
                workspaces.optJSONObject(i);

            if (item == null) continue;

            boolean pinned =
                item.optBoolean("pinned", false);

            if (pinned != pinnedOnly) continue;

            String name = item.optString("name");

            boolean archived =
                item.optBoolean("archived", false);

            String prefix = archived
                ? "📦 "
                : pinned ? "📌 " : "📁 ";

            TextView row = text(
                prefix + name,
                15,
                archived ? GRAY : WHITE
            );

            row.setSingleLine(true);

            row.setPadding(
                dp(22),
                dp(12),
                dp(10),
                dp(12)
            );

            list.addView(row);

            row.setOnClickListener(v -> {
                if (!archived) {
                    closeSidebar();
                    switchWorkspace(item);
                } else {
                    closeSidebar();
                    workspaceOptions(item);
                }
            });

            row.setOnLongClickListener(v -> {
                closeSidebar();
                workspaceOptions(item);
                return true;
            });
        }
    }

    private void refreshSidebar() {
        closeSidebar();
        openSidebar();
    }

    private void closeSidebar() {
        if (!sidebarOpen) return;

        sidebarOpen = false;

        if (sidebar != null) {
            screen.removeView(sidebar);
            sidebar = null;
        }

        if (sidebarShade != null) {
            screen.removeView(sidebarShade);
            sidebarShade = null;
        }
    }

    private void openTool(String tool) {
        if (busy) {
            toast("Stop the response first");
            return;
        }

        String title;
        String prompt;

        switch (tool) {
            case "IMAGE":
                title = "🎨 AI Image Studio";

                prompt =
                    "You are my AI Image Director. "
                    + "Help me plan images for stories, "
                    + "write detailed image prompts, "
                    + "create character reference descriptions, "
                    + "maintain character consistency, "
                    + "and select correct expressions and poses. "
                    + "Ask what image project I want to create.";

                break;

            case "VIDEO":
                title = "🎬 AI Video Studio";

                prompt =
                    "You are my AI Video Director. "
                    + "Help create stories, scripts, "
                    + "storyboards, scene lists, image prompts, "
                    + "video prompts, narration scripts, "
                    + "and free-tool workflows. "
                    + "Ask what kind of video I want.";

                break;

            case "APP":
                title = "📱 Create App";

                prompt =
                    "You are my Android App Development Assistant. "
                    + "Help me design and code Android apps "
                    + "using GitHub and GitHub Actions. "
                    + "Give complete working files and "
                    + "help troubleshoot build errors. "
                    + "Ask what app I want to build.";

                break;

            default:
                title = "🎮 Create Game";

                prompt =
                    "You are my Game Development Assistant. "
                    + "Help me build Android games, "
                    + "starting with practical 2D projects. "
                    + "Explain game design, source code, "
                    + "assets and APK builds. "
                    + "Ask what game I want.";
                break;
        }

        final String initialPrompt = prompt;

        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                "Open this AI assistant in your current Workspace?\n\n"
                + "JARVIS will help with planning and prompts. "
                + "External generation and GitHub automation "
                + "are not connected yet."
            )
            .setPositiveButton("Start", (d, w) -> {
                showHome();
                sendMessage(initialPrompt);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showLiveScreen() {
        new AlertDialog.Builder(this)
            .setTitle("👁 Live Screen")
            .setMessage(
                "Live Screen Sharing is not active yet.\n\n"
                + "A future update will use Android "
                + "screen capture permission with "
                + "Start, Pause and Stop controls.\n\n"
                + "For now, use + to attach a screenshot "
                + "and ask JARVIS to analyze it."
            )
            .setPositiveButton(
                "Choose Screenshot",
                (d, w) -> chooseImage()
            )
            .setNegativeButton("Close", null)
            .show();
    }

    private void showConnectedApps() {
        new AlertDialog.Builder(this)
            .setTitle("🔗 Connected Apps")
            .setMessage(
                "External app and website automation "
                + "requires a supported API or "
                + "authorized Android integration.\n\n"
                + "No external apps are connected yet.\n\n"
                + "JARVIS can currently help plan "
                + "workflows in chat."
            )
            .setPositiveButton(
                "Ask JARVIS",
                (d, w) -> {
                    showHome();

                    sendMessage(
                        "Help me choose free AI tools "
                        + "and plan an app or website "
                        + "integration for my project."
                    );
                }
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
            .setTitle("AI Mode")
            .setItems(options, (d, which) -> {
                if (which == 0) mode = "FAST";
                if (which == 1) mode = "DEEP";
                if (which == 2) mode = "TRUTH";

                prefs.edit()
                    .putString("ai_mode", mode)
                    .apply();

                showHome();
            })
            .show();
    }

    private void showSettings() {
        LinearLayout panel = vertical();

        panel.setPadding(
            dp(22),
            dp(10),
            dp(22),
            dp(10)
        );

        TextView note = text(
            "Enter your private Gemini API key. "
            + "Do not share it or put it in GitHub.",
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
            .setTitle("JARVIS Settings")
            .setView(panel)
            .setPositiveButton("Save", (d, w) -> {
                String key =
                    keyInput.getText().toString().trim();

                if (!key.isEmpty()) {
                    prefs.edit()
                        .putString("gemini_key", key)
                        .apply();

                    toast("API key saved");
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startVoice() {
        if (busy) {
            toast("Stop current response first");
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
            toast("Voice input unavailable");
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

        if (resultCode != RESULT_OK ||
            data == null) {

            return;
        }

        if (requestCode == IMAGE_REQUEST) {
            handleSelectedImage(data.getData());
            return;
        }

        if (requestCode == VOICE_REQUEST) {
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
    public void onBackPressed() {
        if (sidebarOpen) {
            closeSidebar();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        requestToken++;

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
