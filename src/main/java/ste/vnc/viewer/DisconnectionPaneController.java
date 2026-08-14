package ste.vnc.viewer;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

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

    protected AnimationTimer scrambleTimer;
    private long lastUpdateNanos = 0;
    private static final long REFRESH_INTERVAL_NANOS = 100_000_000L; // 0.75 seconds
    private static final int CELL_SIZE = 4;

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
    }

    /**
     * Starts the scramble animation.
     */
    public void startScramble() {
        if (scrambleTimer != null) {
            scrambleTimer.start();
        }
    }

    /**
     * Stops the scramble animation.
     */
    public void stopScramble() {
        if (scrambleTimer != null) {
            scrambleTimer.stop();
        }
    }

    private void drawScrambleEffect() {
        if (scrambleCanvas == null || rootPane.getWidth() <= 0 || rootPane.getHeight() <= 0) {
            return;
        }

        // Resize canvas to match pane
        scrambleCanvas.setWidth(rootPane.getWidth());
        scrambleCanvas.setHeight(rootPane.getHeight());

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
