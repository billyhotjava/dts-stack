Place your Hadoop client configs here for the Inceptor/Hive catalog.

Required files (at minimum):
- core-site.xml
- hdfs-site.xml

Optional (if using HiveServer/LLAP, Sentry/Ranger, Kerberos, etc.):
- hive-site.xml (only if you need to override defaults)
- krb5.conf and keytabs (Kerberos clusters; requires extra Trino configuration)

These files are mounted into the Trino container at /etc/trino/hive-conf,
and referenced by the catalog config in /etc/trino/catalog/inceptor.properties.

After adding configs, restart Trino and verify:
- docker compose exec dts-trino curl -s http://localhost:8080/v1/info
- docker compose exec dts-trino trino-cli: SHOW CATALOGS;  -- should list 'inceptor'
