#!/bin/bash

# Script to enable update logging and run the VNC viewer
# Usage: ./log_updates.sh [start|stop|analyze]

LOG_DIR="/tmp/vnc-update-logs"

case "$1" in
    start)
        echo "Starting VNC Viewer with update logging..."
        echo "Logs will be saved to: $LOG_DIR"
        mkdir -p "$LOG_DIR"
        # Find the latest log file and remove it to start fresh
        rm -f "$LOG_DIR"/update_log_*.txt
        # Run the application
        cd /home/ste/Projects/vncviewerfx
        /opt/apache-maven-3.9.11/bin/mvn javafx:run
        ;;
    stop)
        echo "Stopping logging and showing latest log..."
        LATEST_LOG=$(ls -t "$LOG_DIR"/update_log_*.txt 2>/dev/null | head -1)
        if [ -n "$LATEST_LOG" ]; then
            echo "Latest log: $LATEST_LOG"
            echo "--- Last 50 lines ---"
            tail -50 "$LATEST_LOG"
        else
            echo "No log files found in $LOG_DIR"
        fi
        ;;
    analyze)
        echo "Analyzing update logs..."
        LATEST_LOG=$(ls -t "$LOG_DIR"/update_log_*.txt 2>/dev/null | head -1)
        if [ -n "$LATEST_LOG" ]; then
            echo "Analyzing: $LATEST_LOG"
            echo ""
            echo "=== Summary ==="
            echo "Total lines: $(wc -l < "$LATEST_LOG")"
            echo "BEGIN_UPDATE calls: $(grep -c 'BEGIN_UPDATE' "$LATEST_LOG")"
            echo "MARK_DIRTY calls: $(grep -c 'MARK_DIRTY' "$LATEST_LOG")"
            echo "DRAIN_DIRTY calls: $(grep -c 'DRAIN_DIRTY' "$LATEST_LOG")"
            echo "DRAW_FRAMEBUFFER (INCREMENTAL): $(grep -c 'INCREMENTAL' "$LATEST_LOG")"
            echo "DRAW_FRAMEBUFFER (FULL): $(grep -c 'FULL' "$LATEST_LOG")"
            echo "RESIZE calls: $(grep -c 'RESIZE' "$LATEST_LOG")"
            echo "FB_UPDATE_START: $(grep -c 'FB_UPDATE_START' "$LATEST_LOG")"
            echo "FB_UPDATE_END: $(grep -c 'FB_UPDATE_END' "$LATEST_LOG")"
            echo ""
            echo "=== Log file location ==="
            echo "$LATEST_LOG"
        else
            echo "No log files found in $LOG_DIR"
        fi
        ;;
    *)
        echo "Usage: $0 [start|stop|analyze]"
        echo "  start  - Start the application with logging"
        echo "  stop   - Stop logging and show the latest log"
        echo "  analyze - Analyze the latest log file"
        ;;
esac
