package dev.orbit.clock.core;

/** Accumulates intentional turns only; reversals and pauses reset progress. */
public final class RotaryGesture {
    public enum Action { NONE, SNOOZE, DISMISS }
    private float rotation;
    private long lastTurn;
    private boolean completed;
    public Action turn(float delta, long elapsedMillis) {
        if (completed || !Float.isFinite(delta) || delta == 0) return Action.NONE;
        if (elapsedMillis - lastTurn > 1500 || rotation * delta < 0) rotation = 0;
        lastTurn = elapsedMillis;
        rotation += delta;
        if (rotation <= -3) { completed = true; return Action.SNOOZE; }
        if (rotation >= 6) { completed = true; return Action.DISMISS; }
        return Action.NONE;
    }
    public boolean isSnoozing() { return rotation < 0; }
    public int progress() { return Math.min(100, (int)(Math.abs(rotation) / (rotation < 0 ? 3 : 6) * 100)); }
}
