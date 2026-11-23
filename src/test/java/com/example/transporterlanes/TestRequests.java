package com.example.transporterlanes;

import com.example.transporterlanes.dto.InputRequest;
import com.example.transporterlanes.dto.LaneQuoteRequest;
import com.example.transporterlanes.dto.LaneRequest;
import com.example.transporterlanes.dto.TransporterRequest;
import java.util.List;

/** Small, readable request builders shared by the application-layer tests. */
public final class TestRequests {

    private TestRequests() {
    }

    public static LaneRequest lane(long id) {
        return new LaneRequest(id, "Origin " + id, "Destination " + id);
    }

    public static LaneQuoteRequest quote(long laneId, long amount) {
        return new LaneQuoteRequest(laneId, amount);
    }

    public static TransporterRequest transporter(long id, LaneQuoteRequest... quotes) {
        return new TransporterRequest(id, "Transporter " + id, List.of(quotes));
    }

    public static InputRequest input(List<LaneRequest> lanes, List<TransporterRequest> transporters) {
        return new InputRequest(lanes, transporters);
    }

    /**
     * Two lanes, two transporters. K = 1 is infeasible (each covers one lane only); K = 2 costs 100 + 200.
     */
    public static InputRequest twoLaneDataset() {
        return input(
                List.of(lane(1), lane(2)),
                List.of(transporter(10, quote(1, 100)), transporter(20, quote(2, 200))));
    }
}
