package com.solactive.stocktracker.entity;

import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.enums.RejectReason;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Processing record of one file: its status, row counts, timing and any problems found.
 */
@Entity
@Table(name = "file_import")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "errorSummary")
public class FileImport {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** File name as supplied (no directories). */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** How the file reached the application. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ImportSource source;

    /** Current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportStatus status;

    /** Hex SHA-256 of the content; {@code null} if the file could not be read at all. */
    @Column(name = "checksum", length = 64, columnDefinition = "bpchar(64)")
    private String checksum;

    /** Data rows read. */
    @Column(name = "total_rows", nullable = false)
    private long totalRows;

    /** Rows used in the high/low calculation. */
    @Column(name = "accepted_rows", nullable = false)
    private long acceptedRows;

    /** Rows rejected by validation. */
    @Column(name = "rejected_rows", nullable = false)
    private long rejectedRows;

    /** File-level problem: why the file failed, or a note such as "already processed". */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** When processing started. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** When processing finished; {@code null} while processing. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Every rejected row counted by reason, largest group first. */
    @Builder.Default
    @OneToMany(mappedBy = "fileImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowCount DESC, firstLine ASC")
    private List<FileImportErrorSummary> errorSummary = new ArrayList<>();

    /**
     * Adds the count of rows rejected for one reason and links it to this file.
     *
     * @param code      reason code
     * @param rowCount  rows rejected for this reason
     * @param firstLine line number of the first such row
     * @param example   message of the first such row
     */
    public void addErrorGroup(RejectReason code, long rowCount, long firstLine, String example) {
        errorSummary.add(FileImportErrorSummary.builder().fileImport(this).code(code).rowCount(rowCount)
                .firstLine(firstLine).example(example).build());
    }
}
