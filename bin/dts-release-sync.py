#!/usr/bin/env python3
"""Publish existing amd64 images and managed runtime files; consume signed releases.

No image builds. Runtime ownership and upgrades remain with dts-runtime-files.sh
and dts-upgrade-lite. Configuration and private keys live outside the repository.
"""
import argparse
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import time


def run(args, **kwargs):
    return subprocess.run([str(x) for x in args], check=True, **kwargs)


def output(args, **kwargs):
    return run(args, stdout=subprocess.PIPE, **kwargs).stdout


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_bytes(data)
    os.replace(temporary, path)


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as tar:
        entries = tar.getmembers()
        for entry in entries:
            parts = Path(entry.name).parts
            if (not parts or entry.name.startswith('/') or '..' in parts
                    or not (entry.isfile() or entry.isdir())):
                raise ValueError('Unsafe archive member: ' + entry.name)
        # All links and special files have been rejected, including hard links.
        tar.extractall(destination, members=entries)


def image_info(path):
    with tarfile.open(path) as tar:
        manifest = json.load(tar.extractfile('manifest.json'))
        if len(manifest) != 1:
            raise ValueError('Require one image per archive: ' + path.name)
        raw = tar.extractfile(manifest[0]['Config']).read()
        config = json.loads(raw)
        tags = manifest[0].get('RepoTags') or []
        if config.get('architecture') != 'amd64' or config.get('os') != 'linux':
            raise ValueError('Not linux/amd64: ' + path.name)
        if len(tags) != 1 or not re.fullmatch(r'dts-[a-z0-9-]+:[A-Za-z0-9_.-]+', tags[0]):
            raise ValueError('Unexpected image tag: ' + path.name)
        return {'tag': tags[0], 'imageId': 'sha256:' + hashlib.sha256(raw).hexdigest(),
                'created': config['created'], 'archive': path.name}


def source_snapshot(repo):
    if output(['git', '-C', repo, 'status', '--porcelain', '--untracked-files=no']).strip():
        raise ValueError('Tracked deploy files are dirty; publish only committed runtime files')
    tracked = output(['git', '-C', repo, 'ls-files', '-z'])
    script = 'source "$1/bin/lib/dts-runtime-files.sh"; while IFS= read -r -d "" p; do dts_managed_runtime_file "$p" && printf "%s\\0" "$p"; done; true'
    managed = output(['bash', '-c', script, 'managed', repo], input=tracked).split(b'\0')
    paths = [p.decode() for p in managed if p]
    files = {}
    for rel in paths:
        p = repo / rel
        if p.is_symlink():
            raise ValueError('Managed runtime symlink: ' + rel)
        if p.is_file():
            files[rel] = digest(p)
    images = {}
    for path in sorted((repo / 'builds/dist').glob('*.tar')):
        stat = path.stat()
        if time.time() - stat.st_mtime < 120:
            raise ValueError('Image export still settling: ' + path.name)
        info = image_info(path)
        service = info['tag'].split(':')[0]
        if service not in images or info['created'] > images[service]['created']:
            info.update(size=stat.st_size, mtime=stat.st_mtime_ns)
            images[service] = info
    if not images:
        raise ValueError('No amd64 image archives in builds/dist')
    fingerprint = hashlib.sha256(canonical({'files': files, 'images': images})).hexdigest()
    return files, images, fingerprint


