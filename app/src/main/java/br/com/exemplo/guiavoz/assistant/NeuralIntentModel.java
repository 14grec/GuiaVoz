package br.com.exemplo.guiavoz.assistant;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Inferência da rede hierárquica treinada em training/train_brain.py. */
public final class NeuralIntentModel {
    private static final byte[] MAGIC = new byte[]{'G', 'V', 'B', '2'};

    public static final class Prediction {
        public final String action;
        public final String object;
        public final String channel;
        public final String label;
        public final float confidence;
        public final float actionConfidence;
        public final float objectConfidence;
        public final float channelConfidence;
        public final float intentConfidence;

        Prediction(Result action, Result object, Result channel, Result intent) {
            this.action = action.label;
            this.object = object.label;
            this.channel = channel.label;
            this.label = intent.label;
            this.actionConfidence = action.confidence;
            this.objectConfidence = object.confidence;
            this.channelConfidence = channel.confidence;
            this.intentConfidence = intent.confidence;
            this.confidence = Math.min(intent.confidence,
                    Math.min(action.confidence, Math.min(object.confidence, channel.confidence)));
        }

        public String semanticKey() {
            return action + ":" + object + ":" + channel;
        }
    }

    private static final class Result {
        final String label;
        final float confidence;
        Result(String label, float confidence) { this.label = label; this.confidence = confidence; }
    }

    private static final class Layer {
        final float[][] weights;
        final float[] biases;
        Layer(float[][] weights, float[] biases) { this.weights = weights; this.biases = biases; }
    }

    private static final class Head {
        final String[] labels;
        final Layer[] layers;
        Head(String[] labels, Layer[] layers) { this.labels = labels; this.layers = layers; }
    }

    private final int inputDimension;
    private final int ngramMinimum;
    private final int ngramMaximum;
    private final Map<String, Head> heads;

    private NeuralIntentModel(int inputDimension, int ngramMinimum, int ngramMaximum,
                              Map<String, Head> heads) {
        this.inputDimension = inputDimension;
        this.ngramMinimum = ngramMinimum;
        this.ngramMaximum = ngramMaximum;
        this.heads = heads;
    }

    public static NeuralIntentModel load(InputStream source) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(source))) {
            for (byte expected : MAGIC) {
                if (input.readByte() != expected) throw new IOException("Modelo neural inválido.");
            }
            int inputDimension = positive(input.readInt(), "dimensão de entrada");
            int ngramMinimum = positive(input.readInt(), "n-grama mínimo");
            int ngramMaximum = positive(input.readInt(), "n-grama máximo");
            int headCount = positive(input.readInt(), "quantidade de cabeças");
            if (headCount > 8) throw new IOException("Modelo com saídas demais.");
            Map<String, Head> heads = new LinkedHashMap<>();
            for (int headIndex = 0; headIndex < headCount; headIndex++) {
                String name = readText(input, 64);
                int labelCount = positive(input.readInt(), "quantidade de rótulos");
                String[] labels = new String[labelCount];
                for (int index = 0; index < labelCount; index++) labels[index] = readText(input, 256);
                int layerCount = positive(input.readInt(), "quantidade de camadas");
                if (layerCount > 8) throw new IOException("Modelo neural com camadas demais.");
                Layer[] layers = new Layer[layerCount];
                int expectedRows = inputDimension;
                for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
                    int rows = positive(input.readInt(), "linhas da camada");
                    int columns = positive(input.readInt(), "colunas da camada");
                    if (rows != expectedRows || rows > 16_384 || columns > 4_096) {
                        throw new IOException("Dimensões neurais incompatíveis.");
                    }
                    float[][] weights = new float[rows][columns];
                    for (int row = 0; row < rows; row++) {
                        for (int column = 0; column < columns; column++) weights[row][column] = input.readFloat();
                    }
                    int biasCount = positive(input.readInt(), "vieses da camada");
                    if (biasCount != columns) throw new IOException("Vieses incompatíveis.");
                    float[] biases = new float[biasCount];
                    for (int index = 0; index < biasCount; index++) biases[index] = input.readFloat();
                    layers[layerIndex] = new Layer(weights, biases);
                    expectedRows = columns;
                }
                if (expectedRows != labelCount) throw new IOException("Saída neural incompatível.");
                heads.put(name, new Head(labels, layers));
            }
            for (String required : new String[]{"action", "object", "channel", "intent"}) {
                if (!heads.containsKey(required)) throw new IOException("Cabeça ausente: " + required);
            }
            return new NeuralIntentModel(inputDimension, ngramMinimum, ngramMaximum, heads);
        }
    }

    public Prediction predict(String text) {
        float[] features = extractFeatures(text);
        return new Prediction(run(heads.get("action"), features), run(heads.get("object"), features),
                run(heads.get("channel"), features), run(heads.get("intent"), features));
    }

    private Result run(Head head, float[] features) {
        float[] values = features;
        for (int layerIndex = 0; layerIndex < head.layers.length; layerIndex++) {
            Layer layer = head.layers[layerIndex];
            float[] output = layer.biases.clone();
            for (int row = 0; row < values.length; row++) {
                float value = values[row];
                if (value == 0f) continue;
                for (int column = 0; column < output.length; column++) output[column] += value * layer.weights[row][column];
            }
            if (layerIndex < head.layers.length - 1) {
                for (int index = 0; index < output.length; index++) output[index] = Math.max(0f, output[index]);
            } else softmax(output);
            values = output;
        }
        int best = 0;
        for (int index = 1; index < values.length; index++) if (values[index] > values[best]) best = index;
        return new Result(head.labels[best], values[best]);
    }

    private float[] extractFeatures(String text) {
        String normalized = "^" + TextNormalizer.normalize(text) + "$";
        float[] result = new float[inputDimension];
        for (int size = ngramMinimum; size <= ngramMaximum; size++) {
            for (int index = 0; index + size <= normalized.length(); index++) {
                long hash = Integer.toUnsignedLong(fnv1a(normalized.substring(index, index + size)));
                result[(int) (hash % inputDimension)] += 1f;
            }
        }
        double sum = 0d;
        for (float value : result) sum += value * value;
        if (sum > 0d) {
            float norm = (float) Math.sqrt(sum);
            for (int index = 0; index < result.length; index++) result[index] /= norm;
        }
        return result;
    }

    private int fnv1a(String value) {
        int hash = 0x811C9DC5;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) { hash ^= item & 0xff; hash *= 0x01000193; }
        return hash;
    }

    private void softmax(float[] values) {
        float maximum = values[0];
        for (float value : values) maximum = Math.max(maximum, value);
        double sum = 0d;
        for (int index = 0; index < values.length; index++) { values[index] = (float) Math.exp(values[index] - maximum); sum += values[index]; }
        for (int index = 0; index < values.length; index++) values[index] /= (float) sum;
    }

    private static String readText(DataInputStream input, int maximum) throws IOException {
        int length = positive(input.readInt(), "tamanho do texto");
        if (length > maximum) throw new IOException("Texto neural excessivamente grande.");
        byte[] encoded = new byte[length];
        input.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static int positive(int value, String label) throws IOException {
        if (value <= 0) throw new IOException("Valor inválido para " + label + ".");
        return value;
    }
}
