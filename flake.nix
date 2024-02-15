{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    systems.url = "github:nix-systems/default";
    devenv.url = "github:cachix/devenv";
  };

  nixConfig = {
    extra-trusted-public-keys =
      "devenv.cachix.org-1:w1cLUi8dv3hnoSPGAuibQv+f9TZLr6cv/Hm9XgU50cw=";
    extra-substituters = "https://devenv.cachix.org";
  };

  outputs = { self, nixpkgs, devenv, systems, ... }@inputs:
    let forEachSystem = nixpkgs.lib.genAttrs (import systems);
    in {
      packages = forEachSystem (system: {
        devenv-up = self.devShells.${system}.default.config.procfileScript;
      });

      devShells = forEachSystem (system:
        let pkgs = nixpkgs.legacyPackages.${system};
        in {
          default = devenv.lib.mkShell {
            inherit inputs pkgs;
            modules = [{
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

            }];
          };
        });
    };
}
