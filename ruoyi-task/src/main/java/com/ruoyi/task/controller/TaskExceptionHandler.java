package com.ruoyi.task.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 任务接口专用的 JSON 请求体解析异常处理器，仅作用于 {@link TaskController}。
 * <p>优先于通用异常处理器处理无法读取的请求体，例如非法枚举值或日期格式。
 * 返回统一业务错误提示，避免暴露框架类名、原始输入等异常细节。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskExceptionHandler
{
    /**
     * 将请求体读取或反序列化异常转换为固定的任务参数格式提示。
     *
     * @param exception Spring 捕获的请求体解析异常，仅用于匹配异常处理入口
     * @return 包含统一格式错误提示的 AjaxResult，不返回原始异常内容
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult invalidBody(HttpMessageNotReadableException exception)
    {
        return AjaxResult.error("请求格式错误，请检查优先级、状态和日期格式（yyyy-MM-dd HH:mm:ss）");
    }
}
