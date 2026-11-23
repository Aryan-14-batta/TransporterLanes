package com.example.transporterlanes.service;

import com.example.transporterlanes.domain.Lane;
import com.example.transporterlanes.domain.LaneQuote;
import com.example.transporterlanes.domain.Transporter;
import com.example.transporterlanes.dto.ErrorResponse.FieldError;
import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.LaneQuoteRequest;
import com.example.transporterlanes.dto.TransporterRequest;
import com.example.transporterlanes.exception.InvalidInputException;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.TransporterRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransporterInputService {

    private final InputDataValidator validator;
    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final LaneQuoteRepository laneQuoteRepository;

    public TransporterInputService(InputDataValidator validator, LaneRepository laneRepository,
                                   TransporterRepository transporterRepository,
                                   LaneQuoteRepository laneQuoteRepository) {
        this.validator = validator;
        this.laneRepository = laneRepository;
        this.transporterRepository = transporterRepository;
        this.laneQuoteRepository = laneQuoteRepository;
    }

    /**
     * Replaces the stored dataset with the request's contents, all-or-nothing.
     *
     * <p>The whole request is validated before the database is touched. Deletion and insertion then run in a single
     * transaction, so any failure in either phase rolls back and leaves the previous dataset intact. Each phase is
     * flushed explicitly so that database errors surface inside this method (and its transaction), not at commit.
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceDataset(InputRequest request) {
        List<FieldError> errors = validator.validate(request);
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }

        // Children first, to satisfy the foreign keys.
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();

        Map<Long, Lane> lanes = byId(laneRepository.saveAllAndFlush(request.lanes().stream()
                .map(lane -> new Lane(lane.id(), lane.origin(), lane.destination()))
                .toList()), Lane::getId);
        Map<Long, Transporter> transporters = byId(transporterRepository.saveAllAndFlush(request.transporters().stream()
                .map(transporter -> new Transporter(transporter.id(), transporter.name()))
                .toList()), Transporter::getId);

        List<LaneQuote> quotes = new ArrayList<>();
        for (TransporterRequest transporter : request.transporters()) {
            for (LaneQuoteRequest quote : transporter.laneQuotes()) {
                quotes.add(new LaneQuote(lanes.get(quote.laneId()), transporters.get(transporter.id()), quote.quote()));
            }
        }
        laneQuoteRepository.saveAllAndFlush(quotes);
    }

    /** saveAll returns the managed instances; quotes must reference those, not the unmanaged originals. */
    private static <T> Map<Long, T> byId(List<T> saved, Function<T, Long> id) {
        return saved.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
