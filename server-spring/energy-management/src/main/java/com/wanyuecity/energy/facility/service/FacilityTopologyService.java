package com.wanyuecity.energy.facility.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.facility.entity.FacilityTopologyNode;
import com.wanyuecity.energy.facility.mapper.FacilityTopologyNodeMapper;
import com.wanyuecity.energy.facility.service.dto.FacilityTopologyNodeRequest;
import com.wanyuecity.energy.facility.service.dto.FacilityTopologyNodeView;
import com.wanyuecity.energy.stall.mapper.StallMapper;
import com.wanyuecity.energy.system.service.BusinessDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacilityTopologyService {
    public static final Set<String> SYSTEM_TYPES = Set.of("WATER_SUPPLY", "DRAINAGE", "OIL_EXHAUST", "FRESH_AIR");
    private static final Set<String> OPERATING_STATUSES = Set.of("NORMAL", "STOPPED", "FAULT", "MAINTENANCE");

    private final FacilityTopologyNodeMapper mapper;
    private final StallMapper stallMapper;
    private final BusinessDictionaryService dictionaries;

    public List<FacilityTopologyNodeView> tree(String requestedSystemType) {
        String systemType = normalizeSystemType(requestedSystemType);
        List<FacilityTopologyNode> rows = allNodes(systemType);
        Map<Long, FacilityTopologyNodeView> views = rows.stream().map(this::view).collect(Collectors.toMap(
            FacilityTopologyNodeView::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<FacilityTopologyNodeView> roots = new ArrayList<>();
        for (FacilityTopologyNode row : rows) {
            FacilityTopologyNodeView node = views.get(row.getId());
            FacilityTopologyNodeView parent = row.getParentId() == null ? null : views.get(row.getParentId());
            if (parent == null) roots.add(node); else parent.getChildren().add(node);
        }
        return roots;
    }

    @Transactional
    public FacilityTopologyNodeView create(String requestedSystemType, FacilityTopologyNodeRequest request) {
        String systemType = normalizeSystemType(requestedSystemType);
        validate(systemType, request, null);
        FacilityTopologyNode node = new FacilityTopologyNode();
        node.setSystemType(systemType);
        apply(node, request);
        mapper.insert(node);
        return view(node);
    }

    @Transactional
    public FacilityTopologyNodeView update(String requestedSystemType, Long id, FacilityTopologyNodeRequest request) {
        String systemType = normalizeSystemType(requestedSystemType);
        FacilityTopologyNode node = require(systemType, id);
        validate(systemType, request, id);
        apply(node, request);
        mapper.updateById(node);
        return view(node);
    }

    @Transactional
    public void delete(String requestedSystemType, Long id) {
        String systemType = normalizeSystemType(requestedSystemType);
        require(systemType, id);
        long children = mapper.selectCount(new LambdaQueryWrapper<FacilityTopologyNode>()
            .eq(FacilityTopologyNode::getParentId, id));
        if (children > 0) throw BizException.badRequest("该节点仍有下级，请先调整或删除下级节点");
        mapper.deleteById(id);
    }

    public FacilityTopologyNode require(String requestedSystemType, Long id) {
        String systemType = normalizeSystemType(requestedSystemType);
        FacilityTopologyNode node = mapper.selectById(id);
        if (node == null || !systemType.equals(node.getSystemType())) throw BizException.notFound("设施拓扑节点不存在");
        return node;
    }

    public String normalizeSystemType(String value) {
        String result = value == null ? "" : value.trim().toUpperCase();
        if (!SYSTEM_TYPES.contains(result)) throw BizException.badRequest("不支持的设施拓扑类型");
        return result;
    }

    public String routeSegment(String requestedSystemType) {
        return switch (normalizeSystemType(requestedSystemType)) {
            case "WATER_SUPPLY" -> "water-supply";
            case "DRAINAGE" -> "drainage";
            case "OIL_EXHAUST" -> "oil-exhaust";
            default -> "fresh-air";
        };
    }

    private void validate(String systemType, FacilityTopologyNodeRequest request, Long id) {
        validateParent(systemType, request.getParentId());
        if (id != null && reaches(request.getParentId(), id, systemType)) {
            throw BizException.badRequest("不能把节点自身或其下级设置为上级节点");
        }
        long duplicates = mapper.selectCount(new LambdaQueryWrapper<FacilityTopologyNode>()
            .eq(FacilityTopologyNode::getSystemType, systemType)
            .eq(FacilityTopologyNode::getNodeCode, request.getNodeCode().trim())
            .ne(id != null, FacilityTopologyNode::getId, id));
        if (duplicates > 0) throw BizException.badRequest("同一拓扑中的节点编码不可重复");
        if (!dictionaries.isEnabled(nodeTypeDictionary(systemType), request.getNodeType())) {
            throw BizException.badRequest("请选择有效的节点类型");
        }
        validateOptionalDictionary(lineSpecDictionary(systemType), request.getLineSpecCode(), "请选择有效的管道或风管规格");
        if ("DRAINAGE".equals(systemType)) validateOptionalDictionary("DRAINAGE_MEDIUM", request.getMediumType(), "请选择有效的排水性质");
        if ("OIL_EXHAUST".equals(systemType)) validateOptionalDictionary("EXHAUST_FAN", request.getEquipmentSpecCode(), "请选择有效的排油烟风机");
        if ("FRESH_AIR".equals(systemType)) validateOptionalDictionary("FRESH_AIR_UNIT", request.getEquipmentSpecCode(), "请选择有效的补风机");
        String operatingStatus = blankToNull(request.getOperatingStatus());
        if (operatingStatus != null && !OPERATING_STATUSES.contains(operatingStatus)) throw BizException.badRequest("运行状态不正确");
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) throw BizException.badRequest("状态只能是启用或停用");
        if (request.getStallId() != null && stallMapper.selectById(request.getStallId()) == null) throw BizException.badRequest("关联铺位不存在");
    }

    private void validateParent(String systemType, Long parentId) {
        if (parentId == null) return;
        FacilityTopologyNode parent = mapper.selectById(parentId);
        if (parent == null || !systemType.equals(parent.getSystemType())) throw BizException.badRequest("上级节点不存在或不属于当前拓扑");
    }

    private boolean reaches(Long start, Long target, String systemType) {
        Long current = start;
        Set<Long> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(target)) return true;
            FacilityTopologyNode node = mapper.selectById(current);
            if (node == null || !systemType.equals(node.getSystemType())) return false;
            current = node.getParentId();
        }
        return false;
    }

    private void validateOptionalDictionary(String type, String value, String message) {
        if (value != null && !value.isBlank() && !dictionaries.isEnabled(type, value)) throw BizException.badRequest(message);
    }

    private void apply(FacilityTopologyNode node, FacilityTopologyNodeRequest request) {
        BeanUtils.copyProperties(request, node);
        node.setNodeCode(request.getNodeCode().trim());
        node.setNodeName(request.getNodeName().trim());
        node.setLineSpecCode(blankToNull(request.getLineSpecCode()));
        node.setEquipmentSpecCode(blankToNull(request.getEquipmentSpecCode()));
        node.setMediumType(blankToNull(request.getMediumType()));
        node.setControlComponent(blankToNull(request.getControlComponent()));
        node.setLocation(blankToNull(request.getLocation()));
        node.setPlatformDeviceId(blankToNull(request.getPlatformDeviceId()));
        node.setOperatingStatus(blankToNull(request.getOperatingStatus()) == null ? "NORMAL" : request.getOperatingStatus());
        node.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        node.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private List<FacilityTopologyNode> allNodes(String systemType) {
        return mapper.selectList(new LambdaQueryWrapper<FacilityTopologyNode>()
            .eq(FacilityTopologyNode::getSystemType, systemType)
            .orderByAsc(FacilityTopologyNode::getSortOrder)
            .orderByAsc(FacilityTopologyNode::getId));
    }

    private FacilityTopologyNodeView view(FacilityTopologyNode node) {
        FacilityTopologyNodeView result = new FacilityTopologyNodeView();
        BeanUtils.copyProperties(node, result);
        if (node.getParentId() != null) {
            FacilityTopologyNode parent = mapper.selectById(node.getParentId());
            if (parent != null) result.setParentName(parent.getNodeCode() + " · " + parent.getNodeName());
        }
        result.setNodeTypeName(dictionaries.label(nodeTypeDictionary(node.getSystemType()), node.getNodeType()));
        result.setLineSpecName(dictionaries.label(lineSpecDictionary(node.getSystemType()), node.getLineSpecCode()));
        if ("OIL_EXHAUST".equals(node.getSystemType())) result.setEquipmentSpecName(dictionaries.label("EXHAUST_FAN", node.getEquipmentSpecCode()));
        if ("FRESH_AIR".equals(node.getSystemType())) result.setEquipmentSpecName(dictionaries.label("FRESH_AIR_UNIT", node.getEquipmentSpecCode()));
        if ("DRAINAGE".equals(node.getSystemType())) result.setMediumTypeName(dictionaries.label("DRAINAGE_MEDIUM", node.getMediumType()));
        if (node.getStallId() != null) {
            var stall = stallMapper.selectById(node.getStallId());
            if (stall != null) {
                result.setStallCode(stall.getStallCode());
                result.setStallName(stall.getStallName());
            }
        }
        return result;
    }

    private String nodeTypeDictionary(String systemType) {
        return switch (systemType) {
            case "WATER_SUPPLY" -> "WATER_SUPPLY_NODE_TYPE";
            case "DRAINAGE" -> "DRAINAGE_NODE_TYPE";
            case "OIL_EXHAUST" -> "OIL_EXHAUST_NODE_TYPE";
            default -> "FRESH_AIR_NODE_TYPE";
        };
    }

    private String lineSpecDictionary(String systemType) {
        return switch (systemType) {
            case "WATER_SUPPLY" -> "WATER_SUPPLY_PIPE_SPEC";
            case "DRAINAGE" -> "DRAINAGE_PIPE_CALIBER";
            default -> "AIR_DUCT_SPEC";
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
