# Task Manage Serve

基于 RuoYi 3.9.2 扩展的任务管理后端，采用 Spring Boot 和 Maven 多模块架构。项目复用若依的登录认证、用户角色、权限控制及系统管理能力，通过独立的 `ruoyi-task` 模块提供任务管理、任务详情、操作历史和统计看板接口。

本仓库为 Java 后端，不包含前端应用；所有模块由 `ruoyi-admin` 统一启动。

## 目录

- [项目功能](#项目功能)
- [技术栈](#技术栈)
- [模块结构](#模块结构)
- [快速开始](#快速开始)
- [认证与权限](#认证与权限)
- [统一响应体](#统一响应体)
- [全局异常处理](#全局异常处理)
- [任务接口](#任务接口)
- [开发与验证](#开发与验证)
- [相关文档](#相关文档)

## 项目功能

### 任务业务

- 任务创建、编辑、指派负责人、状态推进及逻辑删除。
- 按关键词、状态、优先级和负责人筛选，支持分页及指定字段排序。
- 任务详情及创建、编辑、指派、状态变更、删除操作历史。
- 状态分布、优先级占比、个人完成率统计，支持按任务创建日期筛选。
- 记录级权限控制，普通用户只能查看自己创建或当前负责的任务。
- 任务变更与操作日志在同一事务内写入。

### 系统基础能力

保留若依的用户、角色、部门、岗位、菜单、字典、参数配置、通知公告、操作日志、登录日志、在线用户、缓存监控、服务监控、Quartz 定时任务和代码生成等后端能力。

`ruoyi-task` 中的业务任务与 `ruoyi-quartz` 中的定时调度任务是两个独立模块。

## 技术栈

以下版本以根目录 [pom.xml](pom.xml) 中的声明为准。

| 技术 | 版本 / 配置 | 用途 |
| --- | --- | --- |
| Java | 17 | 编译与运行环境 |
| Maven | 多模块构建 | 依赖管理、测试与打包 |
| Spring Boot | 4.1.0 | 应用启动与自动配置 |
| Spring Security | 由 Spring Boot 管理 | 登录认证、方法权限控制、BCrypt 密码编码 |
| MyBatis Spring Boot Starter | 4.1.0 | 数据访问与 XML SQL 映射 |
| PageHelper Spring Boot Starter | 4.1.1 | 数据库分页 |
| MySQL | 8.0，任务模块使用 CHECK 约束 | 系统数据、任务与操作日志存储 |
| Druid | 1.2.28 | 数据库连接池与监控 |
| Redis / Spring Data Redis | 客户端依赖由 Spring Boot 管理 | 登录状态、验证码及缓存 |
| JJWT | 0.9.1 | JWT 令牌生成与解析 |
| Jakarta Validation | 由 Spring Boot 管理 | 请求参数校验 |
| Springdoc OpenAPI | 3.1.0 | OpenAPI 与 Swagger UI |
| Quartz | 由 Spring Boot 管理 | 定时任务调度 |
| Fastjson2 | 2.0.64 | JSON 工具与任务操作快照 |
| Apache POI | 5.5.1 | Excel 导入导出 |
| Velocity | 2.3 | 代码生成模板 |

## 模块结构

```text
task-manage-serve/
├── pom.xml                 # 父工程：版本、依赖和模块管理
├── ruoyi-admin/            # 启动入口、系统 Controller、应用配置
├── ruoyi-framework/        # 安全认证、框架配置、全局异常处理
├── ruoyi-system/           # 用户、角色、菜单等系统业务
├── ruoyi-task/             # 任务业务完整模块
│   ├── src/main/java/      # Controller、DTO、领域模型、Service、Mapper
│   ├── src/main/resources/mapper/task/TaskMapper.xml
│   ├── src/test/java/      # 业务单元测试与可选数据库测试
│   ├── sql/task_module.sql # 任务表和日志表初始化
│   └── docs/task-module.md # 任务接口与业务规则详细说明
├── ruoyi-common/           # 响应体、异常、通用模型、注解及工具
├── ruoyi-quartz/           # 定时调度任务
├── ruoyi-generator/        # 代码生成
└── sql/                    # 若依基础表与 Quartz 表初始化
```

任务模块包名为 `com.ruoyi.task`，依赖关系为 `ruoyi-admin → ruoyi-task → ruoyi-common`。任务模块不直接依赖 `ruoyi-system` 或 `ruoyi-framework`，通过通用登录上下文及 Mapper 查询复用用户信息。

## 快速开始

### 1. 准备环境

安装 JDK 17、Maven，准备可访问的 MySQL 8.0 和 Redis 服务。以下构建命令均在仓库根目录执行。

```sh
java -version
mvn -version
```

### 2. 初始化数据库

在目标数据库中按顺序执行：

| 顺序 | SQL 文件 | 说明 |
| --- | --- | --- |
| 1 | [sql/ry_20260417.sql](sql/ry_20260417.sql) | 若依系统表与基础数据 |
| 2 | [sql/quartz.sql](sql/quartz.sql) | Quartz 调度表 |
| 3 | [ruoyi-task/sql/task_module.sql](ruoyi-task/sql/task_module.sql) | `task` 与 `task_log` 表 |

基础脚本包含 `DROP TABLE`，仅在新库初始化或确认允许重建表时执行；已有若依数据库应保留原有数据，按缺失情况补充任务表或调度表。

任务脚本使用 `CREATE TABLE IF NOT EXISTS`，可重复执行，但不负责升级已有同名表。任务表引用现有 `sys_user`，应先完成系统表初始化。

### 3. 配置服务

| 配置文件 | 关键配置 |
| --- | --- |
| [application.yml](ruoyi-admin/src/main/resources/application.yml) | 服务端口、Redis、Token、上传路径、MyBatis、接口文档 |
| [application-druid.yml](ruoyi-admin/src/main/resources/application-druid.yml) | MySQL URL、用户名、密码、连接池与 Druid 监控 |

启动前检查数据库连接配置及 Redis 连接配置是否对应目标环境。项目当前激活 `druid` profile。

常用配置项：

| 配置项 | 当前配置 / 含义 |
| --- | --- |
| `server.port` | `8080` |
| `server.servlet.context-path` | `/` |
| `spring.profiles.active` | `druid` |
| `spring.data.redis.host` / `port` | `localhost` / `6379` |
| `spring.data.redis.database` | `0` |
| `token.header` | `Authorization` |
| `token.expireTime` | `60` 分钟，登录缓存有效期 |
| `ruoyi.profile` | `./uploadPath`，确保运行用户可写 |
| `spring.servlet.multipart.max-file-size` | 单个文件 `10MB` |
| `spring.servlet.multipart.max-request-size` | 单次请求 `20MB` |
| `spring.jackson.time-zone` | `GMT+8` |
| `spring.jackson.date-format` | `yyyy-MM-dd HH:mm:ss` |

数据库密码、Redis 密码和 `token.secret` 应按环境管理；正式环境需设置独立的 Token 密钥。本文档不记录真实连接凭据。

### 4. 构建与启动

```sh
# 构建启动模块及其依赖，同时运行测试。
mvn -pl ruoyi-admin -am clean package

# 从仓库根目录启动，默认监听 8080。
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

也可在 IDE 中运行 `com.ruoyi.RuoYiApplication`。完整启动需要数据库和 Redis 可用。

默认服务地址为 `http://localhost:8080`。

## 认证与权限

项目采用 Spring Security + JWT + Redis 登录状态管理，安全配置使用无 Session 模式。JWT 用于识别登录上下文，登录用户及权限信息由 Redis 缓存管理。

1. 通过 `GET /captchaImage` 获取验证码信息，按当前验证码开关完成登录校验。
2. 通过 `POST /login` 登录，请求字段为 `username`、`password`、`code`、`uuid`。
3. 登录成功后，令牌位于响应顶层 `token` 字段。
4. 后续受保护请求使用 `Authorization` 请求头，值为 `Bearer ` 加登录返回的令牌。
5. 使用 `/logout` 退出登录。

登录接口返回结构示意：

```json
{
  "code": 200,
  "msg": "操作成功",
  "token": "示例令牌"
}
```

除登录、注册、验证码、部分静态资源及安全配置中允许匿名访问的地址外，其余请求需要认证。系统管理接口通过方法权限控制；任务接口不额外依赖菜单权限，记录级权限由任务 Service 校验。

## 统一响应体

### 普通响应：AjaxResult

实现位于 [AjaxResult.java](ruoyi-common/src/main/java/com/ruoyi/common/core/domain/AjaxResult.java)，继承 `HashMap<String, Object>`，统一字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | 整数 | 业务状态码 |
| `msg` | 字符串 | 操作结果或错误消息 |
| `data` | 任意 JSON 类型 | 可选业务数据；通过标准构造方法传入 `null` 时不输出该字段 |

成功响应示例（创建任务后返回任务 ID）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": 123
}
```

无数据的成功响应：

```json
{
  "code": 200,
  "msg": "操作成功"
}
```

业务失败响应：

```json
{
  "code": 403,
  "msg": "没有操作该任务的权限"
}
```

常用工厂方法为 `AjaxResult.success()`、`success(Object data)`、`success(String msg, Object data)`、`warn(String msg)`、`error(String msg)`、`error(int code, String msg)`。

注意：`success(String)` 的字符串代表提示消息；返回字符串数据时使用 `success("操作成功", value)`。响应允许通过 `put` 扩展顶层字段，例如登录接口的 `token`，不能假定所有接口的数据都在 `data` 中。

### 分页响应：TableDataInfo

实现位于 [TableDataInfo.java](ruoyi-common/src/main/java/com/ruoyi/common/core/page/TableDataInfo.java)，由 `BaseController.getDataTable(...)` 设置查询结果：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [],
  "total": 0
}
```

`rows` 为当前页数据，`total` 为符合条件的总记录数，均位于顶层。分页响应不使用 `data` 包裹，也不包含 `pageNum/pageSize` 字段。

### 业务状态码与 HTTP 状态码

常量定义位于 [HttpStatus.java](ruoyi-common/src/main/java/com/ruoyi/common/constant/HttpStatus.java)。

| `code` | 含义 | 常见场景 |
| --- | --- | --- |
| `200` | 成功 | 查询、创建或变更成功 |
| `401` | 未认证 | 未登录或登录状态失效 |
| `403` | 无权限 | 无任务访问权限或方法权限不足 |
| `404` | 不存在 | 任务不存在或已删除 |
| `500` | 默认失败 | 参数校验失败、业务规则不满足或系统异常 |
| `601` | 警告 | `AjaxResult.warn(...)` |

响应体 `code` 是业务码，不等同于 HTTP 状态码。当前异常处理器返回 `AjaxResult`，不会同步设置对应 HTTP 状态；认证失败处理器通过 `ServletUtils.renderString` 输出 JSON。客户端需要同时处理 HTTP 传输错误和响应体 `code`，不能仅凭 HTTP 200 判断业务成功。

统一响应由各 Controller 显式使用 `AjaxResult` 或 `TableDataInfo` 实现，不是自动包装任意返回值；文件下载等接口按各自协议返回。

## 全局异常处理

### 通用异常处理

[GlobalExceptionHandler.java](ruoyi-framework/src/main/java/com/ruoyi/framework/web/exception/GlobalExceptionHandler.java) 使用 `@RestControllerAdvice` 和 `@ExceptionHandler` 统一处理 Controller 调用链中抛出的异常，并返回 `AjaxResult`。

| 异常 | 当前处理行为 |
| --- | --- |
| `AccessDeniedException` | `code=403`，提示“没有权限，请联系管理员授权” |
| `ServiceException` | 保留显式业务码；未指定时使用 `500`，消息为异常的 `message` |
| `HttpRequestMethodNotSupportedException` | `code=500`，返回异常消息 |
| `MissingPathVariableException` | `code=500`，提示缺失的路径变量 |
| `MethodArgumentTypeMismatchException` | `code=500`，提示参数名、要求类型和输入值 |
| `BindException` | `code=500`，返回首条校验错误消息 |
| `MethodArgumentNotValidException` | `code=500`，返回首个字段校验错误消息 |
| `DemoModeException` | `code=500`，提示“演示模式，不允许操作” |
| `RuntimeException` / `Exception` | 记录异常日志，`code=500`，返回异常的 `message` |

业务层使用 `ServiceException` 表达可预期的规则失败。构造方式为 `new ServiceException(message)` 或 `new ServiceException(message, code)`，由全局处理器生成响应，无需在每个 Controller 中重复捕获。

当前通用兜底处理直接返回 `e.getMessage()`，未统一替换为固定的系统错误提示，也未保证隐藏所有内部异常细节。文档中的行为以现有实现为准。

### 任务专用异常处理

[TaskExceptionHandler.java](ruoyi-task/src/main/java/com/ruoyi/task/controller/TaskExceptionHandler.java) 仅作用于 `TaskController`，以最高优先级处理 `HttpMessageNotReadableException`，将非法枚举、日期格式错误或其他 JSON 解析失败转换为：

```json
{
  "code": 500,
  "msg": "请求格式错误，请检查优先级、状态和日期格式（yyyy-MM-dd HH:mm:ss）"
}
```

其他任务业务异常仍由通用处理器处理。任务不存在使用 `404`，记录级权限不足使用 `403`，规则或参数失败默认使用 `500`。

### 认证失败处理

安全过滤器链中的未认证请求由 [AuthenticationEntryPointImpl.java](ruoyi-framework/src/main/java/com/ruoyi/framework/security/handle/AuthenticationEntryPointImpl.java) 处理，输出 `code=401` 的 `AjaxResult`。该处理属于 Spring Security 认证入口，不由 Controller 的全局异常处理器负责。

## 任务接口

基础路径：`/task/tasks`。所有接口要求登录。

| 方法 | 路径 | 功能 | 响应类型 |
| --- | --- | --- | --- |
| GET | `/task/tasks/list` | 分页列表、搜索、筛选、排序 | `TableDataInfo` |
| GET | `/task/tasks/stats` | 状态、优先级及个人完成率统计 | `AjaxResult` |
| GET | `/task/tasks/assignees` | 可选负责人 | `AjaxResult` |
| GET | `/task/tasks/{id}` | 任务详情及操作历史 | `AjaxResult` |
| POST | `/task/tasks` | 创建任务，返回新任务 ID | `AjaxResult` |
| PUT | `/task/tasks/{id}` | 编辑任务信息 | `AjaxResult` |
| PUT | `/task/tasks/{id}/assignee` | 更换负责人 | `AjaxResult` |
| PUT | `/task/tasks/{id}/status` | 推进状态 | `AjaxResult` |
| DELETE | `/task/tasks/{id}` | 逻辑删除 | `AjaxResult` |

核心约定：

- 状态仅允许 `TODO → IN_PROGRESS → DONE`，不允许跳级、退回或重开。
- 优先级为 `LOW`、`MEDIUM`、`HIGH`。
- 管理员可访问全部任务；普通用户只能访问自己创建或当前负责的任务。
- 管理员或创建人可以编辑、指派和删除；管理员或当前负责人可以推进状态。
- 已完成任务不能编辑或重新指派，删除仍按权限规则执行。
- 列表默认 `pageNum=1`、`pageSize=10`，每页最多 `100` 条。
- 任务日期使用北京时间，格式为 `yyyy-MM-dd HH:mm:ss`；统计日期筛选使用 `yyyy-MM-dd`。
- 任务删除为逻辑删除，删除后不参与列表和统计。

完整请求字段、排序范围、统计口径、操作日志与权限细节见 [任务模块文档](ruoyi-task/docs/task-module.md)。

## 开发与验证

```sh
# 运行启动模块及依赖模块的测试。
mvn -pl ruoyi-admin -am test

# 仅运行任务模块及其依赖的测试。
mvn -pl ruoyi-task -am test
```

`TaskServiceTest` 使用模拟依赖验证业务权限、状态流转、重复请求、逻辑删除、查询条件和参数边界等规则。

`TaskDatabaseTest` 为可选真实 MySQL 测试，默认跳过。设置 `TASK_TEST_JDBC_URL`、`TASK_TEST_DB_USER`、`TASK_TEST_DB_PASSWORD` 后启用；目标测试库须包含任务表及正常启用的用户 `1` 和 `100`。测试验证 Mapper、外键、操作快照及事务回滚，测试记录通过事务回滚清理，自增序列可能产生空号。

新增业务时优先复用 `ruoyi-common` 中的响应体、业务异常和通用工具；Controller 负责请求绑定与响应，Service 负责业务规则、记录级权限和事务，Mapper / XML 负责数据库访问。

## 相关文档

Swagger UI 地址为 `http://localhost:8080/swagger-ui.html`，OpenAPI 地址为 `http://localhost:8080/v3/api-docs`。
