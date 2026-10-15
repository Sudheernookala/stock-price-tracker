package com.solactive.stocktracker.entity;

import com.solactive.stocktracker.enums.RejectReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Number of rows of a processed file rejected for one reason.
 */
@Entity
@Table(name = "file_import_error_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "fileImport")
public class FileImportErrorSummary {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The file these rows belong to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_import_id", nullable = false)
    private FileImport fileImport;

    /** Why the rows were rejected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 32)
    private RejectReason code;

    /** How many rows were rejected for this reason. */
    @Column(name = "row_count", nullable = false)
    private long rowCount;

    /** Line number of the first such row. */
    @Column(name = "first_line", nullable = false)
    private long firstLine;

    /** Message of the first such row. */
    @Column(name = "example", nullable = false, length = 500)
    private String example;
}
