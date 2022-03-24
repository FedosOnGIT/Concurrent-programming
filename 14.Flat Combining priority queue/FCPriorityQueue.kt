import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val size = 12
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val operations = atomicArrayOfNulls<Operation<E>>(size)

    private fun index(operation: Operation<E>): Int {
        while (true) {
            val index = Random().nextInt(size)
            if (operations[index].compareAndSet(null, operation))
                return index
        }
    }

    private fun helpOthers() {
        for (i in 0 until size) {
            val operation = operations[i].value
            if (operation != null && !operation.done) {
                when (operation.operation) {
                    Operations.POLL -> operations[i].compareAndSet(
                        operation,
                        Operation(Operations.POLL, null, q.poll(), true)
                    )
                    Operations.PEEK -> operations[i].compareAndSet(
                        operation,
                        Operation(Operations.PEEK, null, q.peek(), true)
                    )
                    Operations.ADD -> {
                        q.add(operation.input)
                        operations[i].compareAndSet(operation, Operation(Operations.ADD, null, null, true))
                    }
                }
            }
        }
    }

    private fun job(operation: Operation<E>) : E? {
        val index = index(operation)
        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                try {
                    helpOthers()
                } finally {
                    lock.compareAndSet(true, update = false)
                }
            }
            if (operations[index].value != operation) {
                val result = operations[index].value
                operations[index].compareAndSet(result, null)
                return result!!.result
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return job(Operation(Operations.POLL, null, null, false))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return job(Operation(Operations.PEEK, null, null,  false))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        job(Operation(Operations.ADD, element, null, false))
    }
}

class Operation<E>(val operation: Operations, val input: E?, val result: E?, val done: Boolean)

enum class Operations {
    POLL, PEEK, ADD
}