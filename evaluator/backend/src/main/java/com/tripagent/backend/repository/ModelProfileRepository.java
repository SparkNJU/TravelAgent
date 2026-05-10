package com.tripagent.backend.repository;

import com.tripagent.backend.entity.ModelProfile;
import com.tripagent.backend.entity.enums.ModelRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelProfileRepository extends JpaRepository<ModelProfile, Long> {

  Optional<ModelProfile> findByModelId(String modelId);

  List<ModelProfile> findByEnabledTrue();

  List<ModelProfile> findByEnabledTrueAndRoleIn(List<ModelRole> roles);

  List<ModelProfile> findByModelProfileIdIn(List<Long> ids);
}
