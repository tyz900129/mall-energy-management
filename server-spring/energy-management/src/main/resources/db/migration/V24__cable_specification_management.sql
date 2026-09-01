CREATE TABLE cable_specification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  spec_code VARCHAR(64) NOT NULL,
  cable_model VARCHAR(100) NOT NULL,
  specification VARCHAR(160) NOT NULL,
  rated_current_a DECIMAL(12,2),
  rated_power_kw DECIMAL(12,2),
  status TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cable_spec_code (spec_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电缆规格参数';

CREATE TABLE stall_engineering_cable (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stall_engineering_profile_id BIGINT NOT NULL,
  cable_specification_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_profile_cable_spec (stall_engineering_profile_id, cable_specification_id),
  CONSTRAINT fk_stall_cable_profile FOREIGN KEY (stall_engineering_profile_id) REFERENCES stall_engineering_profile(id) ON DELETE CASCADE,
  CONSTRAINT fk_stall_cable_spec FOREIGN KEY (cable_specification_id) REFERENCES cable_specification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='铺位电缆配置';

INSERT INTO cable_specification(spec_code,cable_model,specification,status,remark)
SELECT CONCAT('LEGACY-',id), COALESCE(NULLIF(cable_model,''),'历史电缆'), COALESCE(NULLIF(cable_model,''),'待补充规格参数'), 1, '由原铺位工程资料自动迁移，请管理员补充额定参数'
FROM stall_engineering_profile WHERE cable_quantity>0 OR (cable_model IS NOT NULL AND cable_model<>'');

INSERT INTO stall_engineering_cable(stall_engineering_profile_id,cable_specification_id,quantity)
SELECT p.id,s.id,GREATEST(COALESCE(p.cable_quantity,1),1)
FROM stall_engineering_profile p JOIN cable_specification s ON s.spec_code=CONCAT('LEGACY-',p.id)
WHERE p.cable_quantity>0 OR (p.cable_model IS NOT NULL AND p.cable_model<>'');
