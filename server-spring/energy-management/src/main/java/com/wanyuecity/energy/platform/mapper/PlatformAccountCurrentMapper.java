package com.wanyuecity.energy.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyuecity.energy.platform.entity.PlatformAccountCurrent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformAccountCurrentMapper extends BaseMapper<PlatformAccountCurrent> {

    /**
     * Plain DELETE (not TRUNCATE) so the operation participates in the
     * surrounding transaction. TRUNCATE in InnoDB performs an implicit
     * commit which would break the {@code replaceAccounts} atomicity.
     */
    @Delete("DELETE FROM platform_account_current")
    int deleteAll();

    @Delete("DELETE FROM platform_account_current WHERE account_number = #{accountNumber}")
    int deleteByAccountNumber(@Param("accountNumber") String accountNumber);
}

