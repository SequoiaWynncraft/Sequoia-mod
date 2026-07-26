# Rendering Architecture

Sequoia UI rendering is split into three layers:

- `UiRenderer` owns scoped command queuing and backend lifecycle.
- `MinecraftUiRenderer` converts Minecraft window/input coordinates and flushes one UI frame at the end of the game render pass.
- `UiRenderBackend` implements drawing and resource operations. `NanoVgBackend` is the current implementation.

Screens and HUD features should submit `UiCanvas` commands through `UiRenderer`. They must not open frames, capture OpenGL state, allocate native colors, or retain backend-specific image handles. Fonts and images are registered through `UiRenderer`; the backend owns their native lifetime and releases them during shutdown.

Screen commands are tied to the screen instance that submitted them. HUD commands run only when no screen is open. Resource commands survive screen changes so deferred cleanup is not dropped. All valid commands are batched into one backend frame, and one failing draw callback does not prevent later callbacks from running.

`NVGContext` and `NVGWrapper` are compatibility APIs for the remaining Party Finder renderer. New or modified UI should use `UiCanvas`; compatibility usage should not spread to other screens or managers. A future backend can implement `UiRenderBackend` and `UiCanvas` without changing migrated screens.

`UiRenderMetrics` separates the layout pixel ratio from NanoVG's render pixel ratio. Layout dimensions and input conversion use the former, while the latter applies capped oversampling for sharper text and vector edges without changing UI size or hitboxes. At large manually entered UI scales, the render ratio never drops below the layout ratio.

## Themes

UI colors are semantic tokens defined by `UiColor`. Screens request tokens through `ThemeManager.color(...)`; they should not declare screen-level color palettes. Shared surface, text, control, status, and map tokens keep layout code independent from concrete RGBA values.

The bundled values live in grouped YAML files under `assets/seq/themes`. `ThemeReader` validates names, duplicate or unknown keys, RGBA lists, component ranges, and required colors. Optional tokens use their built-in fallback when omitted. External `*.theme.yml` files are loaded from `config/sequoia/themes`, and the complete schema is documented in `docs/theme-template.theme.yml`. `ThemeManager` also starts with a complete built-in default, so a missing or malformed bundled asset cannot leave rendering without a theme.
