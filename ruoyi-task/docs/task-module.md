# 任务管理与任务详情后端

本模块复用现有若依登录、用户、角色、MyBatis XML、PageHelper和全局异常处理。
提供任务管理、任务详情及统计看板后端，不包含前端、用户管理改造、附件、标签或子任务。

代码统一位于与`ruoyi-system`平级的`ruoyi-task` Maven模块，包名为`com.ruoyi.task`。
Controller、任务专用异常处理、领域对象、Service、Mapper XML及测试均归属该模块。
依赖关系为`ruoyi-admin → ruoyi-task → ruoyi-common`，仍由`ruoyi-admin`统一启动；任务模块不依赖`ruoyi-system`或`ruoyi-framework`。
SQL与本文档随模块放置，模块抽离不改变接口路径、业务规则或数据库结构。

## 初始化与启动

环境：JDK 17、Maven、MySQL 8.0、Redis。使用已有若依数据库与登录账号，不重复创建用户或角色。

1. 在目标若依数据库执行 `ruoyi-task/sql/task_module.sql`，新增 `task`、`task_log`。
2. 按已有 `application-druid.yml` 配置连接数据库，按 `application.yml` 配置Redis。
3. 在仓库根目录执行 `mvn -pl ruoyi-admin -am package`。
4. 执行 `java -jar ruoyi-admin/target/task-admin.jar`，默认端口8080。
5. 使用现有 `/login` 登录，后续请求携带 `Authorization: Bearer <登录返回的token>`。

SQL支持重复执行，不删除表或任务；`IF NOT EXISTS`不负责升级已有同名表。用户信息仍由原有用户管理模块维护。
数据库时间与接口业务时间采用北京时间，任务日期格式为 `yyyy-MM-dd HH:mm:ss`。

## 表与索引

`task`保存标题、描述、优先级、状态、负责人、创建人、截止/完成时间、创建/更新审计字段及逻辑删除信息。
`task_log`保存任务ID、操作人ID和账号快照、操作类型、变更前后JSON快照、说明及时间。

- 用户ID与现有`sys_user.user_id`同为BIGINT；负责人、创建人、删除人、日志操作人使用外键关联用户。
- 日志通过外键关联任务；不设置级联删除。现有用户删除接口采用逻辑删除，用户被停用/删除后仍保留任务和历史。
- 创建人、负责人、状态、优先级的联合索引服务于可见范围及筛选，创建时间索引支持默认排序。
- 日志使用`(task_id, create_time, id)`联合索引。
- 关键词使用标题/描述的字面包含匹配，`%`、`_`也是普通字符；不承诺由普通B-tree索引加速包含搜索。
- MySQL CHECK约束限制枚举、删除标记和完成时间。任务变更与日志写入处于同一事务；写操作先锁定任务，指派时共享锁定目标用户。

## 业务规则

|操作|管理员|创建人|当前负责人|
|---|---|---|---|
|查看详情/日志|全部|自己创建的|自己负责的|
|编辑信息、指派、删除|全部|可以|仅同时是创建人时|
|推进状态|全部|仅同时是负责人时|可以|

管理员指用户ID为1的超级管理员，或拥有正常启用`admin`角色的登录用户。列表查询强制限定普通用户为创建人或当前负责人，负责人筛选不会扩大此范围。

- 创建后状态固定`TODO`，流转仅允许`TODO → IN_PROGRESS → DONE`；管理员也不能跳级、退回或重开。
- 推进到`DONE`时记录完成时间。重复提交当前状态返回成功，不写重复日志或改变更新时间。
- 标题去除首尾空白后必填、最长200字符；描述选填、最长5000字符，按纯文本存储。
- 优先级必填：`LOW`低、`MEDIUM`中、`HIGH`高。
- 负责人必填，可选任何正常启用且未删除的用户，包括自己和管理员。新建/指派时再次验证目标用户状态。
- 截止时间可空、允许过去时间。已完成任务禁止编辑信息或重新指派，包括管理员；删除仍按原有权限规则执行。
- 指派后原负责人若不是创建人，即失去访问权限。既有负责人停用时保留任务，创建人/管理员可重新指派。
- 删除为逻辑删除；已删除任务从列表隐藏，其他入口返回“任务不存在或已删除”。不提供恢复或永久删除接口。
- 未发生业务字段变化的编辑/指派返回成功，不更新时间或新增日志。
- 写入请求DTO只包含允许修改的字段，客户端不能设置创建人、创建时间、状态、完成时间或删除标记。

