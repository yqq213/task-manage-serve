package com.ruoyi.task.domain;

import java.math.BigDecimal;
import java.util.List;

/** 看板统计，百分比范围0～100，固定返回所有状态和优先级。 */
public record TaskStats(String scope, long total, List<Distribution> statusDistribution,
        List<Distribution> priorityDistribution, long personalTotal, long personalCompleted,
        BigDecimal personalCompletionRate)
{
    public record Distribution(String code, String name, long count, BigDecimal percentage) { }

    /** SQL按状态和优先级分组，同一查询计算个人负责数量，保证指标读取一致。 */
    public record Aggregate(TaskStatus status, TaskPriority priority, long count, long personalCount) { }
}
