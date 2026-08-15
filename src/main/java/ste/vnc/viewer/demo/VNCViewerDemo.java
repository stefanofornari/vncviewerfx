package ste.vnc.viewer.demo;

import javafx.application.Application;
import javafx.stage.Stage;

import com.tigervnc.rfb.LogWriter;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import ste.vnc.viewer.VNCViewer;
import ste.vnc.viewer.VNCConfiguration;

/**
 * Main JavaFX Application for running a VNC viewer demo.
 * <p>
 * This application demonstrates use of {@link VNCViewer} as a JavaFX control
 * for displaying a remote VNC desktop. You can launch this class directly to try
 * the VNC viewer, or reference the {@code DesktopCanvas} in your own projects for
 * embedded VNC.
 * </p>
 */
public class VNCViewerDemo extends Application {

    private static final LogWriter vlog = new LogWriter(VNCViewerDemo.class.getName());


    @Override
    public void start(Stage primaryStage) {
        //
        // This initializes the Configuration object
        //
        VNCConfiguration vncConfiguration = new  VNCConfiguration(getParameters());

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("VNCViewerDemo.fxml"));
            Parent root = loader.load();

            final VNCViewerDemoController controller = loader.getController();

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

    /**
     * Main entrypoint for launching the JavaFX VNC viewer demo.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