## 接口清单

所有接口要求登录，不额外依赖尚未创建的前端菜单权限。记录级权限在服务层校验。

|方法|路径|说明|
|---|---|---|
|GET|`/task/tasks/list`|分页、搜索、筛选及排序|
|GET|`/task/tasks/stats`|状态分布、优先级占比及个人完成率，支持创建日期筛选|
|GET|`/task/tasks/assignees`|负责人候选，只返回`userId/userName/nickName`|
|GET|`/task/tasks/{id}`|详情和完整操作历史|
|POST|`/task/tasks`|创建，返回新任务ID|
|PUT|`/task/tasks/{id}`|编辑标题、描述、优先级、截止时间|
|PUT|`/task/tasks/{id}/assignee`|指派负责人|
|PUT|`/task/tasks/{id}/status`|推进状态|
|DELETE|`/task/tasks/{id}`|逻辑删除|

### 统计看板

请求示例：`GET /task/tasks/stats?startDate=2026-10-01&endDate=2026-10-11`。
两个日期均选填，格式严格为`yyyy-MM-dd`（MySQL支持年份1000～9999），按北京时间的任务创建时间筛选。
不传日期统计全部时间；只传开始或结束日期则分别限制下界或上界；同时传入时包含起止日期整天。
开始日期晚于结束日期返回“开始日期不能晚于结束日期”，不存在或格式错误的日期返回明确参数错误。
查询使用开始日零点包含、结束日次日零点不包含的条件，不对`create_time`进行函数转换。

管理员分布统计覆盖全部未删除任务（`scope=ALL`）；普通用户覆盖自己创建或当前负责的未删除任务（`scope=VISIBLE`），同一任务不会重复计数。
个人完成率对任何角色都按“本人当前负责的已完成任务数/本人当前负责的任务总数×100%”计算，使用相同创建日期筛选；创建后指派他人的任务不计入个人完成率。
所有指标反映查询时当前状态，不是所选日期结束时的历史状态；已删除任务不参与任何指标。
统计使用单条SQL按状态和优先级聚合，并同时计算个人负责数量，不读取全量任务进行应用层聚合，也不接受客户端传入用户ID扩大范围。

```json
{
  "code":200,
  "msg":"操作成功",
  "data":{
    "scope":"VISIBLE",
    "total":3,
    "statusDistribution":[
      {"code":"TODO","name":"待办","count":1,"percentage":33.33},
      {"code":"IN_PROGRESS","name":"进行中","count":1,"percentage":33.33},
      {"code":"DONE","name":"已完成","count":1,"percentage":33.33}
    ],
    "priorityDistribution":[
      {"code":"LOW","name":"低","count":1,"percentage":33.33},
      {"code":"MEDIUM","name":"中","count":1,"percentage":33.33},
      {"code":"HIGH","name":"高","count":1,"percentage":33.33}
    ],
    "personalTotal":2,
    "personalCompleted":1,
    "personalCompletionRate":50.00
  }
}
```

状态及优先级始终按示例顺序返回三项；空项数量和占比为0，个人负责数量为0时完成率为0。
`percentage`和`personalCompletionRate`均为0～100的数值，四舍五入保留两位小数，分布占比总和可能有舍入偏差。

### 列表查询

查询参数：

|参数|类型|规则|
|---|---|---|
|pageNum|整数|默认1，最小1|
|pageSize|整数|默认10，范围1～100|
|keyword|字符串|最长200字符，匹配标题或描述|
|status|字符串|可选`TODO/IN_PROGRESS/DONE`|
|priority|字符串|可选`LOW/MEDIUM/HIGH`|
|assigneeId|整数|可选，必须为正数|
|orderByColumn|字符串|`createTime/updateTime/dueDate/priority/status/title`，默认`createTime`|
|isAsc|字符串|`asc/desc`，默认`desc`|

