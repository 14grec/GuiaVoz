package br.com.exemplo.guiavoz.whatsapp;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

public final class WhatsAppAccessibilityService extends AccessibilityService {
    public static final String PREFERENCES = "guiavoz_preferences";
    public static final String LOCK_SCREEN_AFTER_AUDIO = "lock_screen_after_audio";

    public enum Action {
        NONE, CALL, CONFIRM_CALL, PLAY_AUDIO,
        READ_SCREEN, TAP_ELEMENT, TYPE_TEXT, SCROLL_DOWN, SCROLL_UP, BACK
    }

    private static final long ACTION_TIMEOUT_MS = 20_000;
    private static final long CALL_CONFIRMATION_TIMEOUT_MS = 4_000;
    private static volatile Action pendingAction = Action.NONE;
    private static volatile long actionExpiresAt;
    private static volatile String pendingTarget = "";
    private static volatile String pendingMessageId = "";
    private static volatile WhatsAppAccessibilityService connectedInstance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;

    public static void requestAction(Action action) {
        requestWhatsAppAction(action, "");
    }

    public static void requestWhatsAppAction(Action action, String messageId) {
        pendingAction = action;
        pendingMessageId = messageId == null ? "" : messageId;
        pendingTarget = "";
        actionExpiresAt = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
    }

    public static boolean requestNavigation(Action action, String target) {
        WhatsAppAccessibilityService service = connectedInstance;
        if (service == null) return false;
        service.handler.post(() -> service.beginNavigation(action, target));
        return true;
    }

