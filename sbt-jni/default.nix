{ pkgs ? import <nixpkgs> { } }:
let
  jdk = pkgs.jdk8;
  sbt = pkgs.sbt.override {
    jre = jdk;
  };
  buildInputs = [
    pkgs.gcc
    jdk
    sbt
  ];
in
{
  inherit buildInputs;
  shell = pkgs.mkShell {
    inherit buildInputs;
  };
}
