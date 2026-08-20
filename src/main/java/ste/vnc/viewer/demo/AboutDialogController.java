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

import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.logging.Logger;

public class AboutDialogController {

    final Logger log = Logger.getLogger(getClass().getName());

    @FXML
    VBox root;

    @FXML
    ImageView appIcon;

    @FXML
    Button closeButton;

    public void initialize() {
        log.finest(() -> "initializing the controller");

        generateAppIcon();
        closeButton.setOnAction(e -> close());

        log.finest(() -> "controller initialized");
    }

    private void generateAppIcon() {
        // Same monitor+arrow glyph as the HTML mockup, traced from its SVG
        // path data (viewBox 0 0 24 24), stroked in phosphor amber on a
        // transparent background — the .about-icon-frame badge in CSS
        // supplies the circular backdrop around it.
        final double canvasSize = 64;
        final double iconSize = 44;
        final double scale = iconSize / 24.0;
        final double offset = (canvasSize - iconSize) / 2.0;

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(canvasSize, canvasSize);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.save();
        gc.translate(offset, offset);
        gc.scale(scale, scale);

        gc.setStroke(Color.rgb(255, 178, 0));
        gc.setLineWidth(1.6 / scale);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        // monitor body: <rect x="2" y="4" width="20" height="13" rx="1.5"/>
        gc.strokeRoundRect(2, 4, 20, 13, 3, 3);
        // stand base: <path d="M8 21h8"/>
        gc.strokeLine(8, 21, 16, 21);
        // stand neck: <path d="M12 17v4"/>
        gc.strokeLine(12, 17, 12, 21);
        // arrow chevron: <path d="M7 9l3 3-3 3"/>
        gc.beginPath();
        gc.moveTo(7, 9);
        gc.lineTo(10, 12);
        gc.lineTo(7, 15);
        gc.stroke();
        // small tick: <path d="M13.5 15h3"/>
        gc.strokeLine(13.5, 15, 16.5, 15);

        gc.restore();

        WritableImage img = new WritableImage((int) canvasSize, (int) canvasSize);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvas.snapshot(params, img);
        appIcon.setImage(img);
    }

    public void setOnClose(Runnable handler) {
        closeButton.setOnAction(e -> handler.run());
    }

    private void close() {
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            root.getScene().getWindow().hide();
        }
    }
}