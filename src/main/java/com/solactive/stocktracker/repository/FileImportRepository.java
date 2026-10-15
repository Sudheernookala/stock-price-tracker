package com.solactive.stocktracker.repository;

import com.solactive.stocktracker.entity.FileImport;
import com.solactive.stocktracker.enums.ImportStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link FileImport} records.
 */
public interface FileImportRepository extends JpaRepository<FileImport, Long> {

    /**
     * Checks whether identical content was already processed with one of the given statuses.
     *
     * @param checksum SHA-256 of the content
     * @param statuses statuses that count as "already processed"
     * @return {@code true} if such a record exists
     */
    boolean existsByChecksumAndStatusIn(String checksum, Collection<ImportStatus> statuses);

    /**
     * Returns the most recent records, newest first.
     *
     * @param pageable limits the number of records returned
     * @return records ordered by start time, newest first
     */
    List<FileImport> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);
}
