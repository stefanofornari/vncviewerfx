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

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.kordamp.ikonli.coreui.CoreUiFree;
import org.kordamp.ikonli.javafx.FontIcon;

public class CustomTitleBarController {

    @FXML
    Button burgerButton;

    @FXML
    Label titleLabel;

    @FXML
    Button minimizeButton;

    @FXML
    Button maximizeButton;

    @FXML
    Button closeButton;

    @FXML
    CustomTitleBar titleBar;

    public ContextMenu burgerMenu;
    public MenuItem fullscreenItem;
    public MenuItem aboutItem;
    public MenuItem exitItem;

    private double xOffset = 0;
    private double yOffset = 0;


    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            final Stage stage = stage();

            fullscreenItem = new MenuItem("Fullscreen");
            aboutItem = new MenuItem("About");
            exitItem = new MenuItem("Exit");

            burgerMenu = new ContextMenu();
            burgerMenu.getItems().addAll(fullscreenItem, new SeparatorMenuItem(), aboutItem, new SeparatorMenuItem(), exitItem);

            burgerButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE);
            burgerButton.setGraphic(new FontIcon(CoreUiFree.MENU));
            burgerButton.setOnAction(e -> {
                if (burgerMenu.isShowing()) {
                    burgerMenu.hide();
                } else {
                    burgerMenu.show(burgerButton, javafx.geometry.Side.BOTTOM, 0, 0);
                }
            });

            minimizeButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE);
            minimizeButton.setGraphic(new FontIcon(CoreUiFree.WINDOW_MINIMIZE));
            minimizeButton.setOnAction(e -> stage.setIconified(true));

            maximizeButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE);
            maximizeButton.setGraphic(new FontIcon(CoreUiFree.WINDOW_MAXIMIZE));
            maximizeButton.setOnAction(e -> {
                if (stage.isMaximized()) {
                    maximizeButton.setGraphic(new FontIcon(CoreUiFree.WINDOW_MAXIMIZE));
                    stage.setMaximized(false);
                } else {
                    maximizeButton.setGraphic(new FontIcon(CoreUiFree.WINDOW_RESTORE));
                    stage.setMaximized(true);
                }
            });

            closeButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE);
            closeButton.setGraphic(new FontIcon(CoreUiFree.X_CIRCLE));
            closeButton.setOnAction(e -> stage.close());

            titleBar.visibleProperty().bind(stage.fullScreenProperty().not());
            titleBar.managedProperty().bind(stage.fullScreenProperty().not());

            titleBar.title.bindBidirectional(stage.titleProperty());
            titleBar.title.bindBidirectional(titleBar.controller.titleLabel.textProperty());
        });
    }

    @FXML
    void onTitleBarPressed(final MouseEvent event) {
        // Only handle primary (left) mouse button clicks
        if ((event.getButton() != MouseButton.PRIMARY)) {
            return;
        }

        final Stage stage = stage();

        if (event.getClickCount() == 2) {
            maximizeButton.setGraphic(new FontIcon(CoreUiFree.WINDOW_RESTORE));
            stage.setMaximized(!stage.isMaximized());
            return;
        }

        // A maximized window can not be moved
        if (stage.isMaximized()) {
            return;
        }

        // Capture mouse offset relative to window top-left corner
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    @FXML
    void onTitleBarDragged(final MouseEvent event) {
        if ((event.getButton() != MouseButton.PRIMARY)) {
            return;
        }

        final Stage stage = stage();

        // Reposition stage based on screen coordinates and captured offset
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    public void title(String title) {
        titleLabel.setText(title);
    }

    public String title() {
        return titleLabel.getText();
    }

    public void onFullscreen(Runnable handler) {
        fullscreenItem.setOnAction(e -> handler.run());
    }

    public void onAbout(Runnable handler) {
        aboutItem.setOnAction(e -> handler.run());
    }

    public void onExit(Runnable handler) {
        exitItem.setOnAction(e -> handler.run());
    }

    public boolean isMaximized() {
        return stage().isMaximized();
    }

    public Stage stage() throws IllegalStateException  {
        Stage stage = null;

        if (titleBar != null) {
            stage = (Stage)titleBar.getScene().getWindow();
        }

        if (stage == null) {
            throw new IllegalStateException("no stage available for " + titleBar);
        }

        return stage;
    }
}
