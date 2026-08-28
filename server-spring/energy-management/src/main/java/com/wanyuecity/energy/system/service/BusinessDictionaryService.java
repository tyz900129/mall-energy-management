package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.mapper.BusinessDictionaryMapper;
import com.wanyuecity.energy.system.service.dto.BusinessDictionaryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class BusinessDictionaryService {
    public static final List<String> TYPES=List.of("FLOOR","BUSINESS_TYPE");
    private final BusinessDictionaryMapper mapper;
    public List<BusinessDictionary> list(String type, boolean enabledOnly){
        validateType(type); var q=new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,type);
        if(enabledOnly)q.eq(BusinessDictionary::getStatus,1);
        return mapper.selectList(q.orderByAsc(BusinessDictionary::getSortOrder).orderByAsc(BusinessDictionary::getId));
    }
    @Transactional public BusinessDictionary create(BusinessDictionaryRequest r){validate(r,null);BusinessDictionary d=new BusinessDictionary();apply(d,r);mapper.insert(d);return d;}
    @Transactional public BusinessDictionary update(Long id,BusinessDictionaryRequest r){BusinessDictionary d=mapper.selectById(id);if(d==null)throw BizException.notFound("字典项不存在");validate(r,id);apply(d,r);mapper.updateById(d);return d;}
    @Transactional public void delete(Long id){if(mapper.selectById(id)==null)throw BizException.notFound("字典项不存在");mapper.deleteById(id);}
    public boolean isEnabled(String type,String value){if(value==null||value.isBlank())return true;return mapper.selectCount(new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,type).eq(BusinessDictionary::getItemValue,value).eq(BusinessDictionary::getStatus,1))>0;}
    public String label(String type,String value){if(value==null)return null;BusinessDictionary d=mapper.selectOne(new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,type).eq(BusinessDictionary::getItemValue,value).last("LIMIT 1"));return d==null?value:d.getItemLabel();}
    private void validate(BusinessDictionaryRequest r,Long id){validateType(r.getDictType());long n=mapper.selectCount(new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,r.getDictType()).eq(BusinessDictionary::getItemValue,r.getItemValue()).ne(id!=null,BusinessDictionary::getId,id));if(n>0)throw BizException.badRequest("同一分类下取值不可重复");if(r.getStatus()!=null&&r.getStatus()!=0&&r.getStatus()!=1)throw BizException.badRequest("状态只能是启用或停用");}
    private void validateType(String t){if(!TYPES.contains(t))throw BizException.badRequest("不支持的字典分类");}
    private void apply(BusinessDictionary d,BusinessDictionaryRequest r){d.setDictType(r.getDictType());d.setItemValue(r.getItemValue().trim());d.setItemLabel(r.getItemLabel().trim());d.setSortOrder(r.getSortOrder()==null?0:r.getSortOrder());d.setStatus(r.getStatus()==null?1:r.getStatus());d.setRemark(r.getRemark());}
}

