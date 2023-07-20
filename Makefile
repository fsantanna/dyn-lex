install:
	mkdir -p $(DIR)
	cp out/artifacts/dlex_jar/dlex.jar $(DIR)/dlex.jar
	cp build/dlex.sh $(DIR)/dlex
	cp build/prelude.ceu $(DIR)/prelude.ceu
	ls -l $(DIR)/
	$(DIR)/dlex --version
	$(DIR)/dlex build/hello-world.ceu
