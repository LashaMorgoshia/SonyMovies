package ge.kadri.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class BufferingWatchdogTest {
    @Test public void longSeekBufferingOffersRecoveryOnce() {
        BufferingWatchdog w=new BufferingWatchdog();
        assertFalse(w.shouldOffer(1000,true,true));
        assertFalse(w.shouldOffer(20999,true,true));
        assertTrue(w.shouldOffer(21000,true,true));
        assertFalse(w.shouldOffer(90000,true,true));
    }
    @Test public void aNewSeekGetsItsOwnDeadline() {
        BufferingWatchdog w=new BufferingWatchdog();
        w.shouldOffer(1000,true,true);w.reset();
        assertFalse(w.shouldOffer(19000,true,true));
        assertFalse(w.shouldOffer(21000,true,true));
        assertTrue(w.shouldOffer(39000,true,true));
    }
    @Test public void pausedPlaybackDoesNotPrompt() {
        BufferingWatchdog w=new BufferingWatchdog();w.shouldOffer(1000,true,true);
        assertFalse(w.shouldOffer(22000,true,false));
        assertFalse(w.shouldOffer(90000,true,true));
        assertTrue(w.shouldOffer(110000,true,true));
    }
    @Test public void successfulPlaybackClearsThePreviousStall() {
        BufferingWatchdog w=new BufferingWatchdog();w.shouldOffer(0,true,true);
        assertFalse(w.shouldOffer(10000,false,true));
        assertFalse(w.shouldOffer(20000,true,true));
        assertTrue(w.shouldOffer(40000,true,true));
    }
    @Test public void emptySuccessfulHttpResponseAllowsAnotherVideoTrack() {
        assertTrue(new VideoDataSource.ErrorPolicy().isEligibleForFallback(new VideoDataSource.EmptySegmentException()));
    }
    @Test public void wrappedEmptySegmentStillAllowsTrackFallback() {
        assertTrue(new VideoDataSource.ErrorPolicy().isEligibleForFallback(new IOException(new VideoDataSource.EmptySegmentException())));
    }
    @Test public void connectionTimeoutDoesNotBlackListAHealthyVideoTrack() {
        assertFalse(new VideoDataSource.ErrorPolicy().isEligibleForFallback(new SocketTimeoutException("read timed out")));
    }
}
