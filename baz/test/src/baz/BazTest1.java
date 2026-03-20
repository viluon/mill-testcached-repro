package baz;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class BazTest1 {
    @Test
    public void testGreet() throws InterruptedException {
        // Sleep so that test workers from different modules overlap in the thread pool.
        Thread.sleep(500);
        assertTrue(Baz.greet("world").contains("baz"));
    }

    @Test
    public void testPrefix() throws InterruptedException {
        Thread.sleep(500);
        assertTrue(Baz.greet("world").startsWith("Hello"));
    }
}
