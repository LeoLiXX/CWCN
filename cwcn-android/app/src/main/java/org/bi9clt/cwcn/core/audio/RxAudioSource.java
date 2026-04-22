package org.bi9clt.cwcn.core.audio;

public interface RxAudioSource {
    enum State {
        IDLE("空闲"),
        STARTING("启动中"),
        RUNNING("采集中"),
        STOPPING("停止中"),
        ERROR("错误");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    interface Callback {
        void onStateChanged(State state, String detail);

        void onAudioFrame(AudioFrame frame);

        void onError(String message, Throwable throwable);
    }

    String id();

    String displayName();

    boolean isAvailable();

    State state();

    void setCallback(Callback callback);

    void start();

    void stop();

    void release();
}
