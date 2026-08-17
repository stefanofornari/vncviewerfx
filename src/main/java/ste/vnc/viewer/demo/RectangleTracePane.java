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

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.image.PixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class RectangleTracePane extends BorderPane {
    private final ListView<RectangleEntry> entries = new ListView<>();
    private final Canvas preview = new Canvas(1, 1);
    private final Label details = new Label("No rectangle selected");
    private final AtomicLong sequence = new AtomicLong();
    private final ExecutorService logWriter = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VncRectangleLogWriter");
        thread.setDaemon(true);
        return thread;
    });
    private final Path logDirectory = Path.of("log", "rectangles");
    private Consumer<SelectedRectangle> onSelectionChanged;

    public RectangleTracePane() {
        setPadding(new Insets(4));

        Label title = new Label("Framebuffer rectangles");
        Button clear = new Button("Clear list");
        clear.setOnAction(event -> entries.getItems().clear());
        VBox header = new VBox(4, title, details, clear);

        entries.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, entry) -> {
            if (entry != null) {
                show(entry);
            }
            notifySelectionChanged(entry);
        });

        SplitPane content = new SplitPane(entries, preview);
        content.setOrientation(javafx.geometry.Orientation.VERTICAL);
        content.setDividerPositions(0.65);
        setTop(header);
        setCenter(content);
    }

    /**
     * Registers a listener that is notified whenever the selected rectangle
     * changes. Passing {@code null} clears the selection. The listener is also
     * invoked immediately with the currently selected entry, if any.
     */
    public void setOnSelectionChanged(Consumer<SelectedRectangle> listener) {
        this.onSelectionChanged = listener;
        notifySelectionChanged(entries.getSelectionModel().getSelectedItem());
    }

    private void notifySelectionChanged(RectangleEntry entry) {
        if (onSelectionChanged == null) {
            return;
        }
        if (entry != null) {
            onSelectionChanged.accept(entry.toSelectedRectangle());
        } else {
            onSelectionChanged.accept(null);
        }
    }

    public void record(int update, String operation, int x, int y, int width, int height, int[] pixels) {
        /*
        if (pixels == null || pixels.length != width * height) {
            return;
        }
        long id = sequence.incrementAndGet();
        RectangleEntry entry = new RectangleEntry(id, update, operation, x, y, width, height, pixels);
        logWriter.execute(() -> writeBinary(entry));
        Platform.runLater(() -> entries.getItems().add(entry));
        */
    }

    private void show(RectangleEntry entry) {
        preview.setWidth(Math.max(1, entry.width));
        preview.setHeight(Math.max(1, entry.height));
        preview.getGraphicsContext2D().getPixelWriter().setPixels(
            0, 0, entry.width, entry.height,
            PixelFormat.getIntArgbInstance(), entry.pixels, 0, entry.width);
        details.setText(entry.toString());
    }

    private void writeBinary(RectangleEntry entry) {
        try {
            Files.createDirectories(logDirectory);
            Path file = logDirectory.resolve(String.format("%08d-%s-%dx%d.bin",
                entry.sequence, entry.operation, entry.width, entry.height));
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                out.writeInt(entry.x);
                out.writeInt(entry.y);
                out.writeInt(entry.width);
                out.writeInt(entry.height);
                out.writeInt(entry.update);
                for (int pixel : entry.pixels) {
                    out.writeInt(pixel);
                }
            }
        } catch (IOException ignored) {
            // Rectangle capture must not interrupt the RFB processing thread.
        }
    }

    public record SelectedRectangle(long sequence, int update, String operation,
        int x, int y, int width, int height) {
    }

    private static final class RectangleEntry {
        private final long sequence;
        private final int update;
        private final String operation;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int[] pixels;

        private RectangleEntry(long sequence, int update, String operation, int x, int y,
            int width, int height, int[] pixels) {
            this.sequence = sequence;
            this.update = update;
            this.operation = operation;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        @Override
        public String toString() {
            return String.format("%08d update=%d %s (%d,%d) %dx%d",
                sequence, update, operation, x, y, width, height);
        }

        private SelectedRectangle toSelectedRectangle() {
            return new SelectedRectangle(sequence, update, operation, x, y, width, height);
        }
    }
}
