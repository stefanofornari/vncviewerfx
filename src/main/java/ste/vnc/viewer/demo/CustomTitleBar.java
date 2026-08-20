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

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class CustomTitleBar extends HBox {

    private final Logger log = Logger.getLogger(getClass().getName());
    public final StringProperty title = new SimpleStringProperty();

    public CustomTitleBarController controller;

    public CustomTitleBar() {
        log.finest(() -> "creating a new component");

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("CustomTitleBar.fxml"));
        fxmlLoader.setRoot(this);
        try {
            fxmlLoader.load();
            controller = fxmlLoader.getController();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load CustomTitleBar.fxml", exception);
        }

        log.finest("component created");
    }

    public void setTitle(final String title) {
        this.title.set(title);
    }

    public String getTitle() {
        return this.title.get();
    }

    public void onFullscreen(final Runnable handler) {
        controller.onFullscreen(handler);
    }

    public void onAbout(final Runnable handler) {
        controller.onAbout(handler);
    }

    public void onExit(final Runnable handler) {
        controller.onExit(handler);
    }
}
