package dlex

class Static (outer: Expr.Do, val ups: Ups, val vars: Vars) {
    val unused: MutableSet<Expr.Dcl> = mutableSetOf()
    val cons: MutableSet<Expr.Do> = mutableSetOf() // block has at least 1 constructor

    init {
        outer.traverse()
    }

    fun Expr.traverse () {
        when (this) {
            is Expr.Proto  -> {
                cons.add(ups.first_block(this)!!)
                this.body.traverse()
            }
            is Expr.Do     -> this.es.forEach { it.traverse() }
            is Expr.Dcl    -> {
                unused.add(this)
                this.src?.traverse()
            }
            is Expr.Set    -> {
                this.dst.traverse()
                this.src.traverse()
                if (this.dst is Expr.Acc) {
                    val (_,dcl) = vars.get(this.dst)
                    if (dcl.tk.str == "val") {
                        err(this.tk, "set error : destination is immutable")
                    }
                }
            }
            is Expr.If     -> { this.cnd.traverse() ; this.t.traverse() ; this.f.traverse() }
            is Expr.Loop   -> {
                this.body.es.last().let {
                    if (it.is_innocuous()) {
                        //err(it.tk, "expression error : innocuous expression")
                        TODO("never reachable - checked in parser - remove in the future")
                    }
                }
                this.body.traverse()
            }
            is Expr.Break  -> {
                if (ups.pub[this] is Expr.Do && ups.pub[ups.pub[this]] is Expr.Loop) {
                    // ok
                } else {
                    err(this.tk, "break error : expected parent loop")
                }
                this.cnd.traverse()
                this.e?.traverse()
            }
            is Expr.Enum   -> {}
            is Expr.Data   -> {}
            is Expr.Pass   -> this.e.traverse()
            is Expr.Drop   -> this.e.traverse()

            is Expr.Nat    -> {}
            is Expr.Acc    -> {
                val (_,dcl) = vars.get(this)
                unused.remove(dcl)
                if (this.tk.str == "_") {
                    err(this.tk, "access error : cannot access \"_\"")
                }
            }
            is Expr.Nil    -> {}
            is Expr.Tag    -> {}
            is Expr.Bool   -> {}
            is Expr.Char   -> {}
            is Expr.Num    -> {}
            is Expr.Tuple  -> {
                cons.add(ups.first_block(this)!!)
                this.args.forEach{ it.traverse() }
            }
            is Expr.Vector -> {
                cons.add(ups.first_block(this)!!)
                this.args.forEach{ it.traverse() }
            }
            is Expr.Dict   -> {
                cons.add(ups.first_block(this)!!)
                this.args.forEach { it.first.traverse() ; it.second.traverse() }
            }
            is Expr.Index  -> {
                this.col.traverse()
                this.idx.traverse()
            }
            is Expr.Call   -> {
                this.closure.traverse()
                this.args.forEach { it.traverse() }
            }
        }
    }
}
