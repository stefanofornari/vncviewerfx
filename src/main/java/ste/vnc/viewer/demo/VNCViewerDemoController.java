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
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import ste.vnc.viewer.VNCViewer;


public class VNCViewerDemoController {

    @FXML
    private TextArea infoText;
    @FXML
    private RectangleTracePane rectangleTracePane;
    @FXML
    private VNCViewer viewer;

    private Runnable onClose;

    public Stage stage;


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
            //Stage stage = (Stage) viewer.getScene().getWindow();
            stage.titleProperty().bind(
                viewer.uri.map(uri -> uri == null ? "Untitled App" : uri.toString())
            );
        });
    }

    @FXML
    private void onConnect() {
        // TODO
    }

    @FXML
    private void onCloseWindow() {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void onExit() {
        System.exit(0);
    }

    @FXML
    private void onToggleFullScreen() {
        if (stage != null) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    @FXML
    private void onAbout() {
        // TODO
    }

}
