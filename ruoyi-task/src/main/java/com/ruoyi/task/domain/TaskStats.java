package com.ruoyi.task.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 任务看板统计响应，所有指标均基于相同的可见范围、创建日期条件和未删除任务。
 * <p>状态及优先级分布固定返回全部枚举项，缺失项数量为零。
 * 百分比取值 0～100，保留两位小数并四舍五入，分母为零时返回 0.00。</p>
 *
 * @param scope 统计范围：ALL 表示管理员全部任务，VISIBLE 表示普通用户创建或负责的任务
 * @param total 当前统计范围内的任务总数
 * @param statusDistribution 按 TODO、IN_PROGRESS、DONE 顺序返回的状态分布
 * @param priorityDistribution 按 LOW、MEDIUM、HIGH 顺序返回的优先级分布
 * @param personalTotal 当前统计范围内由登录用户负责的任务总数
 * @param personalCompleted 本人负责且状态为 DONE 的任务数
 * @param personalCompletionRate personalCompleted / personalTotal × 100，表示本人负责任务的完成率
 */
public record TaskStats(String scope, long total, List<Distribution> statusDistribution,
        List<Distribution> priorityDistribution, long personalTotal, long personalCompleted,
        BigDecimal personalCompletionRate)
{
    /**
     * 单个状态或优先级的数量及占比。
     *
     * @param code 枚举编码，与数据库及接口枚举值一致
     * @param name 中文展示名称
     * @param count 对应分类的任务数量
     * @param percentage count / total × 100，分母为看板任务总数
     */
    public record Distribution(String code, String name, long count, BigDecimal percentage) { }

    /**
     * Mapper 内部聚合结果，同一 SQL 按状态和优先级分组并计算个人数量，保证指标读取一致。
     *
     * @param status 分组状态
     * @param priority 分组优先级
     * @param count 当前组合的任务总数
     * @param personalCount 当前组合中由登录用户负责的任务数量
     */
    public record Aggregate(TaskStatus status, TaskPriority priority, long count, long personalCount) { }
}
