install:
	cp out/artifacts/dlex_jar/dlex.jar dlex.jar
	cp build/dlex.sh dlex
	cp build/prelude.ceu prelude.ceu
	./dlex --version
	./dlex build/hello-world.ceu
