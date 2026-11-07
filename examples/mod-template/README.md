# Empty CoronaPoker MOD template

Copy the contents of this directory to the `mod` directory next to the
CoronaPoker JAR. In the Windows installed edition, use `app/mod`. Edit
`mod.xml`, then add only the media branches that your MOD needs.

The `.gitkeep` files only preserve empty directories in Git. CoronaPoker
ignores them because media catalogs filter by their supported extensions. They
may be deleted from a distributed MOD.

The [MOD authoring guide](../../docs/MODS.md) covers supported resources and
the [ZIP layouts for distribution](../../docs/MODS.md#package-a-personal-mod).
