package com.tripagent.backend.repository;

import com.tripagent.backend.entity.DatasetSample;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DatasetSampleRepository extends JpaRepository<DatasetSample, Long> {

  List<DatasetSample> findByDatasetDatasetIdOrderBySortOrderAsc(Long datasetId);

  long countByDatasetDatasetId(Long datasetId);

  @Transactional
  void deleteByDatasetDatasetId(Long datasetId);
}
