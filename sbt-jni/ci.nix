{ pkgs ? import <nixpkgs> { } }:
let default = (import ./default.nix { }); in
pkgs.mkShell
{
  buildInputs = default.buildInputs ++ [ pkgs.glibc ];
  GIT_SSL_CAINFO = /etc/ssl/certs/ca-certificates.crt;
  SSL_CERT_FILE = /etc/ssl/certs/ca-certificates.crt;
}
