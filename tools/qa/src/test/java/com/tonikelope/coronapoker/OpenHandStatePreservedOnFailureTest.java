package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
public class OpenHandStatePreservedOnFailureTest {
 @Test public void diagnosticRetainsHandAndCause() {
  TableFailure f=TableFailure.capture(new IllegalStateException("proof mismatch"),44,true);
  assertEquals(44,f.handId()); assertTrue(f.diagnosticBundle().contains("proof mismatch"));
 }
}
