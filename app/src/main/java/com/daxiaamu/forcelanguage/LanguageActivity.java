package com.daxiaamu.forcelanguage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.LocaleList;
import android.os.RemoteException;
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

import rikka.shizuku.Shizuku;

public final class LanguageActivity extends Activity {
    private static final String SPANISH = "es-ES";
    private static final String CHANGE_CONFIGURATION = "android.permission.CHANGE_CONFIGURATION";
    private static final String PREFS = "authorization";
    private static final String PREF_MODE = "auth_mode";
    private static final int REQUEST_SHIZUKU = 41;
    private static final String ROOT = "root";
    private static final String SHIZUKU = "shizuku";
    private static final String AXMANAGER = "axmanager";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String currentMode;
    private String pendingMode;
    private ILocaleService userService;
    private Shizuku.UserServiceArgs userServiceArgs;
    private boolean serviceBinding;
    private boolean mainVisible;
    private boolean authorized;
    private String romDefault = "";

    private TextView authorizationStatus;
    private TextView guideStatus;
    private TextView activeLocale;
    private TextView storedLocale;
    private TextView defaultLocale;
    private ProgressBar progress;
    private Button spanishButton;
    private Button restoreButton;
    private Button rebootButton;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        String mode = pendingMode != null ? pendingMode : currentMode;
        if (isBinderMode(mode)) beginBinderAuthorization(mode, false);
    };
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        userService = null;
        serviceBinding = false;
        authorized = false;
        if (mainVisible) updateAuthorizationStatus(false);
        setButtons(false);
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::onShizukuPermissionResult;

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBinding = false;
            if (binder == null || !binder.pingBinder()) {
                binderAuthorizationFailed(getString(R.string.invalid_service));
                return;
            }
            userService = ILocaleService.Stub.asInterface(binder);
            currentMode = pendingMode != null ? pendingMode : currentMode;
            pendingMode = null;
            saveMode(currentMode);
            authorized = true;
            showMainIfNeeded();
            updateAuthorizationStatus(true);
            refreshLocaleState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            userService = null;
            serviceBinding = false;
            authorized = false;
            if (mainVisible) updateAuthorizationStatus(false);
            setButtons(false);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), LocaleUserService.class.getName()))
                .daemon(false)
                .processNameSuffix("locale")
                .version(3);

        currentMode = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_MODE, null);
        if (isMode(currentMode)) {
            setContentView(buildMainUi());
            mainVisible = true;
        } else {
            showGuide();
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        if (mainVisible) activateSavedMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingMode != null && isBinderMode(pendingMode) && isShizukuAlive()) {
            beginBinderAuthorization(pendingMode, false);
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showGuide() {
        mainVisible = false;
        authorized = false;
        setContentView(buildGuideUi());
    }

    private View buildGuideUi() {
        ScrollView scroll = baseScroll();
        LinearLayout content = baseContent(scroll);
        TextView title = label(getString(R.string.guide_title), 28, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        TextView subtitle = label(getString(R.string.guide_subtitle), 15, R.color.text_secondary);
        LinearLayout.LayoutParams subtitleLayout = layout();
        subtitleLayout.setMargins(0, dp(6), 0, dp(22));
        content.addView(subtitle, subtitleLayout);

        addAuthorizationChoice(content, R.string.choose_root, R.string.root_description,
                v -> chooseMode(ROOT));
        addAuthorizationChoice(content, R.string.choose_shizuku, R.string.shizuku_description,
                v -> chooseMode(SHIZUKU));
        addAuthorizationChoice(content, R.string.choose_axmanager, R.string.axmanager_description,
                v -> chooseMode(AXMANAGER));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        statusRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        guideStatus = label(getString(R.string.choose_authorization), 14, R.color.text_secondary);
        LinearLayout.LayoutParams statusLayout = new LinearLayout.LayoutParams(0, -2, 1f);
        statusLayout.setMargins(dp(12), 0, 0, 0);
        statusRow.addView(guideStatus, statusLayout);
        content.addView(statusRow, spaced());
        return pageWithTopBar(scroll);
    }

    private void addAuthorizationChoice(LinearLayout parent, int titleRes, int descriptionRes,
                                        View.OnClickListener listener) {
        Button button = actionButton(titleRes, true);
        button.setOnClickListener(listener);
        parent.addView(button, buttonLayout());
        TextView description = label(getString(descriptionRes), 13, R.color.text_secondary);
        LinearLayout.LayoutParams params = layout();
        params.setMargins(dp(8), 0, dp(8), dp(16));
        parent.addView(description, params);
    }

    private View buildMainUi() {
        ScrollView scroll = baseScroll();
        LinearLayout content = baseContent(scroll);
        TextView title = label(getString(R.string.title), 28, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        TextView subtitle = label(getString(R.string.subtitle), 15, R.color.text_secondary);
        LinearLayout.LayoutParams subtitleLayout = layout();
        subtitleLayout.setMargins(0, dp(5), 0, dp(24));
        content.addView(subtitle, subtitleLayout);

        LinearLayout authorizationRow = new LinearLayout(this);
        authorizationRow.setGravity(Gravity.CENTER_VERTICAL);
        progress = new ProgressBar(this);
        authorizationRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        authorizationStatus = label(getString(R.string.authorization_checking), 16,
                R.color.text_secondary);
        LinearLayout.LayoutParams authorizationLayout = new LinearLayout.LayoutParams(0, -2, 1f);
        authorizationLayout.setMargins(dp(12), 0, 0, 0);
        authorizationRow.addView(authorizationStatus, authorizationLayout);
        authorizationRow.setOnClickListener(v -> showGuide());
        content.addView(authorizationRow, spaced());

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
        return pageWithTopBar(scroll);
    }

    private View pageWithTopBar(ScrollView scroll) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(getColor(R.color.surface));

        TextView appBar = label(getString(R.string.app_name), 20, android.R.color.white);
        appBar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(20), 0, dp(20), 0);
        appBar.setBackgroundColor(getColor(R.color.accent));
        appBar.setElevation(dp(4));
        page.addView(appBar, new LinearLayout.LayoutParams(-1, dp(56)));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return page;
    }

    private ScrollView baseScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.surface));
        return scroll;
    }

    private LinearLayout baseContent(ScrollView scroll) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content);
        return content;
    }

    private void chooseMode(String mode) {
        pendingMode = mode;
        setGuideBusy(true, getString(R.string.authorization_checking));
        if (ROOT.equals(mode)) requestRoot(true);
        else beginBinderAuthorization(mode, true);
    }

    private void activateSavedMode() {
        setBusy(true);
        updateAuthorizationStatus(false);
        if (ROOT.equals(currentMode)) requestRoot(false);
        else beginBinderAuthorization(currentMode, false);
    }

    private void requestRoot(boolean fromGuide) {
        executor.execute(() -> {
            Result id = root("id");
            if (!id.ok || !id.output.contains("uid=0")) {
                runOnUiThread(() -> rootDenied(id.output, fromGuide));
                return;
            }
            root("pm grant " + getPackageName() + " " + CHANGE_CONFIGURATION);
            runOnUiThread(() -> {
                currentMode = ROOT;
                pendingMode = null;
                saveMode(ROOT);
                authorized = true;
                showMainIfNeeded();
                updateAuthorizationStatus(true);
                refreshLocaleState();
            });
        });
    }

    private void rootDenied(String detail, boolean fromGuide) {
        authorized = false;
        if (fromGuide || !mainVisible) {
            setGuideBusy(false, getString(R.string.authorization_failed));
        } else {
            setBusy(false);
            updateAuthorizationStatus(false);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.root_denied)
                .setMessage(getString(R.string.root_help) + "\n\n" + show(detail))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.retry, (d, w) -> chooseMode(ROOT))
                .show();
    }

    private void beginBinderAuthorization(String mode, boolean showMissingDialog) {
        pendingMode = mode;
        if (!isShizukuAlive()) {
            binderAuthorizationUnavailable(mode, showMissingDialog);
            return;
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindUserService();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                binderAuthorizationFailed(getString(R.string.permission_denied));
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU);
            }
        } catch (Throwable error) {
            binderAuthorizationFailed(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_SHIZUKU) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindUserService();
        else binderAuthorizationFailed(getString(R.string.permission_denied));
    }

    private void bindUserService() {
        if (userService != null) {
            userServiceConnection.onServiceConnected(
                    new ComponentName(getPackageName(), LocaleUserService.class.getName()),
                    userService.asBinder());
            return;
        }
        if (serviceBinding) return;
        serviceBinding = true;
        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection);
        } catch (Throwable error) {
            serviceBinding = false;
            binderAuthorizationFailed(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void binderAuthorizationUnavailable(String mode, boolean showDialog) {
        authorized = false;
        if (mainVisible) {
            setBusy(false);
            updateAuthorizationStatus(false);
        } else {
            setGuideBusy(false, getString(R.string.authorization_waiting));
        }
        if (!showDialog) return;
        boolean ax = AXMANAGER.equals(mode);
        new AlertDialog.Builder(this)
                .setTitle(ax ? R.string.axmanager_not_running_title : R.string.shizuku_not_running_title)
                .setMessage(ax ? R.string.axmanager_not_running_message : R.string.shizuku_not_running_message)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(ax ? R.string.open_axmanager : R.string.open_shizuku,
                        (d, w) -> openManager(ax ? "frb.axeron.manager" : "moe.shizuku.privileged.api"))
                .setPositiveButton(R.string.retry, (d, w) -> beginBinderAuthorization(mode, true))
                .show();
    }

    private void binderAuthorizationFailed(String detail) {
        authorized = false;
        if (mainVisible) {
            setBusy(false);
            updateAuthorizationStatus(false);
        } else {
            setGuideBusy(false, getString(R.string.authorization_failed));
        }
        Toast.makeText(this, getString(R.string.write_failed, show(detail)), Toast.LENGTH_LONG).show();
    }

    private void openManager(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) startActivity(intent);
        else Toast.makeText(this, R.string.manager_not_installed, Toast.LENGTH_LONG).show();
    }

    private boolean isShizukuAlive() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void showMainIfNeeded() {
        if (mainVisible) return;
        setContentView(buildMainUi());
        mainVisible = true;
    }

    private void refreshLocaleState() {
        setBusy(true);
        executor.execute(() -> {
            Result stored = privileged("settings --user current get system system_locales");
            Result product = privileged("getprop ro.product.locale");
            String active = LocaleList.getDefault().toLanguageTags();
            String storedValue = lastLine(stored.output);
            String productValue = lastLine(product.output);
            runOnUiThread(() -> {
                romDefault = isLocale(productValue) ? productValue : firstLocale(active);
                activeLocale.setText(show(active));
                storedLocale.setText(show(storedValue));
                defaultLocale.setText(show(romDefault));
                setBusy(false);
            });
        });
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
        if (!authorized || !isLocale(tag)) return;
        setBusy(true);
        executor.execute(() -> {
            Result result;
            boolean appliedNow = false;
            if (ROOT.equals(currentMode)) {
                result = root("settings --user current put system system_locales " + tag
                        + " && settings --user current get system system_locales");
                if (result.ok && tag.equals(lastLine(result.output))) {
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
            } else {
                result = callSetLocale(tag);
                appliedNow = result.output.contains("LOCALE_APPLIED");
            }
            String saved = tag.equals(lastLine(result.output)) || result.output.contains("\n" + tag)
                    ? tag : lastLine(result.output);
            boolean success = result.ok && tag.equals(saved);
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
        if (!authorized) return;
        setBusy(true);
        executor.execute(() -> {
            Result result = privileged("svc power reboot");
            if (!result.ok) runOnUiThread(() -> {
                setBusy(false);
                Toast.makeText(this, getString(R.string.write_failed, result.output), Toast.LENGTH_LONG).show();
            });
        });
    }

    private Result privileged(String command) {
        if (ROOT.equals(currentMode)) return root(command);
        if (userService == null) return new Result(false, getString(R.string.invalid_service));
        try {
            return decodeServiceResult(userService.execute(command));
        } catch (RemoteException error) {
            return new Result(false, error.getMessage());
        }
    }

    private Result callSetLocale(String tag) {
        if (userService == null) return new Result(false, getString(R.string.invalid_service));
        try {
            return decodeServiceResult(userService.setLocale(tag, getPackageName()));
        } catch (RemoteException error) {
            return new Result(false, error.getMessage());
        }
    }

    private Result decodeServiceResult(String value) {
        if (value == null) return new Result(false, "");
        int newline = value.indexOf('\n');
        if (newline < 0) return new Result(false, value);
        try {
            return new Result(Integer.parseInt(value.substring(0, newline).trim()) == 0,
                    value.substring(newline + 1).trim());
        } catch (NumberFormatException error) {
            return new Result(false, value);
        }
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

    private void updateAuthorizationStatus(boolean connected) {
        if (authorizationStatus == null) return;
        String modeName = getString(modeNameResource(currentMode));
        authorizationStatus.setText(getString(connected
                ? R.string.authorization_connected : R.string.authorization_disconnected, modeName));
        authorizationStatus.setTextColor(getColor(connected ? R.color.success : R.color.error));
    }

    private void setGuideBusy(boolean busy, String message) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (guideStatus != null) guideStatus.setText(message);
    }

    private void setBusy(boolean busy) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        setButtons(!busy && authorized);
    }

    private void setButtons(boolean enabled) {
        if (spanishButton != null) spanishButton.setEnabled(enabled);
        if (restoreButton != null) restoreButton.setEnabled(enabled && isLocale(romDefault));
        if (rebootButton != null) rebootButton.setEnabled(enabled);
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

    private void saveMode(String mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_MODE, mode).apply();
    }

    private int modeNameResource(String mode) {
        if (SHIZUKU.equals(mode)) return R.string.mode_shizuku;
        if (AXMANAGER.equals(mode)) return R.string.mode_axmanager;
        return R.string.mode_root;
    }

    private static boolean isMode(String mode) {
        return ROOT.equals(mode) || SHIZUKU.equals(mode) || AXMANAGER.equals(mode);
    }

    private static boolean isBinderMode(String mode) {
        return SHIZUKU.equals(mode) || AXMANAGER.equals(mode);
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
