package com.wanyuecity.energy.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
    /** 岗位所属部门 */
    private Long deptId;
    /** 岗位类别：MANAGE/TECH/OPER/ADMIN */
    private String postCategory;
    /** 是否允许在用户管理中分配 */
    private Integer isAssignable;
    /** 内置角色保护：不可删除、不可修改编码 */
    private Integer isSystem;
    /** 编制数，0=不限 */
    private Integer headcount;
}
