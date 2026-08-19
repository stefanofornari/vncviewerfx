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
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class VNCViewerDemoQuitSpec extends ApplicationTest {

    private AtomicBoolean exitInvoked;

    @Override
    public void start(Stage stage) throws Exception {
        exitInvoked = new AtomicBoolean(false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("VNCViewerDemo.fxml"));
        VNCViewerDemoQuitController controller = new VNCViewerDemoQuitController();
        controller.exitInvoked = exitInvoked;
        loader.setController(controller);
        BorderPane root = loader.load();

        Scene scene = new Scene(root, 768, 1024);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void ctrl_q_triggers_exit() {
        interact(() -> push(new KeyCodeCombination(
            KeyCode.Q, KeyCombination.SHORTCUT_DOWN
        )));

        then(exitInvoked.get()).isTrue();
    }
}
