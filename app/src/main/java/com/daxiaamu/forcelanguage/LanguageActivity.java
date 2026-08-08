package com.daxiaamu.forcelanguage;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LanguageActivity extends Activity {
    private static final String SPANISH = "es-ES";
    private static final String CHANGE_CONFIGURATION = "android.permission.CHANGE_CONFIGURATION";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView rootStatus;
    private TextView activeLocale;
    private TextView storedLocale;
    private TextView defaultLocale;
    private ProgressBar progress;
    private Button spanishButton;
    private Button restoreButton;
    private Button rebootButton;
    private boolean rootGranted;
    private String romDefault = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        requestRoot();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.surface));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(38), dp(24), dp(24));
        scroll.addView(content);

        TextView title = label(getString(R.string.title), 28, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView subtitle = label(getString(R.string.subtitle), 15, R.color.text_secondary);
        LinearLayout.LayoutParams subtitleLayout = layout();
        subtitleLayout.setMargins(0, dp(5), 0, dp(24));
        content.addView(subtitle, subtitleLayout);

        LinearLayout rootRow = new LinearLayout(this);
        rootRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(this);
        rootRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        rootStatus = label(getString(R.string.root_checking), 16, R.color.text_secondary);
        LinearLayout.LayoutParams rootLayout = new LinearLayout.LayoutParams(0, -2, 1f);
        rootLayout.setMargins(dp(12), 0, 0, 0);
        rootRow.addView(rootStatus, rootLayout);
        rootRow.setOnClickListener(v -> {
            if (!rootGranted) requestRoot();
        });
        content.addView(rootRow, spaced());

        activeLocale = addStatus(content, R.string.runtime_locale);
        storedLocale = addStatus(content, R.string.stored_locale);
        defaultLocale = addStatus(content, R.string.default_locale);

        spanishButton = actionButton(R.string.enable_spanish, true);
        spanishButton.setOnClickListener(v -> confirmSpanish());
        content.addView(spanishButton, buttonLayout());

        restoreButton = actionButton(R.string.restore_default, false);
        restoreButton.setOnClickListener(v -> confirmRestore());
        content.addView(restoreButton, buttonLayout());

        rebootButton = actionButton(R.string.reboot_phone, false);
        rebootButton.setOnClickListener(v -> confirmReboot());
        content.addView(rebootButton, buttonLayout());

        TextView notice = label(getString(R.string.notice), 14, R.color.text_secondary);
        LinearLayout.LayoutParams noticeLayout = layout();
        noticeLayout.setMargins(0, dp(18), 0, 0);
        content.addView(notice, noticeLayout);

        setButtons(false);
        return scroll;
    }

    private void requestRoot() {
        setBusy(true);
        rootStatus.setText(R.string.root_checking);
        rootStatus.setTextColor(getColor(R.color.text_secondary));
        executor.execute(() -> {
            Result id = root("id");
            if (!id.ok || !id.output.contains("uid=0")) {
                runOnUiThread(() -> rootDenied(id.output));
                return;
            }
            root("pm grant " + getPackageName() + " " + CHANGE_CONFIGURATION);
            Result stored = root("settings --user current get system system_locales");
            Result product = root("getprop ro.product.locale");
            String active = LocaleList.getDefault().toLanguageTags();
            String storedValue = lastLine(stored.output);
            String productValue = lastLine(product.output);
            runOnUiThread(() -> rootReady(active, storedValue, productValue));
        });
    }

    private void rootDenied(String detail) {
        rootGranted = false;
        setBusy(false);
        rootStatus.setText(R.string.root_denied);
        rootStatus.setTextColor(getColor(R.color.error));
        new AlertDialog.Builder(this)
                .setTitle(R.string.root_denied)
                .setMessage(getString(R.string.root_help) + "\n\n" + detail)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.retry, (d, w) -> requestRoot())
                .show();
    }

    private void rootReady(String active, String stored, String product) {
        rootGranted = true;
        romDefault = isLocale(product) ? product : firstLocale(active);
        setBusy(false);
        rootStatus.setText(R.string.root_granted);
        rootStatus.setTextColor(getColor(R.color.success));
        activeLocale.setText(show(active));
        storedLocale.setText(show(stored));
        defaultLocale.setText(show(romDefault));
    }

    private void confirmSpanish() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_spanish_title)
                .setMessage(R.string.confirm_spanish_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> writeLocale(SPANISH))
                .show();
    }

    private void confirmRestore() {
        if (!isLocale(romDefault)) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_restore_title)
                .setMessage(getString(R.string.confirm_restore_message, romDefault))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> writeLocale(romDefault))
                .show();
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_reboot_title)
                .setMessage(R.string.confirm_reboot_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> reboot())
                .show();
    }

    private void writeLocale(String tag) {
        if (!rootGranted || !isLocale(tag)) return;
        setBusy(true);
        executor.execute(() -> {
            Result result = root("settings --user current put system system_locales " + tag
                    + " && settings --user current get system system_locales");
            String saved = lastLine(result.output);
            boolean success = result.ok && tag.equals(saved);
            boolean appliedNow = false;
            if (success) {
                try {
                    LocaleShell.setSystemLocale(tag, getPackageName());
                    appliedNow = true;
                } catch (Throwable directError) {
                    Result helper = root("CLASSPATH=" + shellQuote(getApplicationInfo().sourceDir)
                            + " app_process /system/bin " + LocaleShell.class.getName() + " " + tag
                            + " " + getPackageName());
                    appliedNow = helper.ok && helper.output.contains("LOCALE_APPLIED");
                }
            }
            boolean finalAppliedNow = appliedNow;
            runOnUiThread(() -> {
                setBusy(false);
                if (success) {
                    storedLocale.setText(saved);
                    if (finalAppliedNow) {
                        activeLocale.setText(saved);
                        Toast.makeText(this, getString(R.string.apply_success, saved), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, getString(R.string.write_success, saved), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.write_failed, result.output), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void reboot() {
        if (!rootGranted) return;
        setBusy(true);
        executor.execute(() -> {
            Result result = root("svc power reboot");
            if (!result.ok) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, getString(R.string.write_failed, result.output), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Result root(String command) {
        StringBuilder text = new StringBuilder();
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (text.length() > 0) text.append('\n');
                    text.append(line);
                }
            }
            return new Result(process.waitFor() == 0, text.toString().trim());
        } catch (Exception error) {
            return new Result(false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private TextView addStatus(LinearLayout parent, int titleRes) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(label(getString(titleRes), 13, R.color.text_secondary));
        TextView value = label(getString(R.string.unknown), 19, R.color.text_primary);
        value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        block.addView(value);
        parent.addView(block, spaced());
        return value;
    }

    private Button actionButton(int textRes, boolean primary) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setTextSize(16);
        button.setAllCaps(false);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(getColorStateList(R.color.accent));
        }
        return button;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        return view;
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        setButtons(!busy && rootGranted);
    }

    private void setButtons(boolean enabled) {
        if (spanishButton != null) spanishButton.setEnabled(enabled);
        if (restoreButton != null) restoreButton.setEnabled(enabled && isLocale(romDefault));
        if (rebootButton != null) rebootButton.setEnabled(enabled);
    }

    private String show(String value) {
        return value == null || value.isEmpty() || "null".equals(value)
                ? getString(R.string.unknown) : value;
    }

    private static boolean isLocale(String value) {
        return value != null && value.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*");
    }

    private static String firstLocale(String tags) {
        if (tags == null || tags.isEmpty()) return "";
        int comma = tags.indexOf(',');
        return comma < 0 ? tags : tags.substring(0, comma);
    }

    private static String lastLine(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] lines = value.trim().split("\\R");
        return lines[lines.length - 1].trim();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private LinearLayout.LayoutParams layout() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = layout();
        params.setMargins(0, 0, 0, dp(16));
        return params;
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams params = layout();
        params.setMargins(0, dp(4), 0, dp(8));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Result {
        final boolean ok;
        final String output;

        Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output == null ? "" : output;
        }
    }
}
