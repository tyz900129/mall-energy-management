package com.wanyuecity.energy.qrinspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.qrinspection.entity.QrInspectionPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
@Mapper public interface QrInspectionPlanMapper extends BaseMapper<QrInspectionPlan> {
    @Select("SELECT * FROM qr_inspection_plan WHERE enabled=1 AND next_due_at IS NOT NULL AND next_due_at<=#{now}")
    List<QrInspectionPlan> duePlans(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM qr_inspection_plan WHERE scene=#{scene} AND dept_code=#{deptCode} AND enabled=1 ORDER BY id DESC LIMIT 1")
    QrInspectionPlan activeOfDept(@Param("scene") String scene, @Param("deptCode") String deptCode);
}
