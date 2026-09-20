package com.wanyuecity.energy.power.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerTopologyNodeMappingTest {
    @Test
    void dualSupplyFieldsMustBeWritableAsNull() throws Exception {
        TableField secondaryParent = PowerTopologyNode.class.getDeclaredField("secondaryParentId").getAnnotation(TableField.class);
        TableField secondarySwitch = PowerTopologyNode.class.getDeclaredField("secondarySwitchSpecificationId").getAnnotation(TableField.class);

        assertEquals(FieldStrategy.ALWAYS, secondaryParent.updateStrategy());
        assertEquals(FieldStrategy.ALWAYS, secondarySwitch.updateStrategy());
    }
}
