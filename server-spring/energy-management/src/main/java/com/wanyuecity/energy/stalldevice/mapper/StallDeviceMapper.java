package com.wanyuecity.energy.stalldevice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.stalldevice.entity.StallDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StallDeviceMapper extends BaseMapper<StallDevice> {

    @Select("SELECT * FROM stall_device WHERE platform_device_id = #{deviceId} AND status = 'ACTIVE' LIMIT 1")
    StallDevice findActiveByDeviceId(@Param("deviceId") String platformDeviceId);

    @Select("SELECT * FROM stall_device WHERE stall_id = #{stallId} AND status = 'ACTIVE' ORDER BY id ASC")
    List<StallDevice> listActiveByStall(@Param("stallId") Long stallId);

    /** spec §17 设备绑定历史：所有状态都返回（含已解绑），按 effective_from 降序 */
    @Select("SELECT * FROM stall_device WHERE platform_device_id = #{deviceId} ORDER BY effective_from DESC")
    List<StallDevice> listAllByDevice(@Param("deviceId") String platformDeviceId);

    @Select("SELECT * FROM stall_device WHERE status = 'ACTIVE' AND energy_type = #{energyType} ORDER BY id ASC")
    List<StallDevice> listActiveByEnergyType(@Param("energyType") String energyType);
}

