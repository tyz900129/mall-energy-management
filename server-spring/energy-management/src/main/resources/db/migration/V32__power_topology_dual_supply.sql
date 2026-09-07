ALTER TABLE power_topology_node
  ADD COLUMN secondary_parent_id BIGINT NULL AFTER parent_id,
  ADD COLUMN secondary_switch_specification_id BIGINT NULL AFTER switch_specification_id,
  ADD KEY idx_power_topology_secondary_parent (secondary_parent_id),
  ADD KEY idx_power_topology_secondary_switch (secondary_switch_specification_id),
  ADD CONSTRAINT fk_power_topology_secondary_parent
    FOREIGN KEY (secondary_parent_id) REFERENCES power_topology_node(id),
  ADD CONSTRAINT fk_power_topology_secondary_switch
    FOREIGN KEY (secondary_switch_specification_id) REFERENCES power_switch_specification(id);