def prepare(c, state):
    repo = Path(c['repo'])
    files, images, fingerprint = source_snapshot(repo)
    published = state / 'published.json'
    if published.exists() and json.loads(published.read_text())['fingerprint'] == fingerprint:
        print('NO_CHANGE: local images and managed runtime files', flush=True)
        return None
    revision = output(['git', '-C', repo, 'rev-parse', 'HEAD']).decode().strip()
    release = 'release-' + fingerprint[:24]
    package = state / (release + '.tar.gz')
    with tempfile.TemporaryDirectory(dir=state) as tmp:
        work = Path(tmp)
        raw = work / 'runtime.tar.gz'
        # The existing formal packaging entry point, explicitly without either image directory.
        run(['bash', repo / 'builds/dts-build.sh', '--pack', '--no-images', '--output', raw], cwd=repo)
        root = work / 'release'
        extract(raw, root)
        stack = root / 'dts-stack'
        for path in list(stack.rglob('*')):
            if path.is_file() and path.relative_to(stack).as_posix() not in files:
                path.unlink()
        for rel, checksum in files.items():
            if not (stack / rel).is_file() or digest(stack / rel) != checksum:
                raise ValueError('Formal package omitted/changed managed runtime: ' + rel)
        # Only shipped image keys may update the remote .env. Do not ship local secrets.
        tags = {v['tag'] for v in images.values()}
        version_lines = []
        for line in (repo / 'imgversion.conf').read_text().splitlines():
            if re.fullmatch(r'IMAGE_[A-Z0-9_]+=.+', line) and line.split('=', 1)[1].strip('\"\'') in tags:
                version_lines.append(line)
        if len(version_lines) < len(tags):
            raise ValueError('imgversion.conf does not map every shipped image')
        (stack / 'imgversion.conf').write_text('\n'.join(version_lines) + '\n')
        for path in stack.glob('imgversion*.conf'):
            if path.name != 'imgversion.conf':
                path.unlink()
        for info in images.values():
            shutil.copyfile(repo / 'builds/dist' / info['archive'], root / 'images' / info['archive'])
        run(['python3', '-B', repo / 'builds/write-release-metadata.py', '--root', root,
             '--metadata-dir', 'extra', '--revision', revision])
        if source_snapshot(repo)[2] != fingerprint:
            raise ValueError('Source changed while packaging; retry next poll')
        with tarfile.open(str(package) + '.part', 'w:gz', compresslevel=1) as tar:
            for name in ('dts-stack', 'images', 'extra'):
                tar.add(root / name, arcname=name)
        os.replace(str(package) + '.part', package)
    payload = {'format': 1, 'id': release, 'file': package.name, 'sha256': digest(package),
               'size': package.stat().st_size, 'publishedAt': int(time.time()),
               'runtimeCommit': revision, 'fingerprint': fingerprint}
    signature = output(['openssl', 'dgst', '-sha256', '-sign', c['signingKey']], input=canonical(payload))
    envelope = {'payload': payload, 'signature': base64.b64encode(signature).decode()}
    atomic(state / 'latest.json', canonical(envelope))
    return payload


def publish(c, state):
    payload = prepare(c, state)
    if payload is None:
        return
    # SFTP account is restricted on the server. Upload complete package before latest.json.
    remote = c['remoteDir']
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+', remote):
        raise ValueError('Unsafe remoteDir')
    package = state / payload['file']
    if '"' in str(state) or '\n' in str(state):
        raise ValueError('Unsafe state path')
    batch = (f'put "{package}" {remote}/{payload["file"]}.part\n'
             f'rename {remote}/{payload["file"]}.part {remote}/{payload["file"]}\n'
             f'put "{state / "latest.json"}" {remote}/latest.json.part\n'
             f'rename {remote}/latest.json.part {remote}/latest.json\n')
    run(['sftp', '-q', '-i', c['sshKey'], '-oBatchMode=yes', '-oConnectTimeout=15',
         '-oStrictHostKeyChecking=yes', '-b', '-', c['sshHost']], input=batch.encode(), timeout=7200)
    atomic(state / 'published.json', canonical(payload))
    print('PUBLISHED ' + json.dumps(payload), flush=True)


def verified_envelope(raw, public_key):
    envelope = json.loads(raw)
    payload = envelope['payload']
    with tempfile.TemporaryDirectory() as tmp:
        signature = Path(tmp) / 'signature'
        signature.write_bytes(base64.b64decode(envelope['signature'], validate=True))
        run(['openssl', 'dgst', '-sha256', '-verify', public_key, '-signature', signature],
            input=canonical(payload), stdout=subprocess.DEVNULL)
    if (payload.get('format') != 1 or not re.fullmatch(r'release-[a-f0-9]{24}', payload['id'])
            or payload['file'] != payload['id'] + '.tar.gz'
            or not re.fullmatch(r'[a-f0-9]{64}', payload['sha256'])
            or not 0 < payload['size'] < 20 * 1024**3
            or payload['publishedAt'] > time.time() + 300):
        raise ValueError('Invalid signed release metadata')
    return payload


def compose(c, *args):
    return ['docker', 'compose', '-p', c['project'], '-f', str(Path(c['target']) / 'docker-compose-app.yml'), *args]


def containers(c):
    ids = output(compose(c, 'ps', '-a', '-q'), cwd=c['target']).decode().split()
    return json.loads(output(['docker', 'inspect', *ids])) if ids else []


