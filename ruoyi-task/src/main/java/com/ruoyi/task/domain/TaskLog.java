package com.ruoyi.task.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * task_log 表的操作历史对象，与任务变更在同一事务中写入。
 * <p>前后业务快照以 JSON 字符串返回，包含 null 字段，便于展示字段清空及变更差异。
 * 操作人名称和部分说明在查询时转换为展示信息，数据库内的原始审计记录保持不变。</p>
 */
public class TaskLog
{
    /** 日志自增主键，由数据库生成；当前插入映射不回填该值。 */
    private Long id;
    /** 所属任务 ID，任务逻辑删除后仍保留对应历史。 */
    private Long taskId;
    /** 执行本次操作的登录用户 ID。 */
    private Long operatorId;
    /** 写入时保存操作人账号快照；查询优先显示当前昵称，其次账号，最后回退已保存名称。 */
    private String operatorName;
    /** 动作编码：CREATE 创建、UPDATE 编辑、ASSIGN 指派、STATUS 状态变更、DELETE 删除。 */
    private String action;
    /** 变更前的业务 JSON 快照；创建动作没有前态，该值为 null。 */
    private String beforeData;
    /** 变更后的业务 JSON 快照，记录业务字段及完成/删除信息，不包含关联昵称。 */
    private String afterData;
    /** 操作说明；查询时状态转为中文，负责人优先显示当前昵称或账号，转换失败回退原说明。 */
    private String remark;
    /** 操作时间，写入时与任务本次更新时间一致，按北京时间 yyyy-MM-dd HH:mm:ss 返回。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getBeforeData() { return beforeData; }
    public void setBeforeData(String beforeData) { this.beforeData = beforeData; }
    public String getAfterData() { return afterData; }
    public void setAfterData(String afterData) { this.afterData = afterData; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
