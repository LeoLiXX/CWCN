package org.bi9clt.cwcn;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.bi9clt.cwcn.core.app.AppLanguageStore;
import org.bi9clt.cwcn.ui.operate.OperateActivity;

public final class CwcnApplication extends Application {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int startedActivityCount;
    private boolean appForegroundNotified;
    private final Runnable backgroundTransitionRunnable = new Runnable() {
        @Override
        public void run() {
            if (startedActivityCount == 0 && appForegroundNotified) {
                appForegroundNotified = false;
                OperateActivity.requestSharedOperateRxAppForegroundChanged(false);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        new AppLanguageStore(this).applyLanguageMode();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivityCount++;
                mainHandler.removeCallbacks(backgroundTransitionRunnable);
                if (!appForegroundNotified) {
                    appForegroundNotified = true;
                    OperateActivity.requestSharedOperateRxAppForegroundChanged(true);
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (startedActivityCount > 0) {
                    startedActivityCount--;
                }
                if (startedActivityCount == 0) {
                    mainHandler.post(backgroundTransitionRunnable);
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }
}
