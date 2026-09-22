package com.smartcooking.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiscoveryWindowTest {
    @Test public void missingFinishedBroadcastCanBeTerminatedOnce() {
        DiscoveryWindow w = new DiscoveryWindow(); long attempt = w.begin();
        assertTrue(w.active()); assertTrue(w.finish(attempt));
        assertFalse(w.active()); assertFalse(w.finish(attempt));
    }
    @Test public void oldTimeoutCannotTerminateNewAttempt() {
        DiscoveryWindow w = new DiscoveryWindow(); long old = w.begin(); w.finish(old);
        long current = w.begin(); assertFalse(w.finish(old)); assertTrue(w.active());
        assertTrue(w.finish(current));
    }
    @Test public void connectionCancelsScanAndLateBroadcastHasNoEffect() {
        DiscoveryWindow w = new DiscoveryWindow(); long attempt = w.begin();
        assertTrue(w.finish(attempt)); assertFalse(w.finish(w.token())); assertFalse(w.active());
    }
}
