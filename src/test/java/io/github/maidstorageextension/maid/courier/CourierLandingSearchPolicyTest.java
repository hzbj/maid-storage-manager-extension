package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierLandingSearchPolicyTest {
    @Test
    void landingSearchExpandsOneChunkAtATimeUpToTheConfiguredLimit() {
        CourierLandingSearchPolicy.Window first = CourierLandingSearchPolicy.first(64);
        CourierLandingSearchPolicy.Window second = CourierLandingSearchPolicy.next(first, 64);
        CourierLandingSearchPolicy.Window third = CourierLandingSearchPolicy.next(second, 64);
        CourierLandingSearchPolicy.Window fourth = CourierLandingSearchPolicy.next(third, 64);
        CourierLandingSearchPolicy.Window exhausted = CourierLandingSearchPolicy.next(fourth, 64);

        assertEquals(new CourierLandingSearchPolicy.Window(4, 16, false), first);
        assertEquals(new CourierLandingSearchPolicy.Window(17, 32, false), second);
        assertEquals(new CourierLandingSearchPolicy.Window(33, 48, false), third);
        assertEquals(new CourierLandingSearchPolicy.Window(49, 64, false), fourth);
        assertTrue(exhausted.exhausted());
    }

    @Test
    void aCandidateIsRejectedOnlyAfterThreeFailedAttempts() {
        CourierLandingSearchPolicy.Failure one = CourierLandingSearchPolicy.fail(0);
        CourierLandingSearchPolicy.Failure two = CourierLandingSearchPolicy.fail(one.failures());
        CourierLandingSearchPolicy.Failure three = CourierLandingSearchPolicy.fail(two.failures());

        assertFalse(one.reselect());
        assertFalse(two.reselect());
        assertTrue(three.reselect());
        assertEquals(0, three.failures());
    }
}
