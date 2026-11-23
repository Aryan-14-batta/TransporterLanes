package com.example.transporterlanes.optimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loads the assignment's CSV (header "Trade Lane,T1,...,Tn"; rows "Lane i,q1,...,qn") as a problem. */
final class CsvProblems {

    static final String ASSIGNMENT_DATASET = "transporter_assignment_data.csv";

    private CsvProblems() {
    }

    static OptimizationProblem load(String resource, int maxTransporters) {
        List<String[]> rows = readRows(resource);
        String[] header = rows.get(0);
        List<Long> transporterIds = new ArrayList<>();
        for (int column = 1; column < header.length; column++) {
            transporterIds.add(Long.parseLong(header[column].trim().substring(1))); // "T3" -> 3
        }

        List<Long> laneIds = new ArrayList<>();
        List<Quote> quotes = new ArrayList<>();
        for (String[] row : rows.subList(1, rows.size())) {
            long laneId = Long.parseLong(row[0].trim().replace("Lane", "").trim()); // "Lane 3" -> 3
            laneIds.add(laneId);
            for (int column = 1; column < row.length; column++) {
                String cell = row[column].trim();
                if (!cell.isEmpty()) {
                    quotes.add(new Quote(laneId, transporterIds.get(column - 1), Long.parseLong(cell)));
                }
            }
        }
        return new OptimizationProblem(laneIds, transporterIds, quotes, maxTransporters);
    }

    private static List<String[]> readRows(String resource) {
        var stream = Objects.requireNonNull(CsvProblems.class.getResourceAsStream(resource), resource + " not found");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).map(line -> line.split(",", -1)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
