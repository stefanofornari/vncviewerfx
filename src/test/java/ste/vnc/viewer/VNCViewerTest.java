package ste.vnc.viewer;

import static org.assertj.core.api.BDDAssertions.then;

import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Unit and UI tests for {@link VNCViewer} connection/disconnected behaviour.
 */
class VNCViewerTest extends ApplicationTest {

    private VNCViewer canvas;

    @Override
    public void start(Stage stage) {
        canvas = new VNCViewer();
        stage.setScene(new javafx.scene.Scene(canvas));
        stage.show();
    }

    @Test
    void initially_disconnected_and_overlay_visible() {
        then(canvas.connected.get()).isFalse();

        // The disconnection pane should be visible when not connected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane).isNotNull();
        then(disconnectionPane.isVisible()).isTrue();
    }

    @Test
    void setConnected_true_clears_flag_and_hides_overlay() {
        // given: starts disconnected
        then(canvas.connected.get()).isFalse();

        // when
        interact(() -> canvas.connected.set(true));

        // then
        then(canvas.connected.get()).isTrue();

        // The disconnection pane should be hidden when connected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane.isVisible()).isFalse();
    }

    @Test
    void setConnected_false_shows_overlay() {
        // given: starts connected
        interact(() -> canvas.connected.set(true));
        then(canvas.connected.get()).isTrue();

        // when: disconnect
        interact(() -> canvas.connected.set(false));

        // then
        then(canvas.connected.get()).isFalse();

        // The disconnection pane should be visible when disconnected
        Node disconnectionPane = lookup(".disconnection-pane").query();
        then(disconnectionPane.isVisible()).isTrue();
    }

}