多个条件组合生效。优先级/状态按上述业务顺序排序，同值时按ID倒序保证结果顺序确定；日期空值遵守MySQL排序规则。超出最后一页返回空数组。
查询条件不支持客户端指定创建人/可见范围，不接受任意SQL排序表达式。

请求示例：`GET /task/tasks/list?pageNum=1&pageSize=10&status=TODO&priority=HIGH&orderByColumn=createTime&isAsc=desc`

响应结构：

```json
{"code":200,"msg":"查询成功","rows":[],"total":0}
```

### 创建与编辑

创建请求示例（负责人ID 100对应当前库已有普通用户）：

```json
{
  "title":"整理项目验收资料",
  "description":"整理接口清单与运行说明",
  "priority":"HIGH",
  "assigneeId":100,
  "dueDate":"2026-10-20 18:00:00"
}
```

创建成功：`{"code":200,"msg":"操作成功","data":123}`，`data`为实际新任务ID。

编辑请求是四个业务字段的整体替换；标题、优先级必须提供，描述、截止时间传`null`或省略时清空。
更换负责人必须走独立的指派接口。

```json
{"title":"整理验收资料","description":null,"priority":"MEDIUM","dueDate":null}
```

指派请求：`{"assigneeId":100}`。状态请求：`{"status":"IN_PROGRESS"}`。
编辑/指派/状态/删除成功响应：`{"code":200,"msg":"操作成功"}`。

### 详情与历史

详情响应为 `{"code":200,"msg":"操作成功","data":{"task":{...},"logs":[...]}}`。
`task`包含主表业务字段、审计字段及当前负责人/创建人的昵称`assigneeName/creatorName`。
`logs`按操作时间、ID正序排列，每条包含：

```json
{
  "id":1,
  "taskId":123,
  "operatorId":1,
  "operatorName":"管理员",
  "action":"STATUS",
  "beforeData":"{\"status\":\"TODO\"}",
  "afterData":"{\"status\":\"IN_PROGRESS\"}",
  "remark":"状态由待办变更为进行中",
  "createTime":"2026-10-11 12:00:00"
}
```

示例缩略了快照，其实际内容包含完整业务字段、负责人ID、创建人ID、日期和删除信息；JSON文本显式保留空值。
日志类型为`CREATE/UPDATE/ASSIGN/STATUS/DELETE`；创建日志`beforeData`为空。
`operatorName`返回操作人的当前昵称，指派说明中的前后负责人也使用当前昵称；修改昵称后历史记录展示同步变化。
昵称为空时回退账号，用户不存在时操作人回退日志名称快照，负责人回退“用户ID”。
状态说明按`TODO/IN_PROGRESS/DONE`分别展示“待办/进行中/已完成”。已有日志同样生效，数据库原始说明和JSON快照不被改写；缺失快照时保留原始说明。
描述和标题是纯文本，后续前端应使用文本插值展示，不能直接以HTML渲染。

### 错误

沿用若依响应语义，业务错误看响应体`code/msg`，不能只判断HTTP 200：

- `401`：未登录/token失效，由现有安全框架处理。
- `403`：无该任务的查看或操作权限。
- `404`：任务不存在或已删除。
- `500`：参数校验或业务规则失败，如负责人停用、跳级流转、非法枚举/日期。

任务接口对JSON解析错误返回简洁消息，避免暴露框架解析细节；其他错误沿用项目全局异常处理。

## 验证

普通验证不会连接真实数据库：

```sh
mvn -pl ruoyi-admin -am test
```

也可以在根目录执行`mvn -pl ruoyi-task -am test`，仅构建任务模块及其依赖并运行任务测试。

`TaskServiceTest`验证权限分离、状态机、重复请求、审计身份、逻辑删除、筛选可见范围、参数边界。
`TaskDatabaseTest`默认跳过；设置`TASK_TEST_JDBC_URL`、`TASK_TEST_DB_USER`、`TASK_TEST_DB_PASSWORD`后启用，要求目标库已有任务表和正常用户1/100。
它验证真实Mapper、快照、外键、列表条件、历史记录及日志失败的事务回滚，测试记录随事务回滚，不保留任务数据；InnoDB自增序列可能出现空号。

注意：完整启动还需要Redis，且默认8080端口须可用。本模块不新增账号、不改变现有用户密码，也不自动启动或部署云端Java服务。
