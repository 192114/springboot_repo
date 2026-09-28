package com.shadow.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shadow.backend.auth.entity.SmsLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsLogMapper extends BaseMapper<SmsLog> {
}
