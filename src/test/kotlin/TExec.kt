import dlex.*
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File

// search in tests output for
//  definitely|Invalid read|Invalid write|uninitialised|uninitialized
//  - definitely lost
//  - Invalid read of size
//  - uninitialised value
val VALGRIND = ""
//val VALGRIND = "valgrind "
val THROW = false
//val THROW = true

fun all (inp: String, pre: Boolean=false): String {
    val prelude = "build/prelude.ceu"
    val inps = listOf(Pair(Triple("anon",1,1), inp.reader())) + if (!pre) emptyList() else {
        listOf(Pair(Triple(prelude,1,1), File(prelude).reader()))
    }
    val lexer = Lexer(inps)
    val parser = Parser(lexer)
    val es = try {
        parser.exprs()
    } catch (e: Throwable) {
        if (THROW) {
            throw e
        }
        return e.message!!
    }
    //println(es)
    //println(es.map { it.tostr(false)+"\n" }.joinToString(""))
    val c = try {
        val outer = Expr.Do(Tk.Fix("", Pos("anon", 0, 0)), es)
        val ups   = Ups(outer)
        val tags  = Tags(outer)
        val vars  = Vars(outer, ups)
        val clos  = Clos(outer, ups, vars)
        val sta   = Static(outer, ups, vars)
        val coder = Coder(outer, ups, vars, clos, sta)
        coder.main(tags)
    } catch (e: Throwable) {
        if (THROW) {
            throw e
        }
        return e.message!!
    }
    File("out.c").writeText(c)
    val (ok2, out2) = exec("gcc -Werror out.c -lm -o out.exe")
    if (!ok2) {
        return out2
    }
    val (_, out3) = exec("$VALGRIND./out.exe")
    //println(out3)
    return out3
}

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TExec {

    // PRINT

    @Test
    fun aa_print1() {
        val out = all(
            """
            print([10])
        """
        )
        assert(out == "[10]") { out }
    }
    @Test
    fun aa_print2() {
        val out = all(
            """
            print(10)
            println(20)
        """
        )
        assert(out == "1020\n") { out }
    }
    @Test
    fun aa_print3a() {
        val out = all(
            """
            println(func () { nil })
        """
        )
        assert(out.contains("func: 0x")) { out }
    }
    @Test
    fun aa_print3b() {
        val out = all(
            """
            println([[],[1,2,3]])
            println(func () { nil })
        """
        )
        assert(out.contains("[[],[1,2,3]]\nfunc: 0x")) { out }
    }
    @Test
    fun aa_print34() {
        val out = all(
            """
            var f
            set f = (func () { nil })
            do {
                var g
                set g = f
                nil
            }
            println(f)
        """
        )
        assert(out.contains("func: 0x")) { out }
    }
    @Test
    fun aa_print_err1() {
        val out = all(
            """
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun aa_print_err2() {
        val out = all(
            """
            print(1)
            print()
            print(2)
            println()
            println(3)
        """
        )
        assert(out == "12\n3\n") { out }
    }
    @Test
    fun aa_print4() {
        val out = all("print(nil)")
        assert(out == "nil") { out }
    }
    @Test
    fun aa_print5() {
        val out = all("print(true)")
        assert(out == "true") { out }
    }
    @Test
    fun aa_print6() {
        val out = all("println(false)")
        assert(out == "false\n") { out }
    }

    // VAR

    @Test
    fun bb_var1() {
        val out = all(
            """
            var v
            print(v)
        """
        )
        assert(out == "nil") { out }
    }
    @Test
    fun bb_var2() {
        val out = all(
            """
            var vvv = 1
            print(vvv)
        """
        )
        assert(out == "1") { out }
    }
    @Test
    fun bb_var3() {
        val out = all(
            """
            var this-is-a-var = 1
            print(this-is-a-var)
        """
        )
        assert(out == "1") { out }
    }
    @Test
    fun bb_var4_kebab_amb() {
        val out = all(
            """
            var x = 10
            var y = 5
            println(x-y)
        """
        )
        //assert(out == "anon : (lin 4, col 21) : access error : \"x-y\" is ambiguous with \"x\"") { out }
        assert(out == "anon : (lin 4, col 21) : access error : variable \"x-y\" is not declared") { out }
    }
    @Test
    fun bb_var5_kebab_amb() {
        val out = all(
            """
            var x = 10
            val y-z = 5
            println(h-y-z)
        """
        )
        //assert(out == "anon : (lin 4, col 21) : access error : \"h-y-z\" is ambiguous with \"y-z\"") { out }
        assert(out == "anon : (lin 4, col 21) : access error : variable \"h-y-z\" is not declared") { out }
    }
    @Test
    fun bb_06_val() {
        val out = all(
            """
            val v
            set v = 10
        """
        )
        assert(out == "anon : (lin 3, col 13) : set error : destination is immutable") { out }
    }
    @Test
    fun bb_07_und() {
        val out = all(
            """
            val _ = 10
            println(_)
        """
        )
        assert(out == "anon : (lin 3, col 21) : access error : cannot access \"_\"") { out }
    }
    @Test
    fun bb_08_und() {
        val out = all(
            """
            do {
                val _ = println(10)
            }
            do {
                val _ = println(20)
            }
            println(:ok)
        """
        )
        assert(out == "10\n20\n:ok\n") { out }
    }
    @Test
    fun bb_09_nested_var() {
        val out = all(
            """
            val x = do {
                val x = 10
                x
            }
            println(x)
        """
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun bb_10_var() {
        val out = all(
            """
            do {
                val x = println(10)
                println(x)
            }
        """
        )
        assert(out == "10\nnil\n") { out }
    }
    @Test
    fun bb_11_hold() {
        val out = all("""
            val t = [[nil]]
            dump(t[0])
        """)
        assert(out.contains("hold  = 1")) { out }
    }

    // INDEX / TUPLE

    @Test
    fun cc_index01_err() {
        val out = all(
            """
            [1,2,3][1]
            nil
        """
        )
        assert(out == "anon : (lin 2, col 13) : expression error : innocuous expression") { out }
    }
    @Test
    fun cc_index01() {
        val out = all(
            """
            pass [1,2,3][1]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_index011() {
        val out = all(
            """
            println([1,2,3][1])
        """
        )
        assert(out == "2\n") { out }
    }
    @Test
    fun cc_index_err01() {
        val out = all(
            """
            println([1,[2],3][1])   ;; [2] is at block, not at call arg // index made it outside call
        """
        )
        assert(out == "[2]\n") { out }
    }
    @Test
    fun cc_index_err1() {
        val out = all(
            """
            println(1[1])
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 9) : index error : expected collection\n") { out }
    }
    @Test
    fun cc_index_err2() {
        val out = all(
            """
            println([1][[]])
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 9) : index error : expected number\n") { out }
    }
    @Test
    fun cc_index23() {
        val out = all(
            """
            println([[1]][[0][0]])
        """.trimIndent()
        )
        assert(out == "[1]\n") { out }
    }
    @Test
    fun cc_index_err3() {
        val out = all(
            """
            println([1][2])
        """.trimIndent()
        )
        //assert(out == "anon : (lin 1, col 9) : index error : out of bounds\n") { out }
        assert(out == "anon : (lin 1, col 9) : index error : out of bounds\n") { out }
    }
    @Test
    fun cc_tuple4_free() {
        val out = all(
            """
            pass [1,2,3]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_tuple46_free() {
        val out = all(
            """
            pass [[]]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_vec_free() {
        val out = all(
            """
            pass #[]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_dic_free() {
        val out = all(
            """
            pass @[]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_tuple45_free() {
        val out = all(
            """
            pass [1,2,3][1]
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_tuple5_free() {
        val out = all(
            """
            val f = func () { nil }
            f([1,2,3])
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun cc_tuple56_free() {
        val out = all(
            """
            val x = do {
                [1]
            }
            println(x)
        """
        )
        assert(out == "[1]\n") { out }
    }
    @Test
    fun cc_tuple6a_free() {
        val out = all(
            """
            var f
            set f = func (v) {
                if v > 0 {
                    [f(v - 1)]
                } else {
                    0
                }
            }
            println(f(2))
        """, true
        )
        assert(out == "[[0]]\n") { out }
    }
    @Test
    fun cc_tuple6_free() {
        val out = all(
            """
            var f
            set f = func (v) {
                ;;println(v)
                if v > 0 {
                    [f(v - 1)]
                } else {
                    0
                }
            }
            println(f(3))
        """, true
        )
        assert(out == "[[[0]]]\n") { out }
    }
    @Test
    fun cc_tuple7_hold_err() {
        val out = all(
            """
            var f
            set f = func (v) {
                var x
                if v > 0 {
                    set x = f(v - 1)
                    [x]     ;; set error: cannot return "var x" from this scope
                } else {
                    0
                }
            }
            println(f(3))
        """, true
        )
        assert(out == "anon : (lin 3, col 30) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun cc_tuple8_hold_err() {
        val out = all(
            """
            var f
            set f = func (v) {
                if v > 0 {
                    val x = f(v - 1)
                    [x] ;; invalid return
                } else {
                    0
                }
            }
            println(f(3))
        """, true
        )
        assert(out == "anon : (lin 4, col 26) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun cc_tuple9_hold_err() {
        val out = all(
            """
            do {
                var x
                set x = [0]
                x   ;; escape but no access
            }
            println(1)
        """
        )
        //assert(out == "1\n") { out }
        assert(out == "anon : (lin 2, col 13) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun cc_tuple10_hold_err() {
        val out = all(
            """
            println(do {
                var xxx
                set xxx = [0]
                xxx
            })
        """
        )
        //assert(out == "anon : (lin 2, col 21) : set error : incompatible scopes\n") { out }
        //assert(out == "anon : (lin 5, col 17) : return error : incompatible scopes\n") { out }
        //assert(out == "anon : (lin 2, col 21) : set error : incompatible scopes\n") { out }
        assert(out == "anon : (lin 2, col 21) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun cc_tuple14_drop_out() {
        val out = all(
            """
            val out = do {
                val ins = [1,2,3]
                drop(ins)
            }
            println(out)
        """, true
        )
        assert(out == "[1,2,3]\n") { out }
    }
    @Test
    fun cc_tuple15_call_scope() {
        val out = all(
            """
            val f = func (v) {
                v
            }
            f([10])
            val x = f([10])
            println(f([10]))
        """
        )
        //assert(out == "anon : (lin 7, col 21) : f([10])\nanon : (lin 3, col 30) : set error : incompatible scopes\n") { out }
        assert(out == "[10]\n") { out }
    }
    @Test
    fun cc_tuple15x_call_scope() {
        val out = all(
            """
            val f = func (v) {
                v
            }
            println(f(10))
        """
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun cc_tuple16_drop() {
        val out = all(
            """
            val v = do {
                drop([[1,2]])
            }
            println(v)
        """
        )
        assert(out == "[[1,2]]\n") { out }
        //assert(out == "anon : (lin 4, col 13) : invalid drop : expected assignable expression") { out }
    }
    @Test
    fun cc_vector17_drop() {
        val out = all(
            """
            val ttt = #[#[1,2]]
            println(ttt)
        """
        )
        assert(out == "#[#[1,2]]\n") { out }
    }
    @Test
    fun cc_dict18_drop() {
        val out = all(
            """
            val v = do {
                @[(:v,@[(:v,2)])]
            }
            println(v)
        """
        )
        assert(out == "@[(:v,@[(:v,2)])]\n") { out }
    }
    @Test
    fun cc_vector19_print() {
        val out = all(
            """
            println(#[#[1,2]])
        """
        )
        assert(out == "#[#[1,2]]\n") { out }
    }
    @Test
    fun cc_vector20() {
        val out = all(
            """
            val v = #[10]
            println(v[#v - 1])
        """, true
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun cc_24_tuple() {
        val out = all(
            """
            val t = tuple(3)
            println(t)
            set t[1] = 10
            println(t)
        """
        )
        assert(out == "[nil,nil,nil]\n[nil,10,nil]\n") { out }
    }

    // DROP

    @Test
    fun cm_00_drop () {
        val out = all(
            """
            val f = func () {
                [0,'a']
            }
            println(f())
        """
        )
        assert(out == "[0,a]\n") { out }
    }
    @Test
    fun cm_01_drop () {
        val out = all(
            """
            val f = func () {
                [0,'a']
            }
            val g = func () {
                var v = f()
                drop(v)
            }
            println(g())
        """
        )
        assert(out == "[0,a]\n") { out }
    }
    @Test
    fun cm_01x_drop () {
        val out = all(
            """
            val g = do {
                val v = do {
                    val x = [0,'a']
                    drop(x)
                }
                drop(v)
            }
            println(g)
        """
        )
        assert(out == "[0,a]\n") { out }
    }
    @Test
    fun cm_02_drop () {
        val out = all(
            """
            val t = [[1]]
            val s = drop(t[0])
            val d = @[(1,[1])]
            val e = drop(d[1])
            println(t,t[0],s)
            println(d,d[1],e)
        """
        )
        assert(out == "[nil]\tnil\t[1]\n@[(1,nil)]\tnil\t[1]\n") { out }
    }
    @Test
    fun cm_03_drop() {
        val out = all(
            """
        var t = []
        do {
            val x = drop(t)
            println(:x, x)
        }
        println(:t, t)
        """
        )
        assert(out == ":x\t[]\n" +
                ":t\tnil\n") { out }
    }
    @Test
    fun cm_04_dots() {
        val out = all(
            """
            var f = func (...) {
                var x = [...]
                drop(x)
            }
            println(f(1,2,3))
        """
        )
        assert(out == "[[1,2,3]]\n") { out }
        //assert(out == "anon : (lin 4, col 22) : drop error : multiple references\n") { out }
    }
    @Test
    fun cm_04() {
        val out = all(
            """
            var f = func (t) {
                var x = [drop(t)]
                drop(x)
            }
            println(f([1,2,3]))
        """
        )
        assert(out == "[[1,2,3]]\n") { out }
    }
    @Test
    fun cm_05() {
        val out = all(
            """
            val t1 = [1]
            val t2 = [drop(t1)]
            val t3 = drop(t2)
            println(t1, t2, t3)
        """
        )
        assert(out == "nil\tnil\t[[1]]\n") { out }
    }
    @Test
    fun cc_07_global() {
        val out = all(
            """
        val e = func () {nil}
        val g = func () {
            val co = [e]
            drop(co)
        }
        val x = g()
        println(x)
        """, true
        )
        assert(out.contains("[func: 0x")) { out }
    }
    @Test
    fun cc_08_drop() {
        val out = all(
            """
            val F = func (^x) {
                func () {
                    ^^x
                }
            }
            do {
                val x = []
                val f = F(x)
                println(f())
            }
        """
        )
        assert(out == "[]\n") { out }
    }
    @Test
    fun cc_09_drop_nest() {
        val out = all(
            """
            val f = func (v) {
                ;; consumes v
                10
            }
            do {
                val x = []
                val y = f(drop(x))
                println(x, y)
            }
        """
        )
        assert(out == "nil\t10\n") { out }
    }
    @Test
    fun cc_10_drop_multi_err() {
        val out = all("""
            val x = do {
                val t1 = [1,2,3]
                val t2 = t1
                drop(t1)            ;; ERR: `t1` has multiple references
            }
            println(x)
        """)
        //assert(out == "anon : (lin 5, col 22) : drop error : multiple references\n") { out }
        assert(out == "[1,2,3]\n") { out }
    }
    @Test
    fun cc_11_drop_deep() {
        val out = all("""
            do {
                val t1 = [1]
                do {
                    val t2 = drop(t1)
                    println(t2)
                }
                println(t1)
            }
        """)
        assert(out == "[1]\nnil\n") { out }
    }
    @Test
    fun cc_12_drop_deep() {
        val out = all("""
            do {
                val t1 = [1]
                val t2 = t1
                do {
                    val t3 = drop(t1)
                    println(t2)
                }
                println(t1)
            }
        """)
        assert(out == "anon : (lin 6, col 21) : declaration error : cannot move to deeper scope with pending references\n") { out }
    }
    @Test
    fun cc_13_drop_cycle() {
        val out = all(
            """
            val z = do {
                var x = [nil]
                var y = [x]
                set x[0] = y
                drop(x)
            }
            println(z[0][0] == z)
        """
        )
        assert(out == "true\n") { out }
    }
    @Test
    fun cc_14_drop_cycle() {
        val out = all(
            """
            val z = do {
                var x = [nil]
                var y = [x]
                set x[0] = y
                drop(x)
                y
            }
            println(z[0][0] == z)
        """
        )
        assert(out == "true\n") { out }
    }

    // DICT

    @Test
    fun dd_dict0() {
        val out = all(
            """
            println(@[(:key,:val)])
        """
        )
        assert(out == "@[(:key,:val)]\n") { out }
    }
    @Test
    fun dd_dict1() {
        val out = all(
            """
            println(type(@[(1,2)]))
            println(@[(1,2)])
        """
        )
        assert(out == ":dict\n@[(1,2)]\n") { out }
    }
    @Test
    fun dd_dict2() {
        val out = all(
            """
            val t = @[(:x,1)]
            println(t[:x])
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun dict7_init() {
        val out = all("""
            var t = @[x=1, y=2]
            println(t.x, t.y)
        """)
        assert(out == "1\t2\n") { out }
    }
    @Test
    fun dd_dict3() {
        val out = all(
            """
            val t = @[(:x,1)]
            println(t[:y])
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun dd_dict4() {
        val out = all(
            """
            val t = @[(:x,1)]
            set t[:x] = 2
            println(t[:x])
        """
        )
        assert(out == "2\n") { out }
    }
    @Test
    fun dd_dict5() {
        val out = all(
            """
            val t = @[]
            set t[:x] = 1
            set t[:y] = 2
            println(t)
        """
        )
        assert(out == "@[(:x,1),(:y,2)]\n") { out }
    }
    @Test
    fun dd_11_dict_set() {
        val out = all(
            """
            val v = @[]
            do {
                set v[[]] = true
            }
            println(v)
        """
        )
        assert(out == "@[([],true)]\n") { out }
    }
    @Test
    fun dd_12_dict_set_err() {
        val out = all(
            """
            val v = @[]
            do {
                val k = []
                set v[k] = true
            }
            println(v)
        """
        )
        assert(out == "anon : (lin 5, col 21) : set error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun dd_13_dict() {
        val out = all("""
            val tree1 = @[
                (:left, @[
                    (:left, 1),
                    (:right, 2)
                ]),
                (:right, 3)
            ]

            val tree2 = @[
                left= @[
                    left=1,
                    right=2
                ],
                right=3
            ]
            println(tree1)
            println(tree2)
        """)
        assert(out == "@[(:left,@[(:left,1),(:right,2)]),(:right,3)]\n" +
                "@[(:left,@[(:left,1),(:right,2)]),(:right,3)]\n") { out }
    }

    // DICT / NEXT

    @Test
    fun ee_01_dict_next() {
        val out = all(
            """
            val t = @[]
            println(t[nil])
            set t[nil] = 1
        """
        )
        assert(out == "anon : (lin 4, col 17) : dict error : index cannot be nil\n" +
                "nil\n") { out }
    }
    @Test
    fun ee_02_dict_next() {
        val out = all(
            """
            val t = @[]
            set t[:x] = 1
            set t[:y] = 2
            var k
            set k = next(t)
            println(k, t[k])
            set k = next(t,k)
            println(k, t[k])
            set k = next(t,k)
            println(k, t[k])
        """
        )
        assert(out == ":x\t1\n:y\t2\nnil\tnil\n") { out }
    }
    @Test
    fun ee_03_dict_next() {
        val out = all(
            """
            val t = @[]
            set t[:x] = 1
            set t[:y] = 2
            set t[:z] = 3
            set t[:y] = nil
            set t[:x] = nil
            set t[:a] = 10
            set t[:b] = 20
            set t[:c] = 30
            var k = next(t)
            loop {
                break if (k == nil)
                println(k, t[k])
                set k = next(t,k)
            }
        """
        )
        assert(out == ":a\t10\n:b\t20\n:z\t3\n:c\t30\n") { out }
    }
    @Test
    fun ee_04_dict_next() {
        val out = all("""
            next(nil)
        """)
        assert(out == "anon : (lin 2, col 13) : call error : next error : expected dict\n") { out }
    }

    // VECTOR

    @Test
    fun ee_vector1() {
        val out = all(
            """
            println(#[])
        """
        )
        assert(out == "#[]\n") { out }
    }
    @Test
    fun vector2() {
        val out = all(
            """
            println(type(#[]))
            println(#[1,2,3])
        """
        )
        assert(out == ":vector\n#[1,2,3]\n") { out }
    }
    @Test
    fun vector3_err() {
        val out = all(
            """
            var v
            set v = #[]
            ;;println(#v)
            set v[#v] = 10
            ;;println(v)
            set v[5] = 10   ;; error
        """
        )
        //assert(out == "anon : (lin 7, col 17) : index error : out of bounds\n0\n#[10]\n") { out }
        assert(out == "anon : (lin 7, col 17) : index error : out of bounds\n") { out }
    }
    @Test
    fun vector4() {
        val out = all(
            """
            println(#(#[1,2,3]))
        """
        )
        assert(out == "3\n") { out }
    }
    @Test
    fun vector5() {
        val out = all(
            """
            val v = #[1,2,3]
            println(#v, v)
            set v[#v] = 4
            set v[#v] = 5
            println(#v, v)
            set v[#v - 1] = nil
            println(#v, v)
            ;;set #v = 2       ;; error
        """, true
        )
        assert(out == "3\t#[1,2,3]\n5\t#[1,2,3,4,5]\n4\t#[1,2,3,4]\n") { out }
    }
    @Test
    fun todo_vector6a_err() {
        val out = all(
            """
            #[1,nil,3]  ;; v[2] = nil, but #v===1
        """
        )
        assert(out.contains("ceu_vector_set: Assertion `i == vec->its-1' failed.")) { out }
    }
    @Test
    fun todo_vector6b_err() {
        val out = all(
            """
            #[1,'a',3]  ;; different type
        """
        )
        assert(out.contains("ceu_vector_set: Assertion `v.type == vec->unit' failed.")) { out }
    }
    @Test
    fun vector7_err() {
        val out = all(
            """
            #1
        """
        )
        assert(out == "anon : (lin 2, col 13) : call error : length error : not a vector\n") { out }
    }
    @Test
    fun vector8_err() {
        val out = all(
            """
            var v
            set v = #[1,2,3]
            println(v[#v])   ;; err
        """
        )
        //assert(out == "anon : (lin 4, col 23) : index error : out of bounds\n") { out }
        assert(out == "anon : (lin 4, col 21) : index error : out of bounds\n") { out }
    }
    @Test
    fun vector9_err() {
        val out = all(
            """
            set #v = 0
        """
        )
        assert(out == "anon : (lin 2, col 13) : set error : expected assignable destination") { out }
    }
    @Test
    fun vector13_add() {
        val out = all(
            """
            do {       
                val ceu_ifs_17 = true    
                val v = #[]
                if true {                                                           
                    set v[{{#}}(v)] = 10                                              
                } else {                                                            
                    nil
                }
                println(v)
            }
        """, true
        )
        assert(out == "#[10]\n") { out }
    }
    @Test
    fun vector14_err() {
        val out = all(
            """
            ;;val v
            set v[#v-1] = nil
        """, true
        )
        assert(out == "anon : (lin 3, col 17) : access error : variable \"v\" is not declared") { out }
    }
    @Test
    fun vector15_err() {
        val out = all(
            """
            val v
            v[#v-1]
        """, true
        )
        //assert(out == "anon : (lin 3, col 16) : access error : \"v-1\" is ambiguous with \"v\"") { out }
        assert(out == "anon : (lin 3, col 15) : call error : length error : not a vector\n") { out }
    }

    // STRINGS / CHAR

    @Test
    fun string1() {
        val out = all(
            """
            val v = #['a','b','c']
            set v[#v] = 'a'
            set v[2] = 'b'
            println(v[0])
            `puts(${D}v.Dyn->Vector.buf);`
            println(v)
        """
        )
        assert(out == "a\nabba\nabba\n") { out }
    }

    // DCL

    @Test
    fun dcl() {
        val out = all(
            """
            val x
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun dcl_chars() {
        val out = all(
            """
            val x'
            val f!
            val even?
            println(x')
            println(f!)
            println(even?)
        """
        )
        assert(out == "nil\nnil\nnil\n") { out }
    }
    @Test
    fun dcl_redeclaration_err() {
        val out = all(
            """
            val x
            val x
        """
        )
        assert(out == "anon : (lin 3, col 13) : declaration error : variable \"x\" is already declared") { out }
    }
    @Test
    fun todo_dcl4_dup() {
        val out = all(
            """
            do {
                val x
                println(x)
            }
            do {
                val x
                println(x)
            }
        """
        )
        assert(out == "nil\nnil\n") { out }
    }

    // SET

    @Test
    fun set1() {
        val out = all(
            """
            val x = [10]
            println(x)
        """
        )
        assert(out == "[10]\n") { out }
    }
    @Test
    fun set2() {
        val out = all(
            """
            val x = [10,20,[30]]
            set x[1] = 22
            set x[2][0] = 33
            println(x)
        """
        )
        assert(out == "[10,22,[33]]\n") { out }
    }
    @Test
    fun set_err1() {
        val out = all(
            """
            set 1 = 1
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : set error : expected assignable destination") { out }
    }
    @Test
    fun set_err2() {
        val out = all(
            """
            set [1] = 1
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : set error : expected assignable destination") { out }
    }
    @Test
    fun set_index() {
        val out = all(
            """
            val i = 1
            println([1,2,3][i])
        """
        )
        assert(out == "2\n") { out }
    }

    // DO

    @Test
    fun do1() {  // set whole tuple?
        val out = all(
            """
            do { nil }
        """
        )
        assert(out == "") { out }
    }
    @Test
    fun do2() {
        val out = all(
            """
            do {
                val a = 1
                println(a)
            }
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun do3() {
        val out = all(
            """
            val x = do {
                val a = 10
                a
            }
            print(x)
        """
        )
        assert(out == "10") { out }
    }
    @Test
    fun do4() {
        val out = all(
            """
            val x = do {nil}
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun do5() {
        val out = all(
            """
            do {
                val x
            }
            do {
                val x
            }
            println(1)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun if5() {
        val out = all(
            """
            if true {
                val aaa = 10
            } else {
                val xxx = 10
            }
            if true {
                val bbb = 20
            } else {
                val yyy = 20
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun export8() {
        val out = all(
            """
            do {
                val v = []
                val f = func () {
                    v                   ;; holds outer v
                }
                do {
                    val x = f           ;; nested do, but could be in par block from bcast
                    nil
                }
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }

    // SCOPE

    @Test
    fun scope1() {
        val out = all(
            """
            var x
            do {
                set x = [1,2,3]
            }
            println(x)
        """
        )
        assert(out == "[1,2,3]\n") { out }
    }
    @Test
    fun scope_err2() {
        val out = all(
            """
            var x
            do {
                var a
                set a = [1,2,3]
                set x = a           ;; err: x<a
            }
        """
        )
        //assert(out == "anon : (lin 3, col 13) : set error : incompatible scopes\n") { out }
        //assert(out == "anon : (lin 6, col 21) : set error : incompatible scopes\n") { out }
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope4() {
        val out = all(
            """
            var x
            do {
                set x = [1,2,3]
                set x[1] = [4,5,6]
                do {
                    val y = [10,20,30]
                    set y[1] = x[1]
                    set x[2] = y[1]
                }
            }
            println(x)
        """
        )
        assert(out == "[1,[4,5,6],[4,5,6]]\n") { out }
    }
    @Test
    fun scope4x() {
        val out = all(
            """
            val x = [0]
            do {
                set x[0] = [1]
                nil
            }
            println(x)
        """
        )
        assert(out == "[[1]]\n") { out }
    }
    @Test
    fun scope5_err() {
        val out = all(
            """
            var x
            do {
                set x = [1,2,3]
                var y
                set y = [10,20,30]
                set x[2] = y
            }
            println(x)
        """
        )
        //assert(out == "anon : (lin 7, col 21) : set error : incompatible scopes\n") { out }
        assert(out == "anon : (lin 7, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope6() {
        val out = all(
            """
            var x
            do {
                set x = [1,2,3]
                var y
                set y = 30
                set x[2] = y
            }
            println(x)
        """
        )
        assert(out == "[1,2,30]\n") { out }
    }
    @Test
    fun scope7() {
        val out = all(
            """
            var xs
            set xs = do {
                [10]
            }
            println(xs)
        """
        )
        assert(out == "[10]\n") { out }
    }
    @Test
    fun scope8_underscore() {
        val out = all(
            """
            var x
            do {
                val :tmp a = [1,2,3]
                set x = a
            }
            println(x)
        """
        )
        assert(out == "[1,2,3]\n") { out }
        //assert(out == "anon : (lin 3, col 13) : set error : incompatible scopes\n") { out }
    }
    @Test
    fun scope8b_underscore() {
        val out = all(
            """
            var x
            do {
                var :tmp a = [1,2,3]
                set x = a
            }
            println(x)
        """
        )
        assert(out == "anon : (lin 4, col 26) : declaration error : expected \"val\" for \":tmp\"") { out }
        //assert(out == "anon : (lin 3, col 13) : set error : incompatible scopes\n") { out }
    }
    @Test
    fun scope8c_underscore() {
        val out = all(
            """
            var x
            do {
                val :tmp a
                set a = [1,2,3]
                set x = a
            }
            println(x)
        """
        )
        assert(out == "anon : (lin 5, col 17) : set error : destination is immutable") { out }
        //assert(out == "anon : (lin 3, col 13) : set error : incompatible scopes\n") { out }
    }
    @Test
    fun scope9_err() {
        val out = all(
            """
            var x
            do {
                var a
                set a = @[(1,[])]
                set x = a
            }
        """
        )
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope10_err() {
        val out = all(
            """
            var out
            do {
                var x
                set x = []
                set out = [x]   ;; err
            }
            println(1)
        """
        )
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope11_err() {
        val out = all(
            """
            var out
            do {
                var x
                set x = []
                set out = #[x]
            }
            println(1)
        """
        )
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope12_err() {
        val out = all(
            """
            var out
            do {
                var x
                set x = []
                set out = @[(1,x)]
            }
            println(1)
        """
        )
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope13_tuple() {
        val out = all(
            """
            val v = do {
                val x = []
                [x]         ;; invalid return
            }
            println(v)
        """, true
        )
        assert(out == "anon : (lin 2, col 21) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun scope14_tmp_tuple() {
        val out = all(
            """
            val :tmp x = [0]
            set x[0] = []
            println(x)
        """
        )
        assert(out == "[[]]\n") { out }
    }
    @Test
    fun scope15_global_func() {
        val out = all(
            """
            val f = func () { nil }
            val g = func (v) {
                [f, v]
            }
            val tup = do {
                val v = []
                val x = g(v)
                println(x)
            }
        """
        )
        assert(out.contains("[func: 0x")) { out }
    }
    @Test
    fun scope15x_global_func() {
        val out = all(
            """
            val t = []
            val x = do {
                [t]
            }
            println(x)
        """
        )
        assert(out.contains("[[]]\n")) { out }
    }
    @Test
    fun scope16_glb_vs_tup() {
        val out = all(
            """
            val g = func () { nil }
            val f = func (v) {
                [g, v]
            }
            do {
                val t = []
                f(t)
                nil
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope17_glb_vs_tup() {
        val out = all(
            """
            val f = func () {
                nil
            }
            do {
                val t = []
                pass [f, t]
                nil
            }
            do {
                val t = []
                pass [f, t]
                nil
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope19_tup() {
        val out = all(
            """
            do {
                val t1 = []
                do {
                    val t2 = []
                    pass [t1,[],t2]
                    nil
                }
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope19x_tup() {
        val out = all(
            """
            do {
                val t1 = []
                do {
                    val t2 = []
                    [t1,[],t2]
                }
            }
            println(:ok)
        """
        )
        assert(out == "anon : (lin 4, col 17) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope19_leak() {
        val out = all(
            """
            val t1 = [9]
            do {
                pass [t1]
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope20_vec() {
        val out = all(
            """
            do {
                val t1 = []
                do {
                    val t2 = []
                    pass #[t1,[],t2]
                    nil
                }
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope21_dict() {
        val out = all(
            """
            do {
                val t1 = []
                do {
                    val t2 = []
                    pass @[(t1,t2)]
                    nil
                }
            }
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun scope22a_tup() {
        val out = all(
            """
            val d = [nil]
            do {
                val t2 = []
                set d[0] = t2
                nil
            }
        """
        )
        assert(out == "anon : (lin 5, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22b_vec() {
        val out = all(
            """
            val d = [nil]
            do {
                val t2 = #[]
                set d[0] = t2
                nil
            }
        """
        )
        assert(out == "anon : (lin 5, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22c_dic() {
        val out = all(
            """
            val d = @[]
            do {
                val t2 = []
                set d[t2] = 10
                nil
            }
        """
        )
        assert(out == "anon : (lin 5, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22d_dic() {
        val out = all(
            """
            val d = @[]
            do {
                val t2 = []
                set d[10] = t2
                nil
            }
        """
        )
        assert(out == "anon : (lin 5, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22x_dict() {
        val out = all(
            """
            do {
                val t1 = []
                val d = @[(t1,t1)]
                do {
                    val t2 = []
                    set d[t2] = t2
                    nil
                }
            }
            println(:ok)
        """
        )
        assert(out == "anon : (lin 7, col 25) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22y_dict() {
        val out = all(
            """
            val t1 = []
            val d = @[(t1,t1)]
            do {
                val t2 = []
                set d[:x] = t2
                nil
            }
            println(:ok)
        """
        )
        assert(out == "anon : (lin 6, col 21) : set error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope22z_dict() {
        val out = all(
            """
            val xxx = []
            val yyy = @[(xxx,xxx)]
            println(yyy)
            error(:ok)
        """
        )
        assert(out == "anon : (lin 5, col 13) : call error : :ok\n" +
                "@[([],[])]\n") { out }
    }
    @Test
    fun scope22z_tuple() {
        val out = all(
            """
            val xxx = [1]
            val yyy = [xxx,xxx]
            println(yyy)
            error(:ok)
        """
        )
        assert(out == "anon : (lin 5, col 13) : call error : :ok\n" +
                "[[1],[1]]\n") { out }
    }
    @Test
    fun scope26_args() {
        val out = all(
            """
            val f = func (v) {
                [v, [2]]
            }
            do {
                val v = [1]
                val x = f(v)
                println(x)
            }
        """
        )
        assert(out == "[[1],[2]]\n") { out }
    }
    @Test
    fun scope26y_args() {
        val out = all(
            """
            val f = func (v) {
                [v, [2]]
            }
            val v = [1]
            val x = f(v)
            println(x)
        """
        )
        assert(out == "[[1],[2]]\n") { out }
    }
    @Test
    fun scope26z_args() {
        val out = all(
            """
            val v = [1]
            val x = do {
                [v, [2]]
            }
            println(x)
        """
        )
        assert(out == "[[1],[2]]\n") { out }
    }
    @Test
    fun scope26x_args() {
        val out = all(
            """
            val f = func (v) {
                [v, [2]]
            }
            val y = do {
                val v = [1]
                val x = f(v)
                x
            }
            println(y)
        """
        )
        assert(out == "anon : (lin 5, col 21) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope27_glb_vs_tup() {
        val out = all(
            """
            val f = func (t) {
                val x = []
                ;;dump(t)
                set t[0] = x
                ;;dump(t)
                t
            }
            println(f([nil]))
        """
        )
        assert(out == "anon : (lin 2, col 30) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope28() {
        val out = all(
            """
            var f = func (v) {
                [v]
            }
            var g = do {
                val t = [1]
                f(t)
            }
            println(g)
        """
        )
        assert(out == "anon : (lin 5, col 21) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun scope29() {
        val out = all("""
            val f = func (v) {
                v
            }
            val x = [2]
            val y = f([x])
            println(y)

        """)
        assert(out == "[[2]]\n") { out }
    }
    @Test
    fun scope29x() {
        val out = all("""
            val f = func (v) {
                v
            }
            do {
                val x = [2]
                println(f([x]))
            }
        """)
        assert(out == "[[2]]\n") { out }
    }
    @Test
    fun scope30() {
        val out = all("""
            val cycle = func (v) {
                set v[3] = v
                v
            }
            var a = [1]
            var d = do {
                var b = [2]
                var c = cycle([a,b,[3],nil])
                drop(c)
            }
            ;;println(d)  ;; OK: [[1],[2],[3],*]
            println(:ok)
        """)
        assert(out == ":ok\n") { out }
    }

    // SCOPE / INNER

    @Test
    fun ll_01_fleet_tuple_func() {
        val out = all("""
            val f = func (v) {
                println(v[0])
            }
            f([[1]])
        """)
        assert(out == "[1]\n") { out }
    }
    @Test
    fun ll_02_fleet_tuple_func() {
        val out = all("""
            val g = func (v) {
                println(v)
            }
            val f = func (v) {
                println(v[0])
            }
            f([[1]])
        """)
        assert(out == "[1]\n") { out }
    }
    @Test
    fun ll_03_fleet_tuple_func_err() {
        val out = all("""
            var g = func (v) {
                val v' = v
                nil
            }
            g([[1]])
            println(:ok)
        """)
        assert(out == ":ok\n") { out }
    }
    @Test
    fun ll_04_fleet_tuple_func_err() {
        val out = all("""
            var f = func (v) {
                val :tmp x = v[0]
                println(x)
            }
            var g = func (v) {
                val evt = v
                f(evt)
            }
            g([[1]])
        """)
        assert(out == "[1]\n") { out }
    }
    @Test
    fun ll_05_nest() {
        val out = all("""
            var f = func (v) {
                val x = v[0]    ;; v also holds x, both are fleeting -> unsafe
                println(x)      ;; x will be freed and v would contain dangling pointer
            }
            f([[1]])
        """)
        //assert(out == "[1]\n") { out }
        assert(out == "anon : (lin 3, col 17) : declaration error : cannot move to deeper scope with pending references\n") { out }
    }

    // SCOPE | ARGS

    @Test
    fun lm_01_xxx() {
        val out = all("""
            val g = func (v) {
                println(v)
            }
            val f = func (v) {
                g(v)
                g(v)
            }
            f([1])
        """)
        assert(out == "anon : (lin 2, col 30) : argument error : cannot move to deeper scope with pending references\n") { out }
    }


    // IF

    @Test
    fun if1_err() {
        val out = all(
            """
            var x
            set x = if (true) { 1 }
            println(x)
        """
        )
        //assert(out == "anon : (lin 4, col 13) : expected \"else\" : have \"println\"") { out }
        assert(out == "1\n") { out }
    }
    @Test
    fun if2_err() {
        val out = all(
            """
            var x
            set x = 10
            set x = if false { 1 }
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun if3_err() {
        val out = all(
            """
            var x
            set x = 10
            set x = if (nil) {} else { 1 }
            println(x)
        """.trimIndent()
        )
        //assert(out == "anon : (lin 3, col 13) : if error : invalid condition\n") { out }
        assert(out == "anon : (lin 3, col 19) : expected expression : have \"}\"") { out }
    }
    @Test
    fun if4_err() {
        val out = all(
            """
            println(if [] {nil} else {nil})
        """.trimIndent()
        )
        //assert(out == "anon : (lin 1, col 4) : if error : invalid condition\n") { out }
        assert(out == "nil\n") { out }
    }
    @Test
    fun if_hld() {
        val out = all(
            """
            if [] {nil} else {nil}
            println(1)
        """.trimIndent()
        )
        //assert(out == "anon : (lin 1, col 4) : if error : invalid condition\n") { out }
        assert(out == "1\n") { out }
    }

    // OPS: not, and, or

    @Test
    fun op_or_and() {
        val out = all("""
            println(true or println(1))
            println(false and println(1))
        """)
        assert(out == "true\nfalse\n") { out }
    }
    @Test
    fun op_not() {
        val out = all("""
            println(not nil and not false)
        """)
        assert(out == "true\n") { out }
    }
    @Test
    fun op2_or_and() {
        val out = all("""
            println(1 or error(5))
            println(1 and 2)
            println(nil and 2)
            println(nil or 2)
        """)
        assert(out == "1\n2\nnil\n2\n") { out }
    }
    @Test
    fun op3_or_and() {
        val out = all("""
            println(true and ([] or []))
        """)
        assert(out == "[]\n") { out }
    }
    @Test
    fun xop3_or_and() {
        val out = all("""
            val v = do {
                val :tmp x = []
                if x { x } else { [] }
            }
            println(v)
        """)
        assert(out == "[]\n") { out }
    }
    @Test
    fun op4_and_and() {
        val out = all("""
            val v = true and
                true and 10
            println(v)
        """)
        assert(out == "10\n") { out }
    }
    @Test
    fun op5_plus_plus() {
        val out = all("""
            val v = 5 +
                5 + 10
            println(v)
        """, true)
        assert(out == "20\n") { out }
    }

    // FUNC / CALL

    @Test
    fun func1() {
        val out = all(
            """
            var f
            set f = func () { nil }
            var x
            set x = f()
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun func2() {
        val out = all(
            """
            var f
            set f = func () {
                1
            }
            var x
            set x = f()
            println(x)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun func3() {
        val out = all(
            """
            var f
            set f = func (xxx) {
                ;;println(type(xxx))
                xxx
            }
            var yyy
            set yyy = f(10)
            println(yyy)
        """
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun func4() {
        val out = all(
            """
            var f
            set f = func (x) {
                x
            }
            var x
            set x = f()
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun func5_err() {
        val out = all(
            """
            var f
            set f = func (x) {
                [x]
            }
            var x
            set x = f(10)
            println(x)
        """
        )
        assert(out == "[10]\n") { out }
        //assert(out.contains("set error : incompatible scopes")) { out }
    }
    @Test
    fun func7_err() {
        val out = all("1(1)")
        assert(out == "anon : (lin 1, col 1) : call error : expected function\n") { out }
    }
    @Test
    fun func8() {
        val out = all(
            """
            pass 1
            pass (1)
        """
        )
        //assert(out == "anon : (lin 2, col 2) : call error : \"(\" in the next line") { out }
        assert(out == "") { out }
    }
    @Test
    fun func9() {
        val out = all(
            """
            var f
            set f = func (a,b) {
                [a,b]
            }
            println(f())
            println(f(1))
            println(f(1,2))
            println(f(1,2,3))
        """
        )
        assert(out == "[nil,nil]\n[1,nil]\n[1,2]\n[1,2]\n") { out }
    }
    @Test
    fun func10_err() {
        val out = all(
            """
            f()
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : access error : variable \"f\" is not declared") { out }
    }
    @Test
    fun func11() {
        val out = all(
            """
            println(func (x) {
                var fff
                set fff = func (xxx) {
                    println(type(xxx))
                    xxx
                }
                fff(x)
            } (10))
        """
        )
        assert(out == ":number\n10\n") { out }
    }
    @Test
    fun func12() {
        val out = all(
            """
            var fff
            set fff = func (xxx) {
                println(type(xxx))
                xxx
            }
            println(func () {
                fff(10)
            } ())
        """
        )
        assert(out == ":number\n10\n") { out }
    }
    @Test
    fun func13() {
        val out = all(
            """
            func () {
                var fff
                set fff = func () {
                    println(1)
                }
                fff()
            } ()
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun func14() {
        val out = all(
            """
            println(func (x) {
                var fff
                set fff = func (xxx) {
                    println(type(xxx))
                    xxx
                }
                fff(x)
            } (10))
        """
        )
        assert(out == ":number\n10\n") { out }
    }
    @Test
    fun func15() {
        val out = all(
            """
            func (xxx) {
                println(xxx)
                func () {
                    println(xxx)
                }()
            }(10)
        """
        )
        assert(out == "10\n10\n") { out }
    }
    @Test
    fun func16_print() {
        val out = all(
            """
            var f
            set f = println
            f(false)
        """
        )
        assert(out == "false\n") { out }
    }
    @Test
    fun func17_ff() {
        val out = all(
            """
            var f
            f()()
        """
        )
        assert(out == "anon : (lin 3, col 13) : call error : expected function\n") { out }
    }
    @Test
    fun func18_err_rec() {
        val out = all(
            """
            val f = func () {
                f()
            }
        """
        )
        assert(out == "anon : (lin 3, col 17) : access error : variable \"f\" is not declared") { out }
    }
    @Test
    fun func_19_err() {
        val out = all("""
            val f = func (v) {
                1
            }
            val t = [[nil]]
            println(f(t[0]))        ;; 1
            println(f([[nil]][0]))  ;; err
        """)
        assert(out == "anon : (lin 2, col 30) : argument error : cannot move to deeper scope with pending references\n1\n") { out }
    }

    // FUNC / ARGS / DOTS / ...

    @Test
    fun nn_01_dots_tup() {
        val out = all(
            """
            var f = func (...) {
                println(#..., ...[1])
            }
            f(1,2,3)
        """
        )
        assert(out == "3\t2\n") { out }
    }
    @Test
    fun nn_02_dots_err() {
        val out = all(
            """
            var f = func () {
                println(...)
            }
            f(1,2,3)
        """
        )
        //assert(out == "anon : (lin 3, col 25) : access error : variable \"...\" is not declared") { out }
        assert(out == "./out.exe\n") { out }
    }
    @Test
    fun nn_03_dots() {
        val out = all(
            """
            var f = func (...) {
                println(...)
            }
            f(1,2,3)
        """
        )
        assert(out == "1\t2\t3\n") { out }
    }
    @Test
    fun nn_04_dots_gc() {
        val out = all(
            """
            var f = func (...) {
                println(...)
            }
            f(1,2,3)
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "1\t2\t3\n1\n") { out }
    }
    @Test
    fun nn_05_dots() {
        val out = all(
            """
            var f = func (...) {
                println(...)
            }
            f([1,2,3])
        """
        )
        assert(out == "[1,2,3]\n") { out }
    }
    @Test
    fun nn_06_dots_tup() {
        val out = all(
            """
            var f = func (x, ...) {
                var y = ...
                println(#..., y, ...)  ;; empty ... is not put into args
            }
            f(1)
        """
        )
        assert(out == "0\t[]\n") { out }
    }
    @Test
    fun nn_07_dots_main() {
        val out = all(
            """
            println(...)
        """
        )
        assert(out == "./out.exe\n") { out }
    }
    @Test
    fun nn_08_dots_set() {
        val out = all(
            """
            var f = func (...) {
                set ... = 1
                ...
            }
            println(f(1,2,3))
        """
        )
        //assert(out == "1\n") { out }
        assert(out == "anon : (lin 3, col 21) : set error : unexpected ...") { out }
    }

    // LOOP

    @Test
    fun oo_01_loop_err() {
        val out = all("""
            loop {
                do {
                    break if true
                }
            }
            println(:out)
        """)
        assert(out == "anon : (lin 4, col 21) : break error : expected parent loop") { out }
    }
    @Test
    fun oo_02_loop() {
        val out = all(
            """
            do {
                loop {
                    println(:in)
                    break if true
                }
            }
            println(:out)
        """
        )
        assert(out == ":in\n:out\n") { out }
    }
    @Test
    fun oo_03_loop() {
        val out = all(
            """
            var x
            set x = false
            loop {
                break if x
                set x = true
            }
            println(x)
        """
        )
        assert(out == "true\n") { out }
    }
    @Test
    fun oo_04_loop() {
        val out = all(
            """
            val f = func (t) {
                if t[1] == 5 {
                    nil
                } else {
                    set t[1] = t[1] + 1
                    t[1]
                }
            }
            do {
                val it = [f, 0]
                var i = it[0](it)
                loop {
                    break if (i == nil)
                    println(i)
                    set i = it[0](it)
                }
            }
        """, true
        )
        assert(out == "1\n2\n3\n4\n5\n") { out }
    }
    @Test
    fun oo_05_loop() {
        val out = all(
            """
            val f = func (t) {
                nil
            }
            val v = []
            f(v)
            println(:ok)
        """
        )
        assert(out == ":ok\n") { out }
    }
    @Test
    fun oo_06_loop() {
        val out = all(
            """
            val v = loop {break if (10)}
            println(v)
        """
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun oo_07_loop() {
        val out = all(
            """
            val v1 = loop {
                break(10) if true
            }
            val v2 = loop {
                break(nil) if true
            }
            println(v1, v2)
        """
        )
        assert(out == "10\tnil\n") { out }
    }
    @Test
    fun oo_08_loop() {
        val out = all("""
            val x = 10
            println(loop { break(x) if true })
        """)
        assert(out == "10\n") { out }
    }
    @Test
    fun oo_09_loop() {
        val out = all("""
            loop {
                do {
                    val t = []
                    break if true
                }
            }
            println(:ok)
        """)
        assert(out == "anon : (lin 5, col 21) : break error : expected parent loop") { out }
    }
    @Test
    fun oo_10_loop() {
        val out = all("""
            loop {
                val t = []
                break if true
            }
            println(:ok)
        """)
        assert(out == ":ok\n") { out }
    }

    // NATIVE

    @Test
    fun native1() {
        val out = all(
            """
            ```
                printf("xxx\n");
            ```
        """
        )
        assert(out == "xxx\n") { out }
    }
    @Test
    fun native2_num() {
        val out = all(
            """
            var x
            set x = `:number 1.5`
            println(x)
            println(`:number 1.5`)
        """
        )
        assert(out == "1.5\n1.5\n") { out }
    }
    @Test
    fun native3_str() {
        val out = all(
            """
            var x
            set x = `:pointer "ola"`
            ```
                puts(${D}x.Pointer);
            ```
        """
        )
        assert(out == "ola\n") { out }
    }
    @Test
    fun native4_err() {
        val out = all(
            """
            var x
            set x = ``` ```
            println(x)
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun native5() {
        val out = all(
            """
            var x
            set x = 10
            set x =  ```:number
                (printf(">>> %g\n", ${D}x.Number),
                ${D}x.Number*2)
            ```
            println(x)
        """
        )
        assert(out == ">>> 10\n20\n") { out }
    }
    @Test
    fun native6() {
        val out = all(
            """
            var x
            set x = 1
            ```
                ${D}x.Number = 20;
            ```
            println(x)
        """
        )
        assert(out == "20\n") { out }
    }
    @Test
    fun native7_err() {
        val out = all(
            """
             `
             
                $D{x.y}
                
             `
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : native error : (lin 3, col 4) : invalid identifier") { out }
    }
    @Test
    fun native8() {
        val out = all(
            """
            var x
            set x = 0
            var f
            set f = func () {
                ```
                    ${D}x.Number = 20;
                ```
            }
            f()
            println(x)
        """
        )
        assert(out == "20\n") { out }
    }
    @Test
    fun native9_err() {
        val out = all(
            """
             `
                $D 
             `
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : native error : (lin 2, col 4) : invalid identifier") { out }
    }
    @Test
    fun native10_err() {
        val out = all(
            """
            ` ($D) `
        """.trimIndent()
        )
        assert(out == "anon : (lin 1, col 1) : native error : (lin 1, col 4) : invalid identifier") { out }
    }
    @Test
    fun native11_pointer() {
        val out = all(
            """
            var f
            set f = func () {
                `:pointer
                    "ola"
                `
            }
            var g
            set g = func (x) {
                `
                    printf("%s\n", (char*)${D}x.Pointer);
                `
            }
            var x
            set x = f()
            g(x)
        """
        )
        assert(out == "ola\n") { out }
    }
    @Test
    fun native12_pointer() {
        val out = all(
            """
            println(`:pointer"oi"`)
        """
        )
        assert(out.contains("pointer: 0x")) { out }
    }
    @Test
    fun native13_pre() {
        val out = all(
            """
            ```
            int X = 1;
            ```
            var f
            set f = func () {
                `:number X`
            }
            println(f())
        """
        )
        assert(out.contains("1\n")) { out }
    }
    @Test
    fun native14_char() {
        val out = all(
            """
            var c
            set c = `:char 'x'`
            `putchar(${D}c.Char);`
        """
        )
        assert(out == "x") { out }
    }
    @Test
    fun native15_func() {
        val out = all(
            """
            var f = func (v) {
                println(v)
                v
            }
            var f' = `:ceu ${D}f`
            println(f'(10))
        """
        )
        assert(out == "10\n10\n") { out }
    }
    @Test
    fun native16_func() {
        val out = all("""
            val n = `:number 10`                ;; native 10 is converted to `dyn-lex` number
            val x = `:ceu ${D}n`                ;; `x` is set to `dyn-lex` `n` as is
            `printf("> %f\n", ${D}n.Number);`   ;; outputs `n` as a number
        """)
        assert(out == "> 10.000000\n") { out }
    }

    // OPERATORS

    @Test
    fun op_umn() {
        val out = all(
            """
            println(-10)
        """, true
        )
        assert(out == "-10\n") { out }
    }
    @Test
    fun op_id1() {
        val out = all(
            """
            println({{-}}(10,4))
        """, true
        )
        assert(out == "6\n") { out }
    }
    @Test
    fun op_arithX() {
        val out = all(
            """
            println(((10 + -20)*2)/5)
        """, true
        )
        assert(out == "-4\n") { out }
    }
    @Test
    fun op_cmp() {
        val out = all(
            """
            println(1 > 2)
            println(1 < 2)
            println(1 == 1)
            println(1 /= 1)
            println(2 >= 1)
            println(2 <= 1)
        """, true
        )
        assert(out == "false\ntrue\ntrue\nfalse\ntrue\nfalse\n") { out }
    }
    @Test
    fun op_eq() {
        val out = all(
            """
            println(1 == 1)
            println(1 /= 1)
        """
        )
        assert(out == "true\nfalse\n") { out }
    }
    @Test
    fun op_prec_ok() {
        val out = all(
            """
            println(2 + 3 + 1)
        """, true
        )
        assert(out == "6\n") { out }
    }
    @Test
    fun op_assoc() {
        val out = all(
            """
            println((2 * 3) - 1)
        """, true
        )
        assert(out == "5\n") { out }
    }
    @Test
    fun ops_oth() {
        val out = all(
            """
            println(2**3)
            println(8//3)
            println(8%3)
        """, true
        )
        assert(out == "8\n2\n2\n") { out }
    }
    @Test
    fun ops_id() {
        val out = all(
            """
            val add = func (x,y) {
                x + y
            }
            println(10 {{add}} 20)
        """, true
        )
        assert(out == "30\n") { out }
    }

    // ==, ===, /=, =/=

    @Test
    fun pp_01_op_eqeq_tup() {
        val out = all(
            """
            println([1] == [1])
            println([ ] == [1])
            println([1] /= [1])
            println([1,[],[1,2,3]] == [1,[],[1,2,3]])
        """
        )
        assert(out == "false\nfalse\ntrue\nfalse\n") { out }
    }
    @Test
    fun pp_02_op_eqeq_tup() {
        val out = all(
            """
            println([1,[1],1] == [1,[1],1])
        """
        )
        assert(out == "false\n") { out }
    }
    @Test
    fun pp_03_op_eqs_dic() {
        val out = all(
            """
            println(@[] == @[])
            println(@[] /= @[])
        """
        )
        assert(out == "false\ntrue\n") { out }
    }
    @Test
    fun pp_04_op_eqs_vec() {
        val out = all(
            """
            println(#[] ==  #[])
            println(#[] /=  #[])
        """
        )
        assert(out == "false\ntrue\n") { out }
    }
    @Test
    fun pp_05_op_eqs_vec_dic_tup() {
        val out = all(
            """
            println([#[],@[]] == [#[],@[]])
            println([#[],@[]] /= [#[],@[]])
        """
        )
        assert(out == "false\ntrue\n") { out }
    }

    // to-number, to-string, to-tag, string-to-tag

    @Test
    fun tostring1() {
        val out = all(
            """
            var s
            set s = to-string(10)
            println(type(s), s)
        """, true
        )
        assert(out == ":vector\t10\n") { out }
    }
    @Test
    fun tonumber2() {
        val out = all(
            """
            var n
            set n = to-number(#['1','0'])
            println(type(n), n)
        """, true
        )
        assert(out == ":number\t10\n") { out }
    }
    @Test
    fun tonumber_tostring3() {
        val out = all(
            """
            var s
            set s = to-string(to-number(#['1','0']))
            println(type(s), s)
        """, true
        )
        assert(out == ":vector\t10\n") { out }
    }
    @Test
    fun ff_01_string_to_tag() {
        val out = all(
            """
            pass :xyz
            println(string-to-tag(#[':','x']))
            println(to-tag(#[':','x','y','z']))
            println(string-to-tag(#['x','y','z']))
            println(to-tag(:abc))
        """, true
        )
        assert(out == "nil\n:xyz\nnil\n:abc\n") { out }
    }
    @Test
    fun ff_02_string_to_tag() {
        val out = all(
            """
            data :A = []
            data :A.B = []
            data :A.B.C = []
            println(string-to-tag(#[':','A']), string-to-tag(#[':','A','.','B']), string-to-tag(#[':','A','.','B','.','C']))
        """
        )
        assert(out == ":A\t:A.B\t:A.B.C\n") { out }
    }
    @Test
    fun ff_03_string_to_tag() {
        val out = all(
            """
            val x = string-to-tag(#[':','x'])
            println(x == :x)
            val y = string-to-tag(#[':','y'])
            println(y)
        """
        )
        assert(out == "true\nnil\n") { out }
    }

    // string-to-tag

    @Test
    fun ff_04_string_to_tag() {
        val out = all("""
            pass :xyz
            println(string-to-tag(":x"))
            println(string-to-tag(":xyz"))
            println(string-to-tag("xyz"))
        """, true)
        assert(out == "nil\n:xyz\nnil\n") { out }
    }
    @Test
    fun ff_05_string_to_tag() {
        val out = all("""
            data :A = []
            data :A.B = []
            data :A.B.C = []
            println(string-to-tag(":A"), string-to-tag(":A.B"), string-to-tag(":A.B.C"))
        """, true)
        assert(out == ":A\t:A.B\t:A.B.C\n") { out }
    }

    // TYPE

    @Test
    fun type1() {
        val out = all(
            """
            var t
            set t = type(1)
            println(t)
            println(type(t))
            println(type(type(t)))
        """
        )
        assert(out == ":number\n:tag\n:tag\n") { out }
    }

    // TAGS

    @Test
    fun tags1() {
        val out = all(
            """
            println(:xxx)
            println(:xxx == :yyy)
            println(:xxx /= :yyy)
            println(:xxx == :xxx)
            println(:xxx /= :xxx)
        """
        )
        assert(out == ":xxx\nfalse\ntrue\ntrue\nfalse\n") { out }
    }
    @Test
    fun tags2() {
        val out = all(
            """
            func () {
                println(:xxx)
            }()
            func () {
                println(:xxx)
            }()
        """
        )
        assert(out == ":xxx\n:xxx\n") { out }
    }
    @Test
    fun tags3() {
        val out = all(
            """
            func () {
                println(:Xxx.Yyy)
            }()
            func () {
                println(:a.b.c)
            }()
        """
        )
        assert(out == "anon : (lin 3, col 25) : tag error : parent tag :Xxx is not declared") { out }
    }
    @Test
    fun tags3a() {
        val out = all(
            """
            func () {
                println(:Xxx)
            }()
            func () {
                println(:1)
            }()
        """
        )
        assert(out == ":Xxx\n:1\n") { out }
    }
    @Test
    fun tags4_err() {
        val out = all(
            """
            println(tags())
        """
        )
        assert(out.contains("ceu_tags_f: Assertion `n >= 1' failed")) { out }
    }
    @Test
    fun tags4() {
        val out = all(
            """
            println(tags([]))
        """
        )
        assert(out.contains("[]\n")) { out }
    }
    @Test
    fun tags5() {
        val out = all(
            """
            println(tags(1,:2))
        """
        )
        assert(out == "false\n") { out }
    }
    @Test
    fun tags6_err() {
        val out = all(
            """
            tags([],2)
        """
        )
        assert(out.contains("Assertion `tag.type == CEU_VALUE_TAG'")) { out }
    }
    @Test
    fun tags7_err() {
        val out = all(
            """
            tags([],:x,nil)
        """
        )
        assert(out.contains("Assertion `bool.type == CEU_VALUE_BOOL' failed")) { out }
    }
    @Test
    fun tags8() {
        val out = all(
            """
            var t
            set t = []
            var x1
            set x1 = tags(t,:x,true)
            var x2
            set x2 = tags(t,:x,true)
            println(x1, x2, x1==t)
            set x1 = tags(t,:x,false)
            set x2 = tags(t,:x,false)
            println(x1, x2, x1==t)
        """
        )
        assert(out == ":x []\tnil\ttrue\n[]\tnil\ttrue\n") { out }
    }
    @Test
    fun tags9() {
        val out = all(
            """
            var t
            set t = []
            tags(t,:x,true)
            println(tags(t, :x))
            tags(t,:y,true)
            println(tags(t, :y))
            tags(t,:x,false)
            println(tags(t, :x))
        """, true
        )
        assert(out == "true\ntrue\nfalse\n") { out }
    }
    @Test
    fun tags10() {
        val out = all(
            """
            println(:x-a-x, :i.j.a)
        """, true
        )
        assert(out == "anon : (lin 2, col 29) : tag error : parent tag :i.j is not declared") { out }
    }
    @Test
    fun tags10a() {
        val out = all(
            """
            println(:x-a-x, :i-j-a)
        """, true
        )
        assert(out == ":x-a-x\t:i-j-a\n") { out }
    }
    @Test
    fun tags11() {
        val out = all(
            """
            var t = tags([], :T,   true)
            var s = tags([], :T.S, true)
            println(to-number(:T), to-number(:T.S))
            println(tags(t,:T), tags(t,:T.S))
            println(tags(s,:T), tags(s,:T.S))
        """, true
        )
        assert(out == "15\t271\ntrue\tfalse\ntrue\ttrue\n") { out }
    }
    @Test
    fun tags12() {
        val out = all(
            """
            pass :A
            pass :A.I
            pass :A.I.X
            pass :A.I.Y
            pass :A.J
            pass :A.J.X
            pass :B
            pass :B.I
            pass :B.I.X
            pass :B.I.X.a
            println(sup?(:A, :A.I))
            println(sup?(:A, :A.I.X))
            println(sup?(:A.I.X, :A.I.Y))
            println(sup?(:A.J, :A.I.Y))
            println(sup?(:A.I.X, :A))
            println(sup?(:B, :B.I.X.a))
        """, true
        )
        assert(out == "true\ntrue\nfalse\nfalse\nfalse\ntrue\n") { out }
    }
    @Test
    fun tags13() {
        val out = all(
            """
            var t = []
            tags(t, :X, true)
            tags(t, :Y, true)
            tags(t, :Z, true)
            ;;println(tags(t))
            var f = func (ts) {
                println(ts)
            }
            f(tags(t))
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "[:Z,:Y,:X]\n1\n") { out }
    }

    // ENUM

    @Test
    fun enum01() {
        val out = all(
            """
            pass :antes
            enum {
                :x = `1000`,
                :y, :z,
                :a = `10`,
                :b, :c
            }
            pass :meio
            enum {
                :i = `100`,
                :j,
            }
            pass :depois
            println (
                to-number(:antes),
                to-number(:x),
                to-number(:y),
                to-number(:z),
                to-number(:a),
                to-number(:b),
                to-number(:c),
                to-number(:meio),
                to-number(:i),
                to-number(:j),
                to-number(:depois)
            )
        """, true
        )
        assert(out == "15\t1000\t1001\t1002\t10\t11\t12\t16\t100\t101\t17\n") { out }
    }
    @Test
    fun enum02() {
        val out = all(
            """
            enum {
                :x = `1000`,
                :y = `1000`,
            }
            println(:tag, :x, :1000, :y)
        """
        )
        assert(out == ":tag\t:y\t:1000\t:y\n") { out }
    }
    @Test
    fun enum03() {
        val out = all(
            """
            enum {
                :x.y
            }
            println(:tag, :x, :1000, :y)
        """
        )
        assert(out == "anon : (lin 3, col 17) : enum error : enum tag cannot contain '.'") { out }
    }

    // CLOSURE / ESCAPE / FUNC / UPVALS

    @Test
    fun clo1_err() {
        val out = all(
            """
            var ^^x     ;; can't declare upref
        """
        )
        assert(out == "anon : (lin 2, col 13) : var error : cannot declare an upref") { out }
    }
    @Test
    fun clo2_err() {
        val out = all(
            """
            var ^x     ;; upvar can't be global
        """
        )
        assert(out == "anon : (lin 2, col 13) : var error : cannot declare a global upvar") { out }
    }
    @Test
    fun clo3_err() {
        val out = all(
            """
            ^x     ;; upvar can't be in global
        """
        )
        assert(out == "anon : (lin 2, col 13) : access error : variable \"x\" is not declared") { out }
    }
    @Test
    fun clo4_err() {
        val out = all(
            """
            ^^x     ;; upref can't be in global
        """
        )
        assert(out == "anon : (lin 2, col 13) : access error : variable \"x\" is not declared") { out }
    }
    @Test
    fun clo5_err() {
        val out = all(
            """
            var g
            set g = 10
            var f
            set f = func (^x) {
                set ^x = []  ;; err: cannot reassign
                func () {
                    ^^x == g
                }
            }
            println(f([])())
        """
        )
        //assert(out == "anon : (lin 6, col 21) : set error : cannot reassign an upval") { out }
        assert(out == "anon : (lin 6, col 17) : set error : destination is immutable") { out }
    }
    @Test
    fun clo6_err() {
        val out = all(
            """
            var g
            set g = 10
            var f
            set f = func (^x) {
                func () {
                    set ^^x = []  ;; err: cannot reassign
                    ;;^^x + g
                }
            }
            println(f([])())
        """
        )
        //assert(out == "anon : (lin 7, col 25) : set error : cannot reassign an upval") { out }
        assert(out == "anon : (lin 7, col 21) : set error : destination is immutable") { out }
    }
    @Test
    fun clo7_err() {
        val out = all(
            """
            do {
                var ^x     ;; err: no associated upref
                ^x
            }
            println(1)
        """
        )
        assert(out == "anon : (lin 3, col 17) : var error : unreferenced upvar") { out }
    }
    @Test
    fun clo8_err() {
        val out = all(
            """
            do {
                ^^x     ;; err: no associated upvar
            }
        """
        )
        assert(out == "anon : (lin 3, col 17) : access error : variable \"x\" is not declared") { out }
    }
    @Test
    fun clo9_err() {
        val out = all(
            """
            do {
                var x
                ^^x     ;; err: no associated upvar
            }
        """
        )
        assert(out == "anon : (lin 4, col 17) : access error : incompatible upval modifier") { out }
    }
    @Test
    fun clo10_err() {
        val out = all(
            """
            do {
                var ^x
                ^^x     ;; err: no associated upvar
            }
        """
        )
        assert(out == "anon : (lin 4, col 17) : access error : unnecessary upref modifier") { out }
    }
    @Test
    fun clo11_err() {
        val out = all(
            """
            var f
            set f = do {
                var x = []
                func () {   ;; block_set(1)
                    x       ;; because of x
                }           ;; err: scope on return
            }
            println(f(10))
        """
        )
        assert(out == "anon : (lin 3, col 21) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun clo12_err() {
        val out = all(
            """
            var f
            set f = func (x) {
                func () {   ;; block_set(1)
                    x       ;; because of x
                }           ;; err: scope on return
            }
            println(f(10)())
        """
        )
        assert(out == "anon : (lin 3, col 30) : block escape error : cannot copy reference to outer scope\n") { out }
    }
    @Test
    fun clo13_err() {
        val out = all(
            """
            var g
            set g = 10
            var f
            set f = func (^x) {
                func () {       ;; block_set(0)
                    ^^x + g     ;; all (non-global) upvals are marked
                }
            }
            println(f(20)())
        """, true
        )
        assert(out == "30\n") { out }
    }
    @Test
    fun clo14() {
        val out = all(
            """
            var g
            set g = 10
            var f
            set f = func (^x) {
                func () {
                    ^^x[0] + g
                }
            }
            println(f([20])())
        """, true
        )
        assert(out == "30\n") { out }
    }
    @Test
    fun clo15() {
        val out = all(
            """
            var f
            set f = func (^x) {
                ;;;val :tmp z =;;; func (y) {
                    [^^x,y]
                }
                ;;dump(z)
                ;;z
            }
            println(f([10])(20))
        """
        )
        assert(out == "[[10],20]\n") { out }
    }
    @Test
    fun clo16() {
        val out = all(
            """
            var f
            set f = func () {
                var ^x = 10     ;; TODO: needs initialization
                func (y) {
                    [^^x,y]
                }
            }
            println(f()(20))
        """
        )
        assert(out == "[10,20]\n") { out }
    }
    @Test
    fun clo17() {
        val out = all(
            """
            var f
            set f = func (^x) {
                println(:1, ^x)
                func () {
                    println(:2, ^^x)
                    ^^x
                }
            }
            println(:3, f(10)())
        """
        )
        assert(out == ":1\t10\n:2\t10\n:3\t10\n") { out }
    }
    @Test
    fun clo18() {
        val out = all(
            """
            var f
            set f = func (^x) {
                func () {
                    ^^x
                }
            }
            println(f(10)())
        """
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun clo19() {
        val out = all(
            """
            var curry
            set curry = func (^fff) {
                func (^xxx) {
                    func (yyy) {
                        ^^fff(^^xxx,yyy)
                    }
                }
            }

            var f = func (a,b) {
                [a,b]
            }
            var f' = curry(f)
            println(f'(1)(2))
        """
        )
        assert(out == "[1,2]\n") { out }
    }
    @Test
    fun clo20() {
        val out = all(
            """
            var curry
            set curry = func (^fff) {
                func (^xxx) {
                    func (yyy) {
                        ^^fff(^^xxx,yyy)
                    }
                }
            }
            var f = func (a,b) {
                [a,b]
            }
            println(curry(f)(1)(2))
        """
        )
        assert(out == "[1,2]\n") { out }
    }
    @Test
    fun clo21() {
        val out = all(
            """
            var f = func (^a) {
                func () {
                    ^^a
                }
            }
            var g = do {
                val t = [1]
                f(t)
            }
            println(g())
        """
        )
        assert(out == "anon : (lin 7, col 21) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun tup22() {
        val out = all(
            """
            do {
                val t = []
                [t]
            }
            println(:ok)
        """
        )
        assert(out == "anon : (lin 2, col 13) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun clo23() {
        val out = all(
            """
            var f = func (^a) {
                func () {
                    ^^a
                }
            }
            var g = do {
                var t = [1]
                drop(f(t))
            }
            println(g())
        """
        )
        assert(out == "anon : (lin 7, col 21) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun clo23x() {
        val out = all(
            """
            var f = func (^a) {
                func () {
                    ^^a
                }
            }
            var g = do {
                var t = [1]
                drop(f(drop(t)))
            }
            println(g())
        """
        )
        assert(out == "[1]\n") { out }
    }
    @Test
    fun clo25_compose() {
        val out = all(
            """
            var comp = func (^f) {
                func (v) {
                    ^^f(v)
                }
            }
            var f = func (x) {
                x
            }
            var ff = comp(f)
            println(ff(2))
        """,
        )
        assert(out == "2\n") { out }
    }
    @Test
    fun clo26_compose() {
        val out = all(
            """
            var comp = func (^f,^g) {
                func (v) {
                    ^^f(^^g(v))
                }
            }
            var f = func (x) {
                x
            }
            var ff = comp(f,f)
            println(ff(2))
        """,
        )
        assert(out == "2\n") { out }
    }
    @Test
    fun clo27_escape_err() {
        val out = all(
            """
            val x = 10
            val f = func (y) {
                val g = func () {
                    y
                }
                drop(g)
            }
            println(f(1)())
            """,
        )
        assert(out == "anon : (lin 7, col 22) : drop error : value is not movable\n") { out }
    }

    //  MEM-GC-REF-COUNT

    @Test
    fun gc0() {
        val out = all(
            """
            do {
                val xxx = []
                nil
            }
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun gc1() {
        val out = all(
            """
            var xxx = []
            set xxx = []
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun gc2() {
        val out = all(
            """
            pass []  ;; not checked
            pass []  ;; not checked
            println(`:number ceu_gc_count`)
        """
        )
        //assert(out == "2\n") { out }
        assert(out == "0\n") { out }
    }
    @Test
    fun gc3_cycle() {
        val out = all(
            """
            var x = [nil]
            var y = [x]
            set x[0] = y
            set x = nil
            set y = nil
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "0\n") { out }
    }
    @Test
    fun gc4() {
        val out = all(
            """
            var x = []
            var y = [x]
            set x = nil
            println(`:number ceu_gc_count`)
            set y = nil
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "0\n2\n") { out }
    }
    @Test
    fun gc5() {
        val out = all(
            """
            var x = []
            do {
                var y = x
            }
            set x = nil
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun gc6() {
        val out = all(
            """
            var x = [[],[]]
            set x = nil
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "3\n") { out }
    }
    @Test
    fun gc7() {
        val out = all(
            """
            var f = func (v) {
                v
            }
            #( #[ f([1]) ] )
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "2\n") { out }
        //assert(out == "0\n") { out }
    }
    @Test
    fun gc8() {
        val out = all(
            """
            do {
                val out = do {
                    val ins = [1,2,3]
                    drop(ins)
                }
                println(`:number ceu_gc_count`)
            }
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "0\n1\n") { out }
    }
    @Test
    fun gc9() {
        val out = all(
            """
            var out
            set out = do {
                var ins
                set ins = [1,2,3]
                ins
            }
            println(out)
        """, true
        )
        assert(out == "anon : (lin 3, col 23) : block escape error : cannot copy reference to outer scope\n" +
                "") { out }
    }
    @Test
    fun gc10() {
        val out = all(
            """
            do {
                do {
                    var v = []
                    drop(v)
                }
                ;; [] not captured, should be checked 
                println(`:number ceu_gc_count`)
            }
            println(`:number ceu_gc_count`)
        """, true
        )
        //assert(out == "1\n1\n") { out }
        assert(out == "0\n0\n") { out }
    }
    @Test
    fun gc11() {
        val out = all(
            """
            var f = func (v) {
                v   ;; not captured, should be checked after call
            }
            f([])   ;; v is not captured
            ;; [] not captured, should be checked 
            println(`:number ceu_gc_count`)
        """
        )
        //assert(out == "anon : (lin 7, col 21) : f([10])\nanon : (lin 3, col 30) : set error : incompatible scopes\n") { out }
        //assert(out == "1\n") { out }
        assert(out == "0\n") { out }
    }
    @Test
    fun gc12() {
        val out = all(
            """
            println([]) ;; println does not check
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "[]\n1\n") { out }
    }
    @Test
    fun gc15_arg() {
        val out = all(
            """
            var f = func (v) {
                nil
            }
            f([])
            println(`:number ceu_gc_count`)
        """
        )
        assert(out == "1\n") { out }
    }

    // MISC

    @Test
    fun id_c() {
        val out = all(
            """
            var xxx
            set xxx = func () {nil}
            println(xxx())
        """
        )
        assert(out == "nil\n") { out }
    }
    @Test
    fun clo1() {
        val out = all(
            """
            var f
            set f = func (^x) {
                func (y) {
                    if ^^x { ^^x } else { y }
                }
            }
            println(f(3)(1))
        """
        )
        assert(out == "3\n") { out }
    }

    // TEMPLATE

    @Test
    fun tplate01_err() {
        val out = all(
            """
            data :T = []
            data :T = []
        """, true
        )
        assert(out == "anon : (lin 3, col 18) : data error : data :T is already declared") { out }
    }
    @Test
    fun tplate02_err() {
        val out = all(
            """
            data :T = []
            var t :T
            println(t.x)
        """, true
        )
        assert(out == "anon : (lin 4, col 23) : index error : undeclared data field :x") { out }
    }
    @Test
    fun tplate03_err() {
        val out = all(
            """
            data :T = []
            var v :U
            println(v)
        """, true
        )
        assert(out == "anon : (lin 3, col 19) : declaration error : data :U is not declared") { out }
        //assert(out == "nil\n") { out }
    }
    @Test
    fun tplate04() {
        val out = all(
            """
            data :T = [x,y]
            var t :T
            set t = [1,2]
            println(t.x, t.y)
        """, true
        )
        assert(out == "1\t2\n") { out }
    }
    @Test
    fun tplate05() {
        val out = all(
            """
            data :T = [x,y]
            var t :T
            set t = [1,2]
            println(t.x, t.y)
        """, true
        )
        assert(out == "1\t2\n") { out }
    }
    @Test
    fun tplate06() {
        val out = all(
            """
            data :T = [x,y]
            data :T.S = [z]
            var s :T.S = [1,2,3]
            println(s.x,s.y,s.z)
        """, true
        )
        assert(out == "1\t2\t3\n") { out }
    }
    @Test
    fun tplate07() {
        val out = all(
            """
            data :T = [x,y]
            data :T.S = [z]
            var s :T.S = [1,2,3]
            var t :T = s
            var x :T.S = t
            println(s)
            println(t)
            println(x)
        """, true
        )
        assert(out == "[1,2,3]\n[1,2,3]\n[1,2,3]\n") { out }
    }
    @Test
    fun tplate08() {
        val out = all(
            """
            data :T = [x,y]
            data :T.S = [z]
            var t :T = tags([], :T, true)
            var s :T.S
            set s = tags([], :T.S, true)
            println(tags(t,:T), tags(t,:T.S))
            println(tags(s,:T), tags(s,:T.S))
        """, true
        )
        assert(out == "true\tfalse\ntrue\ttrue\n") { out }
    }
    @Test
    fun tplate09_err() {
        val out = all(
            """
            data :T = [x,x]
        """, true
        )
        assert(out == "anon : (lin 2, col 18) : data error : found duplicate ids") { out }
    }
    @Test
    fun tplate10_err() {
        val out = all(
            """
            data :T   = [x,y]
            data :T.S = [x]
        """, true
        )
        assert(out == "anon : (lin 3, col 18) : data error : found duplicate ids") { out }
    }
    @Test
    fun tplate11_err() {
        val out = all(
            """
            data :T.S = [x]
        """, true
        )
        assert(out == "anon : (lin 2, col 18) : tag error : parent tag :T is not declared") { out }
    }
    @Test
    fun tplate12_err() {
        val out = all(
            """
            data :T = [x:U]
        """, true
        )
        assert(out == "anon : (lin 2, col 25) : data error : data :U is not declared") { out }
    }
    @Test
    fun tplate12() {
        val out = all(
            """
            data :T = [v]
            data :U = [t:T]
            var u :U = [[10]]
            println(u.t.v)
        """, true
        )
        assert(out == "10\n") { out }
    }
    @Test
    fun tplate13_err() {
        val out = all(
            """
            data :T = [v]
            data :U = [t:T]
            var u :U = [[10]]
            println(u.t.X)
        """, true
        )
        assert(out == "anon : (lin 5, col 25) : index error : undeclared data field :X") { out }
        //assert(out == "anon : (lin 5, col 21) : index error : expected number\n" +
        //        ":error") { out }
    }
    @Test
    fun tplate14_err() {
        val out = all(
            """
            data :T = [v]
            data :U = [t:T]
            var u :U = [[10]]
            println(u.X.v)
        """, true
        )
        assert(out == "anon : (lin 5, col 23) : index error : undeclared data field :X") { out }
    }
    @Test
    fun tplate15_err() {
        val out = all(
            """
            data :T = [v]
            data :U = [t:T,X]
            var u :U = [[10]]
            println(u.X.v)
        """, true
        )
        assert(out == "anon : (lin 5, col 21) : index error : out of bounds\n") { out }
    }
    @Test
    fun tplate16() {
        val out = all(
            """
            data :U = [a]
            data :T = [x,y]
            data :T.S = [z:U]
            var s :T.S
            set s = tags([1,2,tags([3],:U,true)], :T.S, true)
            println(tags(s,:T), tags(s.z,:U))
            set s.z = tags([10], :U, true)
            println(tags(s,:T), tags(s.z,:U))
        """, true
        )
        assert(out == "true\ttrue\ntrue\ttrue\n") { out }
    }
    @Test
    fun tplate17_func() {
        val out = all(
            """
            data :T = [x,y]
            var f = func (t:T) {
                t.x
            }
            println(f([1,99]))
        """, true
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun tplate18_tup() {
        val out = all(
            """
            data :T = [v]
            val t :T = [[1,2,3]]
            println(t.v[1])
        """, true
        )
        assert(out == "2\n") { out }
    }
    @Test
    fun tplate19_err() {
        val out = all(
            """
            val f = func (x :X) { x.s }
        """
        )
        assert(out == "anon : (lin 2, col 29) : declaration error : data :X is not declared") { out }
    }

    // POLY
    /*
    @Test
    fun vv_01_poly_ret_type() {
        val out = all("""
            poly var min
            poly set min :number = 1
            poly set min :char   = 'a'
            var n :number = min
            var c :char   = min
            println(min)
            println(n, c)
        """)
        assert(out == "1\t'a'\n") { out }
    }
    @Test
    fun vv_02_poly_ret_tag() {
        val out = all("""
            poly var min
            poly set min :Nat  = 1
            poly set min :Char = 'a'
            var n :Nat  = min
            var c :Char = min
            println(n, c)
        """)
        assert(out == "[1,'a']\n") { out }
    }
    @Test
    fun vv_02_poly_func_ret() {
        val out = all("""
            poly var read
            poly set read = func () -> :Nat {
                return 1
            }
            poly func read () -> :Char {
                return 'a'
            }
            var n :Nat  = read()
            var c :Char = read()
            println(n, c)
        """, true)
        assert(out == "[1,'a']\n") { out }
    }
    @Test
    fun vv_03_poly_func_args_one() {
        val out = all("""
            poly var f
            poly set f = func (v:Nat) {
                println(:Nat, v)
            }
            poly func f (v:Char) {
                println(:Char, v)
            }
            f(:Nat  [1])
            f(:Char ['a'])
        """, true)
        assert(out == ":Nat\t[1]\n:Char\t['a']\n") { out }
    }
    @Test
    fun vv_04_poly_func_args_one() {
        val out = all("""
            poly var f
            poly func f (v:number) {
                println(:number, v)
            }
            poly set f = func (v:char) {
                println(:char, v)
            }
            f(1)
            f('a')
        """, true)
        assert(out == ":number\t1\n:char\t'a'\n") { out }
    }
    @Test
    fun vv_04_poly_func_args_multi() {
        val out = all("""
            poly var f
            poly func f (x:number, y:number) {
                println(:number, x, :number, y)
            }
            poly set f = func (x:number, y:char) {
                println(:number, x, :char, y)
            }
            f(1, 'a')
            f(1, 2)
        """, true)
        assert(out == ":number\t1\t:char\t'a'\n:number\t1\t:number\t1\n") { out }
    }
    @Test
    fun vv_05_poly_set_nopoly_err() {
        val out = all("""
            var min
            poly set min :number = 1
        """)
        assert(out == "1\t'a'\n") { out }
    }
    */

    // ALL
    @Test
    fun zz_01_sum() {
        val out = all("""
            var sum = func (n) {                                                            
                var i = n                                                                   
                var s = 0                                                                   
                loop {                                                                      
                    break(s) if i == 0
                    set s = s + i                                                           
                    set i = i - 1                                                           
                }                                                                           
            }                                                                               
            println(sum(5))                                                                
        """, true)
        assert(out == "15\n") { out }
    }
    @Test
    fun zz_02_func_scope() {
        val out = all("""
            var f
            set f = func (v) {
                if v == nil {
                    1
                } else {
                    f(v[0])
                }
            }
            val t = [[nil]]
            println(f(t))
        """)
        assert(out == "1\n") { out }
    }
    @Test
    fun zz_03_arthur() {
        val out = all("""
            val tree1 = @[
                (:left, @[
                    (:left, nil),
                    (:right, nil)
                ]),
                (:right, nil)
            ]
            var itemCheck
            set itemCheck = func (tree) {
                if tree == nil {
                    1
                }
                else {
                    itemCheck(tree[:left]) + itemCheck(tree[:right])
                }
            }
            println(itemCheck(tree1))
        """, true)
        assert(out == "3\n") { out }
    }
}
