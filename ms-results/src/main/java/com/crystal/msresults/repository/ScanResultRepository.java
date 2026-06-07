package com.crystal.msresults.repository;

import com.crystal.msresults.model.entity.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, UUID> {

    Optional<ScanResult> findByJobId(String jobId);

    boolean existsByJobId(String jobId);
}
