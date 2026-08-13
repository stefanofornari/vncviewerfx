# VNCViewerFX Scope Overview

## Project Name
- VNC Viewer FX

## Version
- 0.0.0-SNAPSHOT

## Package
- ste.vnc.viewer

## Overall Description
VNCViewerFX is a JavaFX component (based on javafx.scene.canvas.Canvas) that can
be embedded into JavaFX applications to provide a viewer of a screen streamed
via VNC protocol. In its first version, the component provides a limited set of
capabilities and in particular a defined configuration.

The project provides developer documentation as javadoc and a GitPages web site.
It also provides a demo application to showcase the use of the component in a
JavaFX program.

## In-Scope Capabilities
- JavaFX component for embedding a VNC viewer into host JavaFX applications
- Connection to a local VNC server
- VNC protocol settings:
  - shared connection
  - no local cursor
  - desktop resize support
  - extended desktop size support
  - client redirect support
  - desktop rename support
  - custom compression level, set to 0
  - no JPEG compression
  - ZRLE encoding

## Out-of-Scope / Future Enhancements
- Authentication / API key management
- Full encoding and protocol features support

## Development Toolchain
- JavaFX
- Java
- Maven
- JUnit 5
- TestFX
- AssertJ
- Headless test execution support
- TDD-oriented workflow

## Target Systems/Platforms
- JavaFX desktop applications
- Demo application for local desktop execution
- Host applications embedding the component

## Path/Link to `coding-standard.md`
- `uzz/specs/coding-standard.md`

## Path/Link to `development-framework.md`
- `uzz/specs/development-framework.md`
