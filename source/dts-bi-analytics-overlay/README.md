# dts-bi-analytics-overlay

Overlay for Metabase (dts-bi-analytics). Keep upstream source clean in `source/dts-bi-analytics`; put all customizations here and let the build script stage them into a temp workspace.

Recommended layout:
- `patches/` – git-format patches applied to the staged tree.
- `plugins/` – drivers/plugins to vendored into the image.
- `config/` – env templates, init SQL, custom settings files.
- `docker/` – custom Dockerfile/entrypoint or extra assets for the image build.

Use `tools/bi-build.sh metabase <tag> [--push]` to build multi-arch images with this overlay.
