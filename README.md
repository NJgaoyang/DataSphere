# DataSphere

DataSphere 是面向企业内部的数据开发与治理平台，作为独立项目维护。前端统一使用 `frontend-v2`，元数据库统一使用 `datasphere`。

## 技术基线

- Backend: Java 21 + Spring Boot 3.4.5 + MySQL 8.0 + Flyway
- Frontend: Vue 3 + TypeScript + Vite（唯一前端目录：`frontend-v2`）
- Metadata database: `datasphere`
- Package: `datasphere-0.1.0-SNAPSHOT.jar`
- Main process: `DataSphere`
- Repository: `https://github.com/NJgaoyang/DataSphere.git`

## 数据库版本策略

新安装只执行 `src/main/resources/db/migration/V1__baseline.sql`，一次性创建当前完整数据库结构和必要系统种子数据。历史项目的迁移版本不会带入 DataSphere。

后续数据库结构发生变化时，继续新增 `V2__*.sql`、`V3__*.sql` 等 Flyway 增量迁移；已部署环境启动时会自动执行尚未应用的新版本。已经发布的迁移文件不得修改。

## 本地构建

```bash
mvn clean test
mvn package -DskipTests
```

Maven 在打包阶段会自动执行 `frontend-v2` 的 `npm ci` 与生产构建，并将前端静态资源打进 Spring Boot jar。

## 新环境部署

先在 MySQL 8.0 中执行：

```sql
CREATE DATABASE IF NOT EXISTS datasphere
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
```

复制 `deploy/datasphere.env.example` 为运行环境配置，填写数据库、凭据主密钥和外部运行组件地址。首次生产启动还需要设置 `DATASPHERE_ADMIN_INITIAL_PASSWORD`，成功启动后从环境文件删除该明文变量。详细步骤见 `deploy/DEPLOYMENT.md`。

源码目录可直接使用：

```bash
scripts/start.sh
scripts/status.sh
scripts/stop.sh
```

构建可搬迁发布包：

```bash
scripts/build-release.sh
```

产物为 `target/datasphere-0.1.0-SNAPSHOT-dist.tar.gz`。解压到另一台 JDK 21 服务器、修改环境配置后即可启动，无需重新构建前端或后端。
