package com.tripagent.backend.repository;

import com.tripagent.backend.entity.Dataset;
import com.tripagent.backend.entity.enums.DatasetSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

  Optional<Dataset> findByName(String name);

  List<Dataset> findAllByOrderByDatasetIdAsc();

  List<Dataset> findBySourceOrderByDatasetIdAsc(DatasetSource source);

  List<Dataset> findByEnabledTrueOrderByDatasetIdAsc();

  boolean existsByName(String name);
}
