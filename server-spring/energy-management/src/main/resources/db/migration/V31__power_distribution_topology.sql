CREATE TABLE power_topology_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NULL,
  node_code VARCHAR(64) NOT NULL,
  node_name VARCHAR(120) NOT NULL,
  node_type VARCHAR(64) NOT NULL,
  switch_specification_id BIGINT NULL,
  rated_voltage_v DECIMAL(12,2) NULL,
  rated_current_a DECIMAL(12,2) NULL,
  rated_power_kw DECIMAL(12,3) NULL,
  location VARCHAR(200) NULL,
  stall_id BIGINT NULL,
  platform_device_id VARCHAR(100) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_power_topology_node_code (node_code),
  KEY idx_power_topology_parent (parent_id,sort_order),
  KEY idx_power_topology_switch (switch_specification_id),
  KEY idx_power_topology_stall (stall_id),
  CONSTRAINT fk_power_topology_parent FOREIGN KEY (parent_id) REFERENCES power_topology_node(id),
  CONSTRAINT fk_power_topology_switch FOREIGN KEY (switch_specification_id) REFERENCES power_switch_specification(id),
  CONSTRAINT fk_power_topology_stall FOREIGN KEY (stall_id) REFERENCES stall(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配电拓扑节点';

INSERT INTO business_dictionary(dict_type,item_value,item_label,sort_order,status,remark) VALUES
 ('POWER_NODE_TYPE','TRANSFORMER','变压器',10,1,'配电拓扑默认节点类型'),
 ('POWER_NODE_TYPE','DISTRIBUTION_SWITCH','配电开关',20,1,'配电拓扑默认节点类型'),
 ('POWER_NODE_TYPE','DISTRIBUTION_BOX','配电箱',30,1,'配电拓扑默认节点类型'),
 ('POWER_NODE_TYPE','TERMINAL_BOX','终端箱',40,1,'配电拓扑默认节点类型'),
 ('POWER_NODE_TYPE','ELECTRICAL_DEVICE','用电设备',50,1,'配电拓扑默认节点类型');
