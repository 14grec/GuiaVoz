package br.com.exemplo.guiavoz.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registro declarativo: descreve capacidades, nunca baixa nem executa código. */
public final class CapabilityRegistry {
    private static final String CACHE = "capability_registry.json";
    private static final String REMOTE =
            "https://raw.githubusercontent.com/14grec/GuiaVoz/main/capabilities/registry.json";
    private static final List<String> ALLOWED = List.of(
            "OPEN", "READ_NOTIFICATIONS", "READ_SCREEN", "TAP", "TYPE", "SCROLL",
            "SEND_MESSAGE", "REPLY_MESSAGE", "CALL", "PLAY_AUDIO", "CONTROL_MEDIA",
            "SEARCH", "NAVIGATE"
    );

    public static final class Entry {
        public final String packageName;
        public final String label;
        public final List<String> capabilities;

        Entry(String packageName, String label, List<String> capabilities) {
            this.packageName = packageName;
            this.label = label;
            this.capabilities = Collections.unmodifiableList(capabilities);
        }
    }

    private final Context context;
    private Map<String, Entry> entries = Collections.emptyMap();

    public CapabilityRegistry(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void loadBundled() {
        try (InputStream source = context.getAssets().open("capability_registry.json")) {
            entries = parse(read(source));
        } catch (Exception ignored) {
            entries = Collections.emptyMap();
        }
        try (InputStream source = context.openFileInput(CACHE)) {
            Map<String, Entry> cached = parse(read(source));
            if (!cached.isEmpty()) entries = cached;
        } catch (Exception ignored) {}
    }

    public synchronized boolean refreshOnline() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(REMOTE).openConnection();
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(4_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "GuiaVoz/0.5");
            if (connection.getResponseCode() != 200) return false;
            String json = read(connection.getInputStream());
            Map<String, Entry> parsed = parse(json);
            if (parsed.isEmpty()) return false;
            try (java.io.FileOutputStream output = context.openFileOutput(CACHE, Context.MODE_PRIVATE)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
            entries = parsed;
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public synchronized Entry find(String packageName) {
        return entries.get(packageName);
    }

    private Map<String, Entry> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schema", 0) != 1) throw new IllegalArgumentException("schema");
        JSONArray apps = root.getJSONArray("apps");
        if (apps.length() > 500) throw new IllegalArgumentException("registry too large");
        Map<String, Entry> result = new LinkedHashMap<>();
        for (int index = 0; index < apps.length(); index++) {
            JSONObject app = apps.getJSONObject(index);
            String packageName = app.getString("package").trim();
            if (!packageName.matches("[a-zA-Z0-9_.]{3,200}")) continue;
            JSONArray declared = app.getJSONArray("capabilities");
            List<String> safe = new ArrayList<>();
            for (int item = 0; item < declared.length(); item++) {
                String capability = declared.getString(item);
                if (ALLOWED.contains(capability) && !safe.contains(capability)) safe.add(capability);
            }
            result.put(packageName, new Entry(packageName,
                    app.optString("label", packageName), safe));
        }
        return result;
    }

    private String read(InputStream source) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(source, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (result.length() > 256_000) throw new IllegalArgumentException("registry too large");
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }
}
