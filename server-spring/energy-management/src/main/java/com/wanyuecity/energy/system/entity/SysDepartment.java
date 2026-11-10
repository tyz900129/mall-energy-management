package com.wanyuecity.energy.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_department")
public class SysDepartment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String deptGroup;

    /** 上级部门；null 表示根（部）。 */
    private Long parentId;

    /** 物化路径，不含自身：根为 "/"，子节点为 <父ancestors><父id>/。 */
    private String ancestors;

    /** 层级：0=部、1=处/课、2=班。 */
    private Integer depth;

    /** GROUP / SECTION / TEAM。 */
    private String deptLevel;

    /** 1=编码被引用，禁止修改。 */
    private Integer codeLocked;

    /** ACTIVE / DISABLED。 */
    private String status;

    /** 软删除时间；非空表示已删除。 */
    private LocalDateTime deletedAt;

    /** 关联营运巡场处室（商管一处~四处）。 */
    private Long opsDeptId;

    private Integer sortOrder;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
