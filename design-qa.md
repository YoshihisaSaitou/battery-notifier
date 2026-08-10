# BN-008 Design QA

- Source visual truth: `C:/Users/YOSHIH~1/AppData/Local/Temp/codex-clipboard-0d52cc48-ffeb-4695-acc7-6e3db795477e.png`
- Human design override: `HUM-BN008-002` removes the numeric labels beneath the slider.
- Implementation screenshot: `artifacts/bn008-threshold-editor-pixel9a.png`
- Source pixels: 406 x 114.
- Implementation pixels: 1080 x 126.
- Runtime viewport: Pixel 9a AVD, API 36, 1080 x 2424 physical pixels, density 420 dpi.
- Normalization: the implementation crop is approximately 411 x 48 dp at a 2.625 density scale; horizontal proportions were compared against the 406 px source crop. The source density is unknown, and the implementation is intentionally shorter because the human removed the label row.
- State: light theme, threshold draft 50%, enabled decrease/increase controls.

## Full-view comparison evidence

The source image and implementation crop were opened together. Both present a circular decrease control on the left, a flexible horizontal slider in the center, and a circular increase control on the right. The source's numeric label row is intentionally absent from the implementation because `HUM-BN008-002` explicitly overrides that portion of the visual reference.

No clipping, overlap, horizontal overflow, or residual numeric text is visible. The 48 dp controls remain distinct from the slider, and removing the label row reduces the component to a single compact operation row.

## Focused region comparison evidence

The whole source is already a focused threshold-control crop, so no smaller sub-crop was needed. Runtime assertions additionally verified that the decrease control center is left of the slider center, the increase control center is right of it, and nodes with text `5`, `50`, or `100` do not exist in the isolated editor fixture.

## Required fidelity surfaces

- Fonts and typography: the editor no longer contains visible text. The existing current-value display outside this isolated component remains the value-reading surface.
- Spacing and layout rhythm: the side controls, 8 dp gaps, and flexible slider form the requested single-row structure. The 48 dp circular controls satisfy the minimum touch target.
- Colors and visual tokens: the implementation uses the app's existing Material 3 color scheme rather than copying the screenshot's brighter blue. Contrast is clear and consistent with the product.
- Image quality and asset fidelity: no raster placeholder, handcrafted icon, or text glyph is used. Add and Remove use the official Google Material 24 px vector paths and render sharply at device density without the full extended icon dependency.
- Copy and content: no numeric scale copy is present beneath the slider. Existing English/Japanese accessibility descriptions are retained for the decrease and increase controls.

## Interaction and accessibility evidence

- Pixel 9a runtime layout and label-absence test: passed 1/1.
- Pixel 9a one-percent adjustment and lower-bound disabled-state test: passed 1/1.
- Screenshot capture test: passed 1/1.
- JVM boundary fixture: passed for 5, 20, and 100.
- TalkBack labels use localized decrease/increase descriptions; Pixel 10 Pro Fold, maximum-font, and physical-device checks remain Human verification work.

## Findings

No actionable P0, P1, or P2 mismatch remains for the requested layout.

## Follow-up polish

- P3: the current Material 3 slider uses a vertical pill thumb and end stop indicator, while the reference uses a circular thumb and sparse ticks. The dense one-percent tick dots found in the first runtime comparison were removed; values still round to whole percentages and the side controls still adjust by exactly one percent.
- P3: color follows the app theme instead of duplicating the reference image's exact blue and gray values.

## Comparison history

1. Initial implementation comparison found the requested side-control and label structure present. The minimum label was confirmed as an intentional `5` product constraint. No P0/P1/P2 correction was required.
2. The screenshot fixture initially targeted a production test tag that was not present in the installed APK. The fixture was changed to own its capture wrapper, rebuilt, and rerun successfully. This was evidence-fixture repair, not a visible design correction.
3. The full extended icon dependency was replaced with the official Add/Remove vector assets, and the dense discrete tick dots were removed. A fresh Pixel 9a screenshot was opened beside the source; control order, spacing, labels, and icon appearance remain correct with no P0/P1/P2 mismatch.
4. `HUM-BN008-002` removed the numeric-label requirement. The label row and related typography were deleted, the Pixel 9a fixture was changed to assert that `5`, `50`, and `100` do not exist, and a new 1080 x 126 screenshot was opened beside the source. The single-row controls are unclipped and no P0/P1/P2 mismatch remains against the human-updated target.

## Final result

final result: passed
