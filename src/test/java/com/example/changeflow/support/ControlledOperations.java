package com.example.changeflow.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Bounded coordination only; no arbitrary sleeps or business retries. */
public final class ControlledOperations {
    public final CountDownLatch ready = new CountDownLatch(2);
    private final CountDownLatch[] release = {new CountDownLatch(1), new CountDownLatch(1)};
    public final ThreadLocal<Integer> actor = new ThreadLocal<>();
    private final java.util.Map<Integer, java.util.List<String>> events = new java.util.concurrent.ConcurrentHashMap<>();
    public void record(String event) {
        events.computeIfAbsent(actor.get(), ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(event);
    }
    public java.util.List<String> events(int index) { return java.util.List.copyOf(events.get(index)); }
    public void pause() {
        ready.countDown();
        await(release[actor.get()]);
    }
    public void release(int index) { release[index].countDown(); }
    public void releaseAll() { release(0); release(1); }
    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Controlled operation timed out"); }
        } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
