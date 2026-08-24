package br.com.exemplo.guiavoz.assistant;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Inferência da rede neural treinada em training/train_brain.py. */
public final class NeuralIntentModel {
    private static final byte[] MAGIC = new byte[]{'G', 'V', 'B', '1'};

    public static final class Prediction {
        public final String label;
        public final float confidence;

        Prediction(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    private static final class Layer {
        final float[][] weights;
        final float[] biases;

        Layer(float[][] weights, float[] biases) {
            this.weights = weights;
            this.biases = biases;
        }
    }

    private final int inputDimension;
    private final int ngramMinimum;
    private final int ngramMaximum;
    private final String[] labels;
    private final Layer[] layers;

    private NeuralIntentModel(int inputDimension, int ngramMinimum, int ngramMaximum,
                              String[] labels, Layer[] layers) {
        this.inputDimension = inputDimension;
        this.ngramMinimum = ngramMinimum;
        this.ngramMaximum = ngramMaximum;
        this.labels = labels;
        this.layers = layers;
    }

    public static NeuralIntentModel load(InputStream source) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(source))) {
            for (byte expected : MAGIC) {
                if (input.readByte() != expected) throw new IOException("Modelo neural inválido.");
            }
            int inputDimension = positive(input.readInt(), "dimensão de entrada");
            int ngramMinimum = positive(input.readInt(), "n-grama mínimo");
            int ngramMaximum = positive(input.readInt(), "n-grama máximo");
            int labelCount = positive(input.readInt(), "quantidade de intenções");
            String[] labels = new String[labelCount];
            for (int index = 0; index < labelCount; index++) {
                int length = positive(input.readInt(), "tamanho do rótulo");
                if (length > 256) throw new IOException("Rótulo neural excessivamente grande.");
                byte[] encoded = new byte[length];
                input.readFully(encoded);
                labels[index] = new String(encoded, StandardCharsets.UTF_8);
            }
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
                    for (int column = 0; column < columns; column++) {
                        weights[row][column] = input.readFloat();
                    }
                }
                int biasCount = positive(input.readInt(), "vieses da camada");
                if (biasCount != columns) throw new IOException("Vieses neurais incompatíveis.");
                float[] biases = new float[biasCount];
                for (int index = 0; index < biasCount; index++) biases[index] = input.readFloat();
                layers[layerIndex] = new Layer(weights, biases);
                expectedRows = columns;
            }
            if (expectedRows != labelCount) throw new IOException("Saída neural incompatível.");
            return new NeuralIntentModel(inputDimension, ngramMinimum, ngramMaximum, labels, layers);
        }
    }

    public Prediction predict(String text) {
        float[] values = extractFeatures(text);
        for (int layerIndex = 0; layerIndex < layers.length; layerIndex++) {
            Layer layer = layers[layerIndex];
            float[] output = layer.biases.clone();
            for (int row = 0; row < values.length; row++) {
                float value = values[row];
                if (value == 0f) continue;
                for (int column = 0; column < output.length; column++) {
                    output[column] += value * layer.weights[row][column];
                }
            }
            if (layerIndex < layers.length - 1) {
                for (int index = 0; index < output.length; index++) {
                    output[index] = Math.max(0f, output[index]);
                }
            } else {
                softmax(output);
            }
            values = output;
        }
        int best = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[best]) best = index;
        }
        return new Prediction(labels[best], values[best]);
    }

    private float[] extractFeatures(String text) {
        String normalized = "^" + TextNormalizer.normalize(text) + "$";
        float[] result = new float[inputDimension];
        for (int size = ngramMinimum; size <= ngramMaximum; size++) {
            for (int index = 0; index + size <= normalized.length(); index++) {
                String token = normalized.substring(index, index + size);
                long unsignedHash = Integer.toUnsignedLong(fnv1a(token));
                result[(int) (unsignedHash % inputDimension)] += 1f;
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
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte item : bytes) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private void softmax(float[] values) {
        float maximum = values[0];
        for (float value : values) maximum = Math.max(maximum, value);
        double sum = 0d;
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) Math.exp(values[index] - maximum);
            sum += values[index];
        }
        for (int index = 0; index < values.length; index++) values[index] /= (float) sum;
    }

    private static int positive(int value, String label) throws IOException {
        if (value <= 0) throw new IOException("Valor inválido para " + label + ".");
        return value;
    }
}
