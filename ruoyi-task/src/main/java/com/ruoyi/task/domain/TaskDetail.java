package com.ruoyi.task.domain;

import java.util.List;

/** 同一读取事务中的任务和历史记录。 */
public record TaskDetail(Task task, List<TaskLog> logs) { }
