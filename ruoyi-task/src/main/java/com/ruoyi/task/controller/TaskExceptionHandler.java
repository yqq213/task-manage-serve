package com.ruoyi.task.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;

/** 仅处理任务请求的JSON解析错误，不把框架类名、输入内容等异常细节返回给用户。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler
{
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult invalidBody(HttpMessageNotReadableException exception)
    {
        return AjaxResult.error("请求格式错误，请检查优先级、状态和日期格式（yyyy-MM-dd HH:mm:ss）");
    }
}
