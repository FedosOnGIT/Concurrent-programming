import kotlinx.atomicfu.*

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index >= core.value.size.value)
            throw IllegalArgumentException()
        return core.value.array[index].value!!
    }

    override fun put(index: Int, element: E) {
        var core : Core<E>? = core.value
        if (index >= core!!.size.value)
            throw IllegalArgumentException()
        while (true) {
            core!!.array[index].getAndSet(element)
            core = core.next.value
            if (core == null)
                return
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val core = this.core.value
            val size = core.size.value
            val capacity = core.capacity.value
            if (size < capacity) {
                if (!core.array[size].compareAndSet(null, element)) {
                   core.size.compareAndSet(size, size + 1)
                   continue
                }
                core.size.compareAndSet(size, size + 1)
                return
            } else {
                val next = Core<E>(capacity * 2)
                next.size.compareAndSet(0, size)
                core.next.compareAndSet(null, next)
                for (i in 0 until capacity)
                    core.next.value!!.array[i].compareAndSet(null, core.array[i].value)
                this.core.compareAndSet(core, core.next.value!!)
                continue
            }
        }
    }

    override val size: Int get() {
        return core.value.size.value
    }
}

private class Core<E>(
    input: Int
) {
    val capacity = atomic(input)
    val size = atomic(0)
    val array = atomicArrayOfNulls<E>(input)
    val next : AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME