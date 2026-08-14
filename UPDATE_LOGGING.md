# Update Logging for Debugging Dirty Region Tracking

This document describes how to use the update logging feature to capture and analyze framebuffer update sequences.

## Problem

When regions are changed quickly (e.g., resizing a window), some updates may be lost, resulting in old content not being updated. The mouse cursor may then reveal this old content as it moves.

## Solution: Update Logging

The code has been instrumented to log all framebuffer update operations. This allows us to capture the exact sequence of operations that leads to the issue.

## How to Use

### 1. Start Logging

Logging is disabled by default. To enable it programmatically, call:

```java
UpdateLogger.startSession();
```

This creates a new log file in `/tmp/vnc-update-logs/` with a timestamp-based name.

### 2. Reproduce the Issue

Run the application and perform the actions that cause the issue (e.g., resize the window quickly).

### 3. Stop Logging and Get the Log

```java
UpdateLogger.stopSession();
```

Or let it run and it will be stopped automatically when the application exits.

### 4. Analyze the Log

The log file will contain entries like:

```
[0000] 14:30:00.123 | BEGIN_UPDATE      | 
[0001] 14:30:00.124 | MARK_DIRTY        | x=10 y=20 w=100 h=50
[0002] 14:30:00.125 | MARK_DIRTY        | x=200 y=200 w=50 h=50
[0003] 14:30:00.126 | DRAIN_DIRTY      | count=2 regions=[(10,20,100x50), (200,200,50x50)]
[0004] 14:30:00.127 | DRAW_FRAMEBUFFER  | INCREMENTAL regions=2 fb=1024x768 canvas=1024x768
[0005] 14:30:00.200 | FB_UPDATE_START   | update#1
[0006] 14:30:00.201 | FB_UPDATE_END     | update#1
```

### Using the Script

There's a helper script `log_updates.sh`:

```bash
# Start application with logging
./log_updates.sh start

# After reproducing the issue, in another terminal:
./log_updates.sh stop

# Or to analyze the latest log:
./log_updates.sh analyze
```

## Log Format

Each line has the format:

```
[SEQUENCE] TIMESTAMP | EVENT_TYPE | details
```

### Event Types

- **BEGIN_UPDATE**: Called at the start of a framebuffer update batch (from VNC server)
- **MARK_DIRTY**: A region was marked as dirty (modified)
- **DRAIN_DIRTY**: Dirty regions were retrieved and cleared
- **DRAW_FRAMEBUFFER**: The canvas was drawn (INCREMENTAL or FULL)
- **RESIZE**: The framebuffer or canvas was resized
- **FB_UPDATE_START**: VNC framebuffer update started
- **FB_UPDATE_END**: VNC framebuffer update ended
- **ERROR**: An error occurred (e.g., out of bounds)

## What to Look For

When analyzing the log for the "lost updates" issue, look for:

1. **Missing DRAW_FRAMEBUFFER calls**: After a MARK_DIRTY, there should eventually be a DRAW_FRAMEBUFFER
2. **DRAIN_DIRTY returning empty**: If DRAIN_DIRTY returns empty, the next DRAW_FRAMEBUFFER will do a full redraw
3. **Resize followed by updates**: Check if RESIZE is properly followed by BEGIN_UPDATE and MARK_DIRTY calls
4. **Mismatched dimensions**: Look for RESIZE events where fb and canvas dimensions don't match
5. **Sequence of events**: The pattern should be:
   - FB_UPDATE_START
   - BEGIN_UPDATE
   - One or more MARK_DIRTY
   - FB_UPDATE_END
   - DRAIN_DIRTY (in drawFramebuffer)
   - DRAW_FRAMEBUFFER

If you see MARK_DIRTY calls without corresponding DRAW_FRAMEBUFFER calls, or if the dirty regions are being cleared (DRAIN_DIRTY) but not drawn, that could indicate the issue.

## Creating a Unit Test

Once you've captured a failing sequence, I can help you create a unit test that:

1. Creates an ImageRender
2. Calls beginUpdate()
3. Calls the same sequence of fillRect/imageRect/copyRect/updatePixels
4. Calls drainDirty()
5. Verifies that all regions are properly marked and can be retrieved

This will allow us to reproduce the issue deterministically.
