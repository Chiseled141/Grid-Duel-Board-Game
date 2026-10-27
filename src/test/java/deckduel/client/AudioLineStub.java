package deckduel.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.*;

/** Records device ownership without opening a real mixer or making test sounds. */
final class AudioLineStub implements SourceDataLine, Clip {
    final List<String> calls = new CopyOnWriteArrayList<>();
    final CountDownLatch written = new CountDownLatch(1), stopped = new CountDownLatch(1), closed = new CountDownLatch(1);
    final CountDownLatch looping = new CountDownLatch(1);
    volatile CountDownLatch holdWrite;
    volatile boolean open, running;
    volatile long position;
    volatile int writes;
    private AudioFormat format;

    @Override public void open(AudioFormat value, int size) { format = value; open(); }
    @Override public void open(AudioFormat value) { format = value; open(); }
    @Override public void open(AudioInputStream stream) { format = stream.getFormat(); open(); }
    @Override public void open(AudioFormat value, byte[] bytes, int offset, int size) { format = value; open(); }
    @Override public void open() { open = true; calls.add("open"); }
    @Override public void start() { running = true; calls.add("start"); }
    @Override public int write(byte[] bytes, int offset, int size) {
        writes++; calls.add("write"); written.countDown();
        try {
            if (holdWrite != null && !holdWrite.await(5, TimeUnit.SECONDS)) throw new AssertionError("write was not released");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        return size;
    }
    @Override public void drain() { calls.add("drain"); }
    @Override public void stop() { running = false; calls.add("stop"); stopped.countDown(); }
    @Override public void flush() { calls.add("flush"); }
    @Override public void close() { open = false; running = false; calls.add("close"); closed.countDown(); }
    @Override public void loop(int count) { running = true; calls.add("loop"); looping.countDown(); }
    @Override public void setMicrosecondPosition(long value) { position = value; calls.add("seek"); }
    @Override public long getMicrosecondPosition() { return position; }
    @Override public long getMicrosecondLength() { return 160_000_000; }
    @Override public boolean isOpen() { return open; }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isActive() { return running; }
    @Override public AudioFormat getFormat() { return format; }
    @Override public int getBufferSize() { return 8192; }
    @Override public int available() { return getBufferSize(); }
    @Override public int getFramePosition() { return 0; }
    @Override public long getLongFramePosition() { return 0; }
    @Override public float getLevel() { return 0; }
    @Override public int getFrameLength() { return 0; }
    @Override public void setFramePosition(int frame) { }
    @Override public void setLoopPoints(int start, int end) { }
    @Override public Line.Info getLineInfo() { return new Line.Info(SourceDataLine.class); }
    @Override public Control[] getControls() { return new Control[0]; }
    @Override public boolean isControlSupported(Control.Type type) { return false; }
    @Override public Control getControl(Control.Type type) { throw new IllegalArgumentException("No controls"); }
    @Override public void addLineListener(LineListener listener) { }
    @Override public void removeLineListener(LineListener listener) { }
}
