package br.com.exemplo.guiavoz.whatsapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WhatsAppNotificationService extends NotificationListenerService {
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static volatile PendingIntent latestAudioIntent;
    private static final Map<String, PendingIntent> conversationIntents =
            new ConcurrentHashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String sender = value(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = value(extras.getCharSequence(Notification.EXTRA_TEXT));
        if (sender.isEmpty() || text.isEmpty()) return;

        String normalized = text.toLowerCase(new Locale("pt", "BR"));
        boolean audio = normalized.contains("mensagem de voz")
                || normalized.contains("áudio") || normalized.contains("audio");
        WhatsAppMessageStore.add(this, new WhatsAppMessageStore.Message(
                sender, text, sbn.getPostTime(), audio));
        if (notification.contentIntent != null) {
            conversationIntents.put(TextNormalizer.normalize(sender), notification.contentIntent);
        }
        if (audio && notification.contentIntent != null) latestAudioIntent = notification.contentIntent;
    }

    public static boolean openLatestAudio() {
        PendingIntent intent = latestAudioIntent;
        if (intent == null) return false;
        try {
            WhatsAppAccessibilityService.requestAction(
                    WhatsAppAccessibilityService.Action.PLAY_AUDIO);
            intent.send();
            return true;
        } catch (PendingIntent.CanceledException error) {
            latestAudioIntent = null;
            return false;
        }
    }

    public static boolean openRecentConversation(String requestedName) {
        PendingIntent selected = findConversation(requestedName);
        if (selected == null) return false;
        try {
            WhatsAppAccessibilityService.requestAction(
                    WhatsAppAccessibilityService.Action.CALL);
            selected.send();
            return true;
        } catch (PendingIntent.CanceledException error) {
            return false;
        }
    }

    public static boolean hasRecentConversation(String requestedName) {
        return findConversation(requestedName) != null;
    }

    private static PendingIntent findConversation(String requestedName) {
        String wanted = TextNormalizer.normalize(requestedName);
        if (wanted.isEmpty()) return null;
        PendingIntent selected = conversationIntents.get(wanted);
        if (selected == null) {
            for (Map.Entry<String, PendingIntent> entry : conversationIntents.entrySet()) {
                String sender = entry.getKey();
                if (sender.startsWith(wanted + " ") || sender.endsWith(" " + wanted)
                        || sender.contains(" " + wanted + " ")) {
                    if (selected != null) return null;
                    selected = entry.getValue();
                }
            }
        }
        return selected;
    }

    private String value(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