def healthy(c, expected_services, expected_images, timeout=600):
    deadline = time.monotonic() + timeout
    while True:
        current = {v['Config']['Labels']['com.docker.compose.service']: v for v in containers(c)}
        bad = []
        for service in expected_services:
            v = current.get(service, {})
            status = v.get('State', {})
            if (status.get('Status') != 'running'
                    or status.get('Health', {}).get('Status', 'healthy') != 'healthy'):
                bad.append(service)
        for tag, expected in expected_images.items():
            users = [v for v in current.values() if v['Config']['Image'] == tag]
            if not users or any(v['Image'] != expected for v in users):
                bad.append(tag)
        if not bad:
            print('HEALTHY: ' + ', '.join(sorted(expected_services)), flush=True)
            return
        if time.monotonic() >= deadline:
            raise RuntimeError('Health/image verification failed: ' + ', '.join(bad))
        time.sleep(10)


def consume(c, state, check_only):
    if (state / 'FAILED.json').exists():
        raise RuntimeError('Previous apply failed/interrupted; inspect FAILED.json and upgrade backup before removing it')
    raw = output(['curl', '-fsS', '--connect-timeout', '15', '--max-time', '60', '--max-filesize', '65536',
                  c['baseUrl'].rstrip('/') + '/latest.json'])
    payload = verified_envelope(raw, c['publicKey'])
    applied_file = state / 'applied.json'
    if applied_file.exists():
        applied = json.loads(applied_file.read_text())
        if applied['sha256'] == payload['sha256']:
            print('NO_CHANGE: ' + payload['id'], flush=True)
            return
        if payload['publishedAt'] <= applied['publishedAt']:
            raise ValueError('Refusing older/replayed release')
    package = state / payload['file']
    if not package.exists() or digest(package) != payload['sha256']:
        if shutil.disk_usage(state).free < payload['size'] * 4 + 1024**3:
            raise RuntimeError('Insufficient download/extraction space')
        partial = Path(str(package) + '.part')
        run(['curl', '-fS', '--connect-timeout', '15', '--max-time', '7200', '--retry', '3',
             '--speed-limit', '1024', '--speed-time', '120', '--continue-at', '-',
             '--output', partial, c['baseUrl'].rstrip('/') + '/' + payload['file']])
        if partial.stat().st_size != payload['size'] or digest(partial) != payload['sha256']:
            partial.unlink()
            raise ValueError('Package checksum/size mismatch')
        os.replace(partial, package)
    release = state / payload['id']
    if release.exists():
        shutil.rmtree(release)
    extract(package, release)
    manifest = json.loads((release / 'extra/release-manifest.json').read_text())
    expected = {}
    for item in manifest['images']:
        info = image_info(release / 'images' / item['archive'])
        expected[info['tag']] = info['imageId']
    if not expected:
        raise ValueError('Release contains no images')
    upgrader = release / 'dts-stack/bin/dts-upgrade-lite'
    args = ['--target', c['target'], '--source', release / 'dts-stack', '--images-dir',
            release / 'images', '--extra-dir', release / 'extra']
    run(['bash', upgrader, 'plan', *args], cwd=c['target'])
    if check_only:
        print('CHECK_OK ' + payload['id'], flush=True)
        return
    before = containers(c)
    active = {v['Config']['Labels']['com.docker.compose.service'] for v in before if v['State']['Running']}
    if not active:
        raise ValueError('Refusing update without an existing running stack')
    # Query through Airflow's installed ORM; no credentials or files are copied out.
    schedulers = [v for v in before if v['Config']['Labels']['com.docker.compose.service'] == 'dts-airflow-scheduler']
    if schedulers:
        count = output(['docker', 'exec', schedulers[0]['Id'], 'python', '-c',
            'from airflow.settings import Session; from airflow.models.dagrun import DagRun; '
            's=Session(); print("ACTIVE_RUNS="+str(s.query(DagRun).filter(DagRun.state=="running").count())); s.close()'],
            timeout=60).decode()
        if not re.search(r'^ACTIVE_RUNS=0$', count, re.M):
            print('DEFERRED: Airflow has active runs', flush=True)
            return
    atomic(state / 'before.json', canonical(before))
    # Durable latch prevents an unattended restart loop after any failure/power interruption.
    atomic(state / 'FAILED.json', canonical(payload))
    run(['bash', upgrader, 'apply', *args, '--yes', '--force-recreate'], cwd=c['target'], timeout=3600)
    healthy(c, active, expected)
    atomic(applied_file, canonical(payload))
    (state / 'FAILED.json').unlink()
    print('APPLIED ' + payload['id'], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('publish', 'consume', 'check'))
    parser.add_argument('--config', required=True, type=Path)
    args = parser.parse_args()
    c = json.loads(args.config.read_text())
    state = Path(c['state'])
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(state, 0o700)
    with (state / 'lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print('BUSY: existing release operation', flush=True)
            return
        if args.mode == 'publish':
            publish(c, state)
        else:
            consume(c, state, args.mode == 'check')


if __name__ == '__main__':
    main()
