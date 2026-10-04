#!/usr/bin/env python3
"""No Docker or network: signed release and archive rejection contracts."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release_sync', Path(__file__).parents[1] / 'bin/dts-release-sync.py')
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class ReleaseSyncTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def archive(self, members):
        archive = self.root / 'test.tar'
        with tarfile.open(archive, 'w') as tar:
            for name, data in members.items():
                entry = tarfile.TarInfo(name)
                if data is None:
                    entry.type = tarfile.SYMTYPE
                    entry.linkname = '/tmp'
                    tar.addfile(entry)
                else:
                    entry.size = len(data)
                    tar.addfile(entry, io.BytesIO(data))
        return archive

    def test_reject_path_traversal_before_extracting_any_file(self):
        archive = self.archive({'good': b'ok', '../escape': b'bad'})
        with self.assertRaises(ValueError):
            sync.extract(archive, self.root / 'unpack')
        self.assertFalse((self.root / 'unpack/good').exists())

    def test_reject_archive_symlink(self):
        with self.assertRaises(ValueError):
            sync.extract(self.archive({'link': None}), self.root / 'unpack')

    def test_accept_regular_files(self):
        sync.extract(self.archive({'dts-stack/entry': b'ok'}), self.root / 'unpack')
        self.assertEqual((self.root / 'unpack/dts-stack/entry').read_bytes(), b'ok')

    def test_reject_arm_images(self):
        archive = self.archive({'manifest.json': json.dumps([{'Config': 'config', 'RepoTags': ['dts-platform:1.0.0']}]).encode(),
                                'config': json.dumps({'architecture': 'arm64', 'os': 'linux'}).encode()})
        with self.assertRaisesRegex(ValueError, 'amd64'):
            sync.image_info(archive)

    def test_signed_manifest_and_tampering(self):
        private, public = self.root / 'private.pem', self.root / 'public.pem'
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)], check=True)
        payload = {'format': 1, 'id': 'release-' + 'a' * 24, 'file': 'release-' + 'a' * 24 + '.tar.gz',
                   'sha256': 'b' * 64, 'size': 12, 'publishedAt': int(time.time())}
        signature = sync.output(['openssl', 'dgst', '-sha256', '-sign', private], input=sync.canonical(payload))
        envelope = {'payload': payload, 'signature': sync.base64.b64encode(signature).decode()}
        self.assertEqual(sync.verified_envelope(sync.canonical(envelope), public), payload)
        payload['sha256'] = 'c' * 64
        with self.assertRaises(subprocess.CalledProcessError):
            sync.verified_envelope(sync.canonical(envelope), public)

    def test_failure_latch_blocks_all_mutations(self):
        (self.root / 'FAILED.json').write_text('{}')
        with patch.object(sync, 'run') as run:
            with self.assertRaisesRegex(RuntimeError, 'Previous apply'):
                sync.consume({}, self.root, False)
            run.assert_not_called()

    def test_same_release_never_restarts(self):
        payload = {'sha256': 'a' * 64, 'id': 'same'}
        (self.root / 'applied.json').write_text(json.dumps(payload))
        with patch.object(sync, 'output', return_value=b'{}'), patch.object(sync, 'verified_envelope', return_value=payload), patch.object(sync, 'run') as run:
            sync.consume({'baseUrl': 'http://test', 'publicKey': 'test'}, self.root, False)
            run.assert_not_called()

    def test_reject_old_release(self):
        (self.root / 'applied.json').write_text(json.dumps({'sha256': 'a', 'publishedAt': 100}))
        with patch.object(sync, 'output', return_value=b'{}'), patch.object(sync, 'verified_envelope', return_value={'sha256': 'b', 'publishedAt': 99}):
            with self.assertRaisesRegex(ValueError, 'replayed'):
                sync.consume({'baseUrl': 'http://test', 'publicKey': 'test'}, self.root, False)

    def test_oci_manifest_id_maps_to_same_image_config(self):
        config = {'architecture': 'amd64', 'os': 'linux', 'created': '2026-09-07T00:00:00Z',
                  'rootfs': {'diff_ids': ['sha256:layer']}}
        raw = sync.canonical(config)
        config_id = 'sha256:' + sync.hashlib.sha256(raw).hexdigest()
        manifest = sync.canonical({'config': {'digest': config_id}, 'layers': []})
        manifest_id = 'sha256:' + sync.hashlib.sha256(manifest).hexdigest()
        path = self.archive({'manifest.json': sync.canonical([{'Config': 'config', 'RepoTags': ['dts-platform:1.0.0']}]),
                             'config': raw, 'index.json': sync.canonical({'manifests': [{'digest': manifest_id}]}),
                             'blobs/sha256/' + manifest_id[7:]: manifest})
        result = sync.image_info(path)
        self.assertEqual(set(result['runtimeIds']), {config_id, manifest_id})
        self.assertEqual(result['layers'], ['sha256:layer'])

    def test_static_dag_and_generated_macro_are_in_snapshot(self):
        repo = self.root
        helper = repo / 'bin/lib/dts-runtime-files.sh'
        helper.parent.mkdir(parents=True)
        helper.write_bytes((Path(__file__).parents[1] / 'bin/lib/dts-runtime-files.sh').read_bytes())
        dag = repo / 'services/dts-airflow/dags/dts_release_build_example.py'
        dag.parent.mkdir(parents=True)
        dag.write_text('# required generated static DAG\n')
        macro = repo / 'services/dts-dbt/macros/example.sql'
        macro.parent.mkdir(parents=True)
        macro.write_text('-- required generated macro\n')
        (repo / 'services/dts-dbt/run-model-build.sh').write_text('# source-only helper\n')
        (repo / 'builds/dist').mkdir(parents=True)
        (repo / 'builds/dist/app.tar').write_bytes(b'fixture')
        sync.os.utime(repo / 'builds/dist/app.tar', (1, 1))
        original_output = sync.output
        def fake_output(args, **kwargs):
            return b'' if args[0] == 'git' else original_output(args, **kwargs)
        with patch.object(sync, 'output', side_effect=fake_output), patch.object(sync, 'image_info', return_value={
                'tag': 'dts-platform:1.0.0', 'created': '2026-09-07T00:00:00Z'}):
            files, _, _ = sync.source_snapshot(repo)
        self.assertIn(dag.relative_to(repo).as_posix(), files)
        self.assertIn(macro.relative_to(repo).as_posix(), files)
        self.assertNotIn('services/dts-dbt/run-model-build.sh', files)


if __name__ == '__main__':
    unittest.main()
