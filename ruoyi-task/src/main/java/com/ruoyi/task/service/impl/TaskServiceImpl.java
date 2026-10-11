package com.ruoyi.task.service.impl;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.task.domain.Task;
import com.ruoyi.task.domain.TaskAssignee;
import com.ruoyi.task.domain.TaskDetail;
import com.ruoyi.task.domain.TaskLog;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStatus;
import com.ruoyi.task.domain.TaskPriority;
import com.ruoyi.task.domain.TaskStats;
import com.ruoyi.task.mapper.TaskMapper;
import com.ruoyi.task.service.ITaskService;

/** 任务业务边界：权限、单向状态机及业务数据/历史记录的事务一致性。 */
@Service
public class TaskServiceImpl implements ITaskService
{
    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private final TaskMapper mapper;

    public TaskServiceImpl(TaskMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public TaskStats selectStats(String startDate, String endDate)
    {
        LocalDate start = parseStatsDate(startDate);
        LocalDate end = parseStatsDate(endDate);
        if (start != null && end != null && start.isAfter(end))
        {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        Long userId = SecurityUtils.getUserId();
        boolean admin = isAdmin();
        // 使用无时区的北京时间文本绑定MySQL DATETIME，避免服务器JVM时区影响日期边界。
        // MySQL最大日期之后没有可存储的任务，因此9999-12-31等价于无上界。
        List<TaskStats.Aggregate> rows = mapper.selectStats(admin ? null : userId, userId,
                start == null ? null : start + " 00:00:00",
                end == null || end.equals(LocalDate.of(9999, 12, 31)) ? null : end.plusDays(1) + " 00:00:00");
        long total = rows.stream().mapToLong(TaskStats.Aggregate::count).sum();
        long personalTotal = rows.stream().mapToLong(TaskStats.Aggregate::personalCount).sum();
        long personalCompleted = rows.stream().filter(row -> row.status() == TaskStatus.DONE)
                .mapToLong(TaskStats.Aggregate::personalCount).sum();
        List<TaskStats.Distribution> statuses = Arrays.stream(TaskStatus.values()).map(status -> {
            long count = rows.stream().filter(row -> row.status() == status).mapToLong(TaskStats.Aggregate::count).sum();
            String name = switch (status) { case TODO -> "待办"; case IN_PROGRESS -> "进行中"; case DONE -> "已完成"; };
            return new TaskStats.Distribution(status.name(), name, count, percentage(count, total));
        }).toList();
        List<TaskStats.Distribution> priorities = Arrays.stream(TaskPriority.values()).map(priority -> {
            long count = rows.stream().filter(row -> row.priority() == priority).mapToLong(TaskStats.Aggregate::count).sum();
            String name = switch (priority) { case LOW -> "低"; case MEDIUM -> "中"; case HIGH -> "高"; };
            return new TaskStats.Distribution(priority.name(), name, count, percentage(count, total));
        }).toList();
        return new TaskStats(admin ? "ALL" : "VISIBLE", total, statuses, priorities,
                personalTotal, personalCompleted, percentage(personalCompleted, personalTotal));
    }

    private LocalDate parseStatsDate(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            {
                throw new DateTimeParseException("Invalid format", value, 0);
            }
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT));
            if (date.getYear() < 1000)
            {
                throw new DateTimeParseException("Outside MySQL range", value, 0);
            }
            return date;
        }
        catch (DateTimeParseException exception)
        {
            throw new ServiceException("日期必须是有效的yyyy-MM-dd格式，年份范围1000～9999");
        }
    }

    private BigDecimal percentage(long count, long total)
    {
        return total == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    @Override
    public List<Task> selectList(TaskRequests.Query query)
    {
        Long viewerId = isAdmin() ? null : SecurityUtils.getUserId();
        // 在权限判定后设置分页，避免其他查询消耗分页上下文；不读取未经白名单校验的orderBy。
        PageHelper.startPage(query.pageNum(), query.pageSize()).setReasonable(false);
        try
        {
            return mapper.selectList(query, viewerId);
        }
        finally
        {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetail selectDetail(Long id)
    {
        Task task = requireTask(id, false);
        requirePermission(isAdmin() || isCreator(task) || isAssignee(task));
        return new TaskDetail(task, mapper.selectLogs(id));
    }

    @Override
    public List<TaskAssignee> selectAssignees()
    {
        SecurityUtils.getUserId();
        return mapper.selectAssignees();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaskRequests.Create request)
    {
        requireAssignee(request.assigneeId());
        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setAssigneeId(request.assigneeId());
        task.setDueDate(request.dueDate());
        task.setStatus(TaskStatus.TODO);
        task.setCreatorId(SecurityUtils.getUserId());
        task.setCreateBy(SecurityUtils.getUsername());
        task.setCreateTime(new Date());
        task.setUpdateBy(task.getCreateBy());
        task.setUpdateTime(task.getCreateTime());
        task.setDelFlag("0");
        requireWritten(mapper.insertTask(task));
        appendLog(task, "CREATE", null, "创建任务");
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(Long id, TaskRequests.Edit request)
    {
        Task task = requireManagedTask(id);
        requireUncompleted(task);
        String before = snapshot(task);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        saveChange(task, "UPDATE", before, "修改任务信息");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, TaskRequests.Assignee request)
    {
        Task task = requireManagedTask(id);
        requireUncompleted(task);
        requireAssignee(request.assigneeId());
        String before = snapshot(task);
        Long previous = task.getAssigneeId();
        task.setAssigneeId(request.assigneeId());
        saveChange(task, "ASSIGN", before, "负责人由用户" + previous + "变更为用户" + request.assigneeId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, TaskRequests.Status request)
    {
        Task task = requireTask(id, true);
        requirePermission(isAdmin() || isAssignee(task));
        if (task.getStatus() == request.status())
        {
            return; // 重复提交是幂等操作，不更新时间或新增日志。
        }
        if (!task.getStatus().canAdvanceTo(request.status()))
        {
            throw new ServiceException("状态仅允许待办→进行中→已完成，不能跳级或退回");
        }
        String before = snapshot(task);
        TaskStatus previous = task.getStatus();
        task.setStatus(request.status());
        if (request.status() == TaskStatus.DONE)
        {
            task.setCompletedTime(new Date());
        }
        saveChange(task, "STATUS", before, "状态由" + previous + "变更为" + request.status());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id)
    {
        Task task = requireManagedTask(id);
        String before = snapshot(task);
        task.setDelFlag("2");
        task.setDeletedBy(SecurityUtils.getUserId());
        task.setDeletedTime(new Date());
        saveChange(task, "DELETE", before, "删除任务（保留历史记录）");
    }

    /** 在事务内锁定读取之后校验，已完成任务不允许编辑或指派，包括管理员。 */
    private void requireUncompleted(Task task)
    {
        if (task.getStatus() == TaskStatus.DONE)
        {
            throw new ServiceException("已完成的任务不能编辑或指派");
        }
    }

    private Task requireManagedTask(Long id)
    {
        Task task = requireTask(id, true);
        requirePermission(isAdmin() || isCreator(task));
        return task;
    }

    private Task requireTask(Long id, boolean lock)
    {
        if (id == null || id <= 0)
        {
            throw new ServiceException("任务ID必须大于0");
        }
        // 写操作使用锁定读取，权限及状态校验与更新之间不能被另一个事务改变。
        Task task = mapper.selectById(id, lock);
        if (task == null)
        {
            throw new ServiceException("任务不存在或已删除", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private void requireAssignee(Long id)
    {
        if (id == null || mapper.selectActiveAssignee(id) == null)
        {
            throw new ServiceException("负责人不存在、已停用或已删除");
        }
    }

    private boolean isAdmin()
    {
        List<SysRole> roles = SecurityUtils.getLoginUser().getUser().getRoles();
        return SecurityUtils.isAdmin() || roles != null && roles.stream()
                .anyMatch(role -> "admin".equals(role.getRoleKey()) && "0".equals(role.getStatus()));
    }

    private boolean isCreator(Task task)
    {
        return Objects.equals(task.getCreatorId(), SecurityUtils.getUserId());
    }

    private boolean isAssignee(Task task)
    {
        return Objects.equals(task.getAssigneeId(), SecurityUtils.getUserId());
    }

    private void requirePermission(boolean allowed)
    {
        if (!allowed)
        {
            throw new ServiceException("没有操作该任务的权限", HttpStatus.FORBIDDEN);
        }
    }

    private void requireWritten(int rows)
    {
        if (rows != 1)
        {
            throw new ServiceException("任务操作失败，请重试");
        }
    }

    private void saveChange(Task task, String action, String before, String remark)
    {
        if (before.equals(snapshot(task)))
        {
            return;
        }
        task.setUpdateBy(SecurityUtils.getUsername());
        task.setUpdateTime(new Date());
        requireWritten(mapper.updateTask(task));
        appendLog(task, action, before, remark);
    }

    private void appendLog(Task task, String action, String before, String remark)
    {
        TaskLog history = new TaskLog();
        history.setTaskId(task.getId());
        history.setOperatorId(SecurityUtils.getUserId());
        history.setOperatorName(SecurityUtils.getUsername());
        history.setAction(action);
        history.setBeforeData(before);
        history.setAfterData(snapshot(task));
        history.setRemark(remark);
        history.setCreateTime(task.getUpdateTime());
        requireWritten(mapper.insertLog(history));
        log.info("任务操作 taskId={}, action={}, operatorId={}", task.getId(), action, history.getOperatorId());
    }

    /** 按固定顺序保存完整业务快照，显式保留null，以便展示字段清空操作。 */
    private String snapshot(Task task)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", task.getTitle());
        data.put("description", task.getDescription());
        data.put("priority", task.getPriority());
        data.put("status", task.getStatus());
        data.put("assigneeId", task.getAssigneeId());
        data.put("creatorId", task.getCreatorId());
        data.put("dueDate", format(task.getDueDate()));
        data.put("completedTime", format(task.getCompletedTime()));
        data.put("delFlag", task.getDelFlag());
        data.put("deletedBy", task.getDeletedBy());
        data.put("deletedTime", format(task.getDeletedTime()));
        return JSON.toJSONString(data, JSONWriter.Feature.WriteMapNullValue);
    }

    private String format(Date value)
    {
        return value == null ? null : TIME.format(value.toInstant());
    }
}
