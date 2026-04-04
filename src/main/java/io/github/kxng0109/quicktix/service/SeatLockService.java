package io.github.kxng0109.quicktix.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Service responsible for managing distributed locks for seat reservations using Redis.
 * <p>
 * This service prevents race conditions during high-concurrency checkout phases by
 * ensuring that only one user can hold a specific seat at any given time across all
 * application instances. It utilizes Redis's atomic SETNX operation via Spring's
 * {@link StringRedisTemplate}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockService {

	private final StringRedisTemplate redisTemplate;
	/**
	 * The standard prefix applied to all Redis keys related to seat locks.
	 */
	private static final String LOCK_PREFIX = "seat:lock:";
	/**
	 * The Time-To-Live (TTL) for a seat lock. If the lock is not manually released,
	 * Redis will automatically evict it after this duration to prevent deadlocks.
	 */
	private static final long LOCK_TTL_MINUTES = 15;

	/**
	 * Attempts to acquire an exclusive distributed lock for a specific seat.
	 * <p>
	 * This method uses {@code setIfAbsent} to atomically create a key in Redis.
	 * If the key already exists, another user currently holds the lock, and this
	 * method will immediately return false without modifying the existing lock.
	 *
	 * @param seatId    The unique identifier of the seat to lock.
	 * @param userEmail The email address of the user attempting to acquire the lock.
	 *                  This is stored as the value to verify ownership during release.
	 * @return {@code true} if the lock was successfully acquired; {@code false} if the seat is already locked.
	 */
	public boolean acquireLock(Long seatId, String userEmail) {
		String lockKey = LOCK_PREFIX + seatId;

		Boolean acquired = redisTemplate.opsForValue()
		                                .setIfAbsent(lockKey, userEmail, Duration.ofMinutes(LOCK_TTL_MINUTES));

		if (acquired.equals(Boolean.TRUE)) {
			log.debug("Lock acquired for seat {} by {}", seatId, userEmail);
			return true;
		}

		log.warn("Seat {} is already locked", seatId);
		return false;
	}

	/**
	 * Releases an existing distributed lock for a specific seat.
	 * <p>
	 * This method verifies the ownership of the lock before deletion. The lock will
	 * only be removed if the provided {@code userEmail} matches the value currently
	 * stored in Redis. This prevents malicious or accidental releases of locks held
	 * by other users.
	 *
	 * @param seatId    The unique identifier of the locked seat.
	 * @param userEmail The email address of the user attempting to release the lock.
	 */
	public void releaseLock(Long seatId, String userEmail) {
		String lockKey = LOCK_PREFIX + seatId;

		// Use Lua script for atomic check-and-delete
		String luaScript =
				"if redis.call('get', KEYS[1]) == ARGV[1] then " +
						"return redis.call('del', KEYS[1]) " +
						"else " +
						"return 0 " +
						"end";

		redisTemplate.execute(
				new DefaultRedisScript<>(luaScript, Long.class),
				Collections.singletonList(lockKey),
				userEmail
		);

		log.debug("Lock release attempted for seat {} by {}", seatId, userEmail);
	}

	/**
	 * Forcibly releases a distributed lock for a specific seat, bypassing all ownership validation.
	 * <p>
	 * <b>WARNING:</b> This is an administrative "God-Mode" operation. Unlike {@link #releaseLock(Long, String)},
	 * this method does not check if the current user owns the lock. It directly deletes the key from Redis.
	 * This is designed to resolve infrastructure anomalies where a lock is orphaned due to a network partition
	 * or application crash.
	 * </p>
	 *
	 * @param seatId The unique identifier of the seat whose lock must be destroyed.
	 */
	public void forceReleaseLock(Long seatId) {
		String lockKey = LOCK_PREFIX + seatId;
		redisTemplate.delete(lockKey);

		log.info("Redis lock for seat {} forcefully release", seatId);
	}
}
