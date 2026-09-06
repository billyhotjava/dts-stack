import hashlib
import importlib.util
import io
import json
import tarfile
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("release_metadata", Path(__file__).with_name("write-release-metadata.py"))
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


class ReleaseMetadataTest(unittest.TestCase):
    def test_archive_identity_and_lite_upgrader_checksums_match_actual_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "images").mkdir()
            (root / "dts-stack").mkdir()
            (root / "dts-stack" / "imgversion.conf").write_text("IMAGE_DTS_PLATFORM=dts-platform:s104-test\n")
            config = json.dumps({"architecture": "amd64", "os": "linux", "config": {"Labels": {"org.opencontainers.image.revision": "a" * 40}}}).encode()
            image_id = hashlib.sha256(config).hexdigest()
            archive_path = root / "images" / "platform.tar"
            with tarfile.open(archive_path, "w") as archive:
                content = {image_id + ".json": config, "manifest.json": json.dumps([{"Config": image_id + ".json", "RepoTags": ["dts-platform:s104-test"], "Layers": []}]).encode()}
                for name, data in content.items():
                    entry = tarfile.TarInfo(name)
                    entry.size = len(data)
                    archive.addfile(entry, io.BytesIO(data))
            metadata.write_metadata(root, "misc", "a" * 40)
            manifest = json.loads((root / "misc" / "release-manifest.json").read_text())
            self.assertEqual(manifest["images"][0]["images"][0]["imageId"], "sha256:" + image_id)
            self.assertEqual(manifest["sourceCommit"], "a" * 40)
            digest, filename = (root / "misc" / "checksums.txt").read_text().strip().split("  ")
            self.assertEqual(digest, metadata.sha256(root / "images" / filename))
            for line in (root / "misc" / "files-checksums.txt").read_text().splitlines():
                digest, filename = line.split("  ")
                self.assertEqual(digest, metadata.sha256(root / filename))

    def test_explicit_no_images_does_not_emit_empty_rejected_checksum_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata.write_metadata(root, "extra", "b" * 40)
            self.assertFalse((root / "extra" / "checksums.txt").exists())
            self.assertFalse(json.loads((root / "extra" / "release-manifest.json").read_text())["imagesIncluded"])


if __name__ == "__main__":
    unittest.main()
