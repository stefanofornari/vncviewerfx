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

    @Test
    void reconnect_button_shows_default_text_before_pane_is_visible() {
        then(disconnectionPane.controller.reconnect.getText()).isEqualTo("Retry connection");
    }

    @Test
    void reconnect_button_shows_countdown_once_pane_is_visible() {
        interact(() -> disconnectionPane.setVisible(true));

        then(disconnectionPane.controller.reconnect.getText()).isEqualTo("Retry connection (10s)");
    }

    @Test
    void reconnect_triggers_onRetry() {
        final AtomicBoolean clicked = new AtomicBoolean(false);
        disconnectionPane.onRetry(() -> clicked.set(true));

        // the button is only hit-testable once the pane is actually showing
        interact(() -> disconnectionPane.setVisible(true));
        clickOn("#reconnect");

        then(clicked.get()).isTrue();
    }

    @Test
    void reconnect_button_disabled_while_retry_is_in_progress() {
        interact(() -> disconnectionPane.setVisible(true));

        clickOn("#reconnect");

        then(disconnectionPane.controller.reconnect.isDisabled()).isTrue();

        await().atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> then(disconnectionPane.controller.reconnect.isDisabled()).isFalse());
    }

    @Test
    void hiding_pane_mid_retry_cancels_the_pending_feedback_and_leaves_button_enabled() {
        interact(() -> disconnectionPane.setVisible(true));

        clickOn("#reconnect");
        then(disconnectionPane.controller.reconnect.isDisabled()).isTrue();

        // hide the pane before the "Connecting…" feedback would normally finish
        interact(() -> disconnectionPane.setVisible(false));

        then(disconnectionPane.controller.reconnect.isDisabled()).isFalse();

        // wait past the feedback window to confirm it doesn't fire and
        // re-enable/re-arm the countdown after the fact
        await().atMost(2, TimeUnit.SECONDS)
               .during(1200, TimeUnit.MILLISECONDS)
               .until(() -> !disconnectionPane.controller.reconnect.isDisabled());
    }

    @Test
    void retrying_continues_when_onRetry_throws() {
        // simulates e.g. VNCService.connect() throwing RejectedExecutionException
        // because the server is still down
        disconnectionPane.onRetry(() -> {
            throw new RuntimeException("server unreachable");
        });

        interact(() -> disconnectionPane.setVisible(true));
        clickOn("#reconnect");

        then(disconnectionPane.controller.reconnect.isDisabled()).isTrue();

        // without catching the exception, attemptRetry() never reaches the
        // code that re-enables the button, so this would time out
        await().atMost(2, TimeUnit.SECONDS)
               .untilAsserted(() -> then(disconnectionPane.controller.reconnect.isDisabled()).isFalse());

        // and the countdown must actually be re-armed, not just the button re-enabled
        then(disconnectionPane.controller.reconnect.getText()).isEqualTo("Retry connection (10s)");
    }
}