package br.com.exemplo.guiavoz.whatsapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;

public final class WhatsAppNotificationService extends NotificationListenerService {
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static volatile PendingIntent latestAudioIntent;

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

    private String value(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
