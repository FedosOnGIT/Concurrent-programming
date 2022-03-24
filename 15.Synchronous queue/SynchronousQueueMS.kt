import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.lang.RuntimeException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null, Operation.NOTHING)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun enqueue(next: Node<E>, tail : Node<E>): Boolean {
        return if (tail.next.compareAndSet(null, next)) {
            this.tail.compareAndSet(tail, next)
            true
        } else {
            this.tail.compareAndSet(tail, tail.next.value!!)
            false
        }
    }

    private fun enqueue(element: E, operation: Continuation<Boolean>, tail: Node<E>): Boolean = enqueue(Node(element, operation, Operation.SEND), tail)

    private fun enqueue(operation: Continuation<Boolean>, tail: Node<E>) = enqueue(Node(null, operation, Operation.RECEIVE), tail)

    private fun dequeue(head: Node<E>): Node<E> {
        val next = head.next.value ?: return Node(null, null,  Operation.NOTHING)
        return if (this.head.compareAndSet(head, next)) {
            next
        } else {
            Node(null, null, Operation.NOTHING)
        }
    }

    override suspend fun send(element: E) {
        while (true) {
            val head = head.value
            val tail = tail.value
            if (tail == head || tail.type == Operation.SEND) {
                val action = suspendCoroutine<Boolean> alias@{ continuation ->
                    if (!enqueue(element, continuation, tail)) {
                        continuation.resume(false)
                        return@alias
                    }
                }
                if (action)
                    return
            } else {
                val result = dequeue(head)
                when(result.type) {
                    Operation.NOTHING -> continue
                    Operation.RECEIVE -> {
                        result.x = element
                        result.operation?.resume(true) ?: continue
                        return
                    }
                    Operation.SEND -> throw IllegalArgumentException()
                }
            }
        }
    }

    override suspend fun receive(): E {
        while (true) {
            val head = head.value
            val tail = tail.value
            if (tail == head || tail.type == Operation.RECEIVE) {
                val action = suspendCoroutine<Boolean> alias@{ continuation ->
                    if (!enqueue(continuation, tail)) {
                        continuation.resume(false)
                        return@alias
                    }
                }
                if (action) {
                    return tail.next.value?.x ?: throw RuntimeException()
                }
            } else {
                val result = dequeue(head)
                when(result.type) {
                    Operation.NOTHING -> continue
                    Operation.SEND -> {
                        result.operation?.resume(true) ?: continue
                        return result.x ?: throw RuntimeException()
                    }
                    Operation.RECEIVE -> throw IllegalArgumentException()
                }
            }
        }
    }
}

class Node<E> internal constructor(
    var x: E?,
    val operation: Continuation<Boolean>?,
    val type: Operation
) {
    val next : AtomicRef<Node<E>?> = atomic(null)
}

enum class Operation {
    SEND, RECEIVE, NOTHING
}
