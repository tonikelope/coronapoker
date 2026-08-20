package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
public class RemoteMalformedStateDoesNotExitJvmTest {
 @Test public void remoteFailureIsConfinedToTable() {
  TableFailure f=TableFailure.capture(new ClassCastException("remote"),9,true);
  assertTrue(f.closeTable()); assertFalse(f.exitJvm());
 }
}
