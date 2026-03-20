package bar;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class BarTest1 {
    @Test
    public void testGreet() throws InterruptedException {
        // Sleep so that test workers from different modules overlap in the thread pool.
        Thread.sleep(500);
        assertTrue(Bar.greet("world").contains("bar"));
    }

    @Test
    public void testPrefix() throws InterruptedException {
        Thread.sleep(500);
        assertTrue(Bar.greet("world").startsWith("Hello"));
    }
}
