package ste.vnc.viewer;

import java.net.URI;
import javafx.beans.property.SimpleObjectProperty;
import static org.assertj.core.api.BDDAssertions.then;

import javafx.scene.Node;
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
