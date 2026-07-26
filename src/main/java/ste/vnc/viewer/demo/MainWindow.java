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

import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import ste.vnc.viewer.CConnFX;
import ste.vnc.viewer.VNCCanvas;

/**
 * JavaFX Stage-based window for VNC viewer.
 * Provides window management and UI layout.
 */
class MainWindow {

  private Stage stage;
  private BorderPane root;
  private ScrollPane scrollPane;
  private VNCCanvas canvas;
  private CConnFX connection;
  private Runnable onClose;

  public MainWindow(String title, VNCCanvas canvas, CConnFX connection) {
    this.canvas = canvas;
    this.connection = connection;
    this.stage = new Stage();
    this.root = new BorderPane();

    stage.setTitle(title + " - VNCViewer FX");
    stage.setWidth(800);
    stage.setHeight(600);

    // Setup menu bar
    setupMenuBar();

    // Setup scroll pane
    scrollPane = new ScrollPane(canvas);
    scrollPane.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");
    scrollPane.setPannable(false);
    root.setCenter(scrollPane);

    // Create scene
    Scene scene = new Scene(root, 800, 600);

    // Forward key events from the scene to the desktop canvas so that
    // keyboard input works even if the canvas itself does not have focus.
    // Use an event filter for KEY_PRESSED so we can intercept keys like
    // SPACE before ScrollPane processes them for local scrolling.
    scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.SPACE) {
        canvas.handleKeyPressed(e);
        e.consume();
      }
    });
    scene.setOnKeyPressed(e -> {
      if (e.getCode() != KeyCode.SPACE) {
        canvas.handleKeyPressed(e);
      }
    });
    scene.setOnKeyReleased(e -> canvas.handleKeyReleased(e));
    scene.setOnKeyTyped(e -> canvas.handleKeyTyped(e));

    // Whenever the visible viewport changes size (window resize, DPI changes,
    // etc.), request a matching desktop size from the server (if supported).
    scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
      if (connection != null && newVal != null) {
        int w = (int)Math.round(newVal.getWidth());
        int h = (int)Math.round(newVal.getHeight());
        connection.requestDesktopSize(w, h);
      }
    });

    stage.setScene(scene);

    // Handle window close
    stage.setOnCloseRequest(e -> {
      if (onClose != null) {
        onClose.run();
      }
      if (connection != null) {
        connection.close();
      }
    });
  }

  /**
   * Sets up the menu bar with File, View, and Help menus
   */
  private void setupMenuBar() {
    MenuBar menuBar = new MenuBar();

    // File menu
    Menu fileMenu = new Menu("File");
    MenuItem openItem = new MenuItem("Connect...");
    openItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
    openItem.setOnAction(e -> onConnect());

    MenuItem closeItem = new MenuItem("Close");
    closeItem.setAccelerator(KeyCombination.keyCombination("Ctrl+W"));
    closeItem.setOnAction(e -> stage.close());

    MenuItem exitItem = new MenuItem("Exit");
    exitItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Q"));
    exitItem.setOnAction(e -> stage.getScene().getWindow().hide());

    fileMenu.getItems().addAll(openItem, new SeparatorMenuItem(), closeItem, new SeparatorMenuItem(), exitItem);

    // View menu
    Menu viewMenu = new Menu("View");
    MenuItem fullscreenItem = new MenuItem("Full Screen");
    fullscreenItem.setAccelerator(KeyCombination.keyCombination("F11"));
    fullscreenItem.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));

    MenuItem optionsItem = new MenuItem("Options...");
    optionsItem.setAccelerator(KeyCombination.keyCombination("Ctrl+Alt+O"));
    optionsItem.setOnAction(e -> onOptions());

    viewMenu.getItems().addAll(fullscreenItem, new SeparatorMenuItem(), optionsItem);

    // Help menu
    Menu helpMenu = new Menu("Help");
    MenuItem aboutItem = new MenuItem("About");
    aboutItem.setOnAction(e -> onAbout());

    helpMenu.getItems().addAll(aboutItem);

    menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
    root.setTop(menuBar);
  }

  /**
   * Shows the window
   */
  public void show() {
    stage.show();
  }

  /**
   * Hides the window
   */
  public void hide() {
    stage.hide();
  }

  /**
   * Sets the title
   */
  public void setTitle(String title) {
    stage.setTitle(title);
  }

  /**
   * Resizes the content area
   */
  public void resizeContent(int width, int height) {
    scrollPane.setPrefViewportWidth(width);
    scrollPane.setPrefViewportHeight(height);
  }

  /**
   * Sets the close callback
   */
  public void setOnClose(Runnable callback) {
    this.onClose = callback;
  }

  /**
   * Gets the stage
   */
  public Stage getStage() {
    return stage;
  }

  // Menu action handlers
  private void onConnect() {
    // TODO: Open ServerDialogFx
  }

  private void onOptions() {
    OptionsDialogFX dialog = new OptionsDialogFX(stage);
    dialog.show();
  }

  private void onAbout() {
    // TODO: Show about dialog
  }
}
