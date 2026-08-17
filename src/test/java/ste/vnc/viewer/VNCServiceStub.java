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

import com.tigervnc.network.Socket;
import com.tigervnc.rfb.CMsgWriterV3;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.tigervnc.rfb.CConnection.RFBSTATE_NORMAL;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import static org.mockito.Mockito.mock;

public class VNCServiceStub extends VNCService {

    protected CMsgWriterV3 customWriter;
    protected int forcedState = RFBSTATE_NORMAL;
    protected Socket socketToReturn;
    protected boolean failSocketCreation = false;
    protected final AtomicBoolean protocolInitialized = new AtomicBoolean(false);
    protected boolean closed = false;

    public VNCServiceStub() {
        this(Optional.empty());
    }
    
    public VNCServiceStub(final Optional<ExecutorService> executor) {
        executor.ifPresent(e -> this.executor = e);
    }

    public void customWriter(CMsgWriterV3 writer) {
        this.customWriter = writer;
    }

    public void forcedState(int state) {
        this.forcedState = state;
    }

    @Override
    public CMsgWriterV3 writer() {
        if (customWriter != null) {
            return customWriter;
        }
        return mock(CMsgWriterV3.class);
    }

    @Override
    public int state() {
        return forcedState;
    }

    public void socketToReturn(Socket socket) {
        this.socketToReturn = socket;
    }

    public void failSocketCreation(boolean fail) {
        this.failSocketCreation = fail;
    }

    @Override
    protected Socket createSocket(String host, int port) throws Exception {
        if (failSocketCreation) {
            throw new java.io.IOException("Simulated connection failure");
        }
        return socketToReturn;
    }

    @Override
    protected void doInitialiseProtocol() {
        protocolInitialized.set(true);
    }

    @Override
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