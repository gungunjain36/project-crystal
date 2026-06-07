package com.crystal.msresults.repository;

import com.crystal.msresults.model.entity.ScanIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ScanIssueRepository extends JpaRepository<ScanIssue, Long> {

    @Query("SELECT i FROM ScanIssue i WHERE i.scanResult.jobId = :jobId")
    List<ScanIssue> findByScanResultJobId(@Param("jobId") String jobId);

    @Query("SELECT i.severity, COUNT(i) FROM ScanIssue i GROUP BY i.severity")
    List<Object[]> countBySeverity();
}
