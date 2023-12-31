import dlex.*
import org.junit.Test

class TParser {

    // EXPR.VAR / EVT / ERR

    @Test
    fun expr_var () {
        val l = lexer(" x ")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Acc && e.tk.str == "x")
    }
    @Test
    fun expr_var_err1 () {
        val l = lexer(" { ")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 2) : expected expression : have \"{\"")
    }
    @Test
    fun expr_var_err2 () {
        val l = lexer("  ")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 3) : expected expression : have end of file")
    }
    @Test
    fun err4 () {
        val l = lexer(" err ")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Acc && e.tk.str == "err")
    }

    // EXPR.PARENS

    @Test
    fun expr_parens() {
        val l = lexer(" ( a ) ")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Acc && e.tk.str == "a")
    }
    @Test
    fun expr_parens_err() {
        val l = lexer(" ( a  ")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 7) : expected \")\" : have end of file")
    }
    @Test
    fun op_prec_err() {
        val l = lexer("println(2 * 3 - 1)")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 15) : binary operation error : expected surrounding parentheses")
    }
    @Test
    fun op_prec_ok() {
        val l = lexer("println(2 * (3 - 1))")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "println({{*}}(2,{{-}}(3,1)))")
    }

    // EXPR.NUM / EXPR.NIL / EXPR.BOOL

    @Test
    fun expr_num() {
        val l = lexer(" 1.5F ")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Num && e.tk.str == "1.5F")
    }
    @Test
    fun expr_nil() {
        val l = lexer("nil")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Nil && e.tk.str == "nil")
    }
    @Test
    fun expr_true() {
        val l = lexer("true")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Bool && e.tk.str == "true")
    }
    @Test
    fun expr_false() {
        val l = lexer("false")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Bool && e.tk.str == "false")
    }
    @Test
    fun expr_char() {
        val l = lexer("'x'")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Char && e.tk.str == "'x'")
    }

    // EXPR.ECALL

    @Test
    fun expr_call1() {
        val l = lexer(" f (1.5F, x) ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Call && e.tk.str=="f" && e.closure is Expr.Acc && e.args.size==2)
    }
    @Test
    fun expr_call2() {
        val l = lexer(" f() ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Call && e.closure.tk.str=="f" && e.closure is Expr.Acc && e.args.size==0)
    }
    @Test
    fun expr_call3() {
        val l = lexer(" f(x,8)() ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Call && e.closure is Expr.Call && e.args.size==0)
        assert(e.tostr() == "f(x,8)()")
    }
    @Test
    fun expr_call_err1() {
        val l = lexer("f (999 ")
        val parser = Parser(l)
        assert(trap { parser.expr_4_suf() } == "anon : (lin 1, col 8) : expected \")\" : have end of file")
    }
    @Test
    fun expr_call_err2() {
        val l = lexer(" f ({ ")
        val parser = Parser(l)
        assert(trap { parser.expr_4_suf() } == "anon : (lin 1, col 5) : expected expression : have \"{\"")
    }

    // TUPLE

    @Test
    fun expr_tuple1() {
        val l = lexer(" [ 1.5F, x] ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Tuple && e.args.size==2)
    }
    @Test
    fun expr_tuple2() {
        val l = lexer("[[],[1,2,3]]")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e.tostr() == "[[],[1,2,3]]")
    }
    @Test
    fun expr_tuple_err() {
        val l = lexer("[{")
        val parser = Parser(l)
        assert(trap { parser.expr_4_suf() } == "anon : (lin 1, col 2) : expected expression : have \"{\"")
    }
    @Test
    fun tuple4() {
        val l = lexer("[1.5F,] ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Tuple && e.args.size==1)
    }

    // DICT

    @Test
    fun dict1() {
        val l = lexer(" @[ (1,x) , (:number,2) ] ")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Dict && e.args.size==2)
    }
    @Test
    fun dict2() {
        val l = lexer("@[(:dict,@[]), (:tuple,[1,2,3])]")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "@[(:dict,@[]),(:tuple,[1,2,3])]")
    }
    @Test
    fun dict3_err() {
        val l = lexer("@[({")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 4) : expected expression : have \"{\"")
    }
    @Test
    fun dict4_err() {
        val l = lexer("@[(")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 4) : expected expression : have end of file")
    }
    @Test
    fun dict5_err() {
        val l = lexer("@[(1,{")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 6) : expected expression : have \"{\"")
    }
    @Test
    fun dict6() {
        val l = lexer("@[(1.5F,1),] ")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Dict && e.args.size==1)
    }
    @Test
    fun dict7_err() {
        val l = lexer("@[(1,1]")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 7) : expected \")\" : have \"]\"")
    }

    // VECTOR

    @Test
    fun vector1() {
        val l = lexer(" #[ 1,x , :number,2 ] ")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Vector && e.args.size==4)
    }
    @Test
    fun vector2() {
        val l = lexer("#[:dict,#[], :tuple,[1,2,3]]")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "#[:dict,#[],:tuple,[1,2,3]]")
    }
    @Test
    fun vector3_err() {
        val l = lexer("#[({")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 4) : expected expression : have \"{\"")
    }
    @Test
    fun vector4() {
        val l = lexer("v[{{#}}(v)]")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "v[{{#}}(v)]") { e.tostr() }
    }

    // EXPR.INDEX / PUB

    @Test
    fun index1() {
        val l = lexer("x[10]")
        val parser = Parser(l)
        val e = parser.expr_4_suf()
        assert(e is Expr.Index && e.col is Expr.Acc && e.idx is Expr.Num)
    }
    @Test
    fun index2_err() {
        val l = lexer("x[10")
        val parser = Parser(l)
        assert(trap { parser.expr_4_suf() } == "anon : (lin 1, col 5) : expected \"]\" : have end of file")
    }
    @Test
    fun index4_err() {
        val l = lexer("x . a")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "x[:a]") { e.tostr() }
        //assert(trap { parser.exprSufs() } == "anon : (lin 1, col 5) : expected \"pub\" : have \"a\"")
    }

    // EXPRS

    @Test
    fun exprs_call() {
        val l = lexer("f ()")
        val parser = Parser(l)
        val es = parser.exprs()
        assert(es.size==1 && es[0] is Expr.Call && es[0].tostr() == "f()")
        assert(es.tostr() == "f()\n")
    }
    @Test
    fun exprs_call_err() {
        val l = lexer("f")
        val parser = Parser(l)
        val es = parser.exprs()
        assert(es.tostr() == "f\n")
    }

    // EXPRS

    @Test
    fun exprs_seq1() {
        val l = lexer("; f () ; g () h()\ni() ;\n;")
        val parser = Parser(l)
        val es = parser.exprs()
        assert(es.tostr() == "f()\ng()\nh()\ni()\n") { es.tostr() }
    }
    @Test
    fun exprs_seq2() {
        val l = lexer("; f () \n (1) ; h()\ni() ;\n;")
        val parser = Parser(l)
        //println(parser.exprs().tostr())
        //assert(es.tostr() == "f()\n1\nh()\ni()\n") { es.tostr() }
        assert(trap { parser.exprs() } == "anon : (lin 2, col 3) : expression error : innocuous expression")
    }
    @Test
    fun exprs_seq2a() {
        val l = lexer("; f () \n pass (1) ; h()\ni() ;\n;")
        val parser = Parser(l)
        // TODO: ambiguous
        val es = parser.exprs()
        assert(es.tostr() == "f()\npass 1\nh()\ni()\n") { es.tostr() }
        //assert(ceu.trap { parser.exprs() } == "anon : (lin 2, col 3) : call error : \"(\" in the next line")
    }
    @Test
    fun exprs_seq3() {
        val l = lexer("var v2\n[tp,v1,v2]")
        val parser = Parser(l)
        // TODO: ambiguous
        val es = parser.exprs()
        assert(es.tostr() == "var v2\n[tp,v1,v2]\n") { es.tostr() }
    }

    // EXPR.DCL

    @Test
    fun expr_dcl_var() {
        val l = lexer("var x")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Dcl && e.tk.str=="var" && e.id.str=="x")
        assert(e.tostr() == "var x")
    }
    @Test
    fun expr_dcl_val() {
        val l = lexer("val x")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Dcl && e.tk.str=="val" && e.id.str=="x")
        assert(e.tostr() == "val x")
    }
    @Test
    fun expr_dcl_err() {
        val l = lexer("var [10]")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 5) : expected identifier : have \"[\"")
    }
    @Test
    fun expr_dcl3() {
        val l = lexer("var x = 1")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Dcl && e.id.str == "x" && e.src is Expr.Num)
        assert(e.tostr() == "var x = 1")
    }

    // EXPR.SET

    @Test
    fun expr_set() {
        val l = lexer("set x = [10]")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Set && e.tk.str == "set")
        assert(e.tostr() == "set x = [10]")
    }
    @Test
    fun expr_err1() {  // set number?
        val l = lexer("set 1 = 1")
        val parser = Parser(l)
        //val e = parser.exprN()
        //assert(e.tostr() == "set 1 = 1")
        assert(trap { parser.expr() } == "anon : (lin 1, col 1) : set error : expected assignable destination")
    }
    @Test
    fun expr_err2() {  // set whole tuple?
        val l = lexer("set [1] = 1")
        val parser = Parser(l)
        //val e = parser.exprN()
        //assert(e.tostr() == "set [1] = 1")
        assert(trap { parser.expr() } == "anon : (lin 1, col 1) : set error : expected assignable destination")
    }
    @Test
    fun set_nil_err() {
        val l = lexer("set nil = nil")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 1) : set error : expected assignable destination")
    }
    @Test
    fun set_if_err() {
        val l = lexer("set (if true {nil} else {nil}) = nil")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 1) : set error : expected assignable destination")
    }
    @Test
    fun set_err_err() {
        val l = lexer("set err = nil")
        val parser = Parser(l)
        assert(parser.expr() is Expr.Set)
        //assert(trap { parser.expr() } == "anon : (lin 1, col 1) : set error : expected assignable destination")
    }

    // IF

    @Test
    fun expr_if1() {  // set whole tuple?
        val l = lexer("if true { 1 } else { 0 }")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.If)
        assert(e.tostr() == "if true {\n1\n} else {\n0\n}") { e.tostr() }
    }
    @Test
    fun expr_if2_err() {
        val l = lexer("if true { 1 }")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.If)
        //assert(trap { parser.expr_prim() } == "anon : (lin 1, col 14) : expected \"else\" : have end of file")
    }

    // DO

    @Test
    fun expr_do1_err() {
        val l = lexer("do{}")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 4) : expected expression : have \"}\"")
    }
    @Test
    fun expr_do2() {
        val l = lexer("do { var a; set a=1; print(a) }")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Do && e.es.size==3)
        assert(e.tostr() == "do {\nvar a\nset a = 1\nprint(a)\n}") { e.tostr() }
    }

    // FUNC

    @Test
    fun expr_func1_err() {
        val l = lexer("func () {}")
        val parser = Parser(l)
        assert(trap { parser.expr_prim() } == "anon : (lin 1, col 10) : expected expression : have \"}\"")
    }
    @Test
    fun expr_func2() {
        val l = lexer("func (a,b) { 10 }")
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Proto && e.args.size==2)
        assert(e.tostr() == "(func (a,b) {\n10\n})") { e.tostr() }
    }
    @Test
    fun pp_06_func_dots() {
        val l = lexer("func (...) { println(...) }")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Proto && e.args.size==1)
        assert(e.tostr() == "(func (...) {\nprintln(...)\n})") { e.tostr() }
    }
    @Test
    fun pp_07_func_args_err() {
        val l = lexer("func (1) { nil }")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 7) : expected identifier : have \"1\"")
    }
    @Test
    fun pp_08_func_args_err() {
        val l = lexer("func (..., a) { nil }")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 10) : expected \")\" : have \",\"")
    }
    @Test
    fun pp_09_func_args_err() {
        val l = lexer("println(...,a)")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 12) : expected \")\" : have \",\"")
    }
    @Test
    fun pp_10_func_args_err() {
        val l = lexer("var ...")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 5) : declaration error : unexpected ...")
    }
    @Test
    fun pp_11_func_args_err() {
        val l = lexer("set ... = 10")
        val parser = Parser(l)
        //val e = parser.expr()
        //assert(e.tostr() == "set ... = 10") { e.tostr() }
        assert(trap { parser.expr() } == "anon : (lin 1, col 5) : set error : unexpected ...")
    }

    // LOOP

    @Test
    fun qq_01_loop_err() {
        val l = lexer("loop { pass nil }")
        val parser = Parser(l)
        val e1 = parser.expr() as Expr.Loop
        assert(e1.body.tostr() == "{\npass nil\n}") { e1.body.tostr() }
    }
    @Test
    fun qq_02_loop_err() {
        val l = lexer("loop until {")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 6) : expected \"{\" : have \"until\"")
        //assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 12) : expected expression : have \"{\"")
    }

    // BREAK

    @Test
    fun rr_01_break_err() {
        val l = lexer("break")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 6) : expected \"if\" : have end of file")
    }
    @Test
    fun rr_02_break_err() {
        val l = lexer("break 1")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 7) : expected \"if\" : have \"1\"")
    }
    @Test
    fun rr_03_break_err() {
        val l = lexer("break (1)")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 10) : expected \"if\" : have end of file")
    }
    @Test
    fun rr_04_break_err() {
        val l = lexer("break (1) if")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 13) : expected expression : have end of file")
    }
    @Test
    fun rr_05_break() {
        val l = lexer("break (1) if true")
        val parser = Parser(l)
        val e = parser.expr() as Expr.Break
        assert(e.tostr() == "break(1) if true") { e.tostr() }
    }

    // NATIVE

    @Test
    fun native1() {
        val l = lexer(
            """
            ```
                printf("xxx\n");
            ```
        """.trimIndent()
        )
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Nat)
        assert(e.tostr() == "``` \n    printf(\"xxx\\n\");\n```") { "."+e.tostr()+"." }
    }
    @Test
    fun native2_err() {
        val l = lexer(
            """
            native ``
                printf("xxx\n");
            ```
        """.trimIndent()
        )
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 3, col 1) : native error : expected \"``\"")
    }
    @Test
    fun native3_err() {
        val l = lexer(
            """
            native ``
                printf("xxx\n");
        """
        )
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 4, col 9) : native error : expected \"``\"")
    }
    @Test
    fun native4_err() {
        val l = lexer(
            """
            native `:
        """.trimIndent()
        )
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 8) : tag error : expected identifier")
    }
    @Test
    fun native5() {
        val l = lexer(
            """
            ```:ola```
        """.trimIndent()
        )
        val parser = Parser(l)
        val e = parser.expr_prim()
        assert(e is Expr.Nat)
        assert(e.tostr() == "```:ola ```") { "."+e.tostr()+"." }
    }

    // BINARY / UNARY / OPS

    @Test
    fun bin1_err() {
        val l = lexer("(10+)")
        val parser = Parser(l)
        assert(trap { parser.expr_1_bin() } == "anon : (lin 1, col 5) : expected expression : have \")\"")
    }
    @Test
    fun bin2() {
        val l = lexer("10+1")
        val parser = Parser(l)
        val e = parser.expr_1_bin()
        assert(e is Expr.Call)
        assert(e.tostr() == "{{+}}(10,1)") { e.tostr() }
    }
    @Test
    fun bin3() {
        val l = lexer("10/=1")
        val parser = Parser(l)
        val e = parser.expr_1_bin()
        assert(e is Expr.Call)
        assert(e.tostr() == "{{/=}}(10,1)") { e.tostr() }
    }
    @Test
    fun pre1() {
        val l = lexer("- - 1")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Call)
        assert(e.tostr() == "{{-}}({{-}}(1))") { e.tostr() }
    }
    @Test
    fun bin4() {
        val l = lexer("(1 + 2) + 3")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e is Expr.Call)
        assert(e.tostr() == "{{+}}({{+}}(1,2),3)") { e.tostr() }
    }
    @Test
    fun pre_pos1() {
        val l = lexer("-x[0]")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr() == "{{-}}(x[0])") { e.tostr() }
    }

    // ENUM

    @Test
    fun enum01() {
        val l = lexer(
            """
            enum {
                :x = `1000`,
                :y, :z,
                :a = `10`,
                :b, :c
            }
        """
        )
        val parser = Parser(l)
        val e = parser.exprs()
        assert(e.tostr() == "enum {\n:x = `1000`,\n:y,\n:z,\n:a = `10`,\n:b,\n:c\n}\n") { e.tostr() }
    }
    @Test
    fun enum02_err() {
        val l = lexer(
            """
            enum { :x=1 }
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 23) : expected native : have \"1\"")
    }
    @Test
    fun enum03_err() {
        val l = lexer(
            """
            enum { :x, 1 }
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 24) : expected tag : have \"1\"")
    }

    // TEMPLATE

    @Test
    fun tplate00() {
        val l = lexer(
            """
            data :T = [x,y]
        """
        )
        val parser = Parser(l)
        val e = parser.exprs()
        assert(e.tostr() == "data :T = [x,y]\n") { e.tostr() }
    }
    @Test
    fun tplate01() {
        val l = lexer(
            """
            var t :T = [1,2]
        """
        )
        val parser = Parser(l)
        val e = parser.exprs()
        assert(e.tostr() == "var t :T = [1,2]\n") { e.tostr() }
    }
    @Test
    fun tplate02_err() {
        val l = lexer(
            """
            data X [x,y]
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 18) : expected tag : have \"X\"")
    }
    @Test
    fun tplate03_err() {
        val l = lexer(
            """
            data :X [x,y]
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 21) : expected \"=\" : have \"[\"")
    }
    @Test
    fun tplate04_err() {
        val l = lexer(
            """
            data :X = nil
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 23) : expected \"[\" : have \"nil\"")
    }
    @Test
    fun tplate05_err() {
        val l = lexer(
            """
            data :X = [1,2]
        """
        )
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 2, col 24) : expected identifier : have \"1\"")
    }
    @Test
    fun tplate06() {
        val l = lexer(
            """
            data :U = [t:T]
        """
        )
        val parser = Parser(l)
        val e = parser.exprs()
        assert(e.tostr() == "data :U = [t:T]\n") { e.tostr() }
    }

    // POLY
    /*
    @Test
    fun gg_01_poly_err() {
        val l = lexer("poly x")
        val parser = Parser(l)
        assert(trap { parser.expr() } == "anon : (lin 1, col 6) : poly error : expected var or set")
    }
    @Test
    fun gg_02_poly_err() {
        val l = lexer("poly var x = 1")
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 1, col 12) : expected expression : have \"=\"")
    }
    @Test
    fun gg_03_poly() {
        val l = lexer("poly var x")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr(false) == "poly var x") { e.tostr() }
    }
    @Test
    fun gg_04_poly_err() {
        val l = lexer("poly set x = 1")
        val parser = Parser(l)
        assert(trap { parser.exprs() } == "anon : (lin 1, col 14) : expected \"func\" : have \"1\"")
    }
    @Test
    fun gg_05_poly_set_tag() {
        val l = lexer("poly set min :number = 1")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr(false) == "set min[:number] = 1") { e.tostr() }
    }
    @Test
    fun gg_06_poly_set_func() {
        val l = lexer("poly set f = func () { nil }")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr(false) == "poly var x") { e.tostr() }
    }
    @Test
    fun gg_07_poly_set_err() {
        val l = lexer("poly set f.x :number = 10")
        val parser = Parser(l)
        val e = parser.expr()
        assert(e.tostr(false) == "poly var x") { e.tostr() }
    }
    */
}
