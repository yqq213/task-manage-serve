package com.ruoyi.task.domain;

import java.util.List;

/**
 * 任务详情响应，在同一只读事务中读取任务和历史，服务层完成访问权限校验后返回。
 *
 * @param task 未删除的任务及当前负责人、创建人昵称
 * @param logs 按操作时间、日志 ID 升序排列的历史记录；没有历史时为空列表
 */
public record TaskDetail(Task task, List<TaskLog> logs) { }
