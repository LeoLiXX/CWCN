package org.bi9clt.cwcn.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.databinding.ActivitySplashBinding;
import org.bi9clt.cwcn.ui.operate.OperateActivity;

public final class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 1100L;
    private static final long BRAND_ANIMATION_MS = 320L;
    private static final long META_ANIMATION_MS = 280L;

    private ActivitySplashBinding binding;
    private boolean navigating;
    private final Runnable delayedOpenRunnable = this::openOperate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.authorCallsignText.setText(BuildConfig.AUTHOR_CALLSIGN);
        binding.buildChannelText.setText(formatBuildChannel(BuildConfig.BUILD_TYPE));
        binding.versionText.setText(getString(R.string.splash_version, BuildConfig.VERSION_NAME));
        binding.buildTimeText.setText(getString(R.string.splash_build_time, BuildConfig.BUILD_TIME));
        animateEntrance();
        scheduleAutoOpen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        scheduleAutoOpen();
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            binding.getRoot().removeCallbacks(delayedOpenRunnable);
        }
        binding = null;
        super.onDestroy();
    }

    private void openOperate() {
        if (navigating || isFinishing()) {
            return;
        }
        navigating = true;
        binding.getRoot().removeCallbacks(delayedOpenRunnable);
        Intent intent = new Intent(this, OperateActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void scheduleAutoOpen() {
        binding.getRoot().removeCallbacks(delayedOpenRunnable);
        binding.getRoot().postDelayed(delayedOpenRunnable, SPLASH_DELAY_MS);
    }

    private void animateEntrance() {
        prepareAnimatedView(binding.brandPanel, 22f, 0.96f);
        prepareAnimatedView(binding.metaPanel, 16f, 0.98f);

        binding.getRoot().post(() -> {
            animateView(binding.brandPanel, 0L, BRAND_ANIMATION_MS, 1.0f);
            animateView(binding.metaPanel, 120L, META_ANIMATION_MS, 1.0f);
        });
    }

    private void prepareAnimatedView(View view, float translationYPx, float startScale) {
        view.setAlpha(0f);
        view.setTranslationY(translationYPx);
        view.setScaleX(startScale);
        view.setScaleY(startScale);
    }

    private void animateView(View view, long startDelayMs, long durationMs, float endScale) {
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(endScale)
                .scaleY(endScale)
                .setStartDelay(startDelayMs)
                .setDuration(durationMs)
                .start();
    }

    private String formatBuildChannel(String buildType) {
        if ("release".equalsIgnoreCase(buildType)) {
            return getString(R.string.splash_build_channel_release);
        }
        if ("debug".equalsIgnoreCase(buildType)) {
            return getString(R.string.splash_build_channel_debug);
        }
        return buildType == null || buildType.isEmpty()
                ? getString(R.string.splash_build_channel_unknown)
                : getString(R.string.splash_build_channel_named, buildType);
    }
}
