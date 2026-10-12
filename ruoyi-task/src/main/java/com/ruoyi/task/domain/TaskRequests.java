package com.ruoyi.task.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 任务接口请求 DTO 集合，以独立 record 明确每个接口可接收的字段。
 * <p>创建、编辑请求不提供状态、创建人及审计字段；负责人和状态通过各自专用接口修改。
 * 字段注解供 Controller 的 Bean Validation 校验，权限、状态机及用户有效性由服务层校验。</p>
 */
public final class TaskRequests
{
    /** 类型容器不需要实例化，仅通过其嵌套 record 表达请求。 */
    private TaskRequests() { }

    /**
     * 创建任务请求；初始状态、创建人与审计信息由服务层补充。
     *
     * @param title 必填标题，去除首尾空白后校验非空，最多 200 个字符
     * @param description 可选描述，最多 5000 个字符，null 表示未填写
     * @param priority 必填优先级，取值 LOW、MEDIUM、HIGH
     * @param assigneeId 必填负责人 ID，必须大于 0，服务层进一步校验用户有效性
     * @param dueDate 可选截止时间，严格按北京时间 yyyy-MM-dd HH:mm:ss 解析
     */
    public record Create(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过200字符") String title,
            @Size(max = 5000, message = "描述不能超过5000字符") String description,
            @NotNull(message = "优先级不能为空") TaskPriority priority,
            @NotNull(message = "负责人不能为空") @Positive(message = "负责人ID必须大于0") Long assigneeId,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8", lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE) Date dueDate)
    {
        /** 构造时去除标题首尾空白，保留 null 交由非空约束生成错误提示。 */
        public Create { title = title == null ? null : title.strip(); }
    }

    /**
     * 编辑任务请求，完整覆盖以下四个字段，不采用忽略 null 的局部更新语义。
     *
     * @param title 必填标题，去除首尾空白后校验非空，最多 200 个字符
     * @param description 可选描述，最多 5000 个字符；传 null 或省略时清空原描述
     * @param priority 必填优先级，不能通过本接口修改任务状态或负责人
     * @param dueDate 可选截止时间，严格按北京时间 yyyy-MM-dd HH:mm:ss 解析；null 或省略时清空
     */
    public record Edit(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过200字符") String title,
            @Size(max = 5000, message = "描述不能超过5000字符") String description,
            @NotNull(message = "优先级不能为空") TaskPriority priority,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8", lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE) Date dueDate)
    {
        /** 与创建请求保持相同的标题规范化规则，再由校验注解检查长度和非空。 */
        public Edit { title = title == null ? null : title.strip(); }
    }

    /**
     * 负责人指派请求，仅允许修改负责人。
     *
     * @param assigneeId 必填的新负责人 ID，必须大于 0，且对应启用、未删除的用户
     */
    public record Assignee(@NotNull(message = "负责人不能为空") @Positive Long assigneeId) { }

    /**
     * 状态变更请求，合法流转和操作权限由服务层判断。
     *
     * @param status 必填目标状态；仅允许推进到下一状态，重复提交当前状态不产生变更
     */
    public record Status(@NotNull(message = "目标状态不能为空") TaskStatus status) { }

    /**
     * 列表查询参数，采用构造器绑定并统一补齐分页与排序默认值。
     * <p>用户可见范围由服务层确定，不作为请求字段开放；筛选条件叠加生效。</p>
     *
     * @param pageNum 页码，最小为 1，缺省为 1；超出实际页数时不自动回退
     * @param pageSize 每页条数，范围 1～100，缺省为 10
     * @param keyword 标题或描述的子串关键词，最多 200 个字符；去除首尾空白，空值不筛选
     * @param status 可选任务状态，null 时不筛选
     * @param priority 可选优先级，null 时不筛选
     * @param assigneeId 可选负责人 ID，提供时须大于 0
     * @param orderByColumn 排序白名单：createTime、updateTime、dueDate、priority、status、title；缺省为 createTime
     * @param isAsc 排序方向，仅接受 asc 或 desc，缺省为 desc；相同排序值按任务 ID 降序排列
     */
    public record Query(
            @Min(1) Integer pageNum,
            @Min(1) @Max(value = 100, message = "每页最多100条") Integer pageSize,
            @Size(max = 200) String keyword,
            TaskStatus status,
            TaskPriority priority,
            @Positive Long assigneeId,
            @Pattern(regexp = "createTime|updateTime|dueDate|priority|status|title", message = "不支持该排序字段") String orderByColumn,
            @Pattern(regexp = "asc|desc", message = "排序方向必须是asc或desc") String isAsc)
    {
        /** 仅为空值补齐默认参数并规范化关键词，非法非空参数仍由校验约束拒绝。 */
        public Query
        {
            pageNum = pageNum == null ? 1 : pageNum;
            pageSize = pageSize == null ? 10 : pageSize;
            keyword = keyword == null ? null : keyword.strip();
            orderByColumn = orderByColumn == null ? "createTime" : orderByColumn;
            isAsc = isAsc == null ? "desc" : isAsc;
        }
    }
}
