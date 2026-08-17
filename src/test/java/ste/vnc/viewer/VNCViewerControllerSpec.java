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
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;


import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VNCViewerControllerSpec extends ApplicationTest {

    private DummyVNCViewerController controller;
    private StackPane root;

    @Override
    public void start(Stage stage) {
        VNCViewer viewer = new VNCViewer();
        controller = (DummyVNCViewerController) viewer.controller;

        // Mock socket to prevent NPE inside background thread during connect()
        Socket mockSocket = mock(Socket.class);
        when(mockSocket.inStream()).thenReturn(mock(FdInStream.class));
        when(mockSocket.outStream()).thenReturn(mock(FdOutStream.class));
        ((VNCServiceStub)controller.vnc).socketToReturn(mockSocket);

        root = new StackPane(controller.viewer);
        Scene scene = new Scene(root, 800, 600);

        stage.setScene(scene);
        stage.show(); // Displays window when running with -Pheadful
    }

    @BeforeEach
    void before_each() {
        controller.connect();
    }

    @Test
    @DisplayName("initialize() binds UI visibility properties to VNC connection state")
    void initialize_binds_ui_visibility_to_vnc_connection_state() {
        controller.vnc.connected.set(false);
        then(controller.disconnectionPane.isVisible()).isTrue();
        then(controller.canvas.isVisible()).isFalse();

        controller.vnc.connected.set(true);
        then(controller.disconnectionPane.isVisible()).isFalse();
        then(controller.canvas.isVisible()).isTrue();
    }

    @Test
    @DisplayName("key events on viewer are consumed by event filters")
    void key_events_are_consumed_by_viewer_filters() {
        controller.vnc.connected.set(true);

        AtomicBoolean handlerReached = new AtomicBoolean(false);
        EventHandler<KeyEvent> dummyHandler = e -> handlerReached.set(true);

        // --- 1. KEY_PRESSED ---
        controller.viewer.addEventHandler(KeyEvent.KEY_PRESSED, dummyHandler);
        KeyEvent keyPressed = new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "a", KeyCode.A,
            false, false, false, false
        );
        interact(() -> controller.viewer.fireEvent(keyPressed));
        then(handlerReached.get()).isFalse();
        controller.viewer.removeEventHandler(KeyEvent.KEY_PRESSED, dummyHandler);

        // --- 2. KEY_RELEASED ---
        handlerReached.set(false);
        controller.viewer.addEventHandler(KeyEvent.KEY_RELEASED, dummyHandler);
        KeyEvent keyReleased = new KeyEvent(
            KeyEvent.KEY_RELEASED, "", "a", KeyCode.A,
            false, false, false, false
        );
        interact(() -> controller.viewer.fireEvent(keyReleased));
        then(handlerReached.get()).isFalse();
        controller.viewer.removeEventHandler(KeyEvent.KEY_RELEASED, dummyHandler);

        // --- 3. KEY_TYPED ---
        handlerReached.set(false);
        controller.viewer.addEventHandler(KeyEvent.KEY_TYPED, dummyHandler);
        KeyEvent keyTyped = new KeyEvent(
            KeyEvent.KEY_TYPED, "a", "", KeyCode.UNDEFINED,
            false, false, false, false
        );
        interact(() -> controller.viewer.fireEvent(keyTyped));
        then(handlerReached.get()).isFalse();
        controller.viewer.removeEventHandler(KeyEvent.KEY_TYPED, dummyHandler);
    }

    @Test
    @DisplayName("server clipboard update delegates to setServerClipboardText")
    void server_clipboard_update_delegates_to_set_server_clipboard_text() {
        controller.vnc.clipboard.set("Clipboard From Server");

        then(controller.getClipboard()).isEqualTo("Clipboard From Server");
    }

    @Test
    @DisplayName("removing viewer from scene closes VNC connection")
    void removing_viewer_from_scene_closes_vnc_connection() {
        interact(() -> root.getChildren().remove(controller.viewer));

        then(((VNCServiceStub)controller.vnc).closed).isTrue();
    }
}