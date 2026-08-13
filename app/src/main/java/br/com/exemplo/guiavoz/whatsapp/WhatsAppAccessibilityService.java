package br.com.exemplo.guiavoz.whatsapp;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

public final class WhatsAppAccessibilityService extends AccessibilityService {
    public enum Action { NONE, CALL, PLAY_AUDIO }

    private static final long ACTION_TIMEOUT_MS = 12_000;
    private static volatile Action pendingAction = Action.NONE;
    private static volatile long actionExpiresAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;

    public static void requestAction(Action action) {
        pendingAction = action;
        actionExpiresAt = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
    }

    @Override
    protected void onServiceConnected() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("pt", "BR"));
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!"com.whatsapp".contentEquals(event.getPackageName())) return;
        Action action = pendingAction;
        if (action == Action.NONE) return;
        if (System.currentTimeMillis() > actionExpiresAt) {
            pendingAction = Action.NONE;
            speak("A ação do WhatsApp expirou. Tente o comando novamente.");
            return;
        }
        handler.postDelayed(() -> tryAction(action), 450);
    }

    private void tryAction(Action action) {
        if (pendingAction != action) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> labels = action == Action.CALL
                ? Arrays.asList("Ligação de voz", "Chamada de voz", "Ligar")
                : Arrays.asList("Reproduzir mensagem de voz", "Reproduzir áudio", "Reproduzir audio", "Reproduzir");
        AccessibilityNodeInfo target = findClickable(root, labels);
        if (target == null) return;
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pendingAction = Action.NONE;
            speak(action == Action.CALL
                    ? "Chamada de voz iniciada no WhatsApp."
                    : "Reproduzindo a mensagem de voz.");
        }
    }

    private AccessibilityNodeInfo findClickable(AccessibilityNodeInfo root, List<String> labels) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            String text = node.getText() == null ? "" : node.getText().toString();
            String description = node.getContentDescription() == null
                    ? "" : node.getContentDescription().toString();
            for (String label : labels) {
                if (text.equalsIgnoreCase(label) || description.equalsIgnoreCase(label)) {
                    AccessibilityNodeInfo clickable = node;
                    while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                    if (clickable != null) return clickable;
                }
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private void speak(String message) {
        if (tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "whatsapp-action");
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
