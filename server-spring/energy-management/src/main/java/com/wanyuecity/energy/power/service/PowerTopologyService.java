package com.wanyuecity.energy.power.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.power.entity.PowerTopologyNode;
import com.wanyuecity.energy.power.mapper.PowerTopologyNodeMapper;
import com.wanyuecity.energy.power.service.dto.PowerTopologyNodeRequest;
import com.wanyuecity.energy.power.service.dto.PowerTopologyNodeView;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.entity.BusinessDictionary;
import com.wanyuecity.energy.system.mapper.BusinessDictionaryMapper;
import com.wanyuecity.energy.system.mapper.PowerSwitchSpecificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PowerTopologyService {
 private final PowerTopologyNodeMapper mapper;private final PowerSwitchSpecificationMapper switchMapper;private final BusinessDictionaryMapper dictionaryMapper;private final StallMapper stallMapper;
 public List<PowerTopologyNodeView> tree(){List<PowerTopologyNode> rows=allNodes();Map<Long,PowerTopologyNodeView> views=rows.stream().map(this::view).collect(Collectors.toMap(PowerTopologyNodeView::getId,Function.identity(),(a,b)->a,LinkedHashMap::new));List<PowerTopologyNodeView> roots=new ArrayList<>();for(PowerTopologyNode row:rows){PowerTopologyNodeView node=views.get(row.getId());PowerTopologyNodeView parent=row.getParentId()==null?null:views.get(row.getParentId());if(parent==null)roots.add(node);else parent.getChildren().add(node);}return roots;}
 @Transactional public PowerTopologyNodeView create(PowerTopologyNodeRequest r){validate(r,null);PowerTopologyNode n=new PowerTopologyNode();apply(n,r);mapper.insert(n);return view(n);}
 @Transactional public PowerTopologyNodeView update(Long id,PowerTopologyNodeRequest r){PowerTopologyNode n=require(id);validate(r,id);apply(n,r);mapper.updateById(n);return view(n);}
 @Transactional public void delete(Long id){require(id);long primary=mapper.selectCount(new LambdaQueryWrapper<PowerTopologyNode>().eq(PowerTopologyNode::getParentId,id));long secondary=mapper.selectCount(new LambdaQueryWrapper<PowerTopologyNode>().eq(PowerTopologyNode::getSecondaryParentId,id));if(primary+secondary>0)throw BizException.badRequest("该节点仍被下级作为主电源或备用电源引用，请先调整下级节点");mapper.deleteById(id);}
 public PowerTopologyNode require(Long id){PowerTopologyNode n=mapper.selectById(id);if(n==null)throw BizException.notFound("配电节点不存在");return n;}
 private void validate(PowerTopologyNodeRequest r,Long id){validateParent(r.getParentId(),"主电源上级节点不存在");validateParent(r.getSecondaryParentId(),"备用电源上级节点不存在");if(r.getSecondaryParentId()!=null&&r.getParentId()==null)throw BizException.badRequest("配置备用电源前请先选择主电源");if(r.getParentId()!=null&&r.getParentId().equals(r.getSecondaryParentId()))throw BizException.badRequest("主电源和备用电源不能选择同一节点");if(id!=null&&(reaches(r.getParentId(),id,new HashSet<>())||reaches(r.getSecondaryParentId(),id,new HashSet<>())))throw BizException.badRequest("不能把节点的自身或下级配置为供电来源");if(mapper.selectCount(new LambdaQueryWrapper<PowerTopologyNode>().eq(PowerTopologyNode::getNodeCode,r.getNodeCode().trim()).ne(id!=null,PowerTopologyNode::getId,id))>0)throw BizException.badRequest("节点编码不可重复");if(dictionaryMapper.selectCount(new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,"POWER_NODE_TYPE").eq(BusinessDictionary::getItemValue,r.getNodeType()).eq(BusinessDictionary::getStatus,1))==0)throw BizException.badRequest("请选择有效的节点类型");validateSwitch(r.getSwitchSpecificationId(),"主电源开关规格不存在");validateSwitch(r.getSecondarySwitchSpecificationId(),"备用电源开关规格不存在");if(r.getStatus()!=null&&r.getStatus()!=0&&r.getStatus()!=1)throw BizException.badRequest("状态只能是启用或停用");}
 private boolean reaches(Long start,Long target,Set<Long> visited){if(start==null||!visited.add(start))return false;if(start.equals(target))return true;PowerTopologyNode n=mapper.selectById(start);return n!=null&&(reaches(n.getParentId(),target,visited)||reaches(n.getSecondaryParentId(),target,visited));}
 private void validateParent(Long id,String message){if(id!=null&&mapper.selectById(id)==null)throw BizException.badRequest(message);}private void validateSwitch(Long id,String message){if(id!=null&&switchMapper.selectById(id)==null)throw BizException.badRequest(message);}
 private void apply(PowerTopologyNode n,PowerTopologyNodeRequest r){BeanUtils.copyProperties(r,n);n.setNodeCode(r.getNodeCode().trim());n.setNodeName(r.getNodeName().trim());if(n.getSecondaryParentId()==null)n.setSecondarySwitchSpecificationId(null);if(n.getSortOrder()==null)n.setSortOrder(0);if(n.getStatus()==null)n.setStatus(1);}
 private List<PowerTopologyNode> allNodes(){return mapper.selectList(new LambdaQueryWrapper<PowerTopologyNode>().orderByAsc(PowerTopologyNode::getSortOrder).orderByAsc(PowerTopologyNode::getId));}
 private PowerTopologyNodeView view(PowerTopologyNode n){PowerTopologyNodeView v=new PowerTopologyNodeView();BeanUtils.copyProperties(n,v);applyParentName(v,n.getParentId(),false);applyParentName(v,n.getSecondaryParentId(),true);applySwitch(v,n.getSwitchSpecificationId(),false);applySwitch(v,n.getSecondarySwitchSpecificationId(),true);BusinessDictionary d=dictionaryMapper.selectOne(new LambdaQueryWrapper<BusinessDictionary>().eq(BusinessDictionary::getDictType,"POWER_NODE_TYPE").eq(BusinessDictionary::getItemValue,n.getNodeType()).last("LIMIT 1"));v.setNodeTypeName(d==null?n.getNodeType():d.getItemLabel());if(n.getStallId()!=null){var s=stallMapper.selectById(n.getStallId());if(s!=null)v.setStallCode(s.getStallCode());}return v;}
 private void applyParentName(PowerTopologyNodeView v,Long id,boolean secondary){if(id==null)return;PowerTopologyNode p=mapper.selectById(id);if(p==null)return;String name=p.getNodeCode()+" · "+p.getNodeName();if(secondary)v.setSecondaryParentName(name);else v.setParentName(name);}
 private void applySwitch(PowerTopologyNodeView v,Long id,boolean secondary){if(id==null)return;var s=switchMapper.selectById(id);if(s==null)return;String name=(s.getBrand()==null?"":s.getBrand()+" ")+s.getSwitchModel();if(secondary){v.setSecondarySwitchSpecificationName(name);v.setSecondarySwitchRatedCurrentA(s.getRatedCurrentA());}else{v.setSwitchSpecificationName(name);v.setSwitchRatedCurrentA(s.getRatedCurrentA());}}
}
