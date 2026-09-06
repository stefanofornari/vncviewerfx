/* Copyright (C) 2026 VNC Viewer Contributors
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */
package ste.vnc.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VNCServiceStub extends VNCService {

    protected ste.vnc.rfb.ServerInit customServerInit;
    protected boolean failSocketCreation = false;
    protected final AtomicBoolean protocolInitialized = new AtomicBoolean(false);
    protected boolean closed = false;
    protected Socket socketToReturn;
    protected ste.vnc.rfb.VNCClient mockClient;
    protected transient CountDownLatch readLatch;

    public VNCServiceStub() {
        this(Optional.empty());
    }

    public VNCServiceStub(final Optional<ExecutorService> executor) {
        executor.ifPresent(e -> this.executor = e);
    }

    public void customServerInit(ste.vnc.rfb.ServerInit serverInit) {
        this.customServerInit = serverInit;
    }

    public void failSocketCreation(boolean fail) {
        this.failSocketCreation = fail;
    }

    public void socketToReturn(Socket socket) {
        this.socketToReturn = socket;
    }

    public void mockClient(ste.vnc.rfb.VNCClient client) {
        this.mockClient = client;
    }

    @Override
    protected Socket createSocket(String host, int port) throws Exception {
        if (failSocketCreation) {
            throw new java.io.IOException("Simulated connection failure");
        }
        if (socketToReturn != null) {
            return socketToReturn;
        }
        return null;
    }

    @Override
    protected ste.vnc.rfb.VNCClient createVNCClient(Socket sock, boolean shared) throws IOException {
        if (mockClient != null) {
            final InputStream mockIn = mock(InputStream.class);
            final OutputStream mockOut = mock(OutputStream.class);
            when(mockClient.getInputStream()).thenReturn(mockIn);
            when(mockClient.getOutputStream()).thenReturn(mockOut);
            return mockClient;
        }
        return null;
    }

    @Override
    public void disconnect() {
        if (vncProcessing != null) {
            vncProcessing.cancel(true);
        }
        if (readLatch != null) {
            readLatch.countDown();
        }
        super.disconnect();
    }

    public void serverInit(ste.vnc.rfb.ServerInit serverInit) {
        protocolInitialized.set(true);
        if (customServerInit != null) {
            super.serverInit(customServerInit);
        } else {
            super.serverInit(serverInit);
        }
    }

    public void processMsg() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        super.close();
    }
}
