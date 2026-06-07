package com.crystal.msresults.service;

import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultMessage;
import com.crystal.msresults.model.dto.ScanResultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ScanResultService {

    void persistResult(ScanResultMessage message);

    ScanResultResponse getResultByJobId(String jobId);

    Page<ScanResultResponse> getAllResults(Pageable pageable);

    List<IssueDto> getIssuesByJobId(String jobId);

    Map<String, Long> getSeverityStats();
}