    @Override
    protected void onServiceConnected() {
        connectedInstance = this;
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.25f);
            }
        });
    }

    private void beginNavigation(Action action, String target) {
        pendingAction = action;
        pendingTarget = target == null ? "" : target.trim();
        pendingMessageId = "";
        actionExpiresAt = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
        if (action == Action.BACK) {
            pendingAction = Action.NONE;
            performGlobalAction(GLOBAL_ACTION_BACK);
            speak("Voltei.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && "br.com.exemplo.guiavoz".contentEquals(root.getPackageName())) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> tryAction(action), 550);
        } else {
            handler.postDelayed(() -> tryAction(action), 120);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Action action = pendingAction;
        if (action == Action.NONE) return;
        if (System.currentTimeMillis() > actionExpiresAt) {
            pendingAction = Action.NONE;
            speak("A ação expirou. Tente novamente.");
            return;
        }
        boolean whatsappAction = action == Action.CALL || action == Action.CONFIRM_CALL
                || action == Action.PLAY_AUDIO;
        if (whatsappAction && !"com.whatsapp".contentEquals(event.getPackageName())) return;
        handler.postDelayed(() -> tryAction(action), 350);
    }

    private void tryAction(Action action) {
        if (pendingAction != action) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (action == Action.READ_SCREEN) {
            readScreen(root);
        } else if (action == Action.TAP_ELEMENT) {
            tapElement(root, pendingTarget);
        } else if (action == Action.TYPE_TEXT) {
            typeText(root, pendingTarget);
        } else if (action == Action.SCROLL_DOWN || action == Action.SCROLL_UP) {
            scroll(root, action == Action.SCROLL_DOWN);
        } else {
            tryWhatsAppAction(root, action);
        }
    }

    private void tryWhatsAppAction(AccessibilityNodeInfo root, Action action) {
        List<String> labels;
        if (action == Action.CALL) {
            labels = Arrays.asList("Ligação de voz", "Chamada de voz", "Fazer ligação de voz");
        } else if (action == Action.CONFIRM_CALL) {
            labels = Arrays.asList("Ligar", "Chamar", "Iniciar");
        } else {
            labels = Arrays.asList("Reproduzir mensagem de voz", "Reproduzir áudio",
                    "Reproduzir audio", "Reproduzir");
        }
        AccessibilityNodeInfo target = findClickable(root, labels, action == Action.PLAY_AUDIO);
        if (target == null) return;
        if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        if (action == Action.CALL) {
            pendingAction = Action.CONFIRM_CALL;
            actionExpiresAt = System.currentTimeMillis() + CALL_CONFIRMATION_TIMEOUT_MS;
            handler.postDelayed(this::finishCallWithoutConfirmation, CALL_CONFIRMATION_TIMEOUT_MS);
        } else {
            pendingAction = Action.NONE;
            if (action == Action.CONFIRM_CALL) {
                speak("Chamada iniciada.");
            } else {
                if (!pendingMessageId.isEmpty()) {
                    WhatsAppMessageStore.markHeard(this, pendingMessageId);
                }
                lockScreenAfterAudioIfEnabled();
            }
        }
    }

    private void readScreen(AccessibilityNodeInfo root) {
        Set<String> visible = new LinkedHashSet<>();
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && visible.size() < 14) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isVisibleToUser() && !node.isPassword()) {
                addVisible(visible, node.getText());
                addVisible(visible, node.getContentDescription());
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        pendingAction = Action.NONE;
        if (visible.isEmpty()) speak("Esta tela não expõe textos para a acessibilidade.");
        else speak(String.join(". ", visible) + ".");
    }

    private void tapElement(AccessibilityNodeInfo root, String requested) {
        String normalized = TextNormalizer.normalize(requested);
        if (normalized.isEmpty()) {
            pendingAction = Action.NONE;
            speak("Diga o nome do botão.");
            return;
        }
        if (isSensitive(normalized)) {
            pendingAction = Action.NONE;
            speak("Essa ação é sensível e precisa de confirmação específica.");
            return;
        }
        AccessibilityNodeInfo target = findBestClickable(root, normalized);
        if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pendingAction = Action.NONE;
            speak("Não encontrei " + requested + " nesta tela.");
            return;
        }
        pendingAction = Action.NONE;
        speak("Pronto.");
    }

    private void typeText(AccessibilityNodeInfo root, String value) {
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isEditable()) target = findEditable(root);
        if (target == null || target.isPassword()) {
            pendingAction = Action.NONE;
            speak("Não encontrei um campo de texto seguro.");
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        boolean success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        pendingAction = Action.NONE;
        speak(success ? "Texto preenchido." : "Não consegui preencher o campo.");
    }

    private void scroll(AccessibilityNodeInfo root, boolean down) {
        AccessibilityNodeInfo target = findScrollable(root);
        boolean success = target != null && target.performAction(down
                ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        pendingAction = Action.NONE;
        speak(success ? (down ? "Desci." : "Subi.") : "Esta tela não pode ser rolada.");
    }

    private AccessibilityNodeInfo findBestClickable(AccessibilityNodeInfo root, String wanted) {
        AccessibilityNodeInfo partial = null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            String text = nodeText(node);
            if (!text.isEmpty() && (text.equals(wanted) || text.contains(wanted)
                    || wanted.contains(text))) {
                AccessibilityNodeInfo clickable = clickableParent(node);
                if (clickable != null) {
                    if (text.equals(wanted)) return clickable;
                    if (partial == null) partial = clickable;
                }
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        return partial;
    }

    private AccessibilityNodeInfo findClickable(AccessibilityNodeInfo root, List<String> labels,
                                                boolean selectLast) {
        List<AccessibilityNodeInfo> matches = new ArrayList<>();
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            String text = node.getText() == null ? "" : node.getText().toString();
            String description = node.getContentDescription() == null
                    ? "" : node.getContentDescription().toString();
            for (String label : labels) {
                if (text.equalsIgnoreCase(label) || description.equalsIgnoreCase(label)) {
                    AccessibilityNodeInfo clickable = clickableParent(node);
                    if (clickable != null) matches.add(clickable);
                    break;
                }
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        if (matches.isEmpty()) return null;
        return selectLast ? matches.get(matches.size() - 1) : matches.get(0);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo root) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isEditable() && node.isVisibleToUser()) return node;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo root) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isScrollable() && node.isVisibleToUser()) return node;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null && !current.isClickable()) current = current.getParent();
        return current;
    }

    private String nodeText(AccessibilityNodeInfo node) {
        String text = node.getText() == null ? "" : node.getText().toString();
        String description = node.getContentDescription() == null
                ? "" : node.getContentDescription().toString();
        return TextNormalizer.normalize(text.isEmpty() ? description : text);
    }

    private void addVisible(Set<String> visible, CharSequence value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (!text.isEmpty() && text.length() <= 180) visible.add(text);
    }

    private boolean isSensitive(String target) {
        return target.contains("pagar") || target.contains("comprar")
                || target.contains("excluir") || target.contains("apagar")
                || target.contains("enviar") || target.contains("publicar");
    }

    private void lockScreenAfterAudioIfEnabled() {
        boolean enabled = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(LOCK_SCREEN_AFTER_AUDIO, true);
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN), 900);
    }

    private void finishCallWithoutConfirmation() {
        if (pendingAction != Action.CONFIRM_CALL) return;
        pendingAction = Action.NONE;
        speak("Chamada solicitada.");
    }

    private void speak(String message) {
        if (tts != null && !TextUtils.isEmpty(message)) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guiavoz-accessibility");
        }
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (connectedInstance == this) connectedInstance = null;
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
