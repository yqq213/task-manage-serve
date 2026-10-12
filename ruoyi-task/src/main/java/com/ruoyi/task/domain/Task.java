package com.ruoyi.task.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * task 表的持久化对象，同时用于任务列表及详情响应。
 * <p>继承 BaseEntity 的创建人账号、创建时间、更新人账号及更新时间等公共字段。
 * 用户昵称由查询关联取得，不写入 task 表；写入接口使用独立 DTO，禁止客户端绑定审计字段。
 * 本类声明的日期字段均按北京时间 yyyy-MM-dd HH:mm:ss 序列化。</p>
 */
public class Task extends BaseEntity
{
    /** Java 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 任务自增主键，插入时由数据库生成并回填。 */
    private Long id;
    /** 任务标题；写入请求必填、去除首尾空白，最多 200 个字符。 */
    private String title;
    /** 可选任务描述，最多 5000 个字符；编辑时可传 null 清空。 */
    private String description;
    /** 优先级编码：LOW（低）、MEDIUM（中）、HIGH（高）。 */
    private TaskPriority priority;
    /** 当前状态：TODO → IN_PROGRESS → DONE；新建为 TODO，DONE 为终态。 */
    private TaskStatus status;
    /** 当前负责人对应 sys_user.user_id；新建或指派时须为启用且未删除的用户。 */
    private Long assigneeId;
    /** 负责人当前昵称，查询时取得的展示字段；锁定读取时不加载。 */
    private String assigneeName;
    /** 创建人对应 sys_user.user_id，由创建时的登录用户确定，后续不可修改。 */
    private Long creatorId;
    /** 创建人当前昵称，查询时取得的展示字段；锁定读取时不加载。 */
    private String creatorName;
    /** 可选截止时间，null 表示未设置；编辑时可清空，不用于判断状态是否已完成。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dueDate;
    /** 实际完成时间，推进到 DONE 时由服务层设置，未完成时为 null。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date completedTime;
    /** 逻辑删除标记：0 表示正常，2 表示已删除；业务查询仅读取正常记录。 */
    private String delFlag;
    /** 执行逻辑删除的用户 ID，删除前为 null。 */
    private Long deletedBy;
    /** 逻辑删除时间，删除前为 null；删除任务时保留实体及操作历史。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deletedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public Date getCompletedTime() { return completedTime; }
    public void setCompletedTime(Date completedTime) { this.completedTime = completedTime; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long deletedBy) { this.deletedBy = deletedBy; }
    public Date getDeletedTime() { return deletedTime; }
    public void setDeletedTime(Date deletedTime) { this.deletedTime = deletedTime; }
}
