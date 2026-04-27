package org.bi9clt.cwcn.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.databinding.ActivitySplashBinding;
import org.bi9clt.cwcn.ui.home.HomeActivity;

public final class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 1200L;
    private static final long CARD_ANIMATION_MS = 320L;
    private static final long META_ANIMATION_MS = 280L;
    private static final long BUTTON_ANIMATION_MS = 240L;

    private ActivitySplashBinding binding;
    private boolean navigating;
    private final Runnable delayedOpenRunnable = this::openHome;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.authorCallsignText.setText(BuildConfig.AUTHOR_CALLSIGN);
        binding.buildChannelText.setText(formatBuildChannel(BuildConfig.BUILD_TYPE));
        binding.versionText.setText("Version " + BuildConfig.VERSION_NAME);
        binding.buildTimeText.setText("Build " + BuildConfig.BUILD_TIME);
        applyLaunchIntent(getIntent());
        binding.getRoot().setOnClickListener(view -> openHome());
        binding.enterButton.setOnClickListener(view -> openHome());
        animateEntrance();
        scheduleAutoOpen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLaunchIntent(intent);
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

    private void openHome() {
        if (navigating || isFinishing()) {
            return;
        }
        navigating = true;
        binding.getRoot().removeCallbacks(delayedOpenRunnable);
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void applyLaunchIntent(@Nullable Intent intent) {
        binding.splashHintText.setText("Tap to enter, or wait a moment for CWCN to open.");
    }

    private void scheduleAutoOpen() {
        binding.getRoot().removeCallbacks(delayedOpenRunnable);
        binding.getRoot().postDelayed(delayedOpenRunnable, SPLASH_DELAY_MS);
    }

    private void animateEntrance() {
        prepareAnimatedView(binding.brandPanel, 22f, 0.96f);
        prepareAnimatedView(binding.metaPanel, 16f, 0.98f);
        prepareAnimatedView(binding.enterButton, 10f, 0.98f);
        prepareAnimatedView(binding.splashHintText, 6f, 1.0f);

        binding.getRoot().post(() -> {
            animateView(binding.brandPanel, 0L, CARD_ANIMATION_MS, 1.0f);
            animateView(binding.metaPanel, 120L, META_ANIMATION_MS, 1.0f);
            animateView(binding.enterButton, 220L, BUTTON_ANIMATION_MS, 1.0f);
            animateView(binding.splashHintText, 300L, BUTTON_ANIMATION_MS, 1.0f);
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
            return "Release Build";
        }
        if ("debug".equalsIgnoreCase(buildType)) {
            return "Debug Build";
        }
        return buildType == null || buildType.isEmpty()
                ? "Build"
                : Character.toUpperCase(buildType.charAt(0)) + buildType.substring(1) + " Build";
    }
}
