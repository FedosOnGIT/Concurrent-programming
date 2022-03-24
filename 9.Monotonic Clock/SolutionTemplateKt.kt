/**
 * В теле класса решения разрешено использовать только переменные делегированные в класс RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author :Надуткин Федор
 */
class Solution : MonotonicClock {
    private var c1 by RegularInt(0)
    private var c2 by RegularInt(0)
    private var c3 by RegularInt(0)

    private var c_1 by RegularInt(0)
    private var c_2 by RegularInt(0)
    private var c_3 by RegularInt(0)

    override fun write(time: Time) {
        // write right-to-left
        c1 = time.d1
        c2 = time.d2
        c3 = time.d3

        c_3 = c3
        c_2 = c2
        c_1 = c1
    }

    override fun read(): Time {
        val r1 = c_1
        val r2 = c_2
        val r3 = c_3
        val time1 = Time(r1, r2, r3)

        val r_3 = c3
        val r_2 = c2
        val r_1 = c1
        val time2 = Time(r_1, r_2, r_3)
        if (time1.compareTo(time2) == 0) {
            return time1
        } else {
            if (r1 == r_1) {
                if (r2 == r_2) {
                    return Time(r1, r2, r3)
                }
                return Time(r1, r2, Int.MAX_VALUE)
            }
            return Time(r1, Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }
}