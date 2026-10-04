Place vendor PKI client JARs here (read-only mount into dts-admin at /opt/dts/vendor).

Example:
- Copy svs-uk_custom.jar from the vendor package to this folder.
- Set environment variable DTS_PKI_VENDOR_JAR=/opt/dts/vendor/svs-uk_custom.jar in .env (uncomment).
- Optionally set DTS_PKI_GATEWAY_HOST and DTS_PKI_GATEWAY_PORT for the vendor gateway.

This directory is mounted by docker-compose-app.yml as:
  ./services/dts-admin/vendor:/opt/dts/vendor:ro,z

Do not commit proprietary vendor binaries to version control.
