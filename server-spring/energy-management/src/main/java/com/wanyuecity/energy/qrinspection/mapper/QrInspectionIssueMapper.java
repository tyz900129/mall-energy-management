package com.wanyuecity.energy.qrinspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.qrinspection.entity.QrInspectionIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Mapper public interface QrInspectionIssueMapper extends BaseMapper<QrInspectionIssue> {
    @Select("SELECT * FROM qr_inspection_issue WHERE access_token=#{token} LIMIT 1")
    QrInspectionIssue byToken(@Param("token") String token);

    @Select("SELECT * FROM qr_inspection_issue WHERE status='PENDING' AND deadline_at<=#{now}")
    List<QrInspectionIssue> overdueIssues(@Param("now") LocalDateTime now);
}
