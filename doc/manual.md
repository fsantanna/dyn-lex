# dyn-lex

* DESIGN
    * Lexical Memory Management
    * Hierarchical Tags
* LEXICON
    * Keywords
    * Symbols
    * Operators
    * Identifiers
    * Literals
    * Comments
* TYPES
    * Basic Types
        - `nil` `bool` `char` `number` `pointer` `tag`
    * Collections
        - `tuple` `vector` `dict`
    * Functions
        - `func`
    * User Types
* VALUES
    * Literal Values
        - `nil` `bool` `tag` `number` `char` `pointer`
    * Dynamic Values
        - `tuple` `vector` `dict` `func` `coro` `task`
* STATEMENTS
    * Program, Sequences and Blocks
        - `;` `do` `drop` `pass`
    * Variables and Declarations
        - `val` `var` `...`
    * Assignments
        - `set`
    * Tag Enumerations and Tuple Templates
        - `enum` `data`
    * Calls, Operations and Indexing
        - `f(...)` `x+y` `t[...]` `t.x`
    * Conditionals and Loops
        - `if` `loop`
* STANDARD LIBRARY
    * Equality Operators
        - `/=` `==`
    * Logical Operators
        - `not` `and` `or`
    * Types and Tags
        - `sup?` `tags` `type`
    * Dictionary Next
        - `next`
    * Conversions
        - `to-number` `to-string` `to-tag`
    * Print
        - `print` `println`
* SYNTAX

<!-- CONTENTS -->

# DESIGN

## Lexical Memory Management

