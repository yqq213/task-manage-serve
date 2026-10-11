package com.ruoyi.task;

import java.util.List;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.task.domain.Task;
import com.ruoyi.task.domain.TaskPriority;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStatus;
import com.ruoyi.task.domain.TaskStats;
import java.math.BigDecimal;
import com.ruoyi.task.mapper.TaskMapper;
import com.ruoyi.task.service.impl.TaskServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖创建人/负责人权限分离、状态限制和不可伪造的审计字段。 */
class TaskServiceTest
{
    private TaskMapper mapper;
    private TaskServiceImpl service;
    private Task task;

    @BeforeEach
    void setup()
    {
        mapper = mock(TaskMapper.class);
        service = new TaskServiceImpl(mapper);
        task = new Task();
        task.setId(10L);
        task.setTitle("测试任务");
        task.setDescription("原描述");
        task.setCreatorId(100L);
        task.setAssigneeId(200L);
        task.setPriority(TaskPriority.MEDIUM);
        task.setStatus(TaskStatus.TODO);
        task.setDelFlag("0");
        when(mapper.selectById(eq(10L), anyBoolean())).thenReturn(task);
        when(mapper.updateTask(any())).thenReturn(1);
        when(mapper.insertLog(any())).thenReturn(1);
    }

    static void login(long userId, String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setStatus("0");
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("test-" + userId);
        user.setRoles(List.of(role));
        LoginUser principal = new LoginUser(userId, 1L, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void statsScopeRatesAndDateBounds()
    {
        login(100, "common");
        when(mapper.selectStats(any(), any(), any(), any())).thenReturn(List.of(
                new TaskStats.Aggregate(TaskStatus.TODO, TaskPriority.LOW, 2, 1),
                new TaskStats.Aggregate(TaskStatus.DONE, TaskPriority.HIGH, 1, 1)));
        var stats = service.selectStats("2026-10-01", "2026-10-11");
        verify(mapper).selectStats(100L, 100L, "2026-10-01 00:00:00", "2026-10-12 00:00:00");
        assertEquals("VISIBLE", stats.scope());
        assertEquals(3, stats.total());
        assertEquals(2, stats.personalTotal());
        assertEquals(new BigDecimal("50.00"), stats.personalCompletionRate());
        assertEquals(new BigDecimal("66.67"), stats.statusDistribution().get(0).percentage());
        assertEquals(0, stats.statusDistribution().get(1).count());
        assertEquals("已完成", stats.statusDistribution().get(2).name());
        login(300, "admin");
        service.selectStats(null, null);
        verify(mapper).selectStats(null, 300L, null, null);
    }

    @Test
    void emptyStatsAndInvalidDates()
    {
        login(100, "common");
        when(mapper.selectStats(any(), any(), any(), any())).thenReturn(List.of());
        var stats = service.selectStats(null, null);
        assertEquals(0, stats.total());
        assertEquals(new BigDecimal("0.00"), stats.personalCompletionRate());
        assertEquals(3, stats.statusDistribution().size());
        assertEquals(3, stats.priorityDistribution().size());
        for (String date : List.of("2026-02-30", "2026-2-01", "", "0999-01-01"))
        {
            assertThrows(ServiceException.class, () -> service.selectStats(date, null));
        }
        assertEquals("开始日期不能晚于结束日期", assertThrows(ServiceException.class,
                () -> service.selectStats("2026-10-12", "2026-10-11")).getMessage());
        service.selectStats(null, "2026-02-28");
        verify(mapper).selectStats(100L, 100L, null, "2026-03-01 00:00:00");
        service.selectStats("2026-10-01", null);
        verify(mapper).selectStats(100L, 100L, "2026-10-01 00:00:00", null);
    }

    @Test
    void outsidersCannotReadOrMutateTasks()
    {
        login(300, "common");
        assertEquals(403, assertThrows(ServiceException.class, () -> service.selectDetail(10L)).getCode());
        assertThrows(ServiceException.class, () -> service.delete(10L));
        assertThrows(ServiceException.class, () -> service.changeStatus(10L, new TaskRequests.Status(TaskStatus.IN_PROGRESS)));
        verify(mapper, never()).selectLogs(any());
        verify(mapper, never()).updateTask(any());
        verify(mapper, never()).insertLog(any());
    }

    @Test
    void creatorCanEditButCannotAdvanceSomeoneElsesTask()
    {
        login(100, "common");
        assertThrows(ServiceException.class, () -> service.changeStatus(10L, new TaskRequests.Status(TaskStatus.IN_PROGRESS)));
        service.edit(10L, new TaskRequests.Edit("  新标题  ", null, TaskPriority.HIGH, null));
        ArgumentCaptor<com.ruoyi.task.domain.TaskLog> history = ArgumentCaptor.forClass(com.ruoyi.task.domain.TaskLog.class);
        verify(mapper).insertLog(history.capture());
        assertEquals("新标题", task.getTitle());
        assertEquals(200L, task.getAssigneeId());
        assertEquals(TaskStatus.TODO, task.getStatus());
        assertEquals("原描述", JSON.parseObject(history.getValue().getBeforeData()).getString("description"));
        assertTrue(JSON.parseObject(history.getValue().getAfterData()).containsKey("description"));
        assertNull(JSON.parseObject(history.getValue().getAfterData()).getString("description"));
    }

    @Test
    void assigneeCanAdvanceButCannotManageTask()
    {
        login(200, "common");
        assertThrows(ServiceException.class, () -> service.edit(10L, new TaskRequests.Edit("标题", null, TaskPriority.LOW, null)));
        assertThrows(ServiceException.class, () -> service.assign(10L, new TaskRequests.Assignee(300L)));
        assertThrows(ServiceException.class, () -> service.delete(10L));
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.IN_PROGRESS));
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertNull(task.getCompletedTime());
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.DONE));
        assertNotNull(task.getCompletedTime());
        verify(mapper, times(2)).insertLog(any());
    }

    @Test
    void adminRoleCannotSkipOrReverseStatesAndRetriesDoNotWriteLogs()
    {
        login(300, "admin");
        assertThrows(ServiceException.class, () -> service.changeStatus(10L, new TaskRequests.Status(TaskStatus.DONE)));
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.TODO));
        verify(mapper, never()).insertLog(any());
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.IN_PROGRESS));
        assertThrows(ServiceException.class, () -> service.changeStatus(10L, new TaskRequests.Status(TaskStatus.TODO)));
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.DONE));
        service.changeStatus(10L, new TaskRequests.Status(TaskStatus.DONE));
        assertThrows(ServiceException.class, () -> service.changeStatus(10L, new TaskRequests.Status(TaskStatus.IN_PROGRESS)));
        verify(mapper, times(2)).updateTask(any());
        verify(mapper, times(2)).insertLog(any());
    }

    @Test
    void completedTasksRejectEditsAndAssignmentsForCreatorAndAdmins()
    {
        task.setStatus(TaskStatus.DONE);
        for (long userId : new long[] {100, 1, 300})
        {
            login(userId, userId == 300 ? "admin" : "common");
            assertEquals("已完成的任务不能编辑或指派", assertThrows(ServiceException.class, () ->
                    service.edit(10L, new TaskRequests.Edit("修改标题", null, TaskPriority.LOW, null))).getMessage());
            assertEquals("已完成的任务不能编辑或指派", assertThrows(ServiceException.class, () ->
                    service.assign(10L, new TaskRequests.Assignee(100L))).getMessage());
        }
        assertEquals("测试任务", task.getTitle());
        assertEquals(200L, task.getAssigneeId());
        verify(mapper, times(6)).selectById(10L, true);
        verify(mapper, never()).selectActiveAssignee(any());
        verify(mapper, never()).updateTask(any());
        verify(mapper, never()).insertLog(any());
    }

    @Test
    void inactiveAssigneeIsRejectedAndCreationUsesLoggedInIdentity()
    {
        login(100, "common");
        TaskRequests.Create request = new TaskRequests.Create("新任务", null, TaskPriority.LOW, 200L, null);
        when(mapper.selectActiveAssignee(200L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.create(request));
        verify(mapper, never()).insertTask(any());
        when(mapper.selectActiveAssignee(200L)).thenReturn(200L);
        when(mapper.insertTask(any())).thenAnswer(invocation -> {
            Task created = invocation.getArgument(0);
            created.setId(20L);
            return 1;
        });
        assertEquals(20L, service.create(request));
        ArgumentCaptor<Task> created = ArgumentCaptor.forClass(Task.class);
        verify(mapper).insertTask(created.capture());
        assertEquals(100L, created.getValue().getCreatorId());
        assertEquals("test-100", created.getValue().getCreateBy());
        assertEquals(TaskStatus.TODO, created.getValue().getStatus());
    }

    @Test
    void deletionRetainsHistoryAndDeletedTasksAreUnavailable()
    {
        login(100, "common");
        service.delete(10L);
        assertEquals("2", task.getDelFlag());
        assertEquals(100L, task.getDeletedBy());
        assertNotNull(task.getDeletedTime());
        verify(mapper).insertLog(argThat(history -> "DELETE".equals(history.getAction())));
        when(mapper.selectById(eq(10L), anyBoolean())).thenReturn(null);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.selectDetail(10L)).getCode());
    }

    @Test
    void listVisibilityCannotBeOverriddenByAssigneeFilter()
    {
        login(100, "common");
        TaskRequests.Query query = new TaskRequests.Query(null, null, null, null, null, 300L, null, null);
        service.selectList(query);
        verify(mapper).selectList(query, 100L);
        login(300, "admin");
        service.selectList(query);
        verify(mapper).selectList(query, null);
    }

    @Test
    void validationRejectsWhitespaceTitlesOversizedDescriptionsAndUnsafeSorting()
    {
        try (var factory = Validation.buildDefaultValidatorFactory())
        {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new TaskRequests.Create("  \u3000 ", null, TaskPriority.LOW, 1L, null)).isEmpty());
            assertFalse(validator.validate(new TaskRequests.Edit("标题", "a".repeat(5001), TaskPriority.LOW, null)).isEmpty());
            assertFalse(validator.validate(new TaskRequests.Query(0, 101, null, null, null, null, "id desc; drop table task", "desc")).isEmpty());
            assertTrue(validator.validate(new TaskRequests.Create("正常任务", null, TaskPriority.HIGH, 1L, null)).isEmpty());
        }
    }
}
