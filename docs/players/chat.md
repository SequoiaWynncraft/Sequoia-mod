# Chat and image previews

Sequoia connects guild chat to the Discord bridge.
Use `/seq ignore <IGN>` and `/seq unignore <IGN>` to hide or restore bridge messages from a Minecraft username.
Clickable world names either switch worlds immediately or put `/switch` in the chat box, depending on Settings.

Wynntils 4.2.9 or newer enables item-encoding-v3 previews in Discord, including item stats, major IDs, crafted details, and mounts.

## Image previews

Previews are off by default. Enable **Show image and GIF previews** under **Discord chat**.
Supported links then show **[View Image]** or **[View GIF]**.
Hover to preview, click to enlarge, or shift-click to insert the URL.
Disabling previews stops their network requests.

**Show media links** controls whether media URLs appear in chat.
Keep it on with previews disabled to see plain links. Turn it off to hide media links and media-only messages, while keeping accompanying text and ordinary links.
Hidden media makes no preview requests.

Non-animated PNG, JPEG, and WebP previews support up to 1,024 pixels per side.
GIF, MP4, and animated WebP previews use a 256-pixel limit.
Use `/seqimages clear` to clear cached previews and cancel pending downloads.

## Supported sources

Previews accept HTTPS links from Discord, Tenor, Giphy, Imgur, and Klipy.
Klipy's MP4 renditions of GIFs are supported.
FxTwitter support is limited to direct `https://gif.fxtwitter.com/tweet_video/<id>.gif` or `.webp` URLs without query parameters.

Those hosts receive your IP address when the client downloads a preview.
The mod rejects other hosts, redirects outside the allowed list, private addresses, bodies above 8 MiB, and downloads over 20 seconds.

A bundled helper decodes media outside the game process.
It runs FFmpeg in WebAssembly with seccomp on Linux, Seatbelt on macOS, or an AppContainer on Windows.
See [decoder builds](../../native/README.md) for the implementation.
