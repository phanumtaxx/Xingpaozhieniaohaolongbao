package dev.xingclient.render;

/** Resets elapsed frame time after a deliberate game-thread pause. */
public interface ResumeClock {
    void xing$resetAfterPause(long nowMillis);
}
