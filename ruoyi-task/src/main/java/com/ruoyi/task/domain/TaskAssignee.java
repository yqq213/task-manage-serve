package com.ruoyi.task.domain;

/**
 * 负责人候选响应，仅公开启用且未删除用户的 ID、账号和昵称，不暴露其他用户资料。
 *
 * @param userId 系统用户主键，创建或指派任务时作为 assigneeId 提交
 * @param userName 用户登录账号
 * @param nickName 用户当前昵称
 */
public record TaskAssignee(Long userId, String userName, String nickName) { }
