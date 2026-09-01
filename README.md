# Northstar Dimension Bridge

NeoForge 1.21.1 bridge for [Create: Northstar - Redux].

The mod watches the server's dimension registry and writes a generated data
pack containing Northstar `planet` and `planet_dimension` entries. It does not
control Multiverse or Infinite Dimensions generation.

## Filtering

- An empty whitelist means every discovered dimension is eligible.
- A non-empty whitelist limits registration to those exact dimension IDs.
- The blacklist always wins.
- `minecraft:the_nether` and `minecraft:the_end` are blacklisted by default.
- `minecraft:overworld` and `northstar:*` are reserved because Northstar
  already owns the overworld and its own dimensions.

The generated data pack is stored under the active world's `datapacks` folder
and is named `northstarbridge_generated`. New dimensions trigger an immediate,
debounced server data reload so the client receives the updated Northstar
registry. The bridge verifies the server mapping after each reload and notifies
players when a newly discovered dynamic dimension is ready for telescopes.

Configuration is generated at `config/northstarbridge-common.toml`.

[Create: Northstar - Redux]: https://modrinth.com/mod/northstar-redux
