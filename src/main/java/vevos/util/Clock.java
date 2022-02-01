package vevos.util;

public class Clock {
    private static final double MILLISECONDS_TO_SECONDS = 0.001;
    private long startTime;

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public double getPassedSeconds() {
        final long msPassed = System.currentTimeMillis() - startTime;
        return (msPassed * MILLISECONDS_TO_SECONDS);
    }

    public String printPassedSeconds() {
        return getPassedSeconds() + "s";
    }
}