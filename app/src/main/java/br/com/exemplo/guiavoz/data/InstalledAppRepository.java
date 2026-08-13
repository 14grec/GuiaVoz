package br.com.exemplo.guiavoz.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import br.com.exemplo.guiavoz.assistant.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class InstalledAppRepository {
    public static final class AppInfo {
        public final String label;
        public final String packageName;

        AppInfo(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    public static final class Match {
        public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }

        public final Status status;
        public final AppInfo app;
        public final List<String> alternatives;

        private Match(Status status, AppInfo app, List<String> alternatives) {
            this.status = status;
            this.app = app;
            this.alternatives = alternatives;
        }

        public static Match found(AppInfo app) {
            return new Match(Status.FOUND, app, Collections.emptyList());
        }

        public static Match notFound() {
            return new Match(Status.NOT_FOUND, null, Collections.emptyList());
        }

        public static Match ambiguous(List<String> names) {
            return new Match(Status.AMBIGUOUS, null, names);
        }
    }

    private final Context context;
    private final PackageManager packageManager;

    public InstalledAppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = context.getPackageManager();
    }

    public List<AppInfo> listLaunchableApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results = packageManager.queryIntentActivities(
                    launcherIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        } else {
            //noinspection deprecation
            results = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
        }

        Map<String, AppInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo info : results) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName())) continue;
            String label = String.valueOf(info.loadLabel(packageManager));
            unique.putIfAbsent(packageName, new AppInfo(label, packageName));
        }

        List<AppInfo> apps = new ArrayList<>(unique.values());
        apps.sort(Comparator.comparing(item -> TextNormalizer.normalize(item.label)));
        return apps;
    }

    public Match findByLabel(String requestedLabel) {
        String wanted = TextNormalizer.normalize(requestedLabel);
        if (wanted.isEmpty()) return Match.notFound();
        List<AppInfo> apps = listLaunchableApps();

        List<AppInfo> exact = apps.stream()
                .filter(item -> TextNormalizer.normalize(item.label).equals(wanted))
                .collect(Collectors.toList());
        if (exact.size() == 1) return Match.found(exact.get(0));
        if (exact.size() > 1) return Match.ambiguous(labels(exact));

        List<AppInfo> partial = apps.stream()
                .filter(item -> TextNormalizer.normalize(item.label).contains(wanted))
                .collect(Collectors.toList());
        if (partial.size() == 1) return Match.found(partial.get(0));
        if (partial.size() > 1) return Match.ambiguous(labels(partial));
        return Match.notFound();
    }

    public Intent launchIntent(AppInfo app) {
        return packageManager.getLaunchIntentForPackage(app.packageName);
    }

    private List<String> labels(List<AppInfo> apps) {
        return apps.stream().map(item -> item.label).distinct().limit(5)
                .collect(Collectors.toList());
    }
}
