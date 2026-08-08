package com.daxiaamu.forcelanguage;

import android.content.res.Configuration;
import android.os.LocaleList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/** Runs both in the app and, as a hidden-API fallback, from a root app_process. */
public final class LocaleShell {
    private LocaleShell() {}

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Missing locale tag");
            System.exit(2);
        }
        String packageName = args.length > 1 ? args[1] : "com.daxiaamu.realmeui.spanishenabler";
        try {
            setSystemLocale(args[0], packageName);
            System.out.println("LOCALE_APPLIED");
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                    ? error.getCause() : error;
            System.err.println(cause.getClass().getName() + ": " + cause.getMessage());
            System.exit(1);
        }
    }

    public static void setSystemLocale(String tag, String packageName) throws Exception {
        Locale locale = Locale.forLanguageTag(tag);
        if (locale.getLanguage().isEmpty()) throw new IllegalArgumentException("Invalid locale: " + tag);

        Configuration config = new Configuration();
        config.setLocales(new LocaleList(locale));
        Field userSetLocale = Configuration.class.getField("userSetLocale");
        userSetLocale.setBoolean(config, true);

        Object activityManager = getActivityManager();
        if (invokeIfPresent(activityManager, "updatePersistentConfigurationWithAttribution",
                new Class<?>[]{Configuration.class, String.class, String.class},
                new Object[]{config, packageName, null})) return;
        if (invokeIfPresent(activityManager, "updatePersistentConfiguration",
                new Class<?>[]{Configuration.class}, new Object[]{config})) return;
        if (invokeIfPresent(activityManager, "updateConfiguration",
                new Class<?>[]{Configuration.class}, new Object[]{config})) return;
        throw new NoSuchMethodException("No system configuration update method");
    }

    private static Object getActivityManager() throws Exception {
        try {
            Class<?> activityManager = Class.forName("android.app.ActivityManager");
            Method getService = activityManager.getDeclaredMethod("getService");
            getService.setAccessible(true);
            return getService.invoke(null);
        } catch (ReflectiveOperationException modernError) {
            Class<?> activityManagerNative = Class.forName("android.app.ActivityManagerNative");
            Method getDefault = activityManagerNative.getDeclaredMethod("getDefault");
            getDefault.setAccessible(true);
            return getDefault.invoke(null);
        }
    }

    private static boolean invokeIfPresent(Object target, String name, Class<?>[] types, Object[] args)
            throws Exception {
        try {
            Method method = target.getClass().getMethod(name, types);
            method.setAccessible(true);
            method.invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
