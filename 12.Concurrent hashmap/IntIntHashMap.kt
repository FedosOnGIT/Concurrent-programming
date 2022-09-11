import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * TODO: This class is **NOT** thread-safe.
 */
class IntIntHashMap {
    private val core = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        core.value.move()
        return toValue(core.value.getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            core.value.move()
            val oldValue = core.value.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue
        }
    }

    inner class Core constructor(private val capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        private val map = AtomicIntArray(2 * capacity)
        private val shift: Int
        val next: AtomicRef<Core?>
        private val current: AtomicInt

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
            next = atomic(null)
            current = atomic(0)
        }

        fun move() {
            if (next.value == null) return
            if (current.value >= capacity) return
            while (current.value < capacity) {
                val i = current.value
                if (i >= capacity) break
                val keyIndex = i * 2
                val key = map[keyIndex].value
                val valueIndex = i * 2 + 1
                while (true) {
                    val old = map[valueIndex].value
                    if (old == MOVED_VALUE) break
                    if (isFrozen(old)) {
                        next.value!!.putInternal(key, old)
                        map[valueIndex].compareAndSet(old, MOVED_VALUE)
                        continue
                    }
                    val new = if (key == NULL_KEY) MOVED_VALUE else freeze(old)
                    map[valueIndex].compareAndSet(old, new)
                }
                current.compareAndSet(i, i + 1)
            }
            if (current.compareAndSet(capacity, update = Int.MAX_VALUE)) {
                core.getAndSet(next.value!!)
            }
            core.value.move()
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) return NULL_VALUE // not found -- no value
                if (++probes >= MAX_PROBES) return NULL_VALUE
                if (index == 0) index = map.size
                index -= 2
            }
            val valueIndex = index + 1
            // found key -- return value
            while (true) {
                val old = map[valueIndex].value
                if (old == MOVED_VALUE) return next.value!!.getInternal(key)
                if (isFrozen(old)) {
                    next.value!!.putInternal(key, old)
                    map[valueIndex].compareAndSet(old, MOVED_VALUE)
                    continue
                }
                return old
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) { // optimize for successful lookup
                if (map[index].value == NULL_KEY) {
                    // not found -- claim this slot
                    if (value == DEL_VALUE) return NULL_VALUE // remove of missing item, no need to claim slot
                    if (map[index].compareAndSet(NULL_KEY, key))
                        break
                    continue
                } else if (map[index].value == key) break
                if (++probes >= MAX_PROBES) {
                    rehash()
                    return NEEDS_REHASH
                }
                if (index == 0) index = map.size
                index -= 2
            }
            val valueIndex = index + 1
            if (isFrozen(value)) {
                map[valueIndex].compareAndSet(NULL_VALUE, unfreeze(value))
                return NULL_VALUE
            }
            // found key -- update value
            while (true) {
                val old = map[valueIndex].value
                if (old == MOVED_VALUE) return next.value!!.putInternal(key, value)
                if (isFrozen(old)) {
                    next.value!!.putInternal(key, old)
                    map[valueIndex].compareAndSet(old, MOVED_VALUE)
                    continue
                }
                if (map[valueIndex].compareAndSet(old, value)) return old
            }
        }

        private fun rehash() {
            val newCore = Core(map.size) // map.length is twice the current capacity
            next.compareAndSet(null, newCore)
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        private fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = -1 // returned by `putInternal` to indicate that rehash is needed
private const val MOVED_VALUE = Int.MIN_VALUE
private const val FREEZER = 1 shl 31

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

private fun freeze(value: Int): Int = value or FREEZER
private fun unfreeze(value: Int): Int = value and FREEZER.inv()
private fun isFrozen(value: Int): Boolean = (value and FREEZER) == FREEZER