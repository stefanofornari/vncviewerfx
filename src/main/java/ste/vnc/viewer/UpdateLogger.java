package ste.vnc.viewer;

import com.tigervnc.rfb.Rect;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Logger for tracking framebuffer update sequences to help debug
 * dirty region tracking issues.
 */
public class UpdateLogger {

    private static final String LOG_DIR = "/tmp/vnc-update-logs";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private static BufferedWriter writer;
    private static String currentSession;
    private static int sequenceNumber = 0;

    public static synchronized void startSession() {
        try {
            java.io.File logDir = new java.io.File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            currentSession = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = LOG_DIR + "/update_log_" + currentSession + ".txt";
            writer = new BufferedWriter(new FileWriter(filename));
            writer.write("=== Update Log Session: " + currentSession + " ===\n");
            writer.write("Format: [seq] TIMESTAMP | EVENT_TYPE | details\n");
            writer.write("----------------------------------------\n");
            writer.flush();
            sequenceNumber = 0;
            System.out.println("Update logging started: " + filename);
        } catch (IOException e) {
            System.err.println("Failed to start update logging: " + e.getMessage());
            writer = null;
            currentSession = null;
        }
    }

    public static synchronized void stopSession() {
        if (writer != null) {
            try {
                writer.write("=== Session ended ===\n");
                writer.close();
                System.out.println("Update logging stopped: " + LOG_DIR + "/update_log_" + currentSession + ".txt");
            } catch (IOException e) {
                System.err.println("Failed to stop update logging: " + e.getMessage());
            } finally {
                writer = null;
                currentSession = null;
            }
        }
    }

    public static synchronized void logBeginUpdate() {
        log("BEGIN_UPDATE", "");
    }

    public static synchronized void logMarkDirty(int x, int y, int w, int h) {
        log("MARK_DIRTY", String.format("x=%d y=%d w=%d h=%d", x, y, w, h));
    }

    public static synchronized void logDrainDirty(List<Rect> regions) {
        if (regions == null || regions.isEmpty()) {
            log("DRAIN_DIRTY", "empty");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("count=").append(regions.size()).append(" regions=[");
            for (int i = 0; i < regions.size(); i++) {
                Rect r = regions.get(i);
                if (i > 0) sb.append(", ");
                sb.append(String.format("(%d,%d,%dx%d)", r.tl.x, r.tl.y, r.width(), r.height()));
            }
            sb.append("]");
            log("DRAIN_DIRTY", sb.toString());
        }
    }

    public static synchronized void logDrawFramebuffer(boolean incremental, int regionCount, 
            int fbWidth, int fbHeight, int canvasWidth, int canvasHeight) {
        String type = incremental ? "INCREMENTAL" : "FULL";
        log("DRAW_FRAMEBUFFER", String.format("%s regions=%d fb=%dx%d canvas=%dx%d", 
            type, regionCount, fbWidth, fbHeight, canvasWidth, canvasHeight));
    }

    public static synchronized void logResize(int width, int height) {
        log("RESIZE", String.format("%dx%d", width, height));
    }

    public static synchronized void logFramebufferUpdateStart(int updateCount) {
        log("FB_UPDATE_START", String.format("update#%d", updateCount));
    }

    public static synchronized void logFramebufferUpdateEnd(int updateCount) {
        log("FB_UPDATE_END", String.format("update#%d", updateCount));
    }

    public static synchronized void logError(String message) {
        log("ERROR", message);
    }

    private static void log(String eventType, String details) {
        if (writer == null) {
            return;
        }
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
            String line = String.format("[%04d] %s | %-15s | %s\n", 
                sequenceNumber++, timestamp, eventType, details);
            writer.write(line);
            writer.flush();
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    /**
     * Toggle logging on/off. If starting, creates a new session.
     */
    public static void toggleLogging() {
        if (writer != null) {
            stopSession();
        } else {
            startSession();
        }
    }

    /**
     * Check if logging is currently active.
     */
    public static boolean isLogging() {
        return writer != null;
    }

    /**
     * Static initializer to auto-start logging on class load.
     * Can be disabled by calling stopSession() and the file will be finalized.
     */
    static {
        // Don't auto-start; let the application decide when to start
        // startSession();
    }
}
