package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
public class TableFailureReturnsToLauncherTest {
 @Test public void failureRequestsLauncherTransition() {
  assertTrue(TableFailure.capture(new RuntimeException("boom"),1,true).returnToLauncher());
 }
}
