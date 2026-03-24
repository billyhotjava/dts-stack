# dts-build Minimal Context Design

**Goal:** Reduce Docker build context size in `builds/dts-build.sh` so single-image builds no longer stream the entire repository to the Docker daemon.

**Approach:** Keep existing Dockerfiles unchanged. For images that currently build with `${REPO_ROOT}` as context, `dts-build.sh` will assemble a temporary minimal context containing only the tracked files those Dockerfiles need, plus any prebuilt backend jar artifacts under `builds/`.

**Scope:**
- Backend images: `dts-admin`, `dts-platform`, `dts-ingestion`, `dts-analytics`
- Webapp images: `dts-admin-webapp`, `dts-platform-webapp`, `dts-analytics-webapp-modern`
- Utility image: `dts-airflow-om`
- Existing small-context images `dts-dbt` and `dts-addax` stay unchanged

**Fallback:** If the workspace is not a Git checkout, the script falls back to the existing full-context behavior to preserve compatibility.
