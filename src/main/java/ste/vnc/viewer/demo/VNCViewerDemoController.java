package ste.vnc.viewer.demo;

import com.tigervnc.rfb.Configuration;
import com.tigervnc.rfb.LogWriter;
import com.tigervnc.rfb.Security;
import com.tigervnc.rfb.SecurityClient;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import ste.vnc.viewer.VNCViewer;


public class VNCViewerDemoController {

    @FXML
    private TextArea infoText;
    @FXML
    private RectangleTracePane rectangleTracePane;

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

        //
        // not used for now...
        //
        if (rectangleTracePane != null) {
            rectangleTracePane.setOnSelectionChanged(selected -> {
                if (selected == null) {
                    viewer.clearSelectionOverlay();
                } else {
                    viewer.selectionOverlay(selected.x(), selected.y(), selected.width(), selected.height());
                }
            });
        }
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
    private void onOptions() {
        if (stage != null) {
            new OptionsDialogFX(stage).show();
        }
    }

    @FXML
    private void onAbout() {
        // TODO
    }

}
