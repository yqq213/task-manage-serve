package com.ruoyi.task.domain;

/** 单向任务状态；已完成为终态。 */
public enum TaskStatus
{
    TODO, IN_PROGRESS, DONE;

    public boolean canAdvanceTo(TaskStatus target)
    {
        return this == TODO && target == IN_PROGRESS || this == IN_PROGRESS && target == DONE;
    }
}
