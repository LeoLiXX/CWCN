package org.bi9clt.cwcn.core.rig;

public interface RigControlAdapter {
    String id();

    String displayName();

    String describeCapabilities();

    boolean supportsTextToCw();

    boolean supportsPttControl();

    boolean keyDown();

    boolean keyUp();

    boolean sendText(String text);
}
