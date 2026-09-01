package com.wanyuecity.energy.system.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.entity.StallEngineeringCable;
import com.wanyuecity.energy.stall.mapper.StallEngineeringCableMapper;
import com.wanyuecity.energy.system.entity.PowerSwitchSpecification;
import com.wanyuecity.energy.system.mapper.PowerSwitchSpecificationMapper;
import com.wanyuecity.energy.system.service.dto.PowerSwitchSpecificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor public class PowerSwitchSpecificationService {
 private final PowerSwitchSpecificationMapper mapper; private final StallEngineeringCableMapper usageMapper;
 public List<PowerSwitchSpecification> list(boolean enabledOnly){var q=new LambdaQueryWrapper<PowerSwitchSpecification>();if(enabledOnly)q.eq(PowerSwitchSpecification::getStatus,1);return mapper.selectList(q.orderByAsc(PowerSwitchSpecification::getSortOrder).orderByAsc(PowerSwitchSpecification::getId));}
 @Transactional public PowerSwitchSpecification create(PowerSwitchSpecificationRequest r){validate(r,null);var row=new PowerSwitchSpecification();apply(row,r);mapper.insert(row);return row;}
 @Transactional public PowerSwitchSpecification update(Long id,PowerSwitchSpecificationRequest r){var row=require(id);validate(r,id);apply(row,r);mapper.updateById(row);return row;}
 @Transactional public void delete(Long id){require(id);if(usageMapper.selectCount(new LambdaQueryWrapper<StallEngineeringCable>().eq(StallEngineeringCable::getPowerSwitchSpecificationId,id))>0)throw BizException.badRequest("该开关规格已被铺位使用，请停用而不要删除");mapper.deleteById(id);}
 private PowerSwitchSpecification require(Long id){var row=mapper.selectById(id);if(row==null)throw BizException.notFound("开关规格不存在");return row;}
 private void validate(PowerSwitchSpecificationRequest r,Long id){long n=mapper.selectCount(new LambdaQueryWrapper<PowerSwitchSpecification>().eq(PowerSwitchSpecification::getSpecCode,r.getSpecCode().trim()).ne(id!=null,PowerSwitchSpecification::getId,id));if(n>0)throw BizException.badRequest("开关规格编码不可重复");if(r.getStatus()!=null&&r.getStatus()!=0&&r.getStatus()!=1)throw BizException.badRequest("状态只能是启用或停用");}
 private void apply(PowerSwitchSpecification row,PowerSwitchSpecificationRequest r){BeanUtils.copyProperties(r,row);row.setSpecCode(r.getSpecCode().trim());row.setSwitchModel(r.getSwitchModel().trim());row.setPoleCount(r.getPoleCount().trim());if(row.getStatus()==null)row.setStatus(1);if(row.getSortOrder()==null)row.setSortOrder(0);}
}
