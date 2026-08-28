package com.wanyuecity.energy.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wanyuecity.energy.core.BizException;
import com.wanyuecity.energy.system.entity.SysMenu;
import com.wanyuecity.energy.system.mapper.SysMenuMapper;
import com.wanyuecity.energy.system.mapper.SysRoleMenuMapper;
import com.wanyuecity.energy.system.service.dto.SysMenuNode;
import com.wanyuecity.energy.system.service.dto.SysMenuUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<SysMenuNode> tree() {
        List<SysMenu> all = menuMapper.selectList(new QueryWrapper<SysMenu>().orderByAsc("parent_id", "sort_order"));
        Map<Long, SysMenuNode> nodeMap = new HashMap<>();
        List<SysMenuNode> roots = new ArrayList<>();
        for (SysMenu m : all) {
            nodeMap.put(m.getId(), SysMenuNode.from(m));
        }
        for (SysMenu m : all) {
            SysMenuNode node = nodeMap.get(m.getId());
            if (m.getParentId() == null || m.getParentId() == 0L) {
                roots.add(node);
            } else {
                SysMenuNode parent = nodeMap.get(m.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    public SysMenuNode create(SysMenuUpsertRequest req) {
        if (menuMapper.selectCount(new QueryWrapper<SysMenu>().eq("code", req.getCode())) > 0) {
            throw BizException.badRequest("Menu code already exists");
        }
        SysMenu m = new SysMenu();
        m.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        m.setCode(req.getCode());
        m.setName(req.getName());
        m.setPath(req.getPath());
        m.setIcon(req.getIcon());
        m.setType(req.getType());
        m.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        m.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        menuMapper.insert(m);
        return SysMenuNode.from(m);
    }

    public SysMenuNode update(Long id, SysMenuUpsertRequest req) {
        SysMenu m = menuMapper.selectById(id);
        if (m == null) {
            throw BizException.notFound("Menu not found: " + id);
        }
        if (!m.getCode().equals(req.getCode())) {
            if (menuMapper.selectCount(new QueryWrapper<SysMenu>().eq("code", req.getCode())) > 0) {
                throw BizException.badRequest("Menu code already exists");
            }
            m.setCode(req.getCode());
        }
        m.setName(req.getName());
        m.setPath(req.getPath());
        m.setIcon(req.getIcon());
        m.setType(req.getType());
        m.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        m.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        menuMapper.updateById(m);
        return SysMenuNode.from(m);
    }

    @Transactional
    public void delete(Long id) {
        SysMenu m = menuMapper.selectById(id);
        if (m == null) {
            throw BizException.notFound("Menu not found: " + id);
        }
        Long childCount = menuMapper.selectCount(new QueryWrapper<SysMenu>().eq("parent_id", id));
        if (childCount != null && childCount > 0) {
            throw BizException.badRequest("Cannot delete menu with children");
        }
        roleMenuMapper.delete(new QueryWrapper<com.wanyuecity.energy.system.entity.SysRoleMenu>().eq("menu_id", id));
        menuMapper.deleteById(id);
    }
}
