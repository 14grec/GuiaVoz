package br.com.exemplo.guiavoz;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import br.com.exemplo.guiavoz.assistant.AssistantCommand;
import br.com.exemplo.guiavoz.assistant.CommandParser;
import br.com.exemplo.guiavoz.assistant.NeuralIntentModel;
import br.com.exemplo.guiavoz.assistant.MisunderstoodCommandStore;
import br.com.exemplo.guiavoz.assistant.PhoneTextParser;
import br.com.exemplo.guiavoz.assistant.TextNormalizer;
import br.com.exemplo.guiavoz.data.ContactRepository;
import br.com.exemplo.guiavoz.data.InstalledAppRepository;
import br.com.exemplo.guiavoz.data.CapabilityRegistry;
import br.com.exemplo.guiavoz.media.MediaActionController;
import br.com.exemplo.guiavoz.voice.VoiceController;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppAccessibilityService;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppMessageStore;
import br.com.exemplo.guiavoz.whatsapp.WhatsAppNotificationService;

import java.text.DateFormat;
import java.io.IOException;
import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class MainActivity extends Activity implements VoiceController.Listener {
    private static final int REQUEST_MICROPHONE = 10;
    private static final int REQUEST_CONTACTS = 11;
    private static final int REQUEST_ASSISTANT_ROLE = 12;
    private static final String HELP_TEXT = "Diga: enviar mensagem pelo WhatsApp, responder a última "
            + "mensagem, ouvir mensagens, ligar pelo WhatsApp, abrir Spotify ou ler esta tela.";
    private static final int COLOR_BACKGROUND = Color.rgb(244, 247, 249);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_PRIMARY = Color.rgb(14, 91, 103);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(15, 42, 51);
    private static final int COLOR_ACCENT = Color.rgb(31, 138, 112);
    private static final int COLOR_TEXT_MUTED = Color.rgb(78, 96, 104);

    private CommandParser parser;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private VoiceController voiceController;
    private ContactRepository contactRepository;
    private InstalledAppRepository appRepository;
    private CapabilityRegistry capabilityRegistry;
    private MediaActionController mediaController;
    private TextView statusView;
    private TextView transcriptView;
    private EditText commandInput;
    private Button listenButton;
    private MaterialSwitch lockScreenSwitch;
    private LinearLayout configurationPanel;
    private TextView appScanView;
    private AssistantCommand pendingContactCommand;
    private AssistantCommand lastCommand;
    private PendingSensitiveAction pendingSensitiveAction;

    private enum DialogueStage { RECIPIENT, CONTENT, CONFIRMATION }

    private static final class PendingSensitiveAction {
        AssistantCommand command;
        DialogueStage stage;
        String label = "";
        String phone = "";
        boolean recentConversation;

        PendingSensitiveAction(AssistantCommand command, DialogueStage stage) {
            this.command = command;
            this.stage = stage;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createInterface());
        parser = loadCommandParser();
        contactRepository = new ContactRepository(getContentResolver());
        capabilityRegistry = new CapabilityRegistry(this);
        capabilityRegistry.loadBundled();
        appRepository = new InstalledAppRepository(this);
        appRepository.setRegistry(capabilityRegistry);
        mediaController = new MediaActionController(this);
        voiceController = new VoiceController(this, this);

        listenButton.setOnClickListener(view -> startVoiceCommand());
        findViewById(1002).setOnClickListener(view -> runTypedCommand());
        findViewById(1005).setOnClickListener(view -> openNotificationSettings());
        findViewById(1006).setOnClickListener(view -> openAccessibilitySettings());
        findViewById(1003).setOnClickListener(view -> toggleConfiguration());
        findViewById(1007).setOnClickListener(view -> requestAssistantRole());
        findViewById(1008).setOnClickListener(view -> refreshCapabilities(true));
        lockScreenSwitch.setOnCheckedChangeListener((button, enabled) ->
                getSharedPreferences(WhatsAppAccessibilityService.PREFERENCES, MODE_PRIVATE)
                        .edit().putBoolean(WhatsAppAccessibilityService.LOCK_SCREEN_AFTER_AUDIO,
                                enabled).apply());

        statusView.post(() -> {
            status(getString(R.string.status_ready));
            refreshCapabilities(false);
            if (Intent.ACTION_ASSIST.equals(getIntent().getAction())
                    || Intent.ACTION_VOICE_COMMAND.equals(getIntent().getAction())) {
                startVoiceCommand();
            }
        });
    }

    private CommandParser loadCommandParser() {
        try {
            return new CommandParser(NeuralIntentModel.load(
                    getAssets().open("guiavoz_brain.bin")));
        } catch (IOException | RuntimeException error) {
            return new CommandParser();
        }
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

        content.addView(secondaryButton(1003, "Configurar",
                "Mostra permissões e opções avançadas"), matchWrap());
        configurationPanel = new LinearLayout(this);
        configurationPanel.setOrientation(LinearLayout.VERTICAL);
        configurationPanel.setVisibility(View.GONE);

        LinearLayout testCard = card();
        testCard.addView(sectionTitle("Testar o cérebro"), matchWrap());

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
        configurationPanel.addView(testCard, cardParams());

        LinearLayout settingsCard = card();
        settingsCard.addView(sectionTitle("Permissões e preferências"), matchWrap());
        lockScreenSwitch = new MaterialSwitch(this);
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
        settingsCard.addView(secondaryButton(1007, "Tornar assistente padrão",
                "Solicita ao Android o papel de assistente padrão"), matchWrap());
        settingsCard.addView(secondaryButton(1008, "Atualizar capacidades dos aplicativos",
                "Verifica aplicativos instalados e atualiza o registro online"), matchWrap());
        appScanView = text("Aplicativos: verificando…", 15, COLOR_TEXT_MUTED);
        appScanView.setPadding(0, dp(10), 0, 0);
        settingsCard.addView(appScanView, matchWrap());
        configurationPanel.addView(settingsCard, cardParams());
        content.addView(configurationPanel, matchWrap());

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
        MaterialButton button = new MaterialButton(this);
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
        if (handleDialogue(text)) return;
        String normalized = TextNormalizer.normalize(text);
        if (normalized.contains("entendeu errado") || normalized.contains("comando errado")
                || normalized.contains("nao foi isso")) {
            if (lastCommand != null) {
                MisunderstoodCommandStore.record(this, lastCommand, "user_correction");
                respond("Registrei para melhorar o próximo treino.");
            } else respond("Ainda não há comando anterior para registrar.");
            return;
        }
        AssistantCommand command = parser.parse(text);
        lastCommand = command;
        execute(command);
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
            case WHATSAPP_SEND_MESSAGE, WHATSAPP_REPLY_MESSAGE ->
                    beginWhatsAppMessage(command);
            case PLAY_WHATSAPP_AUDIO -> playLatestWhatsAppAudio();
            case PLAY_NEXT_AUDIO -> playNextWhatsAppAudio();
            case REPEAT_AUDIO -> repeatWhatsAppAudio();
            case CONTINUE_SESSION -> readWhatsAppMessages(true);
            case MEDIA_PLAY -> controlMedia(MediaActionController.Action.PLAY);
            case MEDIA_PAUSE -> controlMedia(MediaActionController.Action.PAUSE);
            case MEDIA_NEXT -> controlMedia(MediaActionController.Action.NEXT);
            case READ_SCREEN -> navigate(WhatsAppAccessibilityService.Action.READ_SCREEN, "");
            case TAP_ELEMENT -> navigate(WhatsAppAccessibilityService.Action.TAP_ELEMENT,
                    command.getTarget());
            case TYPE_TEXT -> navigate(WhatsAppAccessibilityService.Action.TYPE_TEXT,
                    command.getMessage());
            case SCROLL_DOWN -> navigate(WhatsAppAccessibilityService.Action.SCROLL_DOWN, "");
            case SCROLL_UP -> navigate(WhatsAppAccessibilityService.Action.SCROLL_UP, "");
            case BACK -> navigate(WhatsAppAccessibilityService.Action.BACK, "");
            case UNKNOWN -> {
                MisunderstoodCommandStore.record(this, command, "unknown_or_low_confidence");
                respond("Não entendi. Tente dizer a ação, a pessoa e o aplicativo.");
            }
        }
    }

    private boolean handleDialogue(String text) {
        PendingSensitiveAction pending = pendingSensitiveAction;
        if (pending == null) return false;
        String normalized = TextNormalizer.normalize(text);
        if (normalized.equals("cancelar") || normalized.equals("cancele")
                || normalized.equals("nao") || normalized.equals("não")) {
            pendingSensitiveAction = null;
            respond("Cancelado.");
            return true;
        }
        if (pending.stage == DialogueStage.CONFIRMATION) {
            if (normalized.equals("sim") || normalized.equals("confirmar")
                    || normalized.equals("confirme") || normalized.equals("pode")
                    || normalized.equals("pode enviar") || normalized.equals("pode ligar")) {
                pendingSensitiveAction = null;
                executeConfirmed(pending);
            } else {
                ask("Diga sim para confirmar ou não para cancelar.");
            }
            return true;
        }
        if (pending.stage == DialogueStage.RECIPIENT) {
            pending.command = copyCommand(pending.command, text.trim(),
                    pending.command.getMessage());
            if (pending.command.getType() == AssistantCommand.Type.WHATSAPP_CALL) {
                callWhatsApp(pending.command);
            } else if (pending.command.getMessage().isEmpty()) {
                pending.stage = DialogueStage.CONTENT;
                ask("Qual mensagem?");
            } else beginWhatsAppMessage(pending.command);
            return true;
        }
        if (pending.stage == DialogueStage.CONTENT) {
            pending.command = copyCommand(pending.command, pending.command.getTarget(), text.trim());
            beginWhatsAppMessage(pending.command);
            return true;
        }
        return false;
    }

    private AssistantCommand copyCommand(AssistantCommand command, String target, String message) {
        return new AssistantCommand(command.getType(), target, message, command.getOriginal(),
                command.getConfidence(), command.getSource());
    }

    private void beginWhatsAppMessage(AssistantCommand command) {
        String target = command.getTarget();
        if (target.isEmpty() && command.getType() == AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE) {
            target = WhatsAppMessageStore.latestSender(this);
            command = copyCommand(command, target, command.getMessage());
        }
        if (target.isEmpty()) {
            pendingSensitiveAction = new PendingSensitiveAction(command, DialogueStage.RECIPIENT);
            ask("Para quem?");
        } else if (command.getMessage().isEmpty()) {
            pendingSensitiveAction = new PendingSensitiveAction(command, DialogueStage.CONTENT);
            ask("Qual mensagem?");
        } else {
            PendingSensitiveAction pending = new PendingSensitiveAction(command,
                    DialogueStage.CONFIRMATION);
            if (WhatsAppNotificationService.hasRecentConversation(target)) {
                pending.label = target;
                pending.recentConversation = true;
                pendingSensitiveAction = pending;
                askConfirmation(pending);
            } else {
                pendingSensitiveAction = pending;
                resolvePhoneAction(command);
            }
        }
    }

    private void navigate(WhatsAppAccessibilityService.Action action, String target) {
        voiceController.stopSpeaking();
        if (WhatsAppAccessibilityService.requestNavigation(action, target)) {
            status("Executando comando na tela anterior.");
        } else {
            respond("Ative o GuiaVoz nos ajustes de acessibilidade.");
        }
    }

    private void callWhatsApp(AssistantCommand command) {
        String target = command.getTarget().trim();
        if (target.isEmpty()) {
            pendingSensitiveAction = new PendingSensitiveAction(command, DialogueStage.RECIPIENT);
            ask("Para quem?");
            return;
        }
        if (WhatsAppNotificationService.hasRecentConversation(target)) {
            PendingSensitiveAction pending = new PendingSensitiveAction(command,
                    DialogueStage.CONFIRMATION);
            pending.label = target;
            pending.recentConversation = true;
            pendingSensitiveAction = pending;
            askConfirmation(pending);
            return;
        }
        pendingSensitiveAction = new PendingSensitiveAction(command, DialogueStage.CONFIRMATION);
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
            case NOT_FOUND -> respond(isWhatsAppSensitive(command.getType())
                    ? "Não encontrei " + command.getTarget()
                            + " nas conversas recentes nem nos contatos."
                    : "Não encontrei " + command.getTarget() + ".");
            case AMBIGUOUS -> respond("Encontrei mais de um contato: "
                    + String.join(", ", match.alternatives)
                    + ". Diga o nome completo.");
        }
    }

    private void completePhoneAction(AssistantCommand command, String label, String phone) {
        if (isWhatsAppSensitive(command.getType())) {
            PendingSensitiveAction pending = pendingSensitiveAction;
            if (pending == null) pending = new PendingSensitiveAction(command,
                    DialogueStage.CONFIRMATION);
            pending.command = command;
            pending.stage = DialogueStage.CONFIRMATION;
            pending.label = label;
            pending.phone = phone;
            pending.recentConversation = false;
            pendingSensitiveAction = pending;
            askConfirmation(pending);
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

    private boolean isWhatsAppSensitive(AssistantCommand.Type type) {
        return type == AssistantCommand.Type.WHATSAPP_CALL
                || type == AssistantCommand.Type.WHATSAPP_SEND_MESSAGE
                || type == AssistantCommand.Type.WHATSAPP_REPLY_MESSAGE;
    }

    private void askConfirmation(PendingSensitiveAction pending) {
        String label = pending.label.isEmpty() ? pending.command.getTarget() : pending.label;
        if (pending.command.getType() == AssistantCommand.Type.WHATSAPP_CALL) {
            ask("Ligar para " + label + " pelo WhatsApp? Diga sim ou não.");
        } else {
            ask("Enviar para " + label + ": " + pending.command.getMessage()
                    + ". Diga sim ou não.");
        }
    }

    private void executeConfirmed(PendingSensitiveAction pending) {
        AssistantCommand.Type type = pending.command.getType();
        if (type == AssistantCommand.Type.WHATSAPP_CALL) {
            if (pending.recentConversation) {
                if (!WhatsAppNotificationService.openRecentConversation(pending.label)) {
                    respond("A conversa não está mais disponível.");
                } else status("Iniciando chamada pelo WhatsApp.");
            } else openWhatsAppCall(pending.label, pending.phone);
            return;
        }
        if (pending.recentConversation) {
            boolean opened = WhatsAppNotificationService.openRecentConversation(
                    pending.label, WhatsAppAccessibilityService.Action.SEND_MESSAGE,
                    pending.command.getMessage());
            if (opened) status("Enviando mensagem pelo WhatsApp.");
            else respond("A conversa não está mais disponível.");
        } else openWhatsAppMessage(pending.label, pending.phone, pending.command.getMessage());
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
        List<WhatsAppMessageStore.Message> messages = WhatsAppMessageStore.readNew(this);
        if (messages.isEmpty()) {
            respond("Nenhuma mensagem nova.");
            return;
        }
        List<WhatsAppMessageStore.Message> recent = messages.stream().limit(12)
                .collect(Collectors.toList());
        recent.sort(Comparator.comparingLong(item -> item.timestamp));
        String response = groupMessagesForSpeech(recent);
        boolean hasAudio = recent.stream().anyMatch(item -> item.audio);
        List<String> announcedIds = recent.stream().map(item -> item.id)
                .collect(Collectors.toList());
        status(response);
        voiceController.speakThen(response, () -> runOnUiThread(() -> {
            WhatsAppMessageStore.markAnnounced(this, announcedIds);
            if (playAudio && hasAudio) playNextWhatsAppAudio();
        }));
    }

    private String groupMessagesForSpeech(List<WhatsAppMessageStore.Message> messages) {
        Map<String, List<WhatsAppMessageStore.Message>> grouped = new LinkedHashMap<>();
        for (WhatsAppMessageStore.Message message : messages) {
            grouped.computeIfAbsent(message.sender, ignored -> new ArrayList<>()).add(message);
        }
        List<String> groups = new ArrayList<>();
        for (Map.Entry<String, List<WhatsAppMessageStore.Message>> entry : grouped.entrySet()) {
            List<String> contents = new ArrayList<>();
            int audios = 0;
            for (WhatsAppMessageStore.Message message : entry.getValue()) {
                if (message.audio) audios++;
                else contents.add(message.text);
            }
            if (audios == 1) contents.add("um áudio");
            else if (audios > 1) contents.add(audios + " áudios");
            groups.add("Novas mensagens de " + entry.getKey() + ": "
                    + String.join("; ", contents) + ".");
        }
        return String.join(" ", groups);
    }

    private void playLatestWhatsAppAudio() {
        voiceController.stopSpeaking();
        if (WhatsAppNotificationService.openNextAudio(this)) {
            status("Reproduzindo áudio.");
        } else {
            respond("Nenhum áudio recente.");
        }
    }

    private void playNextWhatsAppAudio() {
        voiceController.stopSpeaking();
        if (WhatsAppNotificationService.openNextAudio(this)) {
            status("Reproduzindo próximo áudio.");
        } else {
            respond("Nenhum áudio novo para ouvir.");
        }
    }

    private void repeatWhatsAppAudio() {
        voiceController.stopSpeaking();
        if (WhatsAppNotificationService.repeatLastAudio(this)) {
            status("Repetindo áudio.");
        } else {
            respond("Nenhum áudio anterior para repetir.");
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
        status("Iniciando chamada pelo WhatsApp.");
        WhatsAppAccessibilityService.requestAction(WhatsAppAccessibilityService.Action.CALL);
        safeStart(intent);
    }

    private void openWhatsAppMessage(String label, String phone, String message) {
        String digits = phone.replaceAll("\\D", "");
        if (!phone.trim().startsWith("+") && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        if (digits.length() < 10) {
            respond("O contato " + label + " não tem um número utilizável.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "whatsapp://send?phone=" + digits + "&text=" + Uri.encode(message)));
        intent.setPackage("com.whatsapp");
        WhatsAppAccessibilityService.requestWhatsAppAction(
                WhatsAppAccessibilityService.Action.SEND_MESSAGE, message);
        status("Enviando mensagem pelo WhatsApp.");
        safeStart(intent);
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

    private void toggleConfiguration() {
        boolean show = configurationPanel.getVisibility() != View.VISIBLE;
        configurationPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        status(show ? "Configuração aberta." : "Configuração fechada.");
    }

    private void refreshCapabilities(boolean announce) {
        worker.execute(() -> {
            capabilityRegistry.refreshOnline();
            InstalledAppRepository.ScanReport report = appRepository.scanCapabilities();
            runOnUiThread(() -> {
                String summary = report.installedApps + " apps encontrados; "
                        + report.recognizedApps + " com capacidades específicas.";
                appScanView.setText(summary);
                if (announce) respond(summary);
            });
        });
    }

    private void requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager manager = getSystemService(RoleManager.class);
            if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                respond("Este aparelho não oferece a escolha de assistente padrão.");
                return;
            }
            if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                respond("O GuiaVoz já é o assistente padrão.");
                return;
            }
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                    REQUEST_ASSISTANT_ROLE);
        } else {
            respondAndStart("Escolha o GuiaVoz como aplicativo de assistência.",
                    new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        }
    }

    private void ask(String question) {
        status(question);
        voiceController.speakThen(question, () -> runOnUiThread(() -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) startVoiceCommand();
        }));
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            respond(resultCode == RESULT_OK
                    ? "GuiaVoz definido como assistente padrão."
                    : "A escolha de assistente não foi alterada.");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_ASSIST.equals(intent.getAction())
                || Intent.ACTION_VOICE_COMMAND.equals(intent.getAction())) {
            statusView.post(this::startVoiceCommand);
        }
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
