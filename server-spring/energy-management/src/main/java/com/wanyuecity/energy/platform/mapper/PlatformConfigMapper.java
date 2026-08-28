package com.wanyuecity.energy.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.platform.entity.PlatformConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformConfigMapper extends BaseMapper<PlatformConfig> {

    @Select("SELECT * FROM platform_config WHERE id = 1 LIMIT 1")
    PlatformConfig findSingleton();
}

