package com.example.transporterlanes.service;

import static com.example.transporterlanes.TestRequests.input;
import static com.example.transporterlanes.TestRequests.lane;
import static com.example.transporterlanes.TestRequests.quote;
import static com.example.transporterlanes.TestRequests.transporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.transporterlanes.TestRequests;
import com.example.transporterlanes.domain.Lane;
import com.example.transporterlanes.domain.LaneQuote;
import com.example.transporterlanes.domain.Transporter;
import com.example.transporterlanes.exception.InvalidInputException;
import com.example.transporterlanes.repository.LaneQuoteRepository;
import com.example.transporterlanes.repository.LaneRepository;
import com.example.transporterlanes.repository.TransporterRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransporterInputServiceTest {

    @Mock
    private LaneRepository laneRepository;
    @Mock
    private TransporterRepository transporterRepository;
    @Mock
    private LaneQuoteRepository laneQuoteRepository;
    @Captor
    private ArgumentCaptor<List<LaneQuote>> savedQuotes;

    private TransporterInputService service;

    @BeforeEach
    void setUp() {
        service = new TransporterInputService(
                new InputDataValidator(), laneRepository, transporterRepository, laneQuoteRepository);
    }

    @Test
    void invalidInputIsRejectedBeforeAnyDatabaseAccess() {
        var invalid = input(List.of(lane(1)), List.of(transporter(1, quote(99, 5))));

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> service.replaceDataset(invalid))
                .satisfies(e -> assertThat(e.getErrors()).isNotEmpty());

        verifyNoInteractions(laneRepository, transporterRepository, laneQuoteRepository);
    }

    @Test
    void deletesChildrenFirstThenInsertsParentsBeforeQuotes() {
        when(laneRepository.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));
        when(transporterRepository.saveAllAndFlush(anyList())).thenAnswer(call -> call.getArgument(0));

        service.replaceDataset(TestRequests.twoLaneDataset());

        InOrder order = inOrder(laneQuoteRepository, transporterRepository, laneRepository);
        order.verify(laneQuoteRepository).deleteAllInBatch();
        order.verify(transporterRepository).deleteAllInBatch();
        order.verify(laneRepository).deleteAllInBatch();
        order.verify(laneRepository).saveAllAndFlush(anyList());
        order.verify(transporterRepository).saveAllAndFlush(anyList());
        order.verify(laneQuoteRepository).saveAllAndFlush(anyList());
    }

    @Test
    void quotesReferenceTheManagedEntitiesReturnedBySave() {
        // Simulate JPA merge: save returns different (managed) instances than the ones passed in.
        Lane managedLane1 = new Lane(1L, "Origin 1", "Destination 1");
        Lane managedLane2 = new Lane(2L, "Origin 2", "Destination 2");
        Transporter managedT10 = new Transporter(10L, "Transporter 10");
        Transporter managedT20 = new Transporter(20L, "Transporter 20");
        when(laneRepository.saveAllAndFlush(anyList())).thenReturn(List.of(managedLane1, managedLane2));
        when(transporterRepository.saveAllAndFlush(anyList())).thenReturn(List.of(managedT10, managedT20));

        service.replaceDataset(TestRequests.twoLaneDataset());

        verify(laneQuoteRepository).saveAllAndFlush(savedQuotes.capture());
        assertThat(savedQuotes.getValue()).hasSize(2);
        LaneQuote first = savedQuotes.getValue().get(0);
        LaneQuote second = savedQuotes.getValue().get(1);
        assertThat(first.getLane()).isSameAs(managedLane1);
        assertThat(first.getTransporter()).isSameAs(managedT10);
        assertThat(first.getQuote()).isEqualTo(100);
        assertThat(second.getLane()).isSameAs(managedLane2);
        assertThat(second.getTransporter()).isSameAs(managedT20);
        assertThat(second.getQuote()).isEqualTo(200);
    }
}
