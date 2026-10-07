# rev267 Twilight Forest Part 2A — JVM-default primitive static ids

Date: 2026-10-07

Twilight Forest 2.3.8 declares `idMobRovingCube` as a static int, uses it in one
`registerTFCreature` call, and contains no `PUTSTATIC` for that field anywhere in the source JAR.
Under normal JVM semantics its value is therefore the static primitive default, integer 0.

The generic lifecycle resolver now accepts a JVM static default only when:

- the field is declared in a readable source class;
- it is actually static;
- the descriptor is primitive;
- the entire source JAR contains no explicit `PUTSTATIC` to that exact owner/name/descriptor.

Object/reference defaults are not materialized. If any source write exists, the ordinary
reachable-assignment proof applies instead and dynamic/conflicting writes remain unresolved.

For Twilight Forest entity registration this closes the source numeric-id set:

- 57 registered creature ids from explicit reachable literal `idMob*` assignments;
- 1 registered RovingCube id from JVM default int 0;
- 18 vehicle/projectile ids from literal `idVehicleSpawn*` assignments in `<clinit>`;
- HydraHead uses direct literal id 11.

Total source `registerModEntity` registrations: 77.

`idMobBoggard` and `idMobNagaSegment` have source values but are not among the 58
`registerTFCreature` callsites in this corpus.
