package com.wanyuecity.energy.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.platform.entity.SyncLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SyncLockMapper extends BaseMapper<SyncLock> {

    /**
     * Pessimistic lock — the single row is locked for the duration of the
     * enclosing transaction. Returns the held value so the caller can detect
     * "another instance holds the lock" without another round-trip.
     */
    @Select("SELECT * FROM sync_lock WHERE id = 1 FOR UPDATE")
    SyncLock lockRow();

    @Update("UPDATE sync_lock SET holder = #{holder}, acquired_at = NOW(), note = #{note} WHERE id = 1")
    int mark(@Param("holder") String holder, @Param("note") String note);

    @Update("UPDATE sync_lock SET holder = NULL, acquired_at = NULL, note = NULL WHERE id = 1")
    int release();
}

