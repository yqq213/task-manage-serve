package com.ruoyi.task.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.task.domain.Task;
import com.ruoyi.task.domain.TaskAssignee;
import com.ruoyi.task.domain.TaskLog;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStats;

/**
 * 任务、操作历史及负责人候选的 MyBatis 持久化接口，对应 mapper/task/TaskMapper.xml。
 * <p>任务查询统一排除逻辑删除记录；可见范围由服务层传入，单条任务的权限由服务层校验。
 * 写操作须在事务中先锁定任务，再完成校验、更新和历史写入，避免并发覆盖。</p>
 */
public interface TaskMapper
{
    /**
     * 按状态和优先级分组统计未删除任务，同时统计每组中当前用户负责的数量。
     *
     * @param viewerId 可见范围限制：非 null 时仅查询该用户创建或负责的任务；null 表示不限制
     * @param userId 计算个人负责数量的用户 ID，与可见范围参数用途不同
     * @param startTime 创建时间下界（包含），北京时间 yyyy-MM-dd HH:mm:ss；null 表示无下界
     * @param endTime 创建时间上界（不包含），北京时间 yyyy-MM-dd HH:mm:ss；null 表示无上界
     * @return 实际存在的状态与优先级组合，缺失组合由服务层补齐为零
     */
    List<TaskStats.Aggregate> selectStats(@Param("viewerId") Long viewerId, @Param("userId") Long userId,
            @Param("startTime") String startTime, @Param("endTime") String endTime);
    /**
     * 查询任务列表，关联负责人及创建人的当前昵称，并按白名单字段排序。
     * <p>关键词使用 LOCATE 匹配标题或描述；分页由调用方的 PageHelper 上下文控制。</p>
     *
     * @param query 已校验的筛选和排序参数
     * @param viewerId 非 null 时限制为该用户创建或负责的任务，null 时不限制可见范围
     * @return 符合条件的未删除任务列表
     */
    List<Task> selectList(@Param("query") TaskRequests.Query query, @Param("viewerId") Long viewerId);

    /**
     * 按主键读取未删除任务；锁定模式仅读取任务表，避免关联用户查询扩大锁定范围。
     *
     * @param id 任务主键
     * @param lock true 时追加 FOR UPDATE，须在事务中调用；false 时同时查询用户昵称
     * @return 任务对象；不存在或已删除时返回 null，记录级权限由调用方校验
     */
    Task selectById(@Param("id") Long id, @Param("lock") boolean lock);

    /**
     * 查询任务操作历史，按创建时间、日志 ID 升序排列。
     * <p>操作人名称优先使用当前昵称或账号；状态及指派说明由快照转换为展示文案。
     * 本查询不校验任务是否删除及当前用户权限，调用方须先校验任务访问权限。</p>
     *
     * @param taskId 历史记录所属的任务主键
     * @return 操作历史列表，保留原始前后 JSON 快照
     */
    List<TaskLog> selectLogs(Long taskId);

    /**
     * 查询启用且未删除的系统用户，仅返回负责人候选所需的公开字段。
     *
     * @return 按用户 ID 升序排列的候选列表
     */
    List<TaskAssignee> selectAssignees();

    /**
     * 校验用户可作为负责人，并通过 FOR SHARE 获取共享锁。
     * <p>须在新建或指派事务中调用，保证事务结束前用户不会被并发停用或删除。</p>
     *
     * @param userId 待校验的系统用户 ID
     * @return 有效用户 ID；用户不存在、已停用或已删除时返回 null
     */
    Long selectActiveAssignee(Long userId);

    /**
     * 插入任务，使用数据库自增主键并回填至任务对象。
     *
     * @param task 已由服务层设置初始状态、创建人及审计字段的任务
     * @return 影响行数，服务层要求为 1
     */
    int insertTask(Task task);

    /**
     * 更新未删除任务的业务字段及更新/删除审计信息，不修改创建人和创建时间。
     * <p>直接写入所有列，null 也会覆盖原值；可用于编辑、指派、状态推进及逻辑删除。</p>
     *
     * @param task 在事务中锁定读取后修改的完整任务对象
     * @return 影响行数，不存在或已删除时为 0
     */
    int updateTask(Task task);

    /**
     * 插入操作历史，与对应任务写入处于同一事务，失败时由服务层触发回滚。
     *
     * @param log 包含操作人、动作编码、前后快照、说明及时间的历史记录
     * @return 影响行数，服务层要求为 1；不回填日志主键
     */
    int insertLog(TaskLog log);
}
