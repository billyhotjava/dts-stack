Koal SDK vendor assets
======================

Purpose
- Serve Koal middleware SDK files (e.g., deviceOperator.js) from a dedicated static service `dts-koal-vendor`.
- Avoid relying on `source/` during deployment; keep assets under `services/koal-vendor/vendor/koal/`.

How to use
- Place the official Koal SDK files into:
  - `services/koal-vendor/vendor/koal/`
  - Example files: `deviceOperator.js`, `deviceOperator_types.js`, `devService.js`, `devService_types.js`.
- Then bring up the stack. Traefik routes:
  - `https://bi.${BASE_DOMAIN}/vendor/koal/*`
  - `https://biadmin.${BASE_DOMAIN}/vendor/koal/*`
  both point to this static service.

Permissions
- Files should be world-readable (0644) and directories executable (0755). If needed:
  - `chmod -R a+rX services/koal-vendor/vendor`

Notes
- This directory is part of the deployment assets and is safe to keep on hosts; it replaces any prior bind mounts from `source/`.

