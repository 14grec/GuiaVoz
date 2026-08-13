package br.com.exemplo.guiavoz.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceController implements RecognitionListener, TextToSpeech.OnInitListener {
    public interface Listener {
        void onListeningState(String message);
        void onRecognized(String text);
        void onVoiceError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final TextToSpeech textToSpeech;
    private final Map<String, Runnable> completionActions = new ConcurrentHashMap<>();
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady;

    public VoiceController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.textToSpeech = new TextToSpeech(context, this);
        this.textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}

            @Override public void onDone(String utteranceId) {
                runCompletion(utteranceId);
            }

            @Override public void onError(String utteranceId) {
                runCompletion(utteranceId);
            }
        });
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(this);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int availability = textToSpeech.setLanguage(new Locale("pt", "BR"));
            ttsReady = availability != TextToSpeech.LANG_MISSING_DATA
                    && availability != TextToSpeech.LANG_NOT_SUPPORTED;
            textToSpeech.setSpeechRate(0.92f);
        }
    }

    public boolean canRecognize() {
        return speechRecognizer != null;
    }

    public void listen() {
        if (speechRecognizer == null) {
            listener.onVoiceError("Não há serviço de reconhecimento de voz disponível.");
            return;
        }
        textToSpeech.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale um comando");
        speechRecognizer.startListening(intent);
    }

    public void speak(String text) {
        if (ttsReady && text != null && !text.trim().isEmpty()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guiavoz-response");
        }
    }

    public void speakThen(String text, Runnable completion) {
        if (!ttsReady || text == null || text.trim().isEmpty()) {
            completion.run();
            return;
        }
        String utteranceId = "guiavoz-action-" + UUID.randomUUID();
        completionActions.put(utteranceId, completion);
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        if (result == TextToSpeech.ERROR) runCompletion(utteranceId);
    }

    private void runCompletion(String utteranceId) {
        Runnable completion = completionActions.remove(utteranceId);
        if (completion != null) completion.run();
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        textToSpeech.stop();
        textToSpeech.shutdown();
        completionActions.clear();
    }

    @Override public void onReadyForSpeech(Bundle params) {
        listener.onListeningState("Pode falar. Estou ouvindo.");
    }

    @Override public void onBeginningOfSpeech() {
        listener.onListeningState("Ouvindo seu comando…");
    }

    @Override public void onEndOfSpeech() {
        listener.onListeningState("Processando o comando…");
    }

    @Override public void onError(int error) {
        listener.onVoiceError(errorMessage(error));
    }

    @Override public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            listener.onVoiceError("Não entendi. Toque em ouvir e tente novamente.");
            return;
        }
        listener.onRecognized(matches.get(0));
    }

    @Override public void onPartialResults(Bundle partialResults) {}

    private String errorMessage(int error) {
        return switch (error) {
            case SpeechRecognizer.ERROR_AUDIO -> "Houve um problema com o microfone.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "A permissão do microfone não está disponível.";
            case SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "O serviço de voz está sem conexão. Tente novamente.";
            case SpeechRecognizer.ERROR_NO_MATCH ->
                    "Não entendi. Fale mais perto do microfone e tente novamente.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "O reconhecedor está ocupado. Aguarde um instante e tente novamente.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "Não ouvi nenhuma fala. Toque em ouvir para tentar novamente.";
            default -> "Não foi possível reconhecer a fala. Tente novamente.";
        };
    }

    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
