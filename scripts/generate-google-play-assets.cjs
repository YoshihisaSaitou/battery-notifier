const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const path = require("node:path");
const sharp = require("sharp");

const repositoryRoot = path.resolve(__dirname, "..");
const outputDirectory = path.join(repositoryRoot, "docs", "assets", "google-play");
const iconSource = path.join(repositoryRoot, "docs", "assets", "app-icon-master.svg");
const featureSource = path.join(outputDirectory, "feature-graphic-source.png");
const iconOutput = path.join(outputDirectory, "google-play-app-icon.png");
const featureOutput = path.join(outputDirectory, "google-play-feature-graphic.png");
const manifestOutput = path.join(outputDirectory, "asset-manifest.json");

async function sha256(filePath) {
  const bytes = await fs.readFile(filePath);
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

async function describe(filePath) {
  const [metadata, stats, hash] = await Promise.all([
    sharp(filePath).metadata(),
    fs.stat(filePath),
    sha256(filePath),
  ]);

  return {
    file: path.relative(repositoryRoot, filePath).replaceAll("\\", "/"),
    format: metadata.format,
    width: metadata.width,
    height: metadata.height,
    channels: metadata.channels,
    hasAlpha: metadata.hasAlpha,
    sizeBytes: stats.size,
    sha256: hash,
  };
}

async function main() {
  await fs.mkdir(outputDirectory, { recursive: true });

  await sharp(iconSource, { density: 192 })
    .resize(512, 512, { fit: "fill" })
    .ensureAlpha()
    .png({ compressionLevel: 9, palette: false })
    .toFile(iconOutput);

  await sharp(featureSource)
    .resize(1024, 500, { fit: "cover", position: "centre" })
    .flatten({ background: "#102A43" })
    .removeAlpha()
    .png({ compressionLevel: 9, palette: false })
    .toFile(featureOutput);

  const [icon, feature, featureSourceDescription] = await Promise.all([
    describe(iconOutput),
    describe(featureOutput),
    describe(featureSource),
  ]);

  const manifest = {
    schemaVersion: 1,
    generatedAt: "2026-08-15T22:54:23+09:00",
    specification: "docs/google-play-listing-assets-specification.md",
    assets: {
      appIcon: {
        ...icon,
        source: "docs/assets/app-icon-master.svg",
        requirement: "512 x 512 px, 32-bit PNG with alpha, at most 1,024 KB",
      },
      featureGraphic: {
        ...feature,
        source: featureSourceDescription.file,
        requirement: "1,024 x 500 px, 24-bit PNG without alpha, at most 15 MB",
        altText: {
          ja: "スマートフォンの電池状態がWear OSへ同期され、設定した残量で通知されることを表すグラフィック",
          en: "Graphic showing phone battery status syncing to Wear OS with an alert at the selected level",
        },
      },
    },
    provenance: "docs/assets/google-play/provenance.md",
    generator: "scripts/generate-google-play-assets.cjs",
  };

  await fs.writeFile(manifestOutput, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  process.stdout.write(`${JSON.stringify(manifest.assets, null, 2)}\n`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

