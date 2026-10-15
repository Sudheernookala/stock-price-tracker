package com.solactive.stocktracker.service;

import com.solactive.stocktracker.dto.FileImportResponse;
import java.util.List;

/**
 * Read access to the stored processing records.
 */
public interface FileImportQueryService {

    /**
     * Lists the most recent processing records.
     *
     * @param limit maximum number of records
     * @return the most recent records, newest first, without their error summary
     */
    List<FileImportResponse> findRecent(int limit);

    /**
     * Returns one processing record.
     *
     * @param id record id
     * @return the record including its error summary
     * @throws com.solactive.stocktracker.exception.ResourceNotFoundException when unknown
     */
    FileImportResponse findById(long id);
}
