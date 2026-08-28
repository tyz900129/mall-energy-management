package com.wanyuecity.energy.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountDeviceCurrent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformAccountDeviceCurrentMapper extends BaseMapper<PlatformAccountDeviceCurrent> {

    /** Plain DELETE so the operation participates in the surrounding transaction. */
    @Delete("DELETE FROM platform_account_device_current")
    int deleteAll();
}
