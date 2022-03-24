import kotlinx.atomicfu.*

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val tail = this.tail.value
            val enqInx = tail.enqIdx.getAndIncrement();
            if (enqInx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (!tail.next.compareAndSet(null, newTail)) {
                    tail.next.value?.let { this.tail.compareAndSet(tail, it) }
                    continue
                }
                this.tail.compareAndSet(tail, newTail)
                return
            } else {
                if (tail.elements[enqInx].compareAndSet(null, x))
                    return
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): T? {
        while (true) {
            val head = this.head.value
            val deqInx = head.deqIdx.getAndIncrement()
            if (deqInx >= SEGMENT_SIZE) {
                val next = head.next.value ?: return null
                this.head.compareAndSet(head, next)
                continue
            }
            return (head.elements[deqInx].getAndSet(DONE) ?: continue) as T?
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean get() {
        while (true) {
            if (head.value.isEmpty) {
                if (head.value.next.value == null) return true
                head.value = head.value.next.value!!
                continue
            } else {
                return false
            }
        }
    }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.compareAndSet(0, 1)
        elements[0].compareAndSet(null, x)
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