`dyn-lex` respects the lexical structure of the program also when dealing with
dynamic memory allocation.
When a [dynamic value](#dynamic-values) is first assigned to a variable, it
becomes attached to the [block](#block) in which the variable is declared, and
the value cannot escape that block in further assignments or as return
expressions.
This is valid not only for [collections](#constructors) (tuples, vectors, and
dictionaries), but also for [closures](#prototypes).
This restriction ensures that terminating blocks deallocate all memory at once.
*More importantly, it provides static means to reason about the program.*
To overcome this restriction, `dyn-lex` also provides an explicit [drop](#drop)
operation to deattach a dynamic value from its block.

The next example illustrates lexical memory management and the validity of
assignments:

```
var x1 = [1,2,3]
val x2 = do {
    val y1 = x1         ;; ok, scope of x1>y1
    val y2 = [4,5,6]
    set x1 = y2         ;; no, scope of y2<x1
    [7,8,9]             ;; ok, tuple not yet assigned
}                       ;; deallocates [4,5,6], but not [7,8,9]
```

The assignment `y1=x1` is valid because the tuple `[1,2,3]` held in `x1` is
guaranteed to be in memory while `y1` is visible.
However, the assignment `x1=y2` is invalid because the tuple `[4,5,6]` held in
`y2` is deallocated at the end of the block, but `x1` remains visible.

The next example uses `drop` to reattach a local vector to an outer scope:

```
val to-vector = func (n) {  ;; vector with n items
    val ret = #[]           ;; vector is allocated locally
    var i = 0
    loop {
        if i == n {
            break
        } else {
            nil
        }
        set i = i + 1
        set ret[#ret] = i   ;; each value is appended to vector
    }
    drop(ret)               ;; local vector is moved out
}
```

The function `to-vector` receives a number `n`, and returns a vector from `1`
to `n`.
Since the vector `ret` is allocated inside the function, it requires an
explicit `drop` to reattach it to the caller scope.

Note that values of the [basic types](#basic-types), such as numbers, have no
assignment restrictions because they are copied as a whole.
Note also that `dyn-lex` still supports garbage collection for dynamic values
to handle references in long-lasting blocks.

## Hierarchical Tags

A [tag](#basic-type) is a basic type of `dyn-lex` that represents unique values
in a human-readable form.
Tags are also known as *symbols* or *atoms* in other programming languages.
Any identifier prefixed with a colon (`:`) is a valid tag that is guaranteed to
be unique in comparison to others (i.e., `:x == :x` and `:x /= :y`).
Just like the number `10`, the tag `:x` is a value in itself and needs not to
be declared.
Tags are typically used as keys in dictionaries (e.g., `:x`, `:y`), or as
enumerations representing states (e.g., `:pending`, `:done`).

The next example uses tags as keys in a dictionary:

```
val pos = @[]               ;; a new dictionary
set pos[:x] = 10
set pos.y   = 20            ;; equivalent to pos[:y]=20
println(pos.x, pos[:y])     ;; --> 10, 20
```

Tags can also be used to "tag" dynamic objects, such as dictionaries and
tuples, to support the notion of user types in `dyn-lex`.
For instance, the call `tags(pos,:Pos,true)` associates the tag `:Pos` with the
value `pos`, such that the query `tags(pos,:Pos)` returns `true`.

In `dyn-lex`, tag identifiers using dots (`.`) can describe user type
hierarchies.
For instance, a tag such as `:T.A.x` matches the types `:T`, `:T.A`, and
`:T.A.x` at the same time, as verified by function `sup?`:

```
sup?(:T,     :T.A.x)            ;; --> true
sup?(:T.A,   :T.A.x)            ;; --> true
sup?(:T.A.x, :T.A.x)            ;; --> true
sup?(:T.A.x, :T)                ;; --> false
sup?(:T.A,   :T.B)              ;; --> false
```

The next example illustrates hierarchical tags combined with the function
`tags`:

```
val x = []                  ;; an empty tuple
tags(x, :T.A, true)         ;; x is of user type :T.A
println(tags(x,:T))         ;; --> true
println(tags(x,:T.A))       ;; --> true
println(tags(x,:T.B))       ;; --> false
```

In the example, `x` is set to user type `:T.A`, which is compatible with types
`:T` and `:T.A`, but not with type `:T.B`.

### Hierarchical Tuple Templates

`dyn-lex` also provides a `data` construct to associate a tag with a tuple
template that enumerates field identifiers.
Templates provide field names for tuples, which become similar to *structs* in
C or *classes* in Java.
Each field identifier in the data declaration corresponds to a numeric index in
the tuple, which can then be indexed by field or by number interchangeably.
The next example defines a template `:Pos`, which serves the same purpose as
the dictionary of the first example:

```
data :Pos = [x,y]       ;; a template `:Pos` with fields `x` and `y`
val pos :Pos = [10,20]  ;; declares that `pos` satisfies template `:Pos`
println(pos.x, pos.y)   ;; --> 10, 20
```

In the example, `pos.x` is equivalent to `pos[0]`, and `pos.y` is equivalent to
`pos[1]`.

The template mechanism of `dyn-lex` can also describe a tag hierarchy to
support data inheritance, akin to class hierarchies in Object-Oriented
Programming.
A `data` description can be suffixed with a block to nest templates, in which
inner tags reuse fields from outer tags.
The next example illustrates an `:Event` super-type, in which each sub-type
appends additional data to the tuple template:

```
data :Event = [ts]                  ;; All events carry a timestamp
data :Event.Key = [key]             ;; :Event.Key [ts,key] is a sub-type of :Event [ts]
data :Event.Mouse = [pos :Pos]      ;; :Event.Mouse [ts, pos :Pos]
data :Event.Mouse.Motion = []       ;; :Event.Mouse.Motion [ts, pos :Pos]
data :Event.Mouse.Button = [but]    ;; :Event.Mouse.Button [ts, pos :Pos, but]

val but :Event.Mouse.Button = ;; [ts,[x,y],but]
    tags([0, [10,20], 1], :Event.Mouse.Button, true)
println(but.ts, but.pos.y, tags(but,:Event.Mouse)) ;; --> 0, 20, true
```

The last lines declares `but` as a mouse button template to access `ts` and
`pos`, and also tags its constructor with the appropriate user type, passing
the `tags` test below.

<!--
 ## Integration with C

The compiler of `dyn-lex` converts an input program into an output in C, which is
further compiled to a final executable file.
For this reason, `dyn-lex` has source-level compatibility with C, allowing it to
embed native expressions in programs.

- gcc
- :pre
- $x.Tag
- tag,char,bool,number C types
- C errors
-->

# LEXICON

## Keywords

Keywords cannot be used as [variable identifiers](#identifiers).

The following keywords are reserved in `dyn-lex`:

```
    and                 ;; and operator                     (00)
    break               ;; break loop
    data                ;; data declaration
    do                  ;; do block
    else                ;; else block
    enum                ;; enum declaration
    false               ;; false value
    func                ;; function prototype
    if                  ;; if block
    loop                ;; loop block
    nil                 ;; nil value                        (10)
    not                 ;; not operator
    or                  ;; or operator
    pass                ;; innocuous expression
    poly                ;; TODO
    set                 ;; assign expression
    true                ;; true value
    val                 ;; constant declaration
    var                 ;; variable declaration             (18)
```

## Symbols

The following symbols are reserved in `dyn-lex`:

```
    {   }           ;; block/operators delimeters
    (   )           ;; expression delimeters
    [   ]           ;; index/constructor delimeters
    =               ;; assignment separator
    ;               ;; sequence separator
    ,               ;; argument/constructor separator
    .               ;; index/field discriminator
    ...             ;; variable function/program arguments
    #[              ;; vector constructor
    @[              ;; dictionary constructor
    '   "   `       ;; character/string/native delimiters
    $               ;; native interpolation
    ^               ;; lexer annotation/upvalue modifier
```

## Operators

The following operator symbols can be combined to form operator names in `dyn-lex`:

```
    +    -    *    /
    >    <    =    !
    |    &    ~    %
    #    @
```

Operators names cannot clash with reserved symbols (e.g., `->`).

Examples:

```
|>
<|
+++
```

The following identifiers are also reserved as special operators:

```
    not     and     or
```

Operators can be used in prefix or infix notations in
[operations](#calls-and-operations).

## Identifiers

`dyn-lex` uses identifiers to refer to variables and operators:

```
ID : [^|^^] [A-Za-z_][A-Za-z0-9_'?!-]*  ;; letter/under/digit/quote/quest/excl/dash
   | `{´ OP `}´                         ;; operator enclosed by braces as identifier
OP : [+-*/><=!|&~%#@]+                  ;; see Operators
```

A variable identifier starts with a letter or underscore (`_`) and is followed
by letters, digits, underscores, single quotes (`'`), question marks (`?`),
exclamation marks (`!`), or dashes (`-`).
A dash must be followed by a letter or digit.
Identifiers can be prefixed with carets (`^` or `^^`), which denote
[closure](#prototypes) access modifiers.

Note that dashes are ambiguous with the minus operator.
For this reason, (i) the minus operation requires spaces between operands
(e.g., `x - 1`), and (ii) variables with common parts in identifiers are
rejected (e.g., `x` vs `x-1` vs `a-x`).

An operator identifier is a sequence of operator symbols
(see [Operators](#operators)).
An operator can be used as a variable identifier when enclosed by braces (`{`
and `}`).

Examples:

```
x               ;; simple var id
my-value        ;; var with dash
empty?          ;; var with question
map'            ;; var with prime
>               ;; simple op id
++              ;; op with multi chars
{{-}}             ;; op as var id
```

## Literals

Ceu provides literals for *nil*, *booleans*, *tags*, *numbers*, *characters*,
*strings*, and *native expressions*:

```
NIL  : nil
BOOL : true | false
TAG  : :[A-Za-z0-9\.\-]+      ;; colon + leter/digit/dot/dash
NUM  : [0-9][0-9A-Za-z\.]*    ;; digit/letter/dot
CHR  : '.' | '\.'             ;; single/backslashed character
STR  : ".*"                   ;; string expression
NAT  : `.*`                   ;; native expression
```

The literal `nil` is the single value of the [*nil*](#basic-types) type.

The literals `true` and `false` are the only values of the [*bool*](#basic-types)
type.

A [*tag*](#basic-types) type literal starts with a colon (`:`) and is followed
by letters, digits, dots (`.`), or dashes (`-`).
A dot or dash must be followed by a letter or digit.

A [*number*](#basic-types) type literal starts with a digit and is followed by
digits, letters, and dots (`.`), and is represented as a *C float*.

A [*char*](#basic-types) type literal is a single or backslashed (`\`)
character enclosed by single quotes (`'`), and is represented as a *C char*.

A string literal is a sequence of characters enclosed by double quotes (`"`).
It is expanded to a [vector](#collections) of character literals, e.g., `"abc"`
expands to `#['a','b','c']`.

A native literal is a sequence of characters interpreted as C code enclosed by
multiple back quotes (`` ` ``).
The same number of backquotes must be used to open and close the literal.
Native literals are detailed further.

All literals are valid [values](#values) in `dyn-lex`.

Examples:

```
nil                 ;; nil literal
false               ;; bool literal
:X.Y                ;; tag literal
1.25                ;; number literal
'a'                 ;; char literal
"Hello!"            ;; string literal
`puts("hello");`    ;; native literal
```

### Tags

The following tags are pre-defined in `dyn-lex`:

```
    :nil :tag :bool :char :number :pointer  ;; basic types
    :func                                   ;; function
    :tuple :vector :dict                    ;; collections
    :tmp                                    ;; temporary variable
    :ceu                                    ;; ceu value
```

### Native Literals

A native literal can specify a tag modifier as follows:

```
`:<type> <...>`
`:ceu <...>`
`<...>`
```

The `:<type>` modifier assumes the C code in `<...>` is an expression of the
given type and converts it to `dyn-lex`.
The `:ceu` modifier assumes the code is already a value in `dyn-lex` and does not
convert it.
The lack of a modifier also assumes a C statement, but to be inlined at the
current position.

Native literals can include `dyn-lex` expressions with an identifier prefixed by
dollar sign (`$`) suffixed by dot (`.`) with one of the desired types:
    `.Tag`, `.Bool`, `.Char`, `.Number`, `.Pointer`.

Examples:

```
val n = `:number 10`            ;; native 10 is converted to `dyn-lex` number
val x = `:ceu $n`               ;; `x` is set to `dyn-lex` `n` as is
`printf("> %f\n", $n.Number);`  ;; outputs `n` as a number
```

## Comments

`dyn-lex` provides single-line and multi-line comments.

Single-line comments start with double semi-colons (`;;`) and run until the end
of the line.

Multi-line comments use balanced semi-colons, starting with three or more
semi-colons and running until the same number of semi-colons.
Multi-line comments can contain sequences of semi-colons, as long as they are
shorter than the opening sequence.

Examples:

```
;; a comment        ;; single-line comment
;;;                 ;; multi-line comment
;; a
;; comment
;;;
```

# TYPES

`dyn-lex` is a dynamic language in which values carry their own types during
execution.

The function `type` returns the type of a value as a [tag](#basic-types):

```
type(10)    ;; --> :number
type('x')   ;; --> :char
```

## Basic Types

`dyn-lex` has 6 basic types:

```
nil    bool    char    number    tag    pointer
```

The `nil` type represents the absence of values with its single value
[`nil`](#literals).

The `bool` type represents boolean values with [`true`](#literals) and
[`false`](#literals).
In a boolean context, `nil` and `false` are interpreted as `false` and all
other values from all other types are interpreted as `true`.

The `char` type represents [character literals](#literals).

The `number` type represents real numbers (i.e., *C floats*) with
[number literals](#literals).

The `tag` type represents [tag identifiers](#literals).
Each tag is internally associated with a natural number that represents a
unique value in a global enumeration.
Tags can be explicitly [enumerated](#tag-enumerations-and-tuple-templates) to
interface with [native expressions](#literals).
Tags can form [hierarchies](#hierarchical-tags) to represent
[user types](#user-types) and describe
[tuple templates](#tag-enumerations-and-tuple-templates).

The `pointer` type represents opaque native pointer values from [native
literals](#literals).

## Collections

`dyn-lex` provides 3 collection types:

```
tuple    vector    dict
```

The `tuple` type represents a fixed collection of heterogeneous values, in
which each numeric index, starting at `0`, holds a value of a (possibly)
different type.

The `vector` type represents a variable collection of homogeneous values, in
which each numeric index, starting at `0`,  holds a value of the same type.

The `dict` type (dictionary) represents a variable collection of heterogeneous
values, in which each index (or key) of any type maps to a value of a
(possibly) different type.

Examples:

```
[1, 'a', nil]           ;; a tuple with 3 values
#[1, 2, 3]              ;; a vector of numbers
@[(:x,10), (:y,20)]     ;; a dictionary with 2 mappings
```

## Functions

`dyn-lex` provides a function type:

```
func
```

The `func` type represents [function prototypes](#prototypes), which provides
limited form of closures.

## User Types

Values from non-basic types (i.e., collections and functions) can be associated
with [tags](#basic-types) that represent user types.

The function [`tags`](#types-and-tags) associates tags with values, and also
checks if a value is of the given tag:

```
val x = []              ;; an empty tuple
tags(x, :T, true)       ;; x is now of user type :T
println(tags(x,:T))     ;; --> true
```

Tags form [type hierarchies](hierarchical-tags) based on the dots in their
identifiers, i.e., `:T.A` and `:T.B` are sub-types of `:T`.
Tag hierarchies can nest up to 4 levels.

The function [`sup?`](#types-and-tags) checks super-type relations between
tags:

```
sup?(:T, :T.A)      ;; --> true
sup?(:T.A, :T)      ;; --> false
sup?(:T.A, :T.B)    ;; --> false
```

User types do not require to be predeclared, but can appear in [tuple
template](#tag-enumerations-and-tuple-templates) declarations.

# VALUES

As a dynamic language, each value in `dyn-lex` carries extra information, such
as its own type.

## Literal Values

A *literal value* does not require dynamic allocation since it only carries
extra information about its type.
All [basic types](#basic-types) have [literal](#literals) values:

```
Types : nil | bool | char | number | pointer | tag
Lits  : `nil´ | `false´ | `true´ | TAG | NUM | CHR | STR | NAT
```

Literals are immutable and are copied between variables and blocks as a whole
without any restrictions.

## Dynamic Values

A *dynamic value* requires dynamic allocation since its internal data is too
big to fit in a literal value.
The following types have dynamic values:

```
Dyns : tuple | vector | dict | func
```

Dynamic values are mutable and are manipulated through references, allowing
that multiple aliases refer to the same value.

Dynamic values are always attached to the enclosing [block](#blocks) in which
they were first assigned, and cannot escape to outer blocks in further
assignments or as return expressions.
This restriction permits that terminating blocks deallocate all dynamic values
attached to them.

`dyn-lex` also provides an explicit [drop](#drop) operation to reattach a
dynamic value to an outer scope.

Nevertheless, a dynamic value is still subject to garbage collection, given
that it may loose all references to it, even with its enclosing block active.

### Constructors

`dyn-lex` provides constructors for [collections](#collections) to allocate
tuples, vectors, and dictionaries:

```
Cons : `[´ [List(Expr)] `]´             ;; tuple
     | `#[´ [List(Expr)] `]´            ;; vector
     | `@[´ [List(Key-Val)] `]´         ;; dictionary
            Key-Val : ID `=´ Expr
                    | `(´ Expr `,´ Expr `)´
```

Tuples (`[...]`) and vectors (`#[...]`) are built providing a list of
expressions.

Dictionaries (`@[...]`) are built providing a list of pairs of expressions
(`(key,val)`), in which each pair maps a key to a value.
The first expression is the key, and the second is the value.
If the key is a tag, the alternate syntax `tag=val` may be used (omitting the
tag `:`).

Examples:

```
[1,2,3]             ;; a tuple
#[1,2,3]            ;; a vector
@[(:x,10), x=10]    ;; a dictionary with equivalent key mappings
```

### Prototypes

`dyn-lex` supports function prototypes as values:

```
Func : `func´ [`(´ [List(ID)] [`...´] `)´] Block
```

The keyword `func` is followed by a list of identifiers as parameters enclosed
by parenthesis.
The last parameter can be the symbol [`...`](#variables-and-declarations),
which captures as a tuple all remaining arguments of a call.

The associated block executes when the unit is [invoked](#TODO).
Each argument in the invocation is evaluated and copied to the parameter
identifier, which becomes a local variable in the execution block.

`dyn-lex` supports a restricted form of closures, in which *upvalues* must be
explicit and final.
A closure is a prototype that accesses variables from blocks that terminate,
but which the closure escapes and survives along with these variables, known as
*upvalues*.
Upvalues must be explicitly declared and accessed with the caret prefix (`^`),
and cannot be modified (declarations must use the modifier
[`val`](#variables-and-declarations)).
Finally, inside closures the accesses must be prefixed with double carets
(`^^`).

Examples:

```
val F = func (^v1) {        ;; v1 survives func
    val ^v2 = ^v1 + 1       ;; v2 survives func (outside closure: single caret)
    func (v3) {             ;; closure survives func
        ^^v1 + ^^v2 + v3    ;; (inside closure: double caret)
    }
}
val f = F(5)
println(f(4))               ;; --> 15
```

# STATEMENTS

`dyn-lex` is an expression-based language in which all statements are expressions and
evaluate to a value.

## Program, Sequences and Blocks

A program in `dyn-lex` is a sequence of statements (expressions), and a block is a
sequence of expressions enclosed by braces (`{` and `}´):

```
Prog  : { Expr [`;´] }
Block : `{´ { Expr [`;´] } `}´
```
Each expression in a sequence may be separated by an optional semicolon (`;´).
A sequence of expressions evaluate to its last expression.

The symbol [`...`](#variables-and-declarations) stores the program arguments
as a tuple.

### Blocks

A block delimits a lexical scope for variables and dynamic values:
A variable is only visible to expressions in the block in which it was
declared.
A dynamic value cannot escape the block in which it was created (e.g., from
assignments or returns), unless it is [dropped](#drop) out.
For this reason, when a block terminates, all memory that was allocated inside
it is automatically reclaimed.
This is also valid for active [coroutines](#active-values) and
[tasks](#active-values), which are attached to the block in which they were
first assigned, and are aborted on termination.

A block is not an expression by itself, but it can be turned into one by
prefixing it with an explicit `do`:

```
Do : `do´ Block         ;; an explicit block statement
```

Blocks also appear in compound statements, such as
[conditionals](#conditionals), [loops](#loops), and many others.

Examples:

```
do {                    ;; block prints :ok and evals to 1
    println(:ok)
    1
}

do {
    val a = 1           ;; `a` is only visible in the block
}
pass a                  ;; ERR: `a` is out of scope

var x
do {
    val y = [1,2,3]
    set x = y           ;; ERR: local tuple cannot be assigned to outer block
    y                   ;; ERR: local tuple cannot return from block
}

do {
    val y = #[1,2,3]
    drop(y)             ;; OK: return dropped local vector
}
```

### Drop

```
Drop : `drop´ `(´ Expr `)´
```

The `drop` operation [deattaches](#lexical-memory-management) the given value
from its current [block](#blocks), allowing it to be reattached to an outer
scope.
A `drop` applies recursively to nested values.

A `drop` only applies to [assignable expressions](#assignments), which are
automatically set to `nil` once dropped.

A `drop` only applies to values that have a single reference.

Examples:

```
val v = 10
drop(v)             ;; --> 10 (innocuous drop)

val u = do {
    val t = [10]
    drop(t)         ;; --> [10] (deattaches from `t`, reattaches to `u`)
}

val t = [1,2,3]
f(drop(t))          ;; `f` releases `t`
println(t)          ;; --> nil

do {
    val t1 = [1,2,3]
    val t2 = t1
    drop(t1)        ;; ERR: `t1` has multiple references
}
```

### Pass

The `pass` statement permits that an innocuous expression is used in the
middle of a block:

```
Pass : `pass´ Expr
```

Examples:

```
do {
    1           ;; ERR: innocuous expression
    pass 1      ;; OK:  innocuous but explicit
    ...
}
```

## Variables and Declarations

Regardless of being dynamically typed, all variables in `dyn-lex` must be
declared before use:

```
Val  : `val´ ID [TAG] [`=´ Expr]         ;; constants
Var  : `var´ ID [TAG] [`=´ Expr]         ;; variables
Args : `...´
```

The difference between `val` and `var` is that a `val` is immutable, while a
`var` declaration can be modified by further `set` statements.

The optional initialization expression assigns an initial value to the
variable, which is set to `nil` otherwise.

The `val` modifier forbids that a name is reassigned, but it does not prevent
that [dynamic values](#dynamic-values) are modified.

Optionally, a declaration can be associated with a [tuple
template](#tag-enumerations-and-tuple-templates) tag, which allows the variable
to be indexed by a field name, instead of a numeric position.
Note that the variable is not guaranteed to hold a value matching the template,
not even a tuple is guaranteed.
The template association is static but with no runtime guarantees.

The symbol `...` represents the variable arguments (*varargs*) a function
receives in a call.
In the context of a [function](#prototypes) that expects varargs, it evaluates
to a tuple holding the varargs.
In other scenarios, it evaluates to a tuple holding the program arguments.
When `...` is the last argument of a call, its tuple is expanded as the last
arguments.

`TODO: :tmp`

Examples:

```
val v = [10]            ;; OK
set v = 0               ;; ERR: cannot reassign `v`
set v[0] = 20           ;; OK

data :Pos = [x,y]
val pos1 :Pos = [10,20] ;; (assumes :Pos has fields [x,y])
println(pos1.x)         ;; --> 10
```

## Assignments

An assignments modifies the value of [variables](variables-and-declarations)
and [indexes](indexes-and-fields):

```
Set : `set´ Expr `=´ Expr
```

Examples:

```
var x = 10
set x = 20              ;; OK

val y = [10]
set y = 0               ;; ERR: cannot reassign `y`
set y[0] = 20           ;; OK

set 10 = 29             ;; ERR: not variable or index
```

## Tag Enumerations and Tuple Templates

Tags are global identifiers that need not to be predeclared.
However, they may be explicitly declared when used as enumerations or tuple
templates.

### Tag Enumerations

An `enum` groups related tags in sequence so that they are associated with
numbers in the same order:

```
Enum : `enum´ `{´ List(TAG [`=´ Expr]) `}´
```

Optionally, a tag may receive an explicit numeric value, which is implicitly
incremented for tags in sequence.

Enumerations can be used to interface with external libraries that use
constants to represent a group of related values (e.g., key symbols).

Examples:

```
enum {
    :Key-Left = `:number KEY_LEFT`, ;; explicitly associates with C enumeration
    :Key-Right,                     ;; implicitly associates with remaining
    :Key-Up,                        ;;  keys in sequence
    :Key-Down,
}
if lib-key-pressed() == :Key-Up {
    ;; lib-key-pressed is an external library
    ;; do something if key UP is pressed
}
```

### Tuple Templates

A `data` declaration associates a tag with a tuple template, which associates
tuple positions with field identifiers:

```
Temp : `data´ TAG `=´ `[´ List(ID [TAG]) `]´
```

After the keyword `data`, a declaration expects a tag followed by `=` and a
template.
A template is surrounded by brackets (`[´ and `]´) to represent the tuple, and
includes a list of identifiers, each mapping an index into a field.
Each field can be followed by a tag to represent nested templates.

Then, a [variable declaration](#variables-and-declarations) can specify a tuple
template and hold a tuple that can be accessed by field.

Examples:

```
data :Pos = [x,y]                       ;; a flat template
val pos :Pos = [10,20]                  ;; pos uses :Pos as template
println(pos.x, pos.y)                   ;; --> 10, 20

data :Dim = [w,h]
data :Rect = [pos :Pos, dim :Dim]       ;; a nested template
val r :Rect = [pos, [100,100]]          ;; r uses :Rect as template
println(r.dim, r.pos.x)                 ;; --> [100,100], 10
```

Based on [tags and sub-tags](#user-types), tuple templates can define
hierarchies and reuse fields from parents.
Templates are reused by concatenating a sub-template after its corresponding
super-templates, e.g., `:X.A [a]` with `:X [x]` becomes `:X.A [x,a]`.

Examples:

```
data :Event = [ts]                  ;; :Event [ts] is super-type of...
data :Event.Key = [key]             ;; :Event.Key [ts,key]
data :Event.Mouse = [pos :Pos]      ;; [ts, pos :Pos]
data :Event.Mouse.Motion = []       ;; [ts, pos :Pos]
data :Event.Mouse.Button = [but]    ;; [ts, pos :Pos, but]

val but :Event.Mouse.Button = [0, [10,20], 1]
val evt :Event = but
println(evt.ts, but.pos.y)          ;; --> 0, 20
```

## Calls, Operations and Indexing

### Calls and Operations

In `dyn-lex`, calls and operations are equivalent, i.e., an operation is a call
that uses an [operator](#operatos) with prefix or infix notation:

```
Call : OP Expr                      ;; unary operation
     | Expr OP Expr                 ;; binary operation
     | Expr `(´ [List(Expr)] `)´    ;; function call
```

Operations are interpreted as function calls, i.e., `x + y` is equivalent to
`{+} (x, y)`.

A call expects an expression of type [`func`](#prototypes) and an optional list
of expressions as arguments enclosed by parenthesis.
Each argument is expected to match a parameter of the function declaration.
A call transfers control to the function, which runs to completion and returns
control with a value, which substitutes the call.

As discussed in [Identifiers](#identifiers), the binary minus requires spaces
around it to prevent ambiguity with identifiers containing dashes.

Examples:

```
#vec            ;; unary operation
x - 10          ;; binary operation
{{-}}(x,10)     ;; operation as call
f(10,20)        ;; normal call
```

### Indexes and Fields

[Collections](#collections) in `dyn-lex` (tuples, vectors, and dictionaries)
are accessed through indexes or fields:

```
Index : Expr `[´ Expr `]´
Field : Expr `.´ ID
```

An index operation expects an expression as a collection, and an index enclosed
by brackets (`[` and `]`).
For tuples and vectors, the index must be an number.
For dictionaries, the index can be of any type.

The operation evaluates to the current value the collection holds on the index,
or `nil` if non existent.

A field operation expects an expression as a collection, a dot separator (`.`),
and a field identifier.
A field operation expands to an index operation as follows:
For a tuple or vector `v`, and a numeric identifier `i`, the operation expands
to `v[i]`.
For a dictionary `v`, and a [tag literal](#literals) `k` (with the colon `:`
omitted), the operation expands to `v[:k]`.

A [variable](#variables-and-declarations) associated with a
[tuple template](#tag-enumerations-and-tuple-templates) can also be indexed
using a field operation.

Examples:

```
tup[3]      ;; tuple access by index
vec[i]      ;; vector access by index
dict[:x]    ;; dict access by index
dict.x      ;; dict access by field
val t :T    ;; tuple template
t.x
```

### Precedence and Associativity

Operations in `dyn-lex` can be combined in complex expressions with the
following precedence priority (from higher to lower):

```
1. sufix  operations       ;; t[0], x.i, f(x)
2. prefix operations       ;; -x, #t
3. binary operations       ;; x + y
```

All binary operators are left-associative and have the same precedence.
Expressions with multiple operators require parenthesis for disambiguation:

```
Parens : `(´ Expr `)´
```

Examples:

```
#f(10).x        ;; # ((f(10)) .x)
x + 10 - 1      ;; ERR: requires parenthesis
- x + y         ;; (-x) + y
x or y or z     ;; (x or y) or z
```

## Conditionals and Loops

### Conditionals

`dyn-lex` supports conditionals as follows:

```
If  : `if´ Expr Block [`else´ Block]
```

An `if` tests a condition expression and executes one of the two possible
branches.
If the condition is [true](#basic-types), the `if` executes the first branch.
Otherwise, it executes the optional `else` branch, which defaults to `nil`.

Examples:

```
val v = if x>0 { x } else { -x }
```

### Loops

`dyn-lex` supports loops as follows:

```
Loop  : `loop´ Block
Break : `break´
```

A `loop` executes a block of code continuously until a matching `break` occurs.

Examples:

```
var i = 0
loop {       ;; --> 0,1,2,3,4
    if i == 5 {
        break
    } else {
        nil
    }
    println(i)
    set i = i + 1
}
```

<!-- ---------------------------------------------------------------------- -->

# STANDARD LIBRARY

The standard library provides functions and operations that are primitive in
the sense that they cannot be written in `dyn-lex` itself:

- `/=`:             [Equality Operators](#equality-operators)
- `==`:             [Equality Operators](#equality-operators)
- `and`:            [Logical Operators](#boolean-operators)
- `next`:           [Dictionary Next](#dictionary-next)
- `not`:            [Logical Operators](#boolean-operators)
- `or`:             [Logical Operators](#boolean-operators)
- `print`:          [Print](#print)
- `println`:        [Print](#print)
- `sup?`:           [Types and Tags](#types-and-tags)
- `tags`:           [Types and Tags](#types-and-tags)
- `to-number`:      [Conversions](#conversions)
- `to-string`:      [Conversions](#conversions)
- `to-tag`:         [Conversions](#conversions)
- `type`:           [Types and Tags](#types-and-tags)

## Equality Operators

```
func {{==}} (v1, v2)  ;; --> yes/no
func {{/=}} (v1, v2)  ;; --> yes/no
```

The operator `==` compares two values `v1` and `v2` and returns a boolean.
The operator `/=` is the negation of `==`.

To be considered equal, first the values must be of the same type.
In addition, [literal values](#literal-values) are compared *by value*, while
[Dynamic Values](#dynamic-values) and [Active Values](#active-values) are
compared *by reference*.
<!--
The exception are tuples, which are compared by value, i.e., they must be of
the same size, with all positions having the same value (using `==`).
-->

Examples:

```
1 == 1          ;; --> true
1 /= 1          ;; --> false
1 == '1'        ;; --> false
#[1] == #[1]    ;; --> false
[1] == [1]      ;; --> false
```

## Logical Operators

```
func not (v)
func and (v1, v2)
func or  (v1, v2)
```

The logical operators `not`, `and`, and `or` are functions with a special
syntax to be used as prefix (`not`) and infix operators (`and`,`or`).

A `not` receives a value `v` and expands as follows:

```
if v { false } else { true }
```

The operators `and` and `or` returns one of their operands `v1` or `v2`.

An `and` expands as follows:

```
do {
    val x :tmp = v1
    if x { v2 } else { x }
}
```

An `or` expands as follows:

```
do {
    val x :tmp = v1
    if x { x } else { v2 }
}
```

Examples:

```
not not nil     ;; --> false
nil or 10       ;; --> 10
10 and nil      ;; --> nil
```

## Types and Tags

```
func type (v)           ;; --> :type
func sup? (sup, sub)    ;; --> yes/no
func string-to-tag (s)  ;; --> :tag
func tags (v, t, set)   ;; --> v
func tags (v, t)        ;; --> yes/no
```

The function `type` receives a value `v` and returns its [type](#types) as one
of these tags:
    `:nil`, `:bool`, `:char`, `:number`, `:pointer`, `:tag`,
    `:tuple`, `:vector`, `:dict`,
    `:func`.

The function `sup?` receives a tag `sup`, a tag `sub`, and returns a boolean
to answer if `sup` is a [super-tag](#hierarchical-tags) of `sub`.

The function `tags` sets or queries tags associated with values of [non-basic
types](#user-types).
To set or unset a tag, the function receives a value `v`, a tag `t`, and a
boolean `set` to set or unset the tag.
The function returns the same value passed to it.
To query a tag, the function receives a value `v`, a tag `t` to check, and
returns a boolean to answer if the tag (or any sub-tag) is associated with the
value.

Examples:

```
type(10)                        ;; --> :number
val x = tags([], :x, true)      ;; value x=[] is associated with tag :x
tags(x, :x)                     ;; --> true
```

## Dictionary Next

```
func next (d, k)
```

The function `next` allows to enumerate the keys of a dictionary.
It receives a dictionary `d` and a key `k`, and returns the next key after `k`.
If `k` is `nil`, the function returns the initial key.
The function returns `nil` if there are no reamining keys to enumerate.

## Conversions

```
func to-number (v)  ;; --> number
func to-string (v)  ;; --> "string"
func to-tag (v)     ;; --> :tag
```

The conversion functions receive any value `v` and try to convert it to a value
of the specified type.
If the conversion is not possible, they return `nil`.

Examples:

```
to-number("10")     ;; --> 10
to-number([10])     ;; --> nil
to-string(10)       ;; --> "10"
to-tag(":number")   ;; --> :number
```

## Print

```
func print (...)
func println (...)
```

The functions `print` and `println` outputs the given values and return `nil`.

Examples:

```
println(1, :x, [1,2,3])     ;; --> 1   :x   [1,2,3]
sup? tags
throw type
```

# SYNTAX

```
Prog  : { Expr [`;´] }
Block : `{´ { Expr [`;´] } `}´
Expr  : `do´ Block                                      ;; explicit block
      | `pass´ Expr                                     ;; innocuous expression
      | `drop´ `(´ Expr `)´                             ;; drop expression

      | `val´ ID [TAG] [`=´ Expr]                       ;; declaration constant
      | `var´ ID [TAG] [`=´ Expr]                       ;; declaration variable
      | `set´ Expr `=´ Expr                             ;; assignment

      | `enum´ `{´ List(TAG [`=´ Expr]) `}´             ;; tags enum
      | `data´ Data                                     ;; tags templates
            Data : TAG `=´ `[´ List(ID [TAG]) `]´

      | `nil´ | `false´ | `true´                        ;; literals &
      | NAT | TAG | CHR | NUM | STR | ID | `...´        ;; identifiers
     

      | `[´ [List(Expr)] `]´                            ;; tuple
      | `#[´ [List(Expr)] `]´                           ;; vector
      | `@[´ [List(Key-Val)] `]´                        ;; dictionary
            Key-Val : ID `=´ Expr
                    | `(´ Expr `,´ Expr `)´

      | `(´ Expr `)´                                    ;; parenthesis
      | Expr `(´ [List(Expr)] `)´                       ;; pos call

      | OP Expr                                         ;; pre op
      | Expr OP Expr                                    ;; bin op
      | `not´ Expr                                      ;; op not
      | Expr (`or´|`and´) Expr          ;; op bin

      | Expr `[´ Expr `]´                               ;; pos index
      | Expr `.´ ID                                     ;; pos dict field

      | `if´ Expr Block [`else´ Block]                  ;; conditional

      | `loop´ Block                                    ;; loop
      | `break`                                         ;; loop break

      | `func´ `(´ [List(ID)] `)´ Block                 ;; function

List(x) : x { `,´ x }                                   ;; comma-separated list

ID    : [`^´|`^^´] [A-Za-z_][A-Za-z0-9_\'\?\!\-]*       ;; identifier variable (`^´ upval)
      | `{´ OP `}´                                      ;; identifier operation
TAG   : :[A-Za-z0-9\.\-]+                               ;; identifier tag
OP    : [+-*/><=!|&~%#@]+                               ;; identifier operation
CHR   : '.' | '\\.'                                     ;; literal character
NUM   : [0-9][0-9A-Za-z\.]*                             ;; literal number
NAT   : `.*`                                            ;; native expression
STR   : ".*"                                            ;; string expression
```
