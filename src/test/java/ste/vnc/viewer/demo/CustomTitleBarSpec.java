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

import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class CustomTitleBarSpec extends ApplicationTest {

    private CustomTitleBar titleBar;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        titleBar = new CustomTitleBar();
        titleBar.resize(400, 36);
        stage.setScene(new Scene(titleBar));
        stage.titleProperty().bindBidirectional(titleBar.title);
        stage.show();
    }

    @Test
    void titlebar_has_correct_height() {
        then(titleBar.getHeight()).isEqualTo(36.0);
    }

    @Test
    void titlebar_title_has_default_value() {
        then(titleBar.title.get()).isEqualTo("VNC Viewer Demo");
    }

    @Test
    void titlebar_title_can_be_changed_from_stage_and_component() {
        interact(() -> titleBar.title.set("My Custom Title"));
        then(titleBar.title.get()).isEqualTo("My Custom Title");
        then(titleBar.controller.titleLabel.getText()).isEqualTo("My Custom Title");
        then(stage.getTitle()).isEqualTo("My Custom Title");

        interact(() -> stage.setTitle("My Stage Title"));

        then(titleBar.title.get()).isEqualTo("My Stage Title");
        then(titleBar.controller.titleLabel.getText()).isEqualTo("My Stage Title");
        then(stage.getTitle()).isEqualTo("My Stage Title");
    }

    @Test
    void burger_button_exists_and_is_visible() {
        then(titleBar.controller.burgerButton.isVisible()).isTrue();
    }

    @Test
    void burger_menu_has_correct_items() {
        ContextMenu menu = titleBar.controller.burgerMenu;
        then(menu.getItems()).hasSize(5);
        then(menu.getItems().get(0).getText()).isEqualTo("Fullscreen");
        then(menu.getItems().get(1)).isInstanceOf(javafx.scene.control.SeparatorMenuItem.class);
        then(menu.getItems().get(2).getText()).isEqualTo("About");
        then(menu.getItems().get(3)).isInstanceOf(javafx.scene.control.SeparatorMenuItem.class);
        then(menu.getItems().get(4).getText()).isEqualTo("Exit");
    }

    @Test
    void fullscreen_menu_item_triggers_handler() {
        AtomicBoolean fullscreenClicked = new AtomicBoolean(false);
        titleBar.controller.fullscreenItem.setOnAction(e -> fullscreenClicked.set(true));

        interact(() -> titleBar.controller.fullscreenItem.fire());

        then(fullscreenClicked.get()).isTrue();
    }

    @Test
    void about_menu_item_triggers_handler() {
        AtomicBoolean aboutClicked = new AtomicBoolean(false);
        titleBar.controller.aboutItem.setOnAction(e -> aboutClicked.set(true));

        interact(() -> titleBar.controller.aboutItem.fire());

        then(aboutClicked.get()).isTrue();
    }

    @Test
    void exit_menu_item_triggers_handler() {
        AtomicBoolean exitClicked = new AtomicBoolean(false);
        titleBar.controller.exitItem.setOnAction(e -> exitClicked.set(true));

        interact(() -> titleBar.controller.exitItem.fire());

        then(exitClicked.get()).isTrue();
    }

    @Test
    void minimize_button_minimizes_stage() {
        interact(() -> titleBar.controller.minimizeButton.fire());
        then(stage.isIconified()).isTrue();
    }

    @Test
    void maximize_button_maximizes_stage() {
        interact(() -> titleBar.controller.maximizeButton.fire());
        then(titleBar.controller.isMaximized()).isTrue();
    }

    @Test
    void maximize_button_restores_stage() {
        interact(() -> titleBar.controller.maximizeButton.fire());
        then(titleBar.controller.isMaximized()).isTrue();
        interact(() -> titleBar.controller.maximizeButton.fire());
        then(titleBar.controller.isMaximized()).isFalse();
    }

    @Test
    void close_button_closes_stage() {
        interact(() -> titleBar.controller.closeButton.fire());
        then(stage.isShowing()).isFalse();
    }

    @Test
    void all_window_controls_are_visible() {
        then(titleBar.controller.minimizeButton.isVisible()).isTrue();
        then(titleBar.controller.maximizeButton.isVisible()).isTrue();
        then(titleBar.controller.closeButton.isVisible()).isTrue();
        then(titleBar.controller.burgerButton.isVisible()).isTrue();
        then(titleBar.controller.titleLabel.isVisible()).isTrue();
    }

    @Test
    void fullscreen_hides_titlebar() {
        interact(() -> stage.setFullScreen(true));
        then(titleBar.isVisible()).isFalse();

        interact(() -> stage.setFullScreen(false));
        then(titleBar.isVisible()).isTrue();
    }
}
