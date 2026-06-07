package com.crystal.msalert.service;

import com.crystal.msalert.model.dto.ScanResultMessage;

public interface AlertService {

    /**
     * Process a scan result message and send alerts for high/critical severity issues.
     *
     * @param message the scan result message
     * @return the count of high/critical issues found
     */
    int processResult(ScanResultMessage message);
}
