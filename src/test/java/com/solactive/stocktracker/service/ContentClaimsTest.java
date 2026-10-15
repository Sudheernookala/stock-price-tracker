package com.solactive.stocktracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solactive.stocktracker.exception.ContentInProgressException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link ContentClaims}.
 */
@SuppressWarnings("try")   // claims are held by try-with-resources without being used inside
class ContentClaimsTest {

    private final ContentClaims claims = new ContentClaims();

    @Test
    @DisplayName("the same content cannot be claimed twice at once; different content is not blocked")
    void sameContentIsRejected() {
        try (ContentClaims.Claim ignored = claims.claim("abc")) {
            assertThatThrownBy(() -> claims.claim("abc"))
                    .isInstanceOf(ContentInProgressException.class)
                    .hasMessage("A file with the same content is already being processed. Try again when it has finished.");
            claims.claim("def").close();   // other content: no exception
        }
    }

    @Test
    @DisplayName("a released claim can be claimed again, also after an exception")
    void releasedAfterUseAndAfterFailure() {
        claims.claim("abc").close();
        try (ContentClaims.Claim ignored = claims.claim("abc")) {
            throw new IllegalStateException("processing failed");
        } catch (IllegalStateException expected) {
            // try-with-resources released the claim
        }
        try (ContentClaims.Claim again = claims.claim("abc")) {
            assertThat(again).isNotNull();
        }
    }

    @Test
    @DisplayName("10 threads claiming the same content at once: exactly one succeeds")
    void onlyOneWinnerUnderConcurrency() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch hold = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                start.await();
                try (ContentClaims.Claim ignored = claims.claim("same")) {
                    hold.await();
                    return true;
                } catch (ContentInProgressException ex) {
                    return false;
                }
            });
        }
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<Boolean>> results = tasks.stream().map(executor::submit).toList();
            start.countDown();
            Thread.sleep(200);   // let every thread try to claim while the winner still holds it
            hold.countDown();
            long winners = 0;
            for (Future<Boolean> result : results) {
                winners += result.get() ? 1 : 0;
            }
            assertThat(winners).isEqualTo(1);
        }
    }
}
