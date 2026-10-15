package com.solactive.stocktracker.service.impl;

import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.exception.ResourceNotFoundException;
import com.solactive.stocktracker.repository.FileImportRepository;
import com.solactive.stocktracker.service.FileImportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default {@link FileImportQueryService}. Lists leave out the error summary to avoid loading it
 * for every record; it is returned when a single record is requested.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileImportQueryServiceImpl implements FileImportQueryService {

    private final FileImportRepository repository;

    @Override
    public List<FileImportResponse> findRecent(int limit) {
        return repository.findAllByOrderByStartedAtDescIdDesc(PageRequest.ofSize(limit)).stream()
                .map(record -> FileImportResponse.from(record, false))
                .toList();
    }

    @Override
    public FileImportResponse findById(long id) {
        return repository.findById(id)
                .map(record -> FileImportResponse.from(record, true))
                .orElseThrow(() -> ResourceNotFoundException.fileImport(id));
    }
}
