package com.daxiaamu.forcelanguage;

import android.app.Activity;
import android.app.Application;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class FooterApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String FOOTER_TAG = "developer-credit";

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout) || content.getChildCount() == 0) return;

        for (int i = 0; i < content.getChildCount(); i++) {
            if (FOOTER_TAG.equals(content.getChildAt(i).getTag())) return;
        }

        int footerHeight = dp(activity, 48);
        View page = content.getChildAt(0);
        page.setPadding(page.getPaddingLeft(), page.getPaddingTop(),
                page.getPaddingRight(), page.getPaddingBottom() + footerHeight);

        TextView credit = new TextView(activity);
        credit.setTag(FOOTER_TAG);
        credit.setText(R.string.developer_credit);
        credit.setTextColor(activity.getColor(R.color.text_secondary));
        credit.setTextSize(13);
        credit.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        credit.setGravity(Gravity.CENTER);
        credit.setBackgroundColor(activity.getColor(R.color.surface));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, footerHeight, Gravity.BOTTOM);
        content.addView(credit, params);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}

