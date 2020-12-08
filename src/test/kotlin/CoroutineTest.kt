package stageguard.sctimetable

import io.ktor.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CoroutineTest {
    @Test
    fun test() = runBlocking {
        val channel = Channel<Int>()

        val producer = GlobalScope.launch {
            var data = 0
            while (true) {
                channel.send(data ++)
                delay(1000)
            }
        }
        val consumer = GlobalScope.launch {
            for(i in channel) {
                println(i)
            }
        }
        producer.join()
        consumer.join()
    }
}