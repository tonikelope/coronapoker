package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
public class MalformedRecoveryDoesNotExitJvmTest {
 @Test public void malformedRecoveryBecomesTableFailure() {
  TableFailure f=TableFailure.capture(new IllegalArgumentException("bad recovery"),7,true);
  assertFalse(f.exitJvm()); assertTrue(f.forceRecovery());
 }
}
