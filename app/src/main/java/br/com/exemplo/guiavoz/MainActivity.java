package br.com.exemplo.guiavoz;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import br.com.exemplo.guiavoz.assistant.AssistantCommand;
import br.com.exemplo.guiavoz.assistant.CommandParser;
import br.com.exemplo.guiavoz.assistant.PhoneTextParser;
import br.com.exemplo.guiavoz.data.ContactRepository;
import br.com.exemplo.guiavoz.data.InstalledAppRepository;
import br.com.exemplo.guiavoz.media.MediaActionController;
import br.com.exemplo.guiavoz.voice.VoiceController;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppAccessibilityService;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppMessageStore;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppNotificationService;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class MainActivity extends Activity implements VoiceController.Listener {
    private static final int REQUEST_MICROPHONE = 10;
    private static final int REQUEST_CONTACTS = 11;
    private static final String HELP_TEXT = "Diga: ouvir mensagens, reproduzir áudios do WhatsApp, "
            + "ligar para Maria no WhatsApp, abrir Spotify, pausar música ou que horas são.";
    private static final int COLOR_BACKGROUND = Color.rgb(244, 247, 249);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_PRIMARY = Color.rgb(14, 91, 103);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(15, 42, 51);
    private static final int COLOR_ACCENT = Color.rgb(31, 138, 112);
    private static final int COLOR_TEXT_MUTED = Color.rgb(78, 96, 104);

    private final CommandParser parser = new CommandParser();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private VoiceController voiceController;
    private ContactRepository contactRepository;
    private InstalledAppRepository appRepository;
    private MediaActionController mediaController;
    private TextView statusView;
    private TextView transcriptView;
    private EditText commandInput;
    private Button listenButton;
    private Switch lockScreenSwitch;
    private AssistantCommand pendingContactCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createInterface());
        contactRepository = new ContactRepository(getContentResolver());
        appRepository = new InstalledAppRepository(this);
        mediaController = new MediaActionController(this);
        voiceController = new VoiceController(this, this);

        listenButton.setOnClickListener(view -> startVoiceCommand());
        findViewById(1002).setOnClickListener(view -> runTypedCommand());
        findViewById(1003).setOnClickListener(view -> respond(HELP_TEXT));
        findViewById(1005).setOnClickListener(view -> openNotificationSettings());
        findViewById(1006).setOnClickListener(view -> openAccessibilitySettings());
        findViewById(1010).setOnClickListener(view -> execute(
                new AssistantCommand(AssistantCommand.Type.WHATSAPP_LISTEN_MESSAGES, "", "", "")));
        findViewById(1011).setOnClickListener(view -> execute(
                new AssistantCommand(AssistantCommand.Type.PLAY_WHATSAPP_AUDIO, "", "", "")));
        lockScreenSwitch.setOnCheckedChangeListener((button, enabled) ->
                getSharedPreferences(WhatsAppAccessibilityService.PREFERENCES, MODE_PRIVATE)
                        .edit().putBoolean(WhatsAppAccessibilityService.LOCK_SCREEN_AFTER_AUDIO,
                                enabled).apply());

        statusView.post(() -> status(getString(R.string.status_ready)));
    }

    private View createInterface() {
        int padding = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(COLOR_BACKGROUND);

        TextView badge = text("ASSISTENTE ACESSÍVEL", 13, COLOR_ACCENT);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setLetterSpacing(0.08f);
        content.addView(badge, matchWrap());

        TextView title = text("GuiaVoz", 36, COLOR_PRIMARY_DARK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(4), 0, 0);
        content.addView(title, matchWrap());

        TextView intro = text(getString(R.string.intro), 18, COLOR_TEXT_MUTED);
        intro.setPadding(0, dp(4), 0, dp(18));
        content.addView(intro, matchWrap());

        listenButton = button(1001, getString(R.string.listen), "Inicia o reconhecimento de voz");
        listenButton.setTextSize(23);
        listenButton.setTypeface(null, Typeface.BOLD);
        listenButton.setMinHeight(dp(76));
        listenButton.setBackground(rounded(COLOR_PRIMARY, 20, 0, Color.TRANSPARENT));
        listenButton.setTextColor(Color.WHITE);
        content.addView(listenButton, matchWrap());

        LinearLayout statusCard = card();
        statusCard.addView(sectionLabel("STATUS"), matchWrap());
        statusView = text(getString(R.string.status_ready), 22, COLOR_PRIMARY_DARK);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setPadding(0, dp(6), 0, 0);
        statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
        statusCard.addView(statusView, matchWrap());

        transcriptView = text("Último comando: nenhum", 17, COLOR_TEXT_MUTED);
        transcriptView.setPadding(0, dp(8), 0, 0);
        statusCard.addView(transcriptView, matchWrap());
        content.addView(statusCard, cardParams());

        LinearLayout quickCard = card();
        quickCard.addView(sectionTitle("Ações rápidas"), matchWrap());
        quickCard.addView(secondaryButton(1010, "Ouvir mensagens",
                "Lê textos e reproduz o áudio mais recente"), matchWrap());
        quickCard.addView(secondaryButton(1011, "Reproduzir último áudio",
                "Reproduz o áudio mais recente do WhatsApp"), matchWrap());
        content.addView(quickCard, cardParams());

        LinearLayout testCard = card();
        testCard.addView(sectionTitle("Testar por texto"), matchWrap());

        commandInput = new EditText(this);
        commandInput.setTextSize(19);
        commandInput.setTextColor(COLOR_PRIMARY_DARK);
        commandInput.setHintTextColor(COLOR_TEXT_MUTED);
        commandInput.setHint(getString(R.string.typed_command_hint));
        commandInput.setSingleLine(false);
        commandInput.setMinHeight(dp(64));
        commandInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        commandInput.setBackground(rounded(Color.rgb(242, 246, 247), 14, 1,
                Color.rgb(205, 216, 220)));
        commandInput.setContentDescription("Campo para digitar um comando de teste");
        testCard.addView(commandInput, matchWrap());

        testCard.addView(secondaryButton(1002, getString(R.string.run_text),
                "Executa o comando digitado"), matchWrap());
        content.addView(testCard, cardParams());

        LinearLayout settingsCard = card();
        settingsCard.addView(sectionTitle("Acessos e preferências"), matchWrap());
        lockScreenSwitch = new Switch(this);
        lockScreenSwitch.setText("Bloquear tela ao reproduzir áudio");
        lockScreenSwitch.setTextSize(18);
        lockScreenSwitch.setTextColor(COLOR_PRIMARY_DARK);
        lockScreenSwitch.setMinHeight(dp(56));
        lockScreenSwitch.setChecked(getSharedPreferences(
                WhatsAppAccessibilityService.PREFERENCES, MODE_PRIVATE)
                .getBoolean(WhatsAppAccessibilityService.LOCK_SCREEN_AFTER_AUDIO, true));
        lockScreenSwitch.setContentDescription(
                "Bloquear a tela depois que um áudio do WhatsApp começar");
        settingsCard.addView(lockScreenSwitch, matchWrap());
        settingsCard.addView(secondaryButton(1005, getString(R.string.notification_settings),
                "Permite ler notificações do WhatsApp"), matchWrap());
        settingsCard.addView(secondaryButton(1006, getString(R.string.whatsapp_accessibility),
                "Permite controlar chamadas e áudios do WhatsApp"), matchWrap());
        settingsCard.addView(secondaryButton(1003, getString(R.string.help),
                "Fala exemplos de comandos"), matchWrap());
        content.addView(settingsCard, cardParams());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);
        scroll.addView(content);
        return scroll;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(int id, String label, String description) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setMinHeight(dp(64));
        button.setContentDescription(description);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private Button secondaryButton(int id, String label, String description) {
        Button result = button(id, label, description);
        result.setTextColor(COLOR_PRIMARY);
        result.setBackground(rounded(Color.rgb(238, 247, 246), 14, 1,
                Color.rgb(174, 211, 203)));
        return result;
    }

    private LinearLayout card() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18), dp(16), dp(18), dp(18));
        result.setBackground(rounded(COLOR_SURFACE, 20, 1, Color.rgb(224, 231, 234)));
        result.setElevation(dp(2));
        return result;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(14);
        return params;
    }

    private TextView sectionLabel(String value) {
        TextView result = text(value, 12, COLOR_ACCENT);
        result.setTypeface(null, Typeface.BOLD);
        result.setLetterSpacing(0.08f);
        return result;
    }

    private TextView sectionTitle(String value) {
        TextView result = text(value, 21, COLOR_PRIMARY_DARK);
        result.setTypeface(null, Typeface.BOLD);
        result.setPadding(0, 0, 0, dp(4));
        return result;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startVoiceCommand() {
        if (!voiceController.canRecognize()) {
            respond("Este aparelho não tem um serviço de reconhecimento de voz disponível.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        status("Preparando o microfone…");
        voiceController.listen();
    }

    private void runTypedCommand() {
        String command = commandInput.getText().toString().trim();
        if (command.isEmpty()) {
            respond("Digite um comando antes de executar.");
            return;
        }
        onRecognized(command);
    }

    @Override
    public void onListeningState(String message) {
        status(message);
    }

    @Override
    public void onRecognized(String text) {
        transcriptView.setText("Último comando: “" + text + "”");
        execute(parser.parse(text));
    }

    @Override
    public void onVoiceError(String message) {
        respond(message);
    }

    private void execute(AssistantCommand command) {
        switch (command.getType()) {
            case HELP -> respond(HELP_TEXT);
            case TIME -> {
                String time = DateFormat.getTimeInstance(DateFormat.SHORT,
                        new Locale("pt", "BR")).format(new Date());
                respond("Agora são " + time + ".");
            }
            case OPEN_APP -> openApp(command.getTarget());
            case LIST_APPS -> listApps();
            case DIAL, SMS -> resolvePhoneAction(command);
            case MAP -> openMap(command.getTarget());
            case ACCESSIBILITY_SETTINGS -> openAccessibilitySettings();
            case WHATSAPP_MESSAGES -> readWhatsAppMessages(false);
            case WHATSAPP_LISTEN_MESSAGES -> readWhatsAppMessages(true);
            case WHATSAPP_CALL -> callWhatsApp(command);
            case PLAY_WHATSAPP_AUDIO -> playLatestWhatsAppAudio();
            case MEDIA_PLAY -> controlMedia(MediaActionController.Action.PLAY);
            case MEDIA_PAUSE -> controlMedia(MediaActionController.Action.PAUSE);
            case MEDIA_NEXT -> controlMedia(MediaActionController.Action.NEXT);
            case UNKNOWN -> respond("Não entendi. Diga ajuda para ouvir exemplos.");
        }
    }

    private void callWhatsApp(AssistantCommand command) {
        String target = command.getTarget().trim();
        if (target.isEmpty()) {
            respond("Para quem?");
            return;
        }
        if (WhatsAppNotificationService.hasRecentConversation(target)) {
            String message = "Ligando para " + target + ".";
            status(message);
            voiceController.speakThen(message, () -> {
                if (!WhatsAppNotificationService.openRecentConversation(target)) {
                    runOnUiThread(() -> resolvePhoneAction(command));
                }
            });
            return;
        }
        resolvePhoneAction(command);
    }

    private void resolvePhoneAction(AssistantCommand command) {
        if (command.getTarget().trim().isEmpty()) {
            respond("Diga o nome do contato ou o número.");
            return;
        }
        String directNumber = PhoneTextParser.extract(command.getTarget());
        if (!directNumber.trim().isEmpty()) {
            completePhoneAction(command, command.getTarget(), directNumber);
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactCommand = command;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS);
            return;
        }

        status("Procurando " + command.getTarget() + "…");
        worker.execute(() -> {
            ContactRepository.Match match = contactRepository.findByName(command.getTarget());
            runOnUiThread(() -> handleContactMatch(command, match));
        });
    }

    private void handleContactMatch(AssistantCommand command, ContactRepository.Match match) {
        switch (match.status) {
            case FOUND -> completePhoneAction(command, match.displayName, match.phoneNumber);
            case NOT_FOUND -> respond(command.getType() == AssistantCommand.Type.WHATSAPP_CALL
                    ? "Não encontrei " + command.getTarget()
                            + " nas conversas recentes nem nos contatos."
                    : "Não encontrei " + command.getTarget() + ".");
            case AMBIGUOUS -> respond("Encontrei mais de um contato: "
                    + String.join(", ", match.alternatives)
                    + ". Diga o nome completo.");
        }
    }

    private void completePhoneAction(AssistantCommand command, String label, String phone) {
        if (command.getType() == AssistantCommand.Type.WHATSAPP_CALL) {
            openWhatsAppCall(label, phone);
        } else if (command.getType() == AssistantCommand.Type.DIAL) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null));
            respondAndStart("Discador para " + label + ".", intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null));
            if (!command.getMessage().trim().isEmpty()) {
                intent.putExtra("sms_body", command.getMessage());
            }
            respondAndStart("Mensagem para " + label + ".", intent);
        }
    }

    private void openMap(String destination) {
        if (destination.trim().isEmpty()) {
            respond("Diga o destino depois de mapa para.");
            return;
        }
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(destination));
        respondAndStart("Abrindo o mapa para " + destination + ".",
                new Intent(Intent.ACTION_VIEW, uri));
    }

    private void openAccessibilitySettings() {
        respondAndStart("Abrindo os ajustes de acessibilidade.",
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openNotificationSettings() {
        respondAndStart("Acesso às notificações.",
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void readWhatsAppMessages(boolean playAudio) {
        List<WhatsAppMessageStore.Message> messages = WhatsAppMessageStore.read(this);
        if (messages.isEmpty()) {
            respond("Nenhuma mensagem nova.");
            return;
        }
        List<WhatsAppMessageStore.Message> recent = messages.stream().limit(4)
                .collect(Collectors.toList());
        String count = recent.size() == 1 ? "Uma mensagem nova. "
                : recent.size() + " mensagens novas. ";
        String spoken = recent.stream().map(item -> item.audio
                ? "Áudio de " + item.sender + "."
                : item.sender + " disse: " + item.text + ".")
                .collect(Collectors.joining(" "));
        boolean hasAudio = recent.stream().anyMatch(item -> item.audio);
        String response = count + spoken;
        if (playAudio && hasAudio) {
            status(response);
            voiceController.speakThen(response,
                    () -> runOnUiThread(this::playLatestWhatsAppAudio));
        } else {
            respond(response);
        }
    }

    private void playLatestWhatsAppAudio() {
        voiceController.stopSpeaking();
        if (WhatsAppNotificationService.openLatestAudio()) {
            status("Reproduzindo áudio.");
        } else {
            respond("Nenhum áudio recente.");
        }
    }

    private void openWhatsAppCall(String label, String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (!phone.trim().startsWith("+") && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        if (digits.length() < 10) {
            respond("O contato " + label + " não tem um número utilizável.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("whatsapp://send?phone=" + digits));
        intent.setPackage("com.whatsapp");
        String message = "Ligando para " + label + ".";
        status(message);
        voiceController.speakThen(message, () -> runOnUiThread(() -> {
            // O prazo começa apenas quando o WhatsApp será aberto, não durante a fala.
            WhatsAppAccessibilityService.requestAction(
                    WhatsAppAccessibilityService.Action.CALL);
            safeStart(intent);
        }));
    }

    private void controlMedia(MediaActionController.Action action) {
        if (mediaController.execute(action)) {
            status(action == MediaActionController.Action.PAUSE
                    ? "Mídia pausada."
                    : action == MediaActionController.Action.NEXT
                            ? "Próxima mídia." : "Reproduzindo mídia.");
        } else {
            respond("Nenhuma mídia ativa.");
        }
    }

    private void openApp(String requestedApp) {
        if (requestedApp.trim().isEmpty()) {
            respond("Diga o nome do aplicativo depois de abrir.");
            return;
        }
        status("Procurando o aplicativo " + requestedApp + "…");
        worker.execute(() -> {
            InstalledAppRepository.Match match = appRepository.findByLabel(requestedApp);
            runOnUiThread(() -> {
                switch (match.status) {
                    case FOUND -> {
                        Intent intent = appRepository.launchIntent(match.app);
                        if (intent == null) {
                            respond("O aplicativo " + match.app.label + " não possui uma tela que eu possa abrir.");
                        } else {
                            respondAndStart("Abrindo " + match.app.label + ".", intent);
                        }
                    }
                    case NOT_FOUND -> respond("Não encontrei um aplicativo chamado " + requestedApp + ".");
                    case AMBIGUOUS -> respond("Encontrei opções parecidas: "
                            + String.join(", ", match.alternatives)
                            + ". Diga o nome completo.");
                }
            });
        });
    }

    private void listApps() {
        status("Consultando os aplicativos disponíveis…");
        worker.execute(() -> {
            List<InstalledAppRepository.AppInfo> apps = appRepository.listLaunchableApps();
            runOnUiThread(() -> {
                if (apps.isEmpty()) {
                    respond("Não encontrei aplicativos que eu possa abrir.");
                    return;
                }
                String firstApps = apps.stream().limit(5).map(item -> item.label)
                        .collect(Collectors.joining(", "));
                String suffix = apps.size() > 5 ? ", entre outros." : ".";
                respond("Encontrei " + apps.size() + " aplicativos. " + firstApps + suffix);
            });
        });
    }

    private void respondAndStart(String message, Intent intent) {
        status(message);
        voiceController.speakThen(message, () -> runOnUiThread(() -> safeStart(intent)));
    }

    private void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            respond("Nenhum aplicativo disponível conseguiu realizar essa ação.");
        }
    }

    private void status(String message) {
        statusView.setText(message);
    }

    private void respond(String message) {
        status(message);
        voiceController.speak(message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_MICROPHONE) {
            if (granted) startVoiceCommand();
            else respond("Sem a permissão do microfone, use o campo de texto para testar comandos.");
        } else if (requestCode == REQUEST_CONTACTS) {
            AssistantCommand command = pendingContactCommand;
            pendingContactCommand = null;
            if (granted && command != null) resolvePhoneAction(command);
            else respond("Sem acesso aos contatos, diga diretamente o número de telefone.");
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        if (voiceController != null) voiceController.destroy();
        super.onDestroy();
    }
}
