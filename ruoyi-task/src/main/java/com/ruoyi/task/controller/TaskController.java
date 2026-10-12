package com.ruoyi.task.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.task.domain.TaskRequests;
import com.ruoyi.task.service.ITaskService;

/**
 * 任务管理 REST 接口，统一入口为 {@code /task/tasks}。
 * <p>复用全局登录鉴权，任务可见范围与记录级操作权限由服务层校验。
 * 列表返回分页结构 {@code code/msg/rows/total}，其他接口返回 {@code AjaxResult}。
 * 写入使用独立请求 DTO，避免客户端直接绑定创建人、删除标记等审计字段。</p>
 */
@RestController
@RequestMapping("/task/tasks")
public class TaskController extends BaseController
{
    /** 统一处理任务权限、状态流转、持久化及操作历史的业务服务。 */
    private final ITaskService service;

    /**
     * 通过构造器注入任务服务。
     *
     * @param service 任务业务服务
     */
    public TaskController(ITaskService service)
    {
        this.service = service;
    }

    /**
     * 分页查询未删除任务，支持关键词、状态、优先级、负责人筛选及白名单排序。
     * <p>管理员查询全部任务，普通用户仅查询自己创建或负责的任务；分页由服务层设置。</p>
     *
     * @param query 经 Bean Validation 校验并设置默认值的查询参数
     * @return 包含任务列表及符合筛选条件的总条数的分页响应
     */
    @GetMapping("/list")
    public TableDataInfo list(@Valid @ModelAttribute TaskRequests.Query query)
    {
        return getDataTable(service.selectList(query));
    }

    /**
     * 查询当前用户可见范围内的看板统计，以及本人负责任务的完成情况。
     * <p>日期均可省略，按北京时间的任务创建日期筛选，包含起止日期当天。
     * 服务层校验日期有效性及先后顺序；已删除任务不参与统计。</p>
     *
     * @param startDate 开始日期，格式 yyyy-MM-dd；省略时不限制起始日期
     * @param endDate 结束日期，格式 yyyy-MM-dd；省略时不限制结束日期
     * @return data 为任务总数、状态/优先级分布及个人完成率的成功响应
     */
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate)
    {
        return success(service.selectStats(startDate, endDate));
    }

    /**
     * 查询任务详情及按时间升序排列的操作历史。
     * <p>仅管理员、任务创建人或负责人可读取；任务不存在或已删除时由服务层拒绝。</p>
     *
     * @param id 任务主键，必须大于 0
     * @return data 为同一读取事务中取得的任务与历史记录的成功响应
     */
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return success(service.selectDetail(id));
    }

    /**
     * 获取负责人候选列表，仅包含正常启用且未删除的系统用户。
     *
     * @return data 为用户 ID、账号及昵称列表的成功响应
     */
    @GetMapping("/assignees")
    public AjaxResult assignees()
    {
        return success(service.selectAssignees());
    }

    /**
     * 创建任务，并在同一事务中记录创建历史。
     * <p>初始状态固定为待办，创建人与审计信息取自当前登录用户；负责人须为有效用户。</p>
     *
     * @param request 已校验的标题、描述、优先级、负责人及可选截止时间
     * @return data 为新任务主键的成功响应
     */
    @PostMapping
    public AjaxResult create(@Valid @RequestBody TaskRequests.Create request)
    {
        return success(service.create(request));
    }

    /**
     * 覆盖任务的标题、描述、优先级与截止时间，描述和截止时间可传 null 清空。
     * <p>仅管理员或创建人可编辑，已完成任务禁止编辑；无实际变化时不写入历史。</p>
     *
     * @param id 待编辑的任务主键
     * @param request 经校验的完整可编辑字段
     * @return 不携带业务数据的成功响应
     */
    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable Long id, @Valid @RequestBody TaskRequests.Edit request)
    {
        service.edit(id, request);
        return success();
    }

    /**
     * 更换任务负责人，校验新负责人仍处于启用且未删除状态。
     * <p>仅管理员或创建人可指派，已完成任务禁止指派；负责人未变化时不新增历史。</p>
     *
     * @param id 待指派的任务主键
     * @param request 新负责人 ID
     * @return 不携带业务数据的成功响应
     */
    @PutMapping("/{id}/assignee")
    public AjaxResult assign(@PathVariable Long id, @Valid @RequestBody TaskRequests.Assignee request)
    {
        service.assign(id, request);
        return success();
    }

    /**
     * 推进任务状态，仅允许待办到进行中、进行中到已完成，完成时记录完成时间。
     * <p>仅管理员或当前负责人可操作；重复提交当前状态为幂等操作，不更新时间及历史。</p>
     *
     * @param id 待变更状态的任务主键
     * @param request 目标状态，不允许跳级或回退
     * @return 不携带业务数据的成功响应
     */
    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@PathVariable Long id, @Valid @RequestBody TaskRequests.Status request)
    {
        service.changeStatus(id, request);
        return success();
    }

    /**
     * 逻辑删除任务，记录删除人、删除时间及删除历史，保留数据库任务和历史记录。
     * <p>仅管理员或创建人可操作；已完成任务也可删除，删除后不再出现在业务查询中。</p>
     *
     * @param id 待删除的任务主键
     * @return 不携带业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id)
    {
        service.delete(id);
        return success();
    }
}
