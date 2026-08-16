# Google Play listing asset provenance

## Sources

- App icon source: `docs/assets/app-icon-master.svg`
- Feature graphic generated source: `docs/assets/google-play/feature-graphic-source.png`
- Final deterministic exporter: `scripts/generate-google-play-assets.cjs`
- Image generation route: built-in Codex ImageGen
- Generation date: 2026-08-15

## Initial feature-graphic prompt

```text
Use case: ads-marketing
Asset type: Google Play feature graphic for Battery Notifier, designed for a final 1024 x 500 landscape export
Primary request: Create a polished, text-free promotional illustration that communicates continuous Android phone battery monitoring, a selected low-battery alert, and status sharing to a paired Wear OS watch.
Input images: Image 1 is the approved Battery Notifier brand reference. Preserve its deep navy, white, mint, and amber visual identity, but do not reproduce the complete app icon as a large centered logo.
Scene/backdrop: Rich deep navy background with subtle layered geometric arcs and data-flow particles only near the outer edges.
Subject: A central abstract mint battery-status panel connected by a clean flowing signal path to a smaller circular watch-complication-style status panel, with one restrained amber alert pulse. Use abstract interface geometry, not literal device frames.
Style/medium: Premium flat vector illustration, crisp geometric shapes, minimal fine detail, subtle depth from layered translucent shapes, brand-consistent and readable at small display sizes.
Composition/framing: Very wide landscape. Keep every important subject and the focal point within the central 60% of the canvas. Reserve the far left and right edges for expendable background decoration because Google Play may crop them.
Color palette: #102A43 deep navy, #FFFFFF white, #4FD1A5 mint, #FFB84D amber, with only closely related navy and mint supporting tones.
Text: none.
Constraints: no exact app icon lockup, no prominent logo duplication, no numbers, no letters, no words, no slogans, no UI text; no people; no phones or watch device mockups; no Google Play badge; no Android robot; no third-party logos; no ranking, price, award, or download symbols; no watermark; no tiny intricate detail; no pure black or pure white background.
```

## Final correction prompt

```text
Use case: precise-object-edit
Asset type: Google Play feature graphic for a final 1024 x 500 landscape export
Primary request: Keep the existing deep navy background, centered wide composition, circular data-flow rings, mint signal connection, lighting, and overall polish. Change only the duplicated icon-like subjects.
Required edits:
1. Remove both white bell symbols and their solid amber badge circles.
2. Replace the left alert badge with one restrained abstract amber pulse made of concentric translucent rings and a small glowing amber dot—no bell, no symbol, no logo.
3. Replace the right-side small battery-plus-bell motif with a clean circular watch-complication-style status display made only from a mint progress arc, one simple white status bar, and a small amber alert dot. It must not look like a physical watch or repeat the app icon.
4. Keep the large left battery-status panel as a functional illustration, but remove any exact app-icon lockup feeling.
Invariants: preserve the background, palette, data-flow connection, central safe composition, crisp premium vector style, and text-free design.
Constraints: no text, numbers, letters, slogans, logos, bells, device frames, people, Android robot, Google Play badge, ranking, pricing, download symbols, third-party marks, watermark, or new objects.
```

## Export notes

- The app icon is rendered directly from the approved SVG without generative modification.
- The selected generated source is center-cropped and resized to the exact feature-graphic dimensions.
- The final feature graphic is flattened to RGB with no alpha channel.
- Final dimensions, channels, byte sizes, and SHA-256 hashes are recorded in `asset-manifest.json`.

