package ste.vnc.viewer.demo;

import javafx.application.Application;
import javafx.stage.Stage;

import com.tigervnc.rfb.LogWriter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import ste.vnc.viewer.VNCPane;
import ste.vnc.viewer.VNCConfiguration;

/**
 * Main JavaFX Application for running a VNC viewer demo.
 * <p>
 * This application demonstrates use of {@link VNCPane} as a JavaFX control
 * for displaying a remote VNC desktop. You can launch this class directly to try
 * the VNC viewer, or reference the {@code DesktopCanvas} in your own projects for
 * embedded VNC.
 * </p>
 */
public class VNCViewerFX extends Application {

    private static final LogWriter vlog = new LogWriter(VNCViewerFX.class.getName());


    @Override
    public void start(Stage primaryStage) {

        //
        // This initializes the Configuration object
        //
        VNCConfiguration vncConfiguration = new  VNCConfiguration(getParameters());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
            Parent root = loader.load();

            final VNCViewerFXController controller = loader.getController();

            Scene scene = new Scene(root, 768, 1024);
            primaryStage.setWidth(768); primaryStage.setHeight(1024);
            primaryStage.setTitle("VNC Viewer Demo");
            primaryStage.setScene(scene);

            controller.stage = primaryStage;

            primaryStage.show();
        } catch (Exception e) {
            vlog.error("Failed to start FX viewer: " + e.getMessage());
            // If startup fails, just exit the application
            e.printStackTrace();
            primaryStage.close();
        }
    }

    public static class Param {

        private boolean b;
        private int i;
        private String s;

        Param(boolean b) {
            this.b = b;
        }

        Param(int i) {
            this.i = i;
        }

        Param(String s) {
            this.s = s;
        }

        boolean getValue() {
            return b;
        }

        int getValueInt() {
            return i;
        }

        String getValueStr() {
            return s;
        }

        void setParam(boolean b) {
            this.b = b;
        }

        void setParam(String s) {
            this.s = s;
        }

        String getDefaultStr() {
            return s == null ? "" : s;
        }
    }

    /**
     * Main entrypoint for launching the JavaFX VNC viewer demo.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
