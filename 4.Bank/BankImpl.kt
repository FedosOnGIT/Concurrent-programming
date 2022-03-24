import java.lang.Integer.max
import java.lang.Integer.min
import java.util.concurrent.locks.ReentrantLock

/**
 * Bank implementation.
 *
 *
 * @author :Надуткин Федор
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val numberOfAccounts: Int
        get() = accounts.size

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override fun getAmount(index: Int): Long {
        accounts[index].lock.lock()
        try {
            return accounts[index].amount
        } finally {
            accounts[index].lock.unlock()
        }
    }

    /**
     * :TODO: This method has to be made thread-safe.
     */
    override val totalAmount: Long
        get() {
            accounts.forEach { account ->
                account.lock.lock()
            }
            try {
                return accounts.sumOf { account ->
                    account.amount
                }
            } finally {
                accounts.forEach { account ->
                    account.lock.unlock()
                }
            }
        }

    override fun deposit(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        accounts[index].lock.lock()
        try {
            val account = accounts[index]
            check(!(amount > Bank.MAX_AMOUNT || account.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            accounts[index].lock.unlock()
        }
    }


    override fun withdraw(index: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        accounts[index].lock.lock()
        try {
            val account = accounts[index]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            accounts[index].lock.unlock()
        }
    }

    override fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
        val minimal = min(toIndex, fromIndex)
        val maximal = max(toIndex, fromIndex)
        accounts[minimal].lock.lock()
        accounts[maximal].lock.lock()
        try {
            val from = accounts[fromIndex]
            val to = accounts[toIndex]
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > Bank.MAX_AMOUNT || to.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            accounts[minimal].lock.unlock()
            accounts[maximal].lock.unlock()
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
        var lock: ReentrantLock = ReentrantLock(true)
    }
}