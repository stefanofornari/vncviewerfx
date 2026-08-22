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

import java.net.URI;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import ste.xtest.concurrent.SingleTaskExecutorService;

/**
 * Unit and UI tests for {@link VNCViewer} connection/disconnected behaviour.
 */
class VNCViewerSpec extends ApplicationTest {

    private VNCViewer viewer;

    @Override
    public void start(Stage stage) {
        viewer = new VNCViewer();
        stage.setScene(new javafx.scene.Scene(viewer));
        stage.show();
    }

    @Test
    public void initially_disconnected_and_overlay_visible() {
        then(viewer.connected.get()).isFalse();

        // The disconnection pane should be visible when not connected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane).isNotNull();
        then(disconnectionPane.isVisible()).isTrue();
    }

    @Test
    public void setConnected_true_clears_flag_and_hides_overlay() {
        // given: starts disconnected
        then(viewer.connected.get()).isFalse();

        // when
        interact(() -> viewer.controller.vnc.connected.set(true));

        // then
        then(viewer.connected.get()).isTrue();

        // The disconnection pane should be hidden when connected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane.isVisible()).isFalse();
    }

    @Test
    public void setConnected_false_shows_overlay() {
        // given: starts connected
        interact(() -> viewer.controller.vnc.connected.set(true));
        then(viewer.connected.get()).isTrue();

        // when: disconnect
        interact(() -> viewer.controller.vnc.connected.set(false));

        // then
        then(viewer.connected.get()).isFalse();

        // The disconnection pane should be visible when disconnected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane.isVisible()).isTrue();
    }

    @Test
    public void should_have_default_connection_uri_set_to_localhost() {
        then(viewer.uri.get()).isEqualTo(URI.create("vnc://localhost:5900"));
    }

    @Test
    public void should_set_and_get_connection_uri() {
        viewer.uri.set(URI.create("vnc://192.168.1.100:5902"));

        then(viewer.uri.get()).isEqualTo(URI.create("vnc://192.168.1.100:5902"));
    }

    @Test
    public void should_have_default_reconnect_timeout_of_10_seconds() {
        then(viewer.getReconnectTimeout()).isEqualTo(Duration.seconds(10));
    }

    @Test
    public void should_set_and_get_reconnect_timeout() {
        viewer.setReconnectTimeout(Duration.seconds(30));

        then(viewer.getReconnectTimeout()).isEqualTo(Duration.seconds(30));
    }

    @Test
    public void should_set_and_get_reconnect_timeout_via_aliases() {
        viewer.reconnectTimeout(Duration.seconds(25));

        then(viewer.reconnectTimeout()).isEqualTo(Duration.seconds(25));
    }

    @Test
    public void should_throw_on_negative_reconnect_timeout() {
        thenThrownBy(() -> viewer.setReconnectTimeout(Duration.seconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void should_support_javafx_property_binding_for_reconnect_timeout() {
        SimpleObjectProperty<Duration> source =
            new SimpleObjectProperty<>(Duration.seconds(15));

        viewer.reconnectTimeoutProperty().bind(source);

        then(viewer.getReconnectTimeout()).isEqualTo(Duration.seconds(15));

        source.set(Duration.seconds(25));

        then(viewer.getReconnectTimeout()).isEqualTo(Duration.seconds(25));
    }

    @Test
    public void reconnect_timeout_is_applied_to_disconnection_pane() {
        viewer.setReconnectTimeout(Duration.seconds(20));

        then(viewer.controller.disconnectionPane.getReconnectTimeout())
            .isEqualTo(Duration.seconds(20));
    }

    @Test
    public void reconnect_timeout_change_propagates_to_disconnection_pane() {
        then(viewer.controller.disconnectionPane.getReconnectTimeout())
            .isEqualTo(Duration.seconds(10));

        viewer.setReconnectTimeout(Duration.seconds(45));

        then(viewer.controller.disconnectionPane.getReconnectTimeout())
            .isEqualTo(Duration.seconds(45));
    }

    @Test
    public void should_support_javafx_property_binding() {
        SimpleObjectProperty<URI> configSource =
            new SimpleObjectProperty<>(URI.create("vnc://10.0.0.5:5901"));

        viewer.uri.bind(configSource);

        then(viewer.uri.get()).isEqualTo(URI.create("vnc://10.0.0.5:5901"));

        configSource.set(URI.create("vnc://10.0.0.6:5903"));

        then(viewer.uri.get()).isEqualTo(URI.create("vnc://10.0.0.6:5903"));
    }

    @Test
    public void start_and_stop_connects_and_disconnects() {
        interact( () -> viewer.start());

        then(viewer.connected.get()).isTrue();
        then(viewer.controller.vnc.connected.get()).isTrue();
        then(viewer.controller.disconnectionPane.isVisible()).isFalse();

        viewer.controller.vnc.executor = new SingleTaskExecutorService(() -> {
            viewer.controller.vnc.connected.set(false);
        });

        interact( () -> viewer.stop());
        then(viewer.connected.get()).isFalse();
        then(viewer.controller.vnc.connected.get()).isFalse();
        then(viewer.controller.disconnectionPane.isVisible()).isTrue();
    }

}
