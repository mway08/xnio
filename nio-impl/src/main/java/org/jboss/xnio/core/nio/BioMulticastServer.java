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

package org.jboss.xnio.core.nio;

import java.net.SocketAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.Collections;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.jboss.xnio.channels.MulticastDatagramChannel;
import org.jboss.xnio.channels.UnsupportedOptionException;
import org.jboss.xnio.channels.Configurable;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.spi.UdpServer;
import org.jboss.xnio.spi.Lifecycle;
import org.jboss.xnio.spi.SpiUtils;

/**
 *
 */
public final class BioMulticastServer implements Lifecycle, UdpServer {
    private static final Logger log = Logger.getLogger(BioMulticastServer.class);

    private IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory;
    private BioMulticastChannelImpl[] channels = new BioMulticastChannelImpl[0];
    private SocketAddress[] bindAddresses = new SocketAddress[0];

    private int receiveBufferSize = -1;
    private boolean reuseAddress = false;
    private int sendBufferSize = -1;
    private int trafficClass = -1;
    private boolean broadcast = false;
    private Executor executor;
    private ExecutorService executorService;

    public IoHandlerFactory<? super MulticastDatagramChannel> getHandlerFactory() {
        return handlerFactory;
    }

    public void setHandlerFactory(final IoHandlerFactory<? super MulticastDatagramChannel> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public BioMulticastChannelImpl[] getChannels() {
        return channels;
    }

    public void setChannels(final BioMulticastChannelImpl[] channels) {
        this.channels = channels;
    }

    public SocketAddress[] getBindAddresses() {
        return bindAddresses;
    }

    public void setBindAddresses(final SocketAddress[] bindAddresses) {
        this.bindAddresses = bindAddresses;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(final int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(final boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(final int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public void setTrafficClass(final int trafficClass) {
        this.trafficClass = trafficClass;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(final boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    @SuppressWarnings({"unchecked"})
    public void start() throws IOException {
        if (executor == null) {
            executor = executorService = Executors.newCachedThreadPool();
        }
        if (handlerFactory == null) {
            throw new NullPointerException("handlerFactory is null");
        }
        final int bindCount = bindAddresses.length;
        final MulticastSocket[] sockets = new MulticastSocket[bindCount];
        boolean ok = false;
        try {
            channels = new BioMulticastChannelImpl[bindCount];
            for (int i = 0; i < bindCount; i++) {
                if (bindAddresses[i] == null) {
                    throw new NullPointerException("bindAddresses[i] is null");
                }
                MulticastSocket socket = new MulticastSocket(bindAddresses[i]);
                socket.setBroadcast(broadcast);
                if (receiveBufferSize != -1) socket.setReceiveBufferSize(receiveBufferSize);
                socket.setReuseAddress(reuseAddress);
                if (sendBufferSize != -1) socket.setSendBufferSize(sendBufferSize);
                if (trafficClass != -1) socket.setTrafficClass(trafficClass);
                sockets[i] = socket;
                final IoHandler<? super MulticastDatagramChannel> handler = handlerFactory.createHandler();
                channels[i] = new BioMulticastChannelImpl(sendBufferSize, receiveBufferSize, executor, handler, socket);
                channels[i].open();
                if (! SpiUtils.handleOpened(handler, channels[i])) try {
                    socket.close();
                } catch (Throwable t) {
                    log.trace(t, "Socket close failed");
                }
            }
            ok = true;
        } finally {
            if (! ok) {
                for (MulticastSocket socket : sockets) {
                    if (socket != null) try {
                        socket.close();
                    } catch (Throwable t) {
                        log.trace(t, "Socket close failed");
                    }
                }
            }
        }
    }

    public void stop() {
        if (executorService != null) {
            try {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        executorService.shutdown();
                        return null;
                    }
                });
            } catch (Throwable t) {
                log.trace(t, "Shutting down executor service failed");
            }
        }
        executor = executorService = null;
        if (channels != null) {
            for (BioMulticastChannelImpl channel : channels) {
                IoUtils.safeClose(channel);
            }
        }
    }

    public Object getOption(final String name) throws UnsupportedOptionException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }

    public Map<String, Class<?>> getOptions() {
        return Collections.emptyMap();
    }

    public Configurable setOption(final String name, final Object value) throws IllegalArgumentException, IOException {
        throw new UnsupportedOptionException("No options supported by this server type");
    }
}