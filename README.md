# dyn-lex

`dyn-lex` is an experiment with runtime lexical memory management for objects:

- Objects are attached to lexical blocks.
- Objects cannot escape their blocks.
- Objects are deallocated with their blocks.
- Objects are still garbage collected with long-lasting blocks.
- All verification occurs at runtime.

# Manual

- https://github.com/fsantanna/dyn-lex/blob/main/doc/manual-out.md

# Install

1. Install `gcc` and `java`:

```
$ sudo apt install gcc default-jre
```

2. Install `dlex`:

```
$ wget https://github.com/fsantanna/dyn-lex/releases/download/v0.1.0/install-v0.1.0.sh
$ sh install-v0.1.0.sh ./dlex/
```

- You may want to
    - add `./dlex/` to your `PATH`
    - modify `./dlex/` to another destination

3. Execute `dlex`:

```
$ ./dlex/dlex ./dlex/hello-world.ceu
[1,2,3]
```
