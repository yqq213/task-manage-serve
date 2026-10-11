package com.ruoyi.task.service;

import java.util.List;
import com.ruoyi.task.domain.Task;
import com.ruoyi.task.domain.TaskAssignee;
import com.ruoyi.task.domain.TaskDetail;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStats;

/** 所有业务入口均校验当前登录人的任务权限。 */
public interface ITaskService
{
    TaskStats selectStats(String startDate, String endDate);
    List<Task> selectList(TaskRequests.Query query);
    TaskDetail selectDetail(Long id);
    List<TaskAssignee> selectAssignees();
    Long create(TaskRequests.Create request);
    void edit(Long id, TaskRequests.Edit request);
    void assign(Long id, TaskRequests.Assignee request);
    void changeStatus(Long id, TaskRequests.Status request);
    void delete(Long id);
}
