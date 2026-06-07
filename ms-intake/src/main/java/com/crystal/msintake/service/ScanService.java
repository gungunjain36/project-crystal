package com.crystal.msintake.service;

import com.crystal.msintake.model.dto.ScanRequest;
import com.crystal.msintake.model.dto.ScanResponse;

public interface ScanService {

    ScanResponse initiateScan(ScanRequest request);

    ScanResponse getScanStatus(String jobId);
}
