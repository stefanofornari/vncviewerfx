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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import static org.assertj.core.api.BDDAssertions.then;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.testfx.framework.junit5.ApplicationTest;
import ste.vnc.rfb.ServerInit;
import ste.vnc.rfb.VNCClient;
import ste.xtest.concurrent.SingleTaskExecutorService;

public class VNCServiceLifecycleSpec extends ApplicationTest {

    private VNCServiceStub vncService;
    private Socket mockSocket;

    @Override
    public void start(Stage stage) {
    }

    @BeforeEach
    void before_each() throws Exception {
        mockSocket = mock(Socket.class);

        vncService = new VNCServiceStub();
        vncService.socketToReturn(mockSocket);
        vncService.readLatch = null;

        VNCClient mockVNCClient = mock(ste.vnc.rfb.VNCClient.class);
        ServerInit mockServerInit = new ste.vnc.rfb.ServerInit(
            100, 100, VNCService.NATIVE_PF, "test"
        );
        when(mockVNCClient.getServerInit()).thenReturn(mockServerInit);
        when(mockVNCClient.readMessage()).thenThrow(new IOException("Simulated server disconnect"));
        vncService.mockClient(mockVNCClient);
    }

    @Test
    void start_success() throws InterruptedException {
        CountDownLatch connectedLatch = new CountDownLatch(1);

        vncService.connected.addListener((o, was, is) -> {
            if (is) {
                connectedLatch.countDown();
            }
        });

        vncService.connect("127.0.0.1", 5900);

        then(connectedLatch.await(10, TimeUnit.SECONDS)).isTrue();
        then(vncService.protocolInitialized.get()).isTrue();

        vncService.disconnect();
    }

    @Test
    void connect_failure() throws InterruptedException {
        CountDownLatch closedLatch = new CountDownLatch(1);

        vncService = new VNCServiceStub() {
            @Override
            public void close() {
                super.close();
                closedLatch.countDown();
            }
        };

        vncService.failSocketCreation(true);
        vncService.connect("127.0.0.1", 5900);

        then(closedLatch.await(10, TimeUnit.SECONDS)).isTrue();
        then(vncService.connected.get()).isFalse();
    }

    @Test
    void reconnect_after_disconnection() throws Exception {
        final AtomicInteger executed = new AtomicInteger(0);

        vncService.executor = new SingleTaskExecutorService(() -> executed.incrementAndGet());

        vncService.connect("127.0.0.1", 5900);
        vncService.disconnect();

        then(executed.get()).isEqualTo(1);

        vncService.connect("127.0.0.1", 5900);
        vncService.disconnect();

        then(executed.get()).isEqualTo(2);
    }

    @Test
    void connection_drops_triggers_disconnect() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch disconnectedLatch = new CountDownLatch(1);

        vncService.connected.addListener((obs, wasConnected, isConnected) -> {
            if (isConnected) {
                connectedLatch.countDown();
            } else if (wasConnected) {
                disconnectedLatch.countDown();
            }
        });

        // Mock client configured in @BeforeEach throws IOException on readMessage()
        vncService.connect("127.0.0.1", 5900);

        // 1. Wait until initial connection succeeds
        then(connectedLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // 2. Wait for background task to encounter the dropped connection and close
        then(disconnectedLatch.await(2, TimeUnit.SECONDS)).isTrue();
        then(vncService.connected.get()).isFalse();
    }
}
