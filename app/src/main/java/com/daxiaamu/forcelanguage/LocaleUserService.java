package com.daxiaamu.forcelanguage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class LocaleUserService extends ILocaleService.Stub {
    public LocaleUserService() {}

    @Override
    public String execute(String command) {
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        try {
            Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
            exitCode = process.waitFor();
        } catch (Throwable error) {
            output.append(error.getClass().getSimpleName()).append(": ")
                    .append(error.getMessage());
        }
        return exitCode + "\n" + output.toString().trim();
    }

    @Override
    public String setLocale(String tag, String packageName) {
        if (tag == null || !tag.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*")) {
            return "2\nInvalid locale";
        }
        String saved = execute("settings --user current put system system_locales " + tag
                + " && settings --user current get system system_locales");
        if (!saved.startsWith("0\n") || !tag.equals(lastLine(saved))) return saved;
        try {
            LocaleShell.setSystemLocale(tag, packageName);
            return "0\nLOCALE_APPLIED\n" + tag;
        } catch (Throwable error) {
            return "0\nLOCALE_SAVED\n" + tag + "\n" + error.getClass().getSimpleName();
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static String lastLine(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] lines = value.trim().split("\\R");
        return lines[lines.length - 1].trim();
    }
}
