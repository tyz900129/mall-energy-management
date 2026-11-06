package com.wanyuecity.energy.qrinspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.qrinspection.entity.QrInspectionPlanRound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper public interface QrInspectionPlanRoundMapper extends BaseMapper<QrInspectionPlanRound> {
    @Select("SELECT * FROM qr_inspection_plan_round WHERE plan_id=#{planId} AND due_date=#{dueDate} LIMIT 1")
    QrInspectionPlanRound byPlanAndDate(@Param("planId") Long planId, @Param("dueDate") LocalDate dueDate);

    @Select("SELECT * FROM qr_inspection_plan_round WHERE plan_id=#{planId} ORDER BY due_date DESC LIMIT 1")
    QrInspectionPlanRound latest(@Param("planId") Long planId);

    @Select("SELECT * FROM qr_inspection_plan_round WHERE plan_id=#{planId} AND status='PENDING' ORDER BY due_date ASC LIMIT 1")
    QrInspectionPlanRound firstPending(@Param("planId") Long planId);

    @Select("SELECT * FROM qr_inspection_plan_round WHERE plan_id=#{planId} ORDER BY due_date DESC")
    List<QrInspectionPlanRound> listByPlan(@Param("planId") Long planId);
}
