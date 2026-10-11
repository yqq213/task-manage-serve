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

/** 任务接口复用全局登录鉴权；记录级权限由服务层统一校验。 */
@RestController
@RequestMapping("/task/tasks")
public class TaskController extends BaseController
{
    private final ITaskService service;

    public TaskController(ITaskService service)
    {
        this.service = service;
    }

    @GetMapping("/list")
    public TableDataInfo list(@Valid @ModelAttribute TaskRequests.Query query)
    {
        return getDataTable(service.selectList(query));
    }

    /** 日期可省略，所有指标按北京时间的任务创建日期筛选。 */
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate)
    {
        return success(service.selectStats(startDate, endDate));
    }

    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id)
    {
        return success(service.selectDetail(id));
    }

    @GetMapping("/assignees")
    public AjaxResult assignees()
    {
        return success(service.selectAssignees());
    }

    @PostMapping
    public AjaxResult create(@Valid @RequestBody TaskRequests.Create request)
    {
        return success(service.create(request));
    }

    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable Long id, @Valid @RequestBody TaskRequests.Edit request)
    {
        service.edit(id, request);
        return success();
    }

    @PutMapping("/{id}/assignee")
    public AjaxResult assign(@PathVariable Long id, @Valid @RequestBody TaskRequests.Assignee request)
    {
        service.assign(id, request);
        return success();
    }

    @PutMapping("/{id}/status")
    public AjaxResult changeStatus(@PathVariable Long id, @Valid @RequestBody TaskRequests.Status request)
    {
        service.changeStatus(id, request);
        return success();
    }

    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id)
    {
        service.delete(id);
        return success();
    }
}
