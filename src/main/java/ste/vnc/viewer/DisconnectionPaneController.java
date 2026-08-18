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

import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Controller for the disconnection pane FXML.
 */
public class DisconnectionPaneController {

    @FXML
    public StackPane rootPane;

    @FXML
    public Canvas scrambleCanvas;

    @FXML
    public Label message;

    /** fx:id must stay "reconnect" — DisconnectionPaneTest looks it up as "#reconnect". */
    @FXML
    public Button reconnect;

    final Logger logger = Logger.getLogger(getClass().getName());

    protected AnimationTimer scrambleTimer;
    private long lastUpdateNanos = 0;
    private static final long REFRESH_INTERVAL_NANOS = 100_000_000L; // 0.1 seconds
    private static final int CELL_SIZE = 4;

    private static final int COUNTDOWN_SECONDS = 10;
    private static final Duration CONNECTING_FEEDBACK = Duration.millis(900);

    private final Timeline countdownTimeline = new Timeline();
    private final PauseTransition retryFeedback = new PauseTransition(CONNECTING_FEEDBACK);
    private int remainingSeconds = COUNTDOWN_SECONDS;
    private boolean retrying = false;
    private Runnable onRetry;

    @FXML
    public void initialize() {
        scrambleTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Only update at the specified interval
                if (now - lastUpdateNanos >= REFRESH_INTERVAL_NANOS) {
                    lastUpdateNanos = now;
                    drawScrambleEffect();
                }
            }
        };

        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> onCountdownTick()));

        retryFeedback.setOnFinished(e -> {
            retrying = false;
            reconnect.setDisable(false);
            startCountdown();
        });

        reconnect.setOnAction(e -> attemptRetry());
    }

    /**
     * Registers the action to run when retry fires, whether from a click on
     * the reconnect button or the auto-retry countdown reaching zero.
     */
    public void onRetry(final Runnable onRetry) {
        this.onRetry = onRetry;
    }

    /**
     * Starts the scramble animation and the auto-retry countdown.
     */
    public void startScramble() {
        if (scrambleTimer != null) {
            scrambleTimer.start();
        }
        startCountdown();
    }

    /**
     * Stops the scramble animation and the auto-retry countdown, including
     * cancelling any in-flight "Connecting…" feedback so a retry started
     * just before the pane was hidden doesn't resurface once it's gone.
     */
    public void stopScramble() {
        if (scrambleTimer != null) {
            scrambleTimer.stop();
        }
        countdownTimeline.stop();
        retryFeedback.stop();
        if (retrying) {
            retrying = false;
            reconnect.setDisable(false);
        }
    }

    private void startCountdown() {
        if (retrying) {
            return; // countdown resumes once the in-flight attempt's feedback finishes
        }
        remainingSeconds = COUNTDOWN_SECONDS;
        updateButtonText();
        countdownTimeline.stop();
        countdownTimeline.playFromStart();
    }

    private void onCountdownTick() {
        remainingSeconds--;
        if (remainingSeconds <= 0) {
            attemptRetry();
        } else {
            updateButtonText();
        }
    }

    private void updateButtonText() {
        Platform.runLater(
            () -> reconnect.setText("Retry connection (" + remainingSeconds + "s)")
        );
    }

    private void attemptRetry() {
        if (retrying) {
            return;
        }
        retrying = true;
        countdownTimeline.stop();
        reconnect.setDisable(true);
        reconnect.setText("Connecting\u2026");

        if (onRetry != null) {
            try {
                onRetry.run();
            } catch (RuntimeException e) {
                logger.warning(() -> "failed to reconnect");
            }
        }

        // Purely cosmetic feedback: the caller's Runnable is fire-and-forget,
        // so we don't know when the real reconnect attempt resolves. Hiding
        // the pane on success is left to the caller, same as before.
        retryFeedback.playFromStart();
    }

    private void drawScrambleEffect() {
        // Fail-fast if the pane hasn't received a positive structural layout size yet
        if (scrambleCanvas == null || rootPane.getWidth() <= 10 || rootPane.getHeight() <= 10) {
            return;
        }

        scrambleCanvas.widthProperty().bind(rootPane.widthProperty());
        scrambleCanvas.heightProperty().bind(rootPane.heightProperty());

        GraphicsContext gc = scrambleCanvas.getGraphicsContext2D();

        // Fill with black background
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, rootPane.getWidth(), rootPane.getHeight());

        // Draw coarse grid of semi-random gray cells
        for (int y = 0; y < rootPane.getHeight(); y += CELL_SIZE) {
            for (int x = 0; x < rootPane.getWidth(); x += CELL_SIZE) {
                double gray = Math.random();
                gc.setFill(new Color(gray, gray, gray, 0.4));
                gc.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            }
        }
    }
}