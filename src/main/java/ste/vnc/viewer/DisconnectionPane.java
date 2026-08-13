package ste.vnc.viewer;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;

import java.io.IOException;

/**
 * A JavaFX pane that displays an animated disconnection overlay.
 * <p>
 * This pane shows a black background with an animated scramble effect
 * and a centered message indicating connection loss.
 * </p>
 */
public class DisconnectionPane extends Pane {

    public final DisconnectionPaneController controller;

    /**
     * Creates a new DisconnectionPane with animated scramble effect.
     */
    public DisconnectionPane() {
        getStyleClass().add("disconnection-pane");

        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/ste/vnc/viewer/disconnection-pane.fxml")
        );

        try {
            Pane root = loader.load();
            controller = loader.getController();
            getChildren().add(root);
            setVisible(false); // Initially hidden

            visibleProperty().addListener((obs, was, is) -> {
                if (is) {
                    controller.startScramble();
                } else {
                    controller.stopScramble();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to load disconnection pane FXML", e);
        }
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        if (controller.scrambleCanvas != null) {
            controller.scrambleCanvas.setWidth(width);
            controller.scrambleCanvas.setHeight(height);
        }
    }

}
