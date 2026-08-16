const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs/promises");
const path = require("node:path");
const sharp = require("sharp");

const repositoryRoot = path.resolve(__dirname, "..");
const assetDirectory = path.join(repositoryRoot, "docs", "assets", "google-play");

async function sha256(filePath) {
  const bytes = await fs.readFile(filePath);
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

async function validateAsset(asset, expected) {
  const filePath = path.join(repositoryRoot, asset.file);
  const [metadata, stats, hash] = await Promise.all([
    sharp(filePath).metadata(),
    fs.stat(filePath),
    sha256(filePath),
  ]);

  assert.equal(metadata.format, "png");
  assert.equal(metadata.width, expected.width);
  assert.equal(metadata.height, expected.height);
  assert.equal(metadata.channels, expected.channels);
  assert.equal(metadata.hasAlpha, expected.hasAlpha);
  assert.ok(stats.size <= expected.maximumBytes, `${asset.file} exceeds its byte limit`);
  assert.equal(stats.size, asset.sizeBytes);
  assert.equal(hash, asset.sha256);

  return { metadata, sizeBytes: stats.size, sha256: hash };
}

async function main() {
  const manifest = JSON.parse(
    await fs.readFile(path.join(assetDirectory, "asset-manifest.json"), "utf8"),
  );

  const icon = await validateAsset(manifest.assets.appIcon, {
    width: 512,
    height: 512,
    channels: 4,
    hasAlpha: true,
    maximumBytes: 1024 * 1024,
  });

  const feature = await validateAsset(manifest.assets.featureGraphic, {
    width: 1024,
    height: 500,
    channels: 3,
    hasAlpha: false,
    maximumBytes: 15 * 1024 * 1024,
  });

  assert.ok(manifest.assets.featureGraphic.altText.ja.length > 0);
  assert.ok(manifest.assets.featureGraphic.altText.en.length > 0);

  process.stdout.write(
    `${JSON.stringify({ result: "pass", icon, feature }, null, 2)}\n`,
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

