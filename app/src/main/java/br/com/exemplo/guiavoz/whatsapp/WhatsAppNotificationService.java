package br.com.exemplo.guiavoz.whatsapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WhatsAppNotificationService extends NotificationListenerService {
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final Map<String, PendingIntent> conversationIntents = new ConcurrentHashMap<>();
    private static final Map<String, PendingIntent> audioIntents = new ConcurrentHashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String fallbackSender = value(extras.getCharSequence(Notification.EXTRA_TITLE));
        boolean parsed = parseMessagingStyle(extras, fallbackSender, notification.contentIntent);
        if (!parsed) {
            String text = value(extras.getCharSequence(Notification.EXTRA_TEXT));
            if (!fallbackSender.isEmpty() && !text.isEmpty()) {
                store(fallbackSender, text, sbn.getPostTime(), isAudio(text, null),
                        notification.contentIntent);
            }
        }
    }

    private boolean parseMessagingStyle(Bundle extras, String fallbackSender,
                                        PendingIntent contentIntent) {
        Parcelable[] bundled = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundled == null || bundled.length == 0) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            boolean stored = false;
            for (Parcelable item : bundled) {
                if (!(item instanceof Bundle)) continue;
                Bundle message = (Bundle) item;
                String sender = value(message.getCharSequence("sender"));
                if (sender.isEmpty()) sender = fallbackSender;
                String text = value(message.getCharSequence("text"));
                if (sender.isEmpty() || text.isEmpty()) continue;
                store(sender, text, message.getLong("time", System.currentTimeMillis()),
                        isAudio(text, message.getString("type")), contentIntent);
                stored = true;
            }
            return stored;
        }
        List<Notification.MessagingStyle.Message> messages =
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundled);
        if (messages == null || messages.isEmpty()) return false;
        boolean stored = false;
        for (Notification.MessagingStyle.Message message : messages) {
            String sender = senderOf(message, fallbackSender);
            String text = value(message.getText());
            if (sender.isEmpty() || text.isEmpty()) continue;
            store(sender, text, message.getTimestamp(),
                    isAudio(text, message.getDataMimeType()), contentIntent);
            stored = true;
        }
        return stored;
    }

    private void store(String sender, String text, long timestamp, boolean audio,
                       PendingIntent contentIntent) {
        WhatsAppMessageStore.Message message = new WhatsAppMessageStore.Message(
                sender, text, timestamp, audio);
        WhatsAppMessageStore.add(this, message);
        if (contentIntent != null) {
            conversationIntents.put(TextNormalizer.normalize(sender), contentIntent);
            if (audio) audioIntents.put(message.id, contentIntent);
        }
    }

    public static boolean openLatestAudio() {
        // Mantido para compatibilidade; a Activity usa a versão com Context para controlar estado.
        for (Map.Entry<String, PendingIntent> entry : audioIntents.entrySet()) {
            if (sendAudio(entry.getKey(), entry.getValue())) return true;
        }
        return false;
    }

    public static boolean openNextAudio(Context context) {
        WhatsAppMessageStore.Message message = WhatsAppMessageStore.nextUnheardAudio(context);
        return message != null && openAudio(context, message);
    }

    public static boolean repeatLastAudio(Context context) {
        WhatsAppMessageStore.Message message = WhatsAppMessageStore.lastHeardAudio(context);
        return message != null && openAudio(context, message);
    }

    private static boolean openAudio(Context context, WhatsAppMessageStore.Message message) {
        PendingIntent intent = audioIntents.get(message.id);
        if (intent == null) intent = findConversation(message.sender);
        if (intent == null) return false;
        WhatsAppMessageStore.markAudioRequested(context, message.id);
        return sendAudio(message.id, intent);
    }

    private static boolean sendAudio(String messageId, PendingIntent intent) {
        try {
            WhatsAppAccessibilityService.requestWhatsAppAction(
                    WhatsAppAccessibilityService.Action.PLAY_AUDIO, messageId);
            intent.send();
            return true;
        } catch (PendingIntent.CanceledException error) {
            audioIntents.remove(messageId);
            return false;
        }
    }

    public static boolean openRecentConversation(String requestedName) {
        return openRecentConversation(requestedName,
                WhatsAppAccessibilityService.Action.CALL, "");
    }

    public static boolean openRecentConversation(String requestedName,
                                                  WhatsAppAccessibilityService.Action action,
                                                  String value) {
        PendingIntent selected = findConversation(requestedName);
        if (selected == null) return false;
        try {
            WhatsAppAccessibilityService.requestWhatsAppAction(
                    action, value);
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

    private String senderOf(Notification.MessagingStyle.Message message, String fallback) {
        CharSequence legacy = message.getSender();
        String sender = value(legacy);
        return sender.isEmpty() ? fallback : sender;
    }

    private boolean isAudio(String text, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("audio/")) return true;
        String normalized = TextNormalizer.normalize(text);
        return normalized.contains("mensagem de voz") || normalized.contains("audio")
                || normalized.contains("recado de voz");
    }

    private String value(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
