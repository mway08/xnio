/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * An input stream that reads from a sequence of byte buffers.
 */
public final class ByteBufferInputStream extends InputStream {
    private final ByteBuffer[] buffers;
    private int position = 0;

    private static ByteBuffer[] unroll(Iterator<ByteBuffer> byteBuffers, int count) {
        if (byteBuffers.hasNext()) {
            final ByteBuffer buffer = byteBuffers.next();
            ByteBuffer[] bufs = unroll(byteBuffers, count+1);
            bufs[count] = buffer;
            return bufs;
        } else {
            return new ByteBuffer[count];
        }
    }

    /**
     * Create a byte buffer input stream instance.
     *
     * @param buffers the buffers to read from
     */
    public ByteBufferInputStream(final Iterator<ByteBuffer> buffers) {
        this(unroll(buffers, 0));
    }

    /**
     * Create a byte buffer input stream instance.
     *
     * @param buffers the buffers to read from
     */
    public ByteBufferInputStream(final Iterable<ByteBuffer> buffers) {
        this(buffers.iterator());
    }

    /**
     * Create a byte buffer input stream instance.
     *
     * @param buffer the buffer to read from
     */
    public ByteBufferInputStream(final ByteBuffer buffer) {
        this(new ByteBuffer[] { buffer });
    }

    /**
     * Create a byte buffer input stream instance.
     *
     * @param buffers the buffers to read from
     */
    public ByteBufferInputStream(final ByteBuffer[] buffers) {
        this.buffers = buffers;
    }

    public int read(final byte[] bytes, final int offs, final int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        int cnt = 0;
        while (position < buffers.length) {
            final ByteBuffer buffer = buffers[position];
            if (! buffer.hasRemaining()) {
                position ++;
                continue;
            }
            final int size = Math.min(buffer.remaining(), len - cnt);
            buffer.get(bytes, offs + cnt, size);
            cnt += size;
            if (cnt == len) {
                break;
            }
        }
        return cnt == 0 ? -1 : cnt;
    }

    public int read() throws IOException {
        while (position < buffers.length) {
            final ByteBuffer buffer = buffers[position];
            if (buffer.hasRemaining()) {
                return buffer.get() & 0xff;
            } else {
                position ++;
            }
        }
        return -1;
    }

    public long skip(final long n) throws IOException {
        long cnt = 0;
        while (position < buffers.length) {
            final ByteBuffer buffer = buffers[position];
            if (! buffer.hasRemaining()) {
                position ++;
                continue;
            }
            final int size = (int) Math.min(buffer.remaining(), n - (long)cnt);
            buffer.position(buffer.position() + size);
            if (cnt == n) {
                break;
            }
        }
        return cnt;
    }

    public int available() throws IOException {
        int remaining = 0;
        for (int i = position; i < buffers.length; i ++) {
            remaining += buffers[i].remaining();
            if (remaining < 0) {
                return Integer.MAX_VALUE;
            }
        }
        return remaining;
    }

    public void close() throws IOException {
        position = buffers.length;
    }

    public void mark(final int readlimit) {
        throw new UnsupportedOperationException("mark()");
    }

    public void reset() throws IOException {
        throw new UnsupportedOperationException("reset()");
    }

    public boolean markSupported() {
        return false;
    }
}