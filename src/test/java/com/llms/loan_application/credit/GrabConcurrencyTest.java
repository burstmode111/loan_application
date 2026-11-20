package com.llms.loan_application.credit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
class GrabConcurrencyTest {

    private static final int LOCKLESS = 0;
    private static final int DB_ATOMIC = 1;
    private static final int REDIS_LUA = 2;

    @Autowired CreditLineService service;
    @Autowired CreditLineRedisGrabber grabber;
    @Autowired CreditLineRepository lines;
    @Autowired GrabRecordRepository records;

    @Test
    void noLockOversell() throws Exception {
        run(LOCKLESS);
    }

    @Test
    void atomicNoOversell() throws Exception {
        run(DB_ATOMIC);
    }

    @Test
    void redisNoOversell() throws Exception {
        run(REDIS_LUA);
    }

    private void run(int mode) throws Exception {
        String label = switch (mode) {
            case LOCKLESS -> "1-lockless";
            case DB_ATOMIC -> "2-db-atomic";
            default -> "3-redis-lua";
        };

        int total = 100;
        int threads = 500;
        Long id = service.create(label, total);
        if (mode == REDIS_LUA) {
            grabber.init(id);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    switch (mode) {
                        case LOCKLESS -> service.grab(id, 1);
                        case DB_ATOMIC -> service.grabAtomic(id, 1);
                        default -> grabber.grab(id, 1);
                    }
                    ok.incrementAndGet();
                } catch (Exception e) {
                    if (e.getClass().getSimpleName().contains("ResponseStatus")) {
                        conflict.incrementAndGet();
                    } else {
                        error.incrementAndGet();
                        e.printStackTrace();
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();
        long cost = System.currentTimeMillis() - t0;

        int remaining = lines.findById(id).orElseThrow().getRemainingLimit();
        long sold = total - remaining;

        System.out.println("======== RESULT " + label + " ========");
        System.out.println("cost(ms)      = " + cost);
        System.out.println("ok            = " + ok.get());
        System.out.println("conflict      = " + conflict.get());
        System.out.println("error         = " + error.get());
        System.out.println("grab_record   = " + records.countByCreditLineId(id));
        System.out.println("remaining     = " + remaining);
        System.out.println("deducted      = " + sold);
        System.out.println("oversell      = " + (ok.get() - sold));
    }
}