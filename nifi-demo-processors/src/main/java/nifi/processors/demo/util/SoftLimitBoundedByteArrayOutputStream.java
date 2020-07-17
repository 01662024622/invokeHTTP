package nifi.processors.demo.util;

import java.io.IOException;
import java.io.OutputStream;

public class SoftLimitBoundedByteArrayOutputStream extends OutputStream {

    private final byte[] buffer;
    private int limit;
    private int count;

    public SoftLimitBoundedByteArrayOutputStream(int capacity) {
        this(capacity, capacity);
    }

    public SoftLimitBoundedByteArrayOutputStream(int capacity, int limit) {
        if ((capacity < limit) || (capacity | limit) < 0) {
            throw new IllegalArgumentException("Invalid capacity/limit");
        }
        this.buffer = new byte[capacity];
        this.limit = limit;
        this.count = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (count >= limit) {
            return;
        }
        buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length)
                || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (count + len > limit) {
            len = limit-count;
            if(len == 0){
                return;
            }
        }

        System.arraycopy(b, off, buffer, count, len);
        count += len;
    }

    public void reset(int newlim) {
        if (newlim > buffer.length) {
            throw new IndexOutOfBoundsException("Limit exceeds buffer size");
        }
        this.limit = newlim;
        this.count = 0;
    }

    public void reset() {
        this.limit = buffer.length;
        this.count = 0;
    }

    public int getLimit() {
        return limit;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int size() {
        return count;
    }
}
