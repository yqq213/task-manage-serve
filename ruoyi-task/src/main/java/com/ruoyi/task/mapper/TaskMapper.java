package com.ruoyi.task.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.task.domain.Task;
import com.ruoyi.task.domain.TaskAssignee;
import com.ruoyi.task.domain.TaskLog;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStats;

/** 任务与日志持久化；修改前须在事务中读取并锁定任务。 */
public interface TaskMapper
{
    List<TaskStats.Aggregate> selectStats(@Param("viewerId") Long viewerId, @Param("userId") Long userId,
            @Param("startTime") String startTime, @Param("endTime") String endTime);
    List<Task> selectList(@Param("query") TaskRequests.Query query, @Param("viewerId") Long viewerId);
    Task selectById(@Param("id") Long id, @Param("lock") boolean lock);
    List<TaskLog> selectLogs(Long taskId);
    List<TaskAssignee> selectAssignees();
    /** 共享锁保证新建/指派事务内负责人不会被并发停用或删除。 */
    Long selectActiveAssignee(Long userId);
    int insertTask(Task task);
    int updateTask(Task task);
    int insertLog(TaskLog log);
}
