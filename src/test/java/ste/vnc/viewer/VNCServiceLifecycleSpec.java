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
import com.tigervnc.rdr.FdInStream;
import com.tigervnc.rdr.FdOutStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import static org.assertj.core.api.BDDAssertions.then;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.testfx.framework.junit5.ApplicationTest;

public class VNCServiceLifecycleSpec extends ApplicationTest {

    private VNCServiceStub vncService;
    private Socket mockSocket;
    private FdInStream mockInStream;
    private FdOutStream mockOutStream;

    @Override
    public void start(Stage stage) {
        // No UI needed for these tests; JavaFX platform is initialised by TestFX.
    }

    @BeforeEach
    void before_each() {
        mockSocket = mock(Socket.class);
        mockInStream = mock(FdInStream.class);
        mockOutStream = mock(FdOutStream.class);

        when(mockSocket.inStream()).thenReturn(mockInStream);
        when(mockSocket.outStream()).thenReturn(mockOutStream);
        when(mockSocket.getPeerEndpoint()).thenReturn("127.0.0.1:5900");

        vncService = new VNCServiceStub();
        vncService.socketToReturn(mockSocket);
    }

    @Test
    @DisplayName("start() successfully connects, sets streams, and updates connected property")
    void start_success() throws InterruptedException {
        CountDownLatch connectedLatch = new CountDownLatch(1);

        vncService.connected.addListener((o, was, is) -> {
            if (is) {
                connectedLatch.countDown();
            }
        });

        vncService.connect("127.0.0.1", 5900);

        then(connectedLatch.await(2, TimeUnit.SECONDS)).isTrue();
        then(vncService.protocolInitialized.get()).isTrue();
        verify(mockInStream).setBlockCallback(vncService);

        // Cleanup
        vncService.disconnect();
    }

    @Test
    @DisplayName("start() handles socket failure gracefully and cleans up state")
    void start_failure() throws InterruptedException {
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

        then(closedLatch.await(2, TimeUnit.SECONDS)).isTrue();
        then(vncService.connected.get()).isFalse();
    }

    @Test
    @DisplayName("stop() interrupts RFB thread and close() shuts down socket")
    void stop_interrupts_RFB_thread_and_close() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch disconnectedLatch = new CountDownLatch(1);

        vncService.connected.addListener((o, was, is) -> {
            if (is) {
                connectedLatch.countDown();
            } else {
                disconnectedLatch.countDown();
            }
        });

        vncService.connect("127.0.0.1", 5900);
        then(connectedLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Stop thread and trigger close
        vncService.disconnect();

        then(disconnectedLatch.await(2, TimeUnit.SECONDS)).isTrue();
        then(vncService.connected.get()).isFalse();
        verify(mockSocket, timeout(1000)).shutdown();
    }
}
