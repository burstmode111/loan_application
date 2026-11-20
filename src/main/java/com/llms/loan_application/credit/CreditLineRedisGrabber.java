package com.llms.loan_application.credit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CreditLineRedisGrabber {

    /**
     * KEYS[1] = credit line key, ARGV[1] = amount to grab.
     * Check and decrement happen inside one script, so no other command can slip in between.
     */
    private static final DefaultRedisScript<Long> GRAB_SCRIPT = new DefaultRedisScript<>(
            "local cur = tonumber(redis.call('get', KEYS[1]) or '0') " +
            "local need = tonumber(ARGV[1]) " +
            "if cur < need then return 0 end " +
            "redis.call('decrby', KEYS[1], need) " +
            "return 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final CreditLineRepository lines;
    private final GrabRecordRepository records;

    public CreditLineRedisGrabber(StringRedisTemplate redis,
                                  CreditLineRepository lines,
                                  GrabRecordRepository records) {
        this.redis = redis;
        this.lines = lines;
        this.records = records;
    }

    /** Load the current balance from the database into Redis. Run once before the load test. */
    public void init(Long id) {
        CreditLine line = lines.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "credit line not found"));
        redis.opsForValue().set(key(id), String.valueOf(line.getRemainingLimit()));
    }

    public void grab(Long id, int amount) {
        Long allowed = redis.execute(GRAB_SCRIPT, List.of(key(id)), String.valueOf(amount));
        if (allowed == null || allowed == 0L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no limit");
        }

        // Redis said yes; persist it. If the database disagrees, Redis and DB have drifted apart.
        int updated = lines.deduct(id, amount);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no limit (database)");
        }
        records.save(new GrabRecord(id, amount));
    }

    private String key(Long id) {
        return "credit:line:" + id;
    }
}