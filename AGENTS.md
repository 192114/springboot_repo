# AGENTS.md

Gradle 多模块 Spring Boot 4.1(Java 21)纯后端工程。技术栈:MySQL + MyBatis-Plus + Sa-Token(Redis)+ springdoc + Lombok。

## 模块与依赖

| 模块 | 职责 | 依赖 |
|---|---|---|
| `backend-common` | 共享基础设施(统一响应/异常/日志/通用配置) | 无 |
| `backend-account` | 用户与 App 会话/Token 服务 | common |
| `backend-app` | App 端可运行应用(`AppApplication`,端口 8080) | common + account |
| `backend-admin` | Admin 端可运行应用(`AdminApplication`,端口 8081) | common + account |

依赖只允许沿此方向;`common` 不得依赖任何模块。新功能按端放入 `app` 或 `admin`,跨端复用的服务放 `account`,通用设施放 `common`。

## 常用命令

```bash
./gradlew build                  # 构建 + checkstyle + 测试(checkstyle maxWarnings=0,有 warning 即失败)
./gradlew test                   # 仅测试
./gradlew :backend-app:test      # 单模块测试
./gradlew :backend-app:bootRun   # 运行 App 端(默认 dev profile,需本地 MySQL + Redis)
```

配置文件在 `backend-app/src/main/resources/application{,-dev,-prod}.yaml`(admin 同理)。

## 代码约定

- 统一响应:`Result.success() / Result.success(data) / Result.fail(...)`;业务错误抛 `BusinessException(IResultCode)`,由 `GlobalExceptionHandler` 兜底,不要在 controller 里 try-catch 返回错误
- 包结构按功能域组织:`controller / service + service.impl / mapper / entity / dto / vo`,业务模块常量/枚举放 `constant`
- 实体表字段:MyBatis-Plus 全局 `id-type: auto`、逻辑删除字段 `deleted`(0 未删/1 已删)、驼峰映射
- 密码一律 Argon2(`PasswordUtil`),禁止明文或其他哈希
- 新增 service/util 需配套 JUnit 5 + Mockito 单元测试(参考现有 `src/test` 结构)

## 数据库

- 无 Flyway/Liquibase:`sql/schema.sql` 是全量基线;表结构变更需新写 `sql/*_migration.sql` 并同步更新 `schema.sql`
- 表命名:C 端 `app_` 前缀,管理端 `sys_` 前缀;每表标准列 `id BIGINT AUTO_INCREMENT / deleted / create_time / update_time`,每列带 COMMENT,utf8mb4
- Mapper XML(如需要)放 `classpath*:/mapper/**/*.xml`;目前均使用 BaseMapper/注解方式

## 版本注意

Spring Boot 4.x 较新(starter 命名已是 `spring-boot-starter-webmvc`);MyBatis-Plus / Sa-Token 为中文社区库。查文档优先用 context7,不要凭旧版本记忆写配置。
