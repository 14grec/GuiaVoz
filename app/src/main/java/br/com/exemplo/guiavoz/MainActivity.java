package br.com.exemplo.guiavoz;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import br.com.exemplo.guiavoz.assistant.AssistantCommand;
import br.com.exemplo.guiavoz.assistant.CommandParser;
import br.com.exemplo.guiavoz.assistant.PhoneTextParser;
import br.com.exemplo.guiavoz.data.ContactRepository;
import br.com.exemplo.guiavoz.data.InstalledAppRepository;
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
    private static final String HELP_TEXT = "Você pode dizer: abrir WhatsApp; quais aplicativos estão instalados; "
            + "ligar para Maria; enviar mensagem para João dizendo estou chegando; "
            + "abrir mapa para Avenida Paulista; ler minhas mensagens do WhatsApp; "
            + "ligar via WhatsApp para Maria; executar o áudio do WhatsApp; ou que horas são.";

    private final CommandParser parser = new CommandParser();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private VoiceController voiceController;
    private ContactRepository contactRepository;
    private InstalledAppRepository appRepository;
    private TextView statusView;
    private TextView transcriptView;
    private EditText commandInput;
    private Button listenButton;
    private AssistantCommand pendingContactCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createInterface());
        contactRepository = new ContactRepository(getContentResolver());
        appRepository = new InstalledAppRepository(this);
        voiceController = new VoiceController(this, this);

        listenButton.setOnClickListener(view -> startVoiceCommand());
        findViewById(1002).setOnClickListener(view -> runTypedCommand());
        findViewById(1003).setOnClickListener(view -> respond(HELP_TEXT));
        findViewById(1004).setOnClickListener(view -> execute(
                new AssistantCommand(AssistantCommand.Type.ACCESSIBILITY_SETTINGS, "", "", "")));
        findViewById(1005).setOnClickListener(view -> openNotificationSettings());
        findViewById(1006).setOnClickListener(view -> openAccessibilitySettings());

        statusView.post(() -> respond(getString(R.string.status_ready)));
    }

    private View createInterface() {
        int padding = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.WHITE);

        TextView title = text("GuiaVoz", 34, Color.BLACK);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView intro = text(getString(R.string.intro), 20, Color.BLACK);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(0, dp(12), 0, dp(20));
        content.addView(intro, matchWrap());

        listenButton = button(1001, getString(R.string.listen), "Inicia o reconhecimento de voz");
        listenButton.setTextSize(25);
        listenButton.setMinHeight(dp(88));
        listenButton.setBackgroundColor(Color.rgb(18, 70, 160));
        listenButton.setTextColor(Color.WHITE);
        content.addView(listenButton, matchWrap());

        statusView = text(getString(R.string.status_ready), 21, Color.rgb(20, 20, 20));
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(20), 0, dp(12));
        statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(statusView, matchWrap());

        transcriptView = text("Último comando: nenhum", 18, Color.DKGRAY);
        transcriptView.setGravity(Gravity.CENTER);
        transcriptView.setPadding(0, 0, 0, dp(20));
        content.addView(transcriptView, matchWrap());

        commandInput = new EditText(this);
        commandInput.setTextSize(19);
        commandInput.setTextColor(Color.BLACK);
        commandInput.setHintTextColor(Color.DKGRAY);
        commandInput.setHint(getString(R.string.typed_command_hint));
        commandInput.setSingleLine(false);
        commandInput.setMinHeight(dp(64));
        commandInput.setContentDescription("Campo para digitar um comando de teste");
        content.addView(commandInput, matchWrap());

        content.addView(button(1002, getString(R.string.run_text),
                "Executa o comando digitado"), matchWrap());
        content.addView(button(1003, getString(R.string.help),
                "Fala a lista de comandos disponíveis"), matchWrap());
        content.addView(button(1004, getString(R.string.accessibility_settings),
                "Abre os ajustes de acessibilidade do Android"), matchWrap());
        content.addView(button(1005, getString(R.string.notification_settings),
                "Abre os ajustes para permitir a leitura de notificações do WhatsApp"), matchWrap());
        content.addView(button(1006, getString(R.string.whatsapp_accessibility),
                "Abre os ajustes para permitir chamadas e reprodução de áudio no WhatsApp"), matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
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
        button.setTextSize(19);
        button.setAllCaps(false);
        button.setMinHeight(dp(64));
        button.setContentDescription(description);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
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
            case WHATSAPP_MESSAGES -> readWhatsAppMessages();
            case WHATSAPP_CALL -> resolvePhoneAction(command);
            case PLAY_WHATSAPP_AUDIO -> playLatestWhatsAppAudio();
            case UNKNOWN -> respond("Ainda não conheço esse comando. Diga ajuda para ouvir exemplos.");
        }
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

        status("Procurando o contato " + command.getTarget() + "…");
        worker.execute(() -> {
            ContactRepository.Match match = contactRepository.findByName(command.getTarget());
            runOnUiThread(() -> handleContactMatch(command, match));
        });
    }

    private void handleContactMatch(AssistantCommand command, ContactRepository.Match match) {
        switch (match.status) {
            case FOUND -> completePhoneAction(command, match.displayName, match.phoneNumber);
            case NOT_FOUND -> respond("Não encontrei o contato " + command.getTarget() + ".");
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
            respondAndStart("Abrindo o discador para " + label + ". Confirme a chamada no telefone.", intent);
        } else {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null));
            if (!command.getMessage().trim().isEmpty()) {
                intent.putExtra("sms_body", command.getMessage());
            }
            respondAndStart("Abrindo a mensagem para " + label
                    + ". Revise e confirme o envio no aplicativo.", intent);
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
        respondAndStart("Ative o acesso do GuiaVoz às notificações para ler mensagens novas do WhatsApp.",
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void readWhatsAppMessages() {
        List<WhatsAppMessageStore.Message> messages = WhatsAppMessageStore.read(this);
        if (messages.isEmpty()) {
            respond("Não encontrei mensagens novas do WhatsApp. Ative a leitura de notificações e aguarde uma nova mensagem.");
            return;
        }
        List<WhatsAppMessageStore.Message> recent = messages.stream().limit(8)
                .collect(Collectors.toList());
        String spoken = recent.stream().map(item -> item.audio
                ? item.sender + " enviou uma mensagem de voz."
                : item.sender + " disse: " + item.text + ".")
                .collect(Collectors.joining(" "));
        respond("Suas mensagens recentes do WhatsApp. " + spoken);
    }

    private void playLatestWhatsAppAudio() {
        if (WhatsAppNotificationService.openLatestAudio()) {
            status("Abrindo a mensagem de voz no WhatsApp. O GuiaVoz tentará reproduzi-la.");
        } else {
            respond("Não encontrei uma mensagem de voz recente disponível. Ative a leitura de notificações e aguarde um novo áudio.");
        }
    }

    private void openWhatsAppCall(String label, String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (!phone.trim().startsWith("+") && (digits.length() == 10 || digits.length() == 11)) {
            digits = "55" + digits;
        }
        WhatsAppAccessibilityService.requestAction(WhatsAppAccessibilityService.Action.CALL);
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + digits));
        intent.setPackage("com.whatsapp");
        respondAndStart("Abrindo a conversa de " + label
                + " no WhatsApp. O GuiaVoz tentará iniciar a chamada de voz.", intent);
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
                String firstApps = apps.stream().limit(12).map(item -> item.label)
                        .collect(Collectors.joining(", "));
                String suffix = apps.size() > 12
                        ? ". Há mais " + (apps.size() - 12) + " aplicativos." : ".";
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
        statusView.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
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
