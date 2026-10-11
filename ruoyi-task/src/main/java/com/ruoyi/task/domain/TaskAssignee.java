package com.ruoyi.task.domain;

/** 负责人候选信息，仅公开ID、账号和昵称。 */
public record TaskAssignee(Long userId, String userName, String nickName) { }
