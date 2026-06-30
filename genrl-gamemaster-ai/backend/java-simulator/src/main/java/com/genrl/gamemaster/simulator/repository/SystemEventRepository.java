package com.genrl.gamemaster.simulator.repository;

import com.genrl.gamemaster.simulator.domain.SystemEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SystemEventRepository extends JpaRepository<SystemEvent, UUID> {

    List<SystemEvent> findTop100ByOrderByCreatedAtDesc();
}
