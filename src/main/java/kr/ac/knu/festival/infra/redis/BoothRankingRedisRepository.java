package kr.ac.knu.festival.infra.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
public class BoothRankingRedisRepository {

    private static final String BOOTH_LIKES_KEY = "booth:likes";
    private static final String BOOTH_WAITING_COUNT_KEY = "booth:waiting-count";
    private static final String BOOTH_LIKED_KEY_PREFIX = "booth:liked:";

    private final StringRedisTemplate redisTemplate;

    public BoothRankingRedisRepository(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public RedisChangeResult addLike(Long boothId, String anonymousIdHash) {
        try {
            if (redisTemplate == null) {
                return RedisChangeResult.unavailableResult();
            }
            Long added = redisTemplate.opsForSet().add(likedKey(boothId), anonymousIdHash);
            if (added != null && added > 0) {
                incrementLike(boothId);
                return RedisChangeResult.changedResult();
            }
            return RedisChangeResult.unchangedResult();
        } catch (Exception e) {
            log.warn("Redis addLike failed. boothId={}", boothId, e);
            return RedisChangeResult.unavailableResult();
        }
    }

    public RedisChangeResult removeLike(Long boothId, String anonymousIdHash) {
        try {
            if (redisTemplate == null) {
                return RedisChangeResult.unavailableResult();
            }
            Long removed = redisTemplate.opsForSet().remove(likedKey(boothId), anonymousIdHash);
            if (removed != null && removed > 0) {
                decrementLike(boothId);
                return RedisChangeResult.changedResult();
            }
            return RedisChangeResult.unchangedResult();
        } catch (Exception e) {
            log.warn("Redis removeLike failed. boothId={}", boothId, e);
            return RedisChangeResult.unavailableResult();
        }
    }

    public void incrementLike(Long boothId) {
        incrementScore(BOOTH_LIKES_KEY, boothId, 1);
    }

    public void decrementLike(Long boothId) {
        incrementScore(BOOTH_LIKES_KEY, boothId, -1);
        clampScoreAtZero(BOOTH_LIKES_KEY, boothId);
    }

    public void incrementWaitingCount(Long boothId) {
        incrementScore(BOOTH_WAITING_COUNT_KEY, boothId, 1);
    }

    public void decrementWaitingCount(Long boothId) {
        incrementScore(BOOTH_WAITING_COUNT_KEY, boothId, -1);
        clampScoreAtZero(BOOTH_WAITING_COUNT_KEY, boothId);
    }

    public void decrementWaitingCount(Long boothId, long count) {
        if (count <= 0) {
            return;
        }
        incrementScore(BOOTH_WAITING_COUNT_KEY, boothId, -count);
        clampScoreAtZero(BOOTH_WAITING_COUNT_KEY, boothId);
    }

    public void setLikes(Map<Long, Integer> likeCounts) {
        replaceScores(BOOTH_LIKES_KEY, likeCounts);
    }

    public void setWaitingCounts(Map<Long, Long> waitingCounts) {
        Map<Long, Integer> values = new HashMap<>();
        waitingCounts.forEach((boothId, count) -> values.put(boothId, Math.toIntExact(count)));
        replaceScores(BOOTH_WAITING_COUNT_KEY, values);
    }

    public Map<Long, Integer> getLikeCounts(Collection<Long> boothIds) {
        return getScores(BOOTH_LIKES_KEY, boothIds);
    }

    public Map<Long, Integer> getWaitingCounts(Collection<Long> boothIds) {
        return getScores(BOOTH_WAITING_COUNT_KEY, boothIds);
    }

    public Map<Long, Integer> getAllLikeCounts() {
        try {
            if (redisTemplate == null) {
                return Collections.emptyMap();
            }
            Set<String> members = redisTemplate.opsForZSet().range(BOOTH_LIKES_KEY, 0, -1);
            if (members == null || members.isEmpty()) {
                return Collections.emptyMap();
            }
            return getScores(BOOTH_LIKES_KEY, members.stream().map(Long::valueOf).toList());
        } catch (Exception e) {
            log.warn("Redis getAllLikeCounts failed", e);
            return Collections.emptyMap();
        }
    }

    public int getLikeCount(Long boothId, int fallback) {
        try {
            if (redisTemplate == null) {
                return fallback;
            }
            Double score = redisTemplate.opsForZSet().score(BOOTH_LIKES_KEY, boothId.toString());
            return score == null ? fallback : score.intValue();
        } catch (Exception e) {
            log.warn("Redis getLikeCount failed. boothId={}", boothId, e);
            return fallback;
        }
    }

    private void incrementScore(String key, Long boothId, long delta) {
        try {
            if (redisTemplate == null) {
                return;
            }
            redisTemplate.opsForZSet().incrementScore(key, boothId.toString(), delta);
        } catch (Exception e) {
            log.warn("Redis incrementScore failed. key={}, boothId={}, delta={}", key, boothId, delta, e);
        }
    }

    private void replaceScores(String key, Map<Long, Integer> values) {
        try {
            if (redisTemplate == null) {
                return;
            }
            redisTemplate.delete(key);
            values.forEach((boothId, score) -> redisTemplate.opsForZSet().add(key, boothId.toString(), score));
        } catch (Exception e) {
            log.warn("Redis replaceScores failed. key={}", key, e);
        }
    }

    private Map<Long, Integer> getScores(String key, Collection<Long> boothIds) {
        try {
            if (redisTemplate == null) {
                return Collections.emptyMap();
            }
            Map<Long, Integer> scores = new HashMap<>();
            for (Long boothId : boothIds) {
                Double score = redisTemplate.opsForZSet().score(key, boothId.toString());
                if (score != null) {
                    scores.put(boothId, score.intValue());
                }
            }
            return scores;
        } catch (Exception e) {
            log.warn("Redis getScores failed. key={}", key, e);
            return Collections.emptyMap();
        }
    }

    private void clampScoreAtZero(String key, Long boothId) {
        try {
            if (redisTemplate == null) {
                return;
            }
            Double score = redisTemplate.opsForZSet().score(key, boothId.toString());
            if (score != null && score < 0) {
                redisTemplate.opsForZSet().add(key, boothId.toString(), 0);
            }
        } catch (Exception e) {
            log.warn("Redis clampScoreAtZero failed. key={}, boothId={}", key, boothId, e);
        }
    }

    private String likedKey(Long boothId) {
        return BOOTH_LIKED_KEY_PREFIX + boothId;
    }

    public record RedisChangeResult(
            boolean available,
            boolean changed
    ) {
        public static RedisChangeResult changedResult() {
            return new RedisChangeResult(true, true);
        }

        public static RedisChangeResult unchangedResult() {
            return new RedisChangeResult(true, false);
        }

        public static RedisChangeResult unavailableResult() {
            return new RedisChangeResult(false, false);
        }
    }
}
