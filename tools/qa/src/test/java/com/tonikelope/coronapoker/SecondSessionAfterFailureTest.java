package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
public class SecondSessionAfterFailureTest {
 @Test public void processCanRunAnotherGuardedSessionAfterFailure() {
  SessionGuard g=new SessionGuard(); SessionGuard.Generation old=g.beginSession();
  TableFailure.capture(new RuntimeException("first"),1,true); g.invalidate(old);
  SessionGuard.Generation next=g.beginSession(); AtomicInteger state=new AtomicInteger();
  assertTrue(g.runIfCurrent(next,state::incrementAndGet)); assertEquals(1,state.get());
 }
}
