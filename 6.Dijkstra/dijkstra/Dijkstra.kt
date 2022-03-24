package dijkstra

import kotlinx.atomicfu.locks.synchronized
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Comparator
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

class MultiPriorityQueue(private val workers: Int, private val comparator: Comparator<Node>) {
    private val queues = List(workers) { _ -> PriorityQueue(workers, comparator) }
    private val random = Random()

    private fun getIndexes() : Pair<Int, Int> {
        while (true) {
            val one = random.nextInt(workers)
            val two = random.nextInt(workers)
            if (one == two)
                continue
            return Pair(min(one, two), max(one, two))
        }
    }

    fun poll(): Node? {
        val indexes = getIndexes()
        val first = queues[indexes.first]
        val second = queues[indexes.second]
        synchronized(first) {
            synchronized(second) {
                val one = first.peek()
                val two = second.peek()
                if (one == null)
                    return second.poll()
                if (two == null) {
                    return first.poll()
                }
                return if (comparator.compare(one, two) > 0)
                    first.poll()
                else second.poll()
            }
        }
    }

    fun add(node: Node) {
        val queue = queues[random.nextInt(workers)]
        synchronized(queue) {
            queue.add(node)
        }
    }
}

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    val activeNodes = AtomicInteger(1)
    // Create a priority (by distance) queue and add the start node into it
    val q = MultiPriorityQueue(workers, NODE_DISTANCE_COMPARATOR) // TODO replace me with a multi-queue based PQ!
    q.add(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker
    repeat(workers) {
        thread {
            while (!activeNodes.compareAndSet(0, 0)) {
                // TODO Write the required algorithm here,
                // TODO break from this loop when there is no more node to process.
                // TODO Be careful, "empty queue" != "all nodes are processed".
                val cur: Node = q.poll() ?: continue
                for (e in cur.outgoingEdges) {
                    var old = e.to.distance
                    var current = cur.distance + e.weight
                    if (old > current) {
                        while (!e.to.casDistance(old, current)) {
                            old = e.to.distance
                            current = cur.distance + e.weight
                            if (old <= current)
                                break
                        }
                        activeNodes.incrementAndGet()
                        q.add(e.to)
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}