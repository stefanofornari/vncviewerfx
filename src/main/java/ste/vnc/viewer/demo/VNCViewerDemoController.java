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
package ste.vnc.viewer.demo;

import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.Security;
import com.tigervnc.rfb.SecurityClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import ste.vnc.viewer.VNCViewer;

public class VNCViewerDemoController {

    @FXML
    public TextArea infoText;
    @FXML
    public RectangleTracePane rectangleTracePane;
    @FXML
    public VNCViewer viewer;
    @FXML
    public CustomTitleBar titleBar;

    private static final LogWriter logger = new LogWriter(VNCViewerDemoController.class.getName());

    static {
        logger.setLevel(100);
    }

    @FXML
    public void initialize() {
        // Enable viewer parameters so Configuration.setParam() can see them
        Configuration.enableViewerParams();

        // Restrict security to None only
        SecurityClient.setDefaults();
        Security.enabledSecTypes.clear();
        Security.EnableSecType(Security.secTypeNone);

        Platform.runLater(() -> {
            titleBar.onFullscreen(this::onToggleFullScreen);
            titleBar.onAbout(this::onAbout);
            titleBar.onExit(this::onExit);

            Scene scene = infoText.getScene();
            if (scene != null) {
                KeyCombination quitShortcut = new KeyCodeCombination(
                    KeyCode.Q, KeyCombination.SHORTCUT_DOWN
                );
                scene.getAccelerators().put(quitShortcut, this::onExit);
            }
        });
    }

    @FXML
    private void onConnect() {
        // TODO
    }

    @FXML
    private void onCloseWindow() {
        stage().close();
    }

    void onExit() {
        System.exit(0);
    }

    @FXML
    private void onToggleFullScreen() {
        final Stage stage = stage();
        stage.setFullScreen(!stage.isFullScreen());
    }

    @FXML
    private void onAbout() {
        AboutDialog dialog = new AboutDialog();
        dialog.setOnClose(() -> {
            if (dialog.getScene() != null && dialog.getScene().getWindow() != null) {
                dialog.getScene().getWindow().hide();
            }
        });

        final Scene dialogScene = new javafx.scene.Scene(dialog);
        // Make the Scene background transparent so the VBox rounded corners render smoothly
        dialogScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialogScene.getStylesheets().add(getClass().getResource("VNCViewerDemo.css").toExternalForm());

        javafx.stage.Stage dialogStage = new javafx.stage.Stage();
        dialogStage.initOwner(stage());
        // Use TRANSPARENT style instead of UNDECORATED
        dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        dialogStage.setScene(dialogScene);
        dialogStage.show();
}

    private Stage stage() {
        if (infoText != null) {
            return (Stage)infoText.getScene().getWindow();
        }

        throw new IllegalStateException("no Stage available!");
    }

}