# Changelog

## v0.2.1
* Switch to github actions
* Add nix and direnv to build
* Use scalafmt
* Update sbt to 1.3.13 and scala to 2.12.12
* Remove the flags "-I/usr/include" and "-L/usr/local/include" that were added to jniIncludes
  by default, as they were conflicting with the gcc version provided by nix.
* Add option to disable generating C header files

## v0.2.0
* Update sbt to 1.1.2

## v0.1.2
* Add Windows support

## v0.1.1
* Add scripted tests
* Migrate to AutoPlugin
