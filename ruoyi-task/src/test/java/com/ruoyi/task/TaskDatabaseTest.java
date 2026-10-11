package com.ruoyi.task;

import java.util.Date;
import com.github.pagehelper.PageInterceptor;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.task.domain.TaskPriority;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.domain.TaskStatus;
import com.ruoyi.task.mapper.TaskMapper;
import com.ruoyi.task.service.ITaskService;
import com.ruoyi.task.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 显式启用的真实MySQL验证：正常用例全部回滚，不保留测试任务或日志。
 * 需要已有两张任务表及启用的用户1/100；连接信息只从环境变量读取。
 */
@EnabledIfEnvironmentVariable(named = "TASK_TEST_JDBC_URL", matches = ".+")
class TaskDatabaseTest
{
    private TaskMapper mapper;
    private ITaskService service;
    private DataSourceTransactionManager manager;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception
    {
        MysqlDataSource source = new MysqlDataSource();
        source.setURL(System.getenv("TASK_TEST_JDBC_URL"));
        source.setUser(System.getenv("TASK_TEST_DB_USER"));
        source.setPassword(System.getenv("TASK_TEST_DB_PASSWORD"));
        manager = new DataSourceTransactionManager(source);
        jdbc = new JdbcTemplate(source);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/task/TaskMapper.xml"));
        factory.setPlugins(new PageInterceptor());
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(TaskMapper.class);
        service = transactionalService(mapper);
        TaskServiceTest.login(1, "admin");
    }

