# DataSphere Production Deployment

DataSphere is deployed as an independent application and uses its own `datasphere` metadata database.

Use the portable deployment guide at [`deploy/DEPLOYMENT.md`](../deploy/DEPLOYMENT.md).

The supported deployment artifacts are:

- `datasphere-0.1.0-SNAPSHOT.jar`
- `scripts/start.sh`, `scripts/stop.sh`, `scripts/status.sh`
- `config/datasphere.env` or `.run/datasphere.env`

A fresh installation starts from Flyway `V1__baseline.sql`. Future schema changes are delivered as new incremental Flyway migrations and are applied automatically on startup.
