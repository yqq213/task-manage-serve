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

/** 独立请求类型，不接受创建人、状态及审计字段的批量绑定。 */
public final class TaskRequests
{
    private TaskRequests() { }

    public record Create(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过200字符") String title,
            @Size(max = 5000, message = "描述不能超过5000字符") String description,
            @NotNull(message = "优先级不能为空") TaskPriority priority,
            @NotNull(message = "负责人不能为空") @Positive(message = "负责人ID必须大于0") Long assigneeId,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8", lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE) Date dueDate)
    {
        public Create { title = title == null ? null : title.strip(); }
    }

    /** 编辑仅覆盖这四个字段；传null可清空描述或截止时间。 */
    public record Edit(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过200字符") String title,
            @Size(max = 5000, message = "描述不能超过5000字符") String description,
            @NotNull(message = "优先级不能为空") TaskPriority priority,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8", lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE) Date dueDate)
    {
        public Edit { title = title == null ? null : title.strip(); }
    }

    public record Assignee(@NotNull(message = "负责人不能为空") @Positive Long assigneeId) { }
    public record Status(@NotNull(message = "目标状态不能为空") TaskStatus status) { }

    /** 查询参数采用构造器绑定，用户可见范围由服务层设置，不能由请求覆盖。 */
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
