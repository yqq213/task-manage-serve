package com.ruoyi.task.domain;

/**
 * 单向任务状态机，枚举名称直接用作数据库及接口编码。
 * <p>仅允许 TODO → IN_PROGRESS → DONE，不允许跳级或回退；重复状态由服务层单独按幂等处理。</p>
 */
public enum TaskStatus
{
    /** 待办，新建任务的初始状态，只能推进至进行中。 */
    TODO,
    /** 进行中，只能推进至已完成。 */
    IN_PROGRESS,
    /** 已完成，终态；禁止继续推进、编辑任务信息或重新指派。 */
    DONE;

    /**
     * 判断是否为相邻的向前状态流转，不包含重复提交当前状态的幂等判断。
     *
     * @param target 目标状态，null、当前状态、跳级或回退均不允许
     * @return 仅待办到进行中、进行中到已完成返回 true
     */
    public boolean canAdvanceTo(TaskStatus target)
    {
        return this == TODO && target == IN_PROGRESS || this == IN_PROGRESS && target == DONE;
    }
}
