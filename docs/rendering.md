# Rendering Architecture

Sequoia UI rendering is split into three layers:

- `UiRenderer` owns scoped command queuing and backend lifecycle.
- `MinecraftUiRenderer` converts Minecraft window/input coordinates and flushes one UI frame at the end of the game render pass.
- `UiRenderBackend` implements drawing and resource operations. `NanoVgBackend` is the current implementation.

Screens and HUD features should submit `UiCanvas` commands through `UiRenderer`. They must not open frames, capture OpenGL state, allocate native colors, or retain backend-specific image handles. Fonts and images are registered through `UiRenderer`; the backend owns their native lifetime and releases them during shutdown.

Screen commands are tied to the screen instance that submitted them. HUD commands run only when no screen is open. Resource commands survive screen changes so deferred cleanup is not dropped. All valid commands are batched into one backend frame, and one failing draw callback does not prevent later callbacks from running.

`NVGContext` and `NVGWrapper` are compatibility APIs for the remaining Party Finder renderer. New or modified UI should use `UiCanvas`; compatibility usage should not spread to other screens or managers. A future backend can implement `UiRenderBackend` and `UiCanvas` without changing migrated screens.
