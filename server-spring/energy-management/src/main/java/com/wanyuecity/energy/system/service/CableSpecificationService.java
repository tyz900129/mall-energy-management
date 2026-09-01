package com.wanyuecity.energy.system.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.stall.mapper.StallEngineeringCableMapper;
import com.wanyuecity.energy.stall.entity.StallEngineeringCable;
import com.wanyuecity.energy.system.entity.CableSpecification;
import com.wanyuecity.energy.system.mapper.CableSpecificationMapper;
import com.wanyuecity.energy.system.service.dto.CableSpecificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor public class CableSpecificationService {
 private final CableSpecificationMapper mapper; private final StallEngineeringCableMapper usageMapper;
 public List<CableSpecification> list(boolean enabledOnly){var q=new LambdaQueryWrapper<CableSpecification>();if(enabledOnly)q.eq(CableSpecification::getStatus,1);return mapper.selectList(q.orderByAsc(CableSpecification::getSortOrder).orderByAsc(CableSpecification::getId));}
 @Transactional public CableSpecification create(CableSpecificationRequest r){validate(r,null);var row=new CableSpecification();apply(row,r);mapper.insert(row);return row;}
 @Transactional public CableSpecification update(Long id,CableSpecificationRequest r){var row=require(id);validate(r,id);apply(row,r);mapper.updateById(row);return row;}
 @Transactional public void delete(Long id){require(id);if(usageMapper.selectCount(new LambdaQueryWrapper<StallEngineeringCable>().eq(StallEngineeringCable::getCableSpecificationId,id))>0)throw BizException.badRequest("该电缆规格已被铺位使用，请停用而不要删除");mapper.deleteById(id);}
 public CableSpecification requireEnabled(Long id){var row=require(id);if(!Integer.valueOf(1).equals(row.getStatus()))throw BizException.badRequest("所选电缆规格已停用");return row;}
 private CableSpecification require(Long id){var row=mapper.selectById(id);if(row==null)throw BizException.notFound("电缆规格不存在");return row;}
 private void validate(CableSpecificationRequest r,Long id){long n=mapper.selectCount(new LambdaQueryWrapper<CableSpecification>().eq(CableSpecification::getSpecCode,r.getSpecCode().trim()).ne(id!=null,CableSpecification::getId,id));if(n>0)throw BizException.badRequest("电缆规格编码不可重复");if(r.getStatus()!=null&&r.getStatus()!=0&&r.getStatus()!=1)throw BizException.badRequest("状态只能是启用或停用");}
 private void apply(CableSpecification row,CableSpecificationRequest r){BeanUtils.copyProperties(r,row);row.setSpecCode(r.getSpecCode().trim());row.setCableModel(r.getCableModel().trim());row.setSpecification(r.getSpecification().trim());if(row.getStatus()==null)row.setStatus(1);if(row.getSortOrder()==null)row.setSortOrder(0);}
}
