# dts-bi-dashboard-overlay

Overlay for Superset (dts-bi-dashboard). Keep upstream source clean in `source/dts-bi-dashboard`; place all customizations here and let the build script stage them into a temp workspace.

Recommended layout:
- `patches/` – git-format patches applied to the staged tree.
- `plugins/` – custom Python/JS plugins or security manager overrides copied into the staged tree.
- `config/` – files like `superset_config.py`, metadata seed/export, init scripts.
- `docker/` – custom Dockerfile/entrypoint or extra assets for the image build.

Use `tools/bi-build.sh superset <tag> [--push]` to build multi-arch images with this overlay.
