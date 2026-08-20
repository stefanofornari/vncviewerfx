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

import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class VNCViewerDemoResizeSpec extends ApplicationTest {

    private Stage stage;
    private AnchorPane rootWrapper;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(VNCViewerDemo.class.getResource("VNCViewerDemo.fxml"));
        javafx.scene.Parent root = loader.load();

        Scene scene = new Scene(root, 768, 1024);
        stage.setScene(scene);
        stage.show();
    }

    private Region resizeRegion(String id) {
        return lookup("#resize-" + id).query();
    }

    private void simulateDrag(Region region, double dx, double dy) {
        interact(() -> {
            Bounds bounds = region.getBoundsInParent();
            double startX = bounds.getCenterX();
            double startY = bounds.getCenterY();
            
            PickResult pickResult = new PickResult(region, startX, startY);
            
            MouseEvent pressed = new MouseEvent(MouseEvent.MOUSE_PRESSED,
                startX, startY, startX, startY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false,
                false, false, false, false, pickResult);
            
            MouseEvent dragged = new MouseEvent(MouseEvent.MOUSE_DRAGGED,
                startX, startY, startX + dx, startY + dy, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false,
                false, false, false, false, pickResult);
            
            MouseEvent released = new MouseEvent(MouseEvent.MOUSE_RELEASED,
                startX + dx, startY + dy, startX + dx, startY + dy, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false,
                false, false, false, false, pickResult);
            
            if (region.getOnMousePressed() != null) {
                region.getOnMousePressed().handle(pressed);
            }
            if (region.getOnMouseDragged() != null) {
                region.getOnMouseDragged().handle(dragged);
            }
            if (region.getOnMouseReleased() != null) {
                region.getOnMouseReleased().handle(released);
            }
        });
    }

    @Test
    void resize_regions_exist() {
        then(resizeRegion("N_RESIZE")).isNotNull();
        then(resizeRegion("S_RESIZE")).isNotNull();
        then(resizeRegion("E_RESIZE")).isNotNull();
        then(resizeRegion("W_RESIZE")).isNotNull();
        then(resizeRegion("NE_RESIZE")).isNotNull();
        then(resizeRegion("NW_RESIZE")).isNotNull();
        then(resizeRegion("SE_RESIZE")).isNotNull();
        then(resizeRegion("SW_RESIZE")).isNotNull();
    }

    @Test
    void resize_regions_have_correct_cursors() {
        then(resizeRegion("N_RESIZE").getCursor()).isEqualTo(Cursor.N_RESIZE);
        then(resizeRegion("S_RESIZE").getCursor()).isEqualTo(Cursor.S_RESIZE);
        then(resizeRegion("E_RESIZE").getCursor()).isEqualTo(Cursor.E_RESIZE);
        then(resizeRegion("W_RESIZE").getCursor()).isEqualTo(Cursor.W_RESIZE);
        then(resizeRegion("NE_RESIZE").getCursor()).isEqualTo(Cursor.NE_RESIZE);
        then(resizeRegion("NW_RESIZE").getCursor()).isEqualTo(Cursor.NW_RESIZE);
        then(resizeRegion("SE_RESIZE").getCursor()).isEqualTo(Cursor.SE_RESIZE);
        then(resizeRegion("SW_RESIZE").getCursor()).isEqualTo(Cursor.SW_RESIZE);
    }

    @Test
    void east_resize_increases_width() {
        double initialWidth = stage.getWidth();
        simulateDrag(resizeRegion("E_RESIZE"), 50, 0);
        then(stage.getWidth()).isGreaterThan(initialWidth);
    }

    @Test
    void west_resize_increases_width() {
        double initialWidth = stage.getWidth();
        double initialX = stage.getX();
        simulateDrag(resizeRegion("W_RESIZE"), -50, 0);
        then(stage.getWidth()).isGreaterThan(initialWidth);
        then(stage.getX()).isLessThan(initialX);
    }

    @Test
    void south_resize_increases_height() {
        double initialHeight = stage.getHeight();
        simulateDrag(resizeRegion("S_RESIZE"), 0, 50);
        then(stage.getHeight()).isGreaterThan(initialHeight);
    }

    @Test
    void north_resize_increases_height() {
        double initialHeight = stage.getHeight();
        double initialY = stage.getY();
        simulateDrag(resizeRegion("N_RESIZE"), 0, -50);
        then(stage.getHeight()).isGreaterThan(initialHeight);
        then(stage.getY()).isLessThan(initialY);
    }

    @Test
    void northeast_resize_increases_width_and_height() {
        double initialWidth = stage.getWidth();
        double initialHeight = stage.getHeight();
        double initialY = stage.getY();
        simulateDrag(resizeRegion("NE_RESIZE"), 50, -50);
        then(stage.getWidth()).isGreaterThan(initialWidth);
        then(stage.getHeight()).isGreaterThan(initialHeight);
        then(stage.getY()).isLessThan(initialY);
    }

    @Test
    void southeast_resize_increases_width_and_height() {
        double initialWidth = stage.getWidth();
        double initialHeight = stage.getHeight();
        simulateDrag(resizeRegion("SE_RESIZE"), 50, 50);
        then(stage.getWidth()).isGreaterThan(initialWidth);
        then(stage.getHeight()).isGreaterThan(initialHeight);
    }

    @Test
    void southwest_resize_increases_width_and_height() {
        double initialWidth = stage.getWidth();
        double initialHeight = stage.getHeight();
        double initialX = stage.getX();
        simulateDrag(resizeRegion("SW_RESIZE"), -50, 50);
        then(stage.getWidth()).isGreaterThan(initialWidth);
        then(stage.getHeight()).isGreaterThan(initialHeight);
        then(stage.getX()).isLessThan(initialX);
    }

    @Test
    void northwest_resize_increases_width_and_height() {
        double initialWidth = stage.getWidth();
        double initialHeight = stage.getHeight();
        double initialX = stage.getX();
        double initialY = stage.getY();
        simulateDrag(resizeRegion("NW_RESIZE"), -50, -50);
        then(stage.getWidth()).isGreaterThan(initialWidth);
        then(stage.getHeight()).isGreaterThan(initialHeight);
        then(stage.getX()).isLessThan(initialX);
        then(stage.getY()).isLessThan(initialY);
    }

    @Test
    void west_resize_respects_minimum_width() {
        simulateDrag(resizeRegion("W_RESIZE"), 400, 0);
        then(stage.getWidth()).isEqualTo(VNCViewerDemoController.MIN_WIDTH);
    }

    @Test
    void north_resize_respects_minimum_height() {
        simulateDrag(resizeRegion("N_RESIZE"), 0, 800);
        then(stage.getHeight()).isEqualTo(VNCViewerDemoController.MIN_HEIGHT);
    }
}
