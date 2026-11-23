package com.example.transporterlanes.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** One shared solver instance, many threads: results must match the sequential results exactly. */
class ExactCombinationSolverConcurrencyTest {

    private static final long SEED = 424_242L;
    private static final int PROBLEMS = 300;
    private static final int THREADS = 16;
    private static final int ROUNDS = 10;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void concurrentSolvesOnASharedInstanceMatchSequentialResults() throws Exception {
        ExactCombinationSolver shared = new ExactCombinationSolver();
        List<OptimizationProblem> problems = randomProblems();
        List<AssignmentSolution> sequential = problems.stream().map(shared::solve).toList();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch startTogether = new CountDownLatch(1);
            List<Future<AssignmentSolution>> futures = new ArrayList<>();
            List<Integer> problemIndex = new ArrayList<>();
            for (int round = 0; round < ROUNDS; round++) {
                for (int i = 0; i < problems.size(); i++) {
                    OptimizationProblem problem = problems.get(i);
                    Callable<AssignmentSolution> task = () -> {
                        startTogether.await();
                        return shared.solve(problem);
                    };
                    futures.add(pool.submit(task));
                    problemIndex.add(i);
                }
            }
            startTogether.countDown();

            for (int f = 0; f < futures.size(); f++) {
                int i = problemIndex.get(f);
                assertThat(futures.get(f).get()).as("problem %d, task %d", i, f).isEqualTo(sequential.get(i));
            }
            assertThat(futures).hasSize(PROBLEMS * ROUNDS);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Fully quoted, so every problem is feasible; narrow price ranges so tie-breaking is exercised. */
    private static List<OptimizationProblem> randomProblems() {
        Random random = new Random(SEED);
        List<OptimizationProblem> problems = new ArrayList<>();
        for (int p = 0; p < PROBLEMS; p++) {
            int laneCount = 1 + random.nextInt(8);
            int transporterCount = 1 + random.nextInt(10);
            List<Long> lanes = new ArrayList<>();
            List<Long> transporters = new ArrayList<>();
            List<Quote> quotes = new ArrayList<>();
            for (long l = 1; l <= laneCount; l++) {
                lanes.add(l);
            }
            for (long t = 1; t <= transporterCount; t++) {
                transporters.add(t);
                for (long l = 1; l <= laneCount; l++) {
                    quotes.add(new Quote(l, t, 1 + random.nextInt(20)));
                }
            }
            problems.add(new OptimizationProblem(lanes, transporters, quotes, 1 + random.nextInt(transporterCount)));
        }
        return problems;
    }
}
