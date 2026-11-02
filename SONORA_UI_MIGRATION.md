# SONORA v2.0 UI migration

Source: user's latest SONORA 1.9 project with PixelGridView and four MP4 animations.

## Stage 1 (this archive)
- Update visible player branding to SONORA v2.0.
- Remove EQ shortcut from common header, consistent with approved portrait layout. Existing EQ implementation is preserved.
- Increment app version code to 11 and label the UI migration build.

## Next stages
- Landscape: EQ + MP4 + independent Wave/Bars visualizers; portrait: MP4 + one visualizer, no inline EQ.
- Build matching brushed-metal/chrome skin using reusable Android drawable assets and responsive layouts.
- Implement rotary-only volume control tied to Android media stream.
- Apply approved visual language to Library, Folders, Favorites, Artists, Albums, Genres, Playlists, Settings.
- Test on phone and BYD head unit. Do not treat visual mockups as ready-to-use UI assets.

This build has NOT been compiled or tested on Android hardware.
