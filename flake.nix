{
  inputs = {
    devenv.url = "github:cachix/devenv";
    flake-parts.url = "github:hercules-ci/flake-parts";
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    systems.url = "github:nix-systems/default";
  };

  outputs = { self, nixpkgs, devenv, systems, flake-parts, ... }@inputs:
    flake-parts.lib.mkFlake { inherit inputs; } {

      imports = [ inputs.devenv.flakeModule ];

      systems = nixpkgs.lib.systems.flakeExposed;

      perSystem = { config, self', inputs', pkgs, system, ... }: {

        devenv.shells.default = {

          packages = with pkgs; [ nodePackages.live-server ];

          languages = {
            java.enable = true;
            java.jdk.package = pkgs.jdk;
            scala.enable = true;
            nix.enable = true;
          };

          scripts.watch-examples.exec = ''
            sbt '~examples/fastLinkJS'
          '';

          scripts.serve-examples.exec = ''
            live-server ./examples/ --port=8080 --entry-file=index.html
          '';

        };

      };

    };
}

