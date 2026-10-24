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
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.animation.AlphaAnimation;
import android.widget.*;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.util.TypedValue;

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

    private final int BG = Color.rgb(5, 9, 18);
    private final int CARD = Color.rgb(20, 29, 45);
    private final int CYAN = Color.rgb(0, 215, 255);
    private final int WHITE = Color.WHITE;
    private final int GRAY = Color.rgb(150, 160, 180);

    private SharedPreferences prefs;

    private FrameLayout screen;
    private LinearLayout root;
    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText input;
    private TextView sendButton;

    private JSONArray history = new JSONArray();
    private JSONArray workspaces = new JSONArray();

    private String workspaceId = "default";
    private String workspaceName = "My Chat";
    private String mode = "FAST";

    private boolean busy = false;
    private boolean voiceReply = false;
    private boolean sidebarOpen = false;

    private int requestToken = 0;

    private Thread requestThread;
    private HttpURLConnection activeConnection;
    private TextToSpeech tts;

    private LinearLayout sidebar;
    private View sidebarShade;

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

    private GradientDrawable glowingShape() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);

        d.setColor(Color.rgb(3, 30, 48));
        d.setStroke(dp(3), CYAN);

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

    private TextView iconButton(String value) {
        TextView b = text(value, 21, WHITE);
        b.setGravity(Gravity.CENTER);

        b.setBackground(
            shape(CARD, 25)
        );

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

    private void setupScreen() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);

        root = vertical();

        screen.addView(
            root,
            new FrameLayout.LayoutParams(
                -1,
                -1
            )
        );

        setContentView(screen);
    }

    private void showHome() {
        sidebarOpen = false;
        setupScreen();

        LinearLayout header = horizontal();
        header.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        );

        TextView menu = iconButton("☰");

        header.addView(
            menu,
            new LinearLayout.LayoutParams(
                dp(46),
                dp(46)
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

        LinearLayout.LayoutParams titleParams =
            new LinearLayout.LayoutParams(
                0,
                dp(46),
                1
            );

        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        TextView newChat = iconButton("＋");

        header.addView(
            newChat,
            new LinearLayout.LayoutParams(
                dp(46),
                dp(46)
            )
        );

        newChat.setOnClickListener(v -> newChat());

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
        project.setEllipsize(
            android.text.TextUtils.TruncateAt.END
        );

        info.addView(
            project,
            new LinearLayout.LayoutParams(
                0,
                -2,
                1
            )
        );

        project.setOnClickListener(v -> openSidebar());

        TextView modeView = text(
            "◉ " + mode + "  ⌄",
            13,
            CYAN
        );

        modeView.setPadding(
            dp(12),
            dp(8),
            dp(12),
            dp(8)
        );

        modeView.setBackground(shape(CARD, 18));

        info.addView(modeView);

        modeView.setOnClickListener(v -> selectMode());

        root.addView(info);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);

        messages = vertical();

        messages.setPadding(
            dp(14),
            dp(12),
            dp(14),
            dp(16)
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
        FrameLayout center = new FrameLayout(this);

        LinearLayout.LayoutParams areaParams =
            new LinearLayout.LayoutParams(
                -1,
                0,
                1
            );

        messages.addView(center, areaParams);

        LinearLayout content = vertical();
        content.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams cp =
            new FrameLayout.LayoutParams(
                -1,
                -2,
                Gravity.CENTER
            );

        center.addView(content, cp);

        FrameLayout coreContainer =
            new FrameLayout(this);

        LinearLayout.LayoutParams coreParams =
            new LinearLayout.LayoutParams(
                dp(115),
                dp(115)
            );

        coreParams.gravity = Gravity.CENTER;
        content.addView(coreContainer, coreParams);

        View glow = new View(this);
        glow.setBackground(glowingShape());

        FrameLayout.LayoutParams glowParams =
            new FrameLayout.LayoutParams(
                dp(88),
                dp(88),
                Gravity.CENTER
            );

        coreContainer.addView(glow, glowParams);

        TextView coreSymbol = text(
            "⬡",
            73,
            CYAN
        );

        coreSymbol.setGravity(Gravity.CENTER);

        coreContainer.addView(
            coreSymbol,
            new FrameLayout.LayoutParams(
                -1,
                -1
            )
        );

        ObjectAnimator pulseX =
            ObjectAnimator.ofFloat(
                glow,
                "scaleX",
                0.85f,
                1.15f
            );

        ObjectAnimator pulseY =
            ObjectAnimator.ofFloat(
                glow,
                "scaleY",
                0.85f,
                1.15f
            );

        ObjectAnimator alpha =
            ObjectAnimator.ofFloat(
                glow,
                "alpha",
                0.35f,
                1f
            );

        pulseX.setRepeatCount(
            ValueAnimator.INFINITE
        );

        pulseY.setRepeatCount(
            ValueAnimator.INFINITE
        );

        alpha.setRepeatCount(
            ValueAnimator.INFINITE
        );

        pulseX.setRepeatMode(
            ValueAnimator.REVERSE
        );

        pulseY.setRepeatMode(
            ValueAnimator.REVERSE
        );

        alpha.setRepeatMode(
            ValueAnimator.REVERSE
        );

        AnimatorSet animation = new AnimatorSet();

        animation.playTogether(
            pulseX,
            pulseY,
            alpha
        );

        animation.setDuration(1800);
        animation.start();

        coreContainer.setOnClickListener(v -> startVoice());

        TextView greeting = text(
            "How can I help you?",
            22,
            WHITE
        );

        greeting.setTypeface(null, Typeface.BOLD);
        greeting.setGravity(Gravity.CENTER);

        greeting.setPadding(
            0,
            dp(20),
            0,
            dp(8)
        );

        content.addView(greeting);

        TextView subtitle = text(
            "Your personal AI assistant",
            14,
            GRAY
        );

        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle);
    }

    private void createComposer() {
        LinearLayout outer = vertical();

        outer.setPadding(
            dp(10),
            dp(8),
            dp(10),
            dp(12)
        );

        LinearLayout box = vertical();
        box.setBackground(shape(CARD, 26));

        box.setPadding(
            dp(10),
            dp(5),
            dp(10),
            dp(5)
        );

        input = new EditText(this);

        input.setHint("Message JARVIS...");
        input.setHintTextColor(GRAY);
        input.setTextColor(WHITE);

        input.setTextSize(16);
        input.setBackgroundColor(Color.TRANSPARENT);

        input.setMinLines(1);
        input.setMaxLines(4);

        input.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        box.addView(input);

        LinearLayout controls = horizontal();

        TextView attach = iconButton("＋");

        controls.addView(
            attach,
            new LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        );

        attach.setOnClickListener(v -> showAttachments());

        View spacer = new View(this);

        controls.addView(
            spacer,
            new LinearLayout.LayoutParams(
                0,
                dp(1),
                1
            )
        );

        TextView mic = iconButton("🎙");

        controls.addView(
            mic,
            new LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        );

        mic.setOnClickListener(v -> startVoice());

        sendButton = iconButton("➤");
        sendButton.setTextColor(CYAN);

        controls.addView(
            sendButton,
            new LinearLayout.LayoutParams(
                dp(58),
                dp(44)
            )
        );

        sendButton.setOnClickListener(v -> {
            if (busy) {
                stopRequest();
            } else {
                String message =
                    input.getText().toString().trim();

                if (!message.isEmpty()) {
                    voiceReply = false;
                    input.setText("");
                    sendMessage(message);
                }
            }
        });

        box.addView(controls);
        outer.addView(box);
        root.addView(outer);
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
                    ? Color.rgb(0, 70, 105)
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

    private JSONObject makeMessage(
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

    private void renderHistory() {
        for (int i = 0; i < history.length(); i++) {
            JSONObject item =
                history.optJSONObject(i);

            if (item == null) continue;

            boolean user =
                item.optString("role").equals("user");

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
            .setTitle("✏ Edit Message")
            .setView(editor)
            .setPositiveButton(
                "Save & Resend",
                (d, w) -> {
                    String changed =
                        editor.getText()
                            .toString()
                            .trim();

                    if (changed.isEmpty()) return;

                    JSONArray revised =
                        new JSONArray();

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
                }
            )
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
            toast("Save your Gemini API key first");
            showSettings();
            return;
        }

        if (history.length() == 0) {
            messages.removeAllViews();
        }

        busy = true;
        sendButton.setText("■");

        boolean shouldSpeak = voiceReply;
        voiceReply = false;

        int index = history.length();

        addHistory("user", message);
        addBubble(message, true, index);

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
                    requestHistory
                );
            } catch (Exception e) {
                answer = "Connection problem: "
                    + e.getMessage();
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
                "You are JARVIS, a highly capable "
                + "personal AI assistant. "
                + "Understand Hindi, Hinglish and English. "
                + "Reply naturally in the user's language. "
                + "Be helpful, accurate and practical. "
                + "Do not blindly agree or flatter. "
                + "Politely correct incorrect claims. "
                + "Keep normal replies concise. "
                + "Explain step by step when requested. "
                + "Use earlier conversation as context. "
                + "Help with apps, games, coding, "
                + "stories, images, video planning "
                + "and technical problems. "
                + "Never claim to have completed "
                + "actions that were not performed. ";

            if (selectedMode.equals("DEEP")) {
                instruction +=
                    "Analyze complex problems carefully. "
                    + "Compare options and limitations. ";
            }

            if (selectedMode.equals("TRUTH")) {
                instruction +=
                    "Prioritize accuracy. "
                    + "Check your reasoning for errors. "
                    + "State uncertainty clearly. "
                    + "Never invent citations or "
                    + "pretend to verify external sources. ";
            }

            JSONObject body = new JSONObject();

            JSONObject system = new JSONObject();
            JSONObject part = new JSONObject();

            part.put("text", instruction);

            JSONArray systemParts = new JSONArray();
            systemParts.put(part);

            system.put("parts", systemParts);

            body.put("systemInstruction", system);

            JSONArray all =
                new JSONArray(requestHistory);

            JSONArray recent = new JSONArray();

            int start = Math.max(
                0,
                all.length() - 30
            );

            for (int i = start; i < all.length(); i++) {
                JSONObject item = all.optJSONObject(i);

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
                workspaceId = first.optString("id");
                workspaceName =
                    first.optString("name", "My Chat");
            }
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

    private void newChat() {
        if (busy) {
            toast("Stop the current response first");
            return;
        }

        EditText name = new EditText(this);
        name.setHint("Workspace name");

        new AlertDialog.Builder(this)
            .setTitle("New Workspace")
            .setView(name)
            .setPositiveButton("Create", (d, w) -> {
                String value =
                    name.getText().toString().trim();

                if (value.isEmpty()) {
                    value = "New Project";
                }

                JSONObject item = new JSONObject();

                try {
                    workspaceId =
                        "ws_" + System.currentTimeMillis();

                    workspaceName = value;

                    item.put("id", workspaceId);
                    item.put("name", workspaceName);
                    item.put("pinned", false);
                    item.put("archived", false);

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

    private void switchWorkspace(JSONObject item) {
        if (busy) {
            toast("Stop the current response first");
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

                    openSidebar();

                } else if (which == 1) {
                    renameWorkspace(id, name);

                } else if (which == 2) {
                    updateWorkspace(
                        id,
                        "archived",
                        !archived
                    );

                    openSidebar();

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
                "Delete \"" + name + "\" and its saved chat history?\n\n"
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

                showHome();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void addSidebarItem(
        LinearLayout container,
        String title,
        Runnable action
    ) {
        TextView item = text(
            title,
            16,
            WHITE
        );

        item.setPadding(
            dp(18),
            dp(13),
            dp(12),
            dp(13)
        );

        container.addView(item);

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
            Color.argb(160, 0, 0, 0)
        );

        screen.addView(
            sidebarShade,
            new FrameLayout.LayoutParams(
                -1,
                -1
            )
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

        FrameLayout.LayoutParams sideParams =
            new FrameLayout.LayoutParams(
                width,
                -1,
                Gravity.LEFT
            );

        screen.addView(sidebar, sideParams);

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
            dp(28),
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
            () -> newChat()
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
            () -> showFeature("AI Image Studio")
        );

        addSidebarItem(
            list,
            "🎬 AI Video Studio",
            () -> showFeature("AI Video Studio")
        );

        addSidebarItem(
            list,
            "📱 Create App",
            () -> showFeature("Create App")
        );

        addSidebarItem(
            list,
            "🎮 Create Game",
            () -> showFeature("Create Game")
        );

        addSidebarItem(
            list,
            "👁 Live Screen",
            () -> showFeature("Live Screen")
        );

        addSidebarItem(
            list,
            "🔗 Connected Apps",
            () -> showFeature("Connected Apps")
        );

        addSidebarItem(
            list,
            "⚙ Settings",
            () -> showSettings()
        );

        TextView help = text(
            "Tip: Long press a workspace to Pin, Rename, Archive or Delete.",
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

            row.setEllipsize(
                android.text.TextUtils.TruncateAt.END
            );

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

    private void showFeature(String name) {
        new AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(
                "This feature is planned for a future update.\n\n"
                + "It is not active in JARVIS V4 yet."
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
            .setTitle("Attach to JARVIS")
            .setItems(options, (d, which) -> {
                toast(
                    "File upload will be added "
                    + "in a future update."
                );
            })
            .setNegativeButton("Cancel", null)
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
            "Your Gemini API key is stored locally. "
            + "Never share it or put it in public GitHub code.",
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
            toast("Stop the response first");
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

    private void toast(String message) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show();
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
