package com.wanyuecity.energy.qrinspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.qrinspection.entity.QrInspectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper public interface QrInspectionTaskMapper extends BaseMapper<QrInspectionTask> {
    @Select("SELECT * FROM qr_inspection_task WHERE status='ACTIVE' LIMIT 1")
    QrInspectionTask active();
}
