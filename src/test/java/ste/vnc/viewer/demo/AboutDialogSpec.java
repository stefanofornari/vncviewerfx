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

import static org.assertj.core.api.BDDAssertions.then;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class AboutDialogSpec extends ApplicationTest {

    private AboutDialog aboutDialog;
    private Stage dialogStage;

    @Override
    public void start(Stage stage) {
        aboutDialog = new AboutDialog();
        aboutDialog.setPrefSize(420, 300);

        dialogStage = new Stage();
        dialogStage.setScene(new Scene(aboutDialog));
        dialogStage.show();
    }

    @Test
    void dialog_has_correct_title() {
        then(aboutDialog.controller.root.getStyleClass()).contains("about-dialog");
    }

    @Test
    void dialog_shows_app_name() {
        Label appName = (Label) lookup(".about-title").query();
        then(appName.getText()).isEqualTo("VNC Viewer Demo");
    }

    @Test
    void dialog_shows_version() {
        Label version = (Label) lookup(".about-version").query();
        then(version.getText()).isEqualTo("Version 0.0.0-SNAPSHOT");
    }

    @Test
    void dialog_shows_copyright() {
        Label copyright = (Label) lookup(".about-copyright").query();
        then(copyright.getText()).contains("Copyright (C) 2026");
    }

    @Test
    void dialog_shows_license() {
        Label license = (Label) lookup(".about-license").query();
        then(license.getText()).contains("GNU General Public License");
    }

    @Test
    void dialog_has_close_button() {
        Button closeButton = (Button) lookup(".about-close-button").query();
        then(closeButton).isNotNull();
        then(closeButton.getText()).isEqualTo("Close");
    }

    @Test
    void close_button_hides_dialog() {
        interact(() -> dialogStage.show());
        Button closeButton = (Button) lookup(".about-close-button").query();
        interact(() -> closeButton.fire());
        then(dialogStage.isShowing()).isFalse();
    }

    @Test
    void dialog_has_app_icon() {
        then(aboutDialog.controller.appIcon).isNotNull();
        then(aboutDialog.controller.appIcon.getImage()).isNotNull();
    }
}
