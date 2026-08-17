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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.AnimationTimer;
import static org.assertj.core.api.BDDAssertions.then;

import javafx.stage.Stage;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Unit and UI tests for {@link DisconnectionPane}.
 */
class DisconnectionPaneTest extends ApplicationTest {

    private DisconnectionPane disconnectionPane;

    @Override
    public void start(Stage stage) {
        disconnectionPane = new DisconnectionPane();
        disconnectionPane.resize(100, 100);
        stage.setScene(new javafx.scene.Scene(disconnectionPane));
        stage.show();
    }

    @Test
    void pane_has_correct_message_by_default() {
        then(disconnectionPane.controller.message.getText()).isEqualTo("Connection to VNC server lost");
    }

    @Test
    void pane_is_not_visible_by_default() {
        then(disconnectionPane.isVisible()).isFalse();
    }

    @Test
    void pane_is_resizable() {
        then(disconnectionPane.isResizable()).isTrue();
    }

    @Test
    void scramble_animation_starts_stops_when_pane_becomes_visible_hidden() {
        final AtomicBoolean running = new AtomicBoolean(false);
        disconnectionPane.controller.scrambleTimer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                running.set(true);
            }
        };

        // Given: pane is not visible
        then(disconnectionPane.isVisible()).isFalse();

        // When: make pane visible
        interact(() -> disconnectionPane.setVisible(true));

        then(running.get()).isTrue();

        // When: make pane not visible
        interact(() -> disconnectionPane.setVisible(false));

        running.set(false); await().atMost(250, TimeUnit.MILLISECONDS);

        then(running.get()).isFalse();
    }

    @Test
    void pane_can_be_resized() {
        double newWidth = 200;
        double newHeight = 150;

        interact(() -> disconnectionPane.resize(newWidth, newHeight));

        then(disconnectionPane.getWidth()).isEqualTo(newWidth);
        then(disconnectionPane.getHeight()).isEqualTo(newHeight);
    }

    @Test
    void ui_initialized() {
        disconnectionPane.setVisible(true);

        then(disconnectionPane.controller.message.isVisible());
    }
}