    private ITaskService transactionalService(TaskMapper taskMapper)
    {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(new TaskServiceImpl(taskMapper));
        proxy.addAdvice(interceptor);
        return (ITaskService) proxy.getProxy();
    }

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void realMapperPermissionsSnapshotsFilteringAndSoftDeletion()
    {
        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            String title = "task-module-test-" + System.nanoTime();
            Date deadline = new Date(1893456000000L);
            Long id = service.create(new TaskRequests.Create(title, "描述%_", TaskPriority.HIGH, 100L, deadline));
            var detail = service.selectDetail(id);
            assertEquals(deadline, detail.task().getDueDate());
            assertNotNull(detail.task().getAssigneeName());
            assertEquals("CREATE", detail.logs().get(0).getAction());
            String adminName = jdbc.queryForObject("select coalesce(nullif(trim(nick_name), ''), user_name) from sys_user where user_id = 1", String.class);
            String userName = jdbc.queryForObject("select coalesce(nullif(trim(nick_name), ''), user_name) from sys_user where user_id = 100", String.class);
            assertEquals(adminName, detail.logs().get(0).getOperatorName());
            assertEquals(1, service.selectList(new TaskRequests.Query(1, 10, title, TaskStatus.TODO, TaskPriority.HIGH, 100L, "priority", "desc")).size());
            assertTrue(service.selectAssignees().stream().anyMatch(user -> user.userId() == 100L));
            TaskServiceTest.login(999, "common");
            assertTrue(service.selectList(new TaskRequests.Query(1, 10, title, null, null, 100L, null, null)).isEmpty());
            assertThrows(ServiceException.class, () -> service.selectDetail(id));
            TaskServiceTest.login(100, "common");
            assertEquals(id, service.selectDetail(id).task().getId());
            service.changeStatus(id, new TaskRequests.Status(TaskStatus.IN_PROGRESS));
            TaskServiceTest.login(1, "admin");
            service.edit(id, new TaskRequests.Edit(title, null, TaskPriority.LOW, null));
            service.assign(id, new TaskRequests.Assignee(1L));
            assertEquals("负责人由" + userName + "变更为" + adminName, service.selectDetail(id).logs().get(3).getRemark());
            TaskServiceTest.login(100, "common");
            assertThrows(ServiceException.class, () -> service.selectDetail(id));
            TaskServiceTest.login(1, "admin");
            service.changeStatus(id, new TaskRequests.Status(TaskStatus.DONE));
            service.changeStatus(id, new TaskRequests.Status(TaskStatus.DONE));
            assertEquals(5, service.selectDetail(id).logs().size());
            var history = service.selectDetail(id).logs();
            assertEquals(userName, history.get(1).getOperatorName());
            assertEquals("状态由待办变更为进行中", history.get(1).getRemark());
            assertEquals("状态由进行中变更为已完成", history.get(4).getRemark());
            assertEquals("状态由TODO变更为IN_PROGRESS", jdbc.queryForObject(
                    "select remark from task_log where task_id = ? and action = 'STATUS' order by id limit 1", String.class, id));
            assertNotNull(service.selectDetail(id).task().getCompletedTime());
            TaskServiceTest.login(1, "admin");
            assertThrows(ServiceException.class, () -> service.edit(id, new TaskRequests.Edit("禁止修改", null, TaskPriority.HIGH, null)));
            assertThrows(ServiceException.class, () -> service.assign(id, new TaskRequests.Assignee(100L)));
            assertEquals(title, service.selectDetail(id).task().getTitle());
            assertEquals(1L, service.selectDetail(id).task().getAssigneeId());
            assertEquals(5, service.selectDetail(id).logs().size());
            service.delete(id);
            assertThrows(ServiceException.class, () -> service.selectDetail(id));
            assertTrue(service.selectList(new TaskRequests.Query(1, 10, title, null, null, null, null, null)).isEmpty());
            assertEquals("2", jdbc.queryForObject("select del_flag from task where id = ?", String.class, id));
            assertEquals(6, jdbc.queryForObject("select count(*) from task_log where task_id = ?", Integer.class, id));
            assertEquals("DELETE", jdbc.queryForObject("select action from task_log where task_id = ? order by id desc limit 1", String.class, id));
        });
    }

    @Test
    void statsAggregateVisibilityDatesAndDeletedTasks()
    {
        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            // 使用隔离的创建日期区间，不依赖业务库中已有任务数量。
            String title = "stats-test-" + System.nanoTime();
            Long first = service.create(new TaskRequests.Create(title, null, TaskPriority.LOW, 100L, null));
            Long second = service.create(new TaskRequests.Create(title, null, TaskPriority.HIGH, 1L, null));
            Long third = service.create(new TaskRequests.Create(title, null, TaskPriority.MEDIUM, 100L, null));
            Long excluded = service.create(new TaskRequests.Create(title, null, TaskPriority.LOW, 100L, null));
            service.changeStatus(first, new TaskRequests.Status(TaskStatus.IN_PROGRESS));
            service.changeStatus(first, new TaskRequests.Status(TaskStatus.DONE));
            service.delete(third);
            jdbc.update("update task set create_time = '1000-02-10 00:00:00' where id = ?", first);
            jdbc.update("update task set create_time = '1000-02-10 23:59:59' where id = ?", second);
            jdbc.update("update task set create_time = '1000-02-10 12:00:00' where id = ?", third);
            jdbc.update("update task set create_time = '1000-02-11 00:00:00' where id = ?", excluded);
            var global = service.selectStats("1000-02-10", "1000-02-10");
            assertEquals("ALL", global.scope());
            assertEquals(2, global.total());
            assertEquals(1, global.personalTotal());
            assertEquals(0, global.personalCompleted());
            TaskServiceTest.login(100, "common");
            var personal = service.selectStats("1000-02-10", "1000-02-10");
            assertEquals("VISIBLE", personal.scope());
            assertEquals(1, personal.total());
            assertEquals(1, personal.personalCompleted());
            assertEquals(new java.math.BigDecimal("100.00"), personal.personalCompletionRate());
            TaskServiceTest.login(999, "common");
            assertEquals(0, service.selectStats("1000-02-10", "1000-02-10").total());
        });
    }

    @Test
    void logFailureRollsBackCreationAndExistingTaskMutation()
    {
        TaskMapper failing = mock(TaskMapper.class, delegatesTo(mapper));
        doThrow(new IllegalStateException("simulated log failure")).when(failing).insertLog(any());
        ITaskService failingService = transactionalService(failing);
        String title = "task-rollback-test-" + System.nanoTime();
        assertThrows(IllegalStateException.class, () -> failingService.create(
                new TaskRequests.Create(title, null, TaskPriority.LOW, 100L, null)));
        assertEquals(0, jdbc.queryForObject("select count(*) from task where title = ?", Integer.class, title));

        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            Long id = service.create(new TaskRequests.Create(title, null, TaskPriority.LOW, 100L, null));
            // 在保存点内触发日志失败，验证回滚后的任务与已有创建日志仍一致。
            TransactionTemplate nested = new TransactionTemplate(manager);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            assertThrows(IllegalStateException.class, () -> nested.executeWithoutResult(ignored ->
                    failingService.changeStatus(id, new TaskRequests.Status(TaskStatus.IN_PROGRESS))));
            assertEquals("TODO", jdbc.queryForObject("select status from task where id = ?", String.class, id));
            assertEquals(1, jdbc.queryForObject("select count(*) from task_log where task_id = ?", Integer.class, id));
        });
        assertEquals(0, jdbc.queryForObject("select count(*) from task where title = ?", Integer.class, title));
    }
}
