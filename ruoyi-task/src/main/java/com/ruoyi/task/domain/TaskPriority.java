package com.ruoyi.task.domain;

/**
 * 任务优先级，枚举名称直接用作数据库及接口编码。
 * <p>列表按优先级排序时使用 LOW、MEDIUM、HIGH 的业务顺序，看板按相同顺序展示。</p>
 */
public enum TaskPriority
{
    /** 低优先级，看板展示名称为“低”。 */
    LOW,
    /** 中优先级，看板展示名称为“中”。 */
    MEDIUM,
    /** 高优先级，看板展示名称为“高”。 */
    HIGH
}
