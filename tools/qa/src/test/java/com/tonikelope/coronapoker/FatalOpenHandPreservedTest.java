package com.tonikelope.coronapoker;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
public class FatalOpenHandPreservedTest {
 @Test public void fatalDoesNotSetOpenHandEnd() {
  assertTrue(TableFailure.capture(new RuntimeException("fatal"),12,true).preserveOpenHand());
 }
}
